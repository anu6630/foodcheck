package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.dto.ComparisonDraft
import com.foodcheck.data.dto.ComparisonDto
import com.foodcheck.data.dto.ComparisonItemDraft
import com.foodcheck.data.dto.ComparisonItemDto
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.repositories.ComparisonRepository
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ComparisonMatrix
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.assignPhotos
import com.foodcheck.domain.entities.buildComparisonMatrix
import com.foodcheck.domain.entities.comparisonKey
import com.foodcheck.domain.entities.flattenForComparison
import com.foodcheck.domain.usecases.ScanUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val COMPARE_SLOT_COUNT = 3
const val COMPARE_MIN_READY = 2

/** Same values the scan agent may suggest (backend SCAN_CATEGORIES). */
val COMPARE_CATEGORIES = listOf(
    "Biscuits", "Noodles", "Chips & snacks", "Drinks", "Health drink",
    "Chocolate", "Sweets & candy", "Breakfast cereal", "Baby food", "Sauces", "Other",
)

fun slotLetter(slot: Int): String = ('A' + slot).toString()

/** Packs print names in capitals ("FRUITY JELLY CUBES"); show them as "Fruity Jelly Cubes". */
internal fun readableCase(name: String): String {
    val letters = name.filter { it.isLetter() }
    if (letters.length < 4 || letters.any { it.isLowerCase() }) return name
    return name.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

/** Where a slot's photos live: in memory (new read) or on the server (reused label). */
sealed interface SlotPhotos {
    data class Captured(val front: ByteArray?, val label: ByteArray?) : SlotPhotos
    data class Shelf(val shelfItemId: Int) : SlotPhotos
    data class Saved(val comparisonId: Int, val slot: Int, val hasFront: Boolean, val hasLabel: Boolean) : SlotPhotos
}

/**
 * One product slot. Transitions:
 * Empty → Reading → Ready | Failed(FrontOnly | NoIngredients | Network); Empty → Loading → Ready | Failed;
 * Failed → Reading (add/retake photo, retry) or Empty (remove). Compare is allowed only with ≥2 Ready
 * slots and nothing Reading, Loading or Failed.
 */
sealed interface CompareSlot {
    data object Empty : CompareSlot
    /** Photos in capture order (a front-only fix adds the label); the scan agent works out which shows what. */
    data class Reading(val photos: List<ByteArray>) : CompareSlot
    data class Loading(val name: String) : CompareSlot
    data class Ready(
        val name: String,
        val brand: String?,
        val ingredients: List<AnnotatedIngredient>,
        val photos: SlotPhotos,
        /** Reused label read; null for a new read that still needs uploading. */
        val scanId: Int?,
        val fromMyList: Boolean,
        val labelDate: String?,
        /** The agent saw a cut-off or partly unreadable list — a non-blocking warning. */
        val maybeIncomplete: Boolean = false,
    ) : CompareSlot
    data class Failed(
        val reason: FailReason,
        val message: String,
        val photos: List<ByteArray>,
        /** Kept when the user fixes the slot with a new ingredients photo. */
        val front: ByteArray?,
    ) : CompareSlot

    enum class FailReason { FrontOnly, NoIngredients, Network }
}

data class CompareUiState(
    val slots: List<CompareSlot> = List(COMPARE_SLOT_COUNT) { CompareSlot.Empty },
    val category: String? = null,
    /** True once the user picked a category; the agent's guess never overrides it. */
    val categoryChosen: Boolean = false,
    /** Carried over from a saved comparison ("Swap one"); Compare no longer asks for it. */
    val tags: Set<String> = emptySet(),
    val saving: Boolean = false,
    val message: String? = null,
) {
    val readySlots: List<Int> get() = slots.indices.filter { slots[it] is CompareSlot.Ready }
    val busy: Boolean get() = slots.any { it is CompareSlot.Reading || it is CompareSlot.Loading }
    val hasErrors: Boolean get() = slots.any { it is CompareSlot.Failed }
    val canCompare: Boolean get() = readySlots.size >= COMPARE_MIN_READY && !busy && !hasErrors
    val usedScanIds: Set<Int> get() = slots.mapNotNull { (it as? CompareSlot.Ready)?.scanId }.toSet()
    /** Products already in the tray (being read, ready or needing a fix). */
    val filledCount: Int get() = slots.count { it != CompareSlot.Empty }
    /** Where Gallery / My list adds go; null when all slots are taken. */
    val nextEmptySlot: Int? get() = slots.indexOfFirst { it == CompareSlot.Empty }.takeIf { it >= 0 }
}

data class CompareDecision(
    /** "picked" | "none" | "undecided" */
    val decision: String,
    val chosenSlot: Int?,
    val reasons: List<String>,
    val note: String?,
    val addChosenToShelf: Boolean,
)

class CompareViewModel(
    private val scanUseCase: ScanUseCase,
    private val comparisons: ComparisonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    /** Grouped table for the Ready slots, in slot order. */
    val matrix: StateFlow<ComparisonMatrix?> = _state
        .map { s ->
            val ready = s.readySlots.map { (s.slots[it] as CompareSlot.Ready).ingredients }
            if (ready.size >= COMPARE_MIN_READY) buildComparisonMatrix(ready) else null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val jobs = HashMap<Int, Job>()

    /** Reads a new photo straight away; a front-only photo comes back as [CompareSlot.FailReason.FrontOnly]. */
    fun readPhoto(slot: Int, bytes: ByteArray) = read(slot, listOf(bytes))

    /** Fix a failed slot with a new ingredients photo, keeping its front photo if any. */
    fun fixWithPhoto(slot: Int, bytes: ByteArray) {
        val failed = _state.value.slots[slot] as? CompareSlot.Failed ?: return
        read(slot, listOfNotNull(failed.front, bytes))
    }

    fun retry(slot: Int) {
        val failed = _state.value.slots[slot] as? CompareSlot.Failed ?: return
        if (failed.photos.isNotEmpty()) read(slot, failed.photos)
    }

    private fun read(slot: Int, photos: List<ByteArray>) {
        jobs.remove(slot)?.cancel()
        setSlot(slot, CompareSlot.Reading(photos))
        jobs[slot] = viewModelScope.launch {
            scanUseCase(photos)
                .onSuccess { outcome -> setSlot(slot, slotFromOutcome(slot, photos, outcome)) }
                .onFailure { e ->
                    setSlot(
                        slot,
                        CompareSlot.Failed(
                            CompareSlot.FailReason.Network,
                            e.message ?: "Could not read the label",
                            photos,
                            front = null,
                        ),
                    )
                }
        }
    }

    private fun slotFromOutcome(slot: Int, photos: List<ByteArray>, outcome: ScanOutcome): CompareSlot {
        val detected = outcome.detected
        val assigned = detected.assignPhotos(photos)
        val ingredients = outcome.labelIngredients().flattenForComparison()
        if (ingredients.isEmpty()) {
            return if (detected.frontOnly) {
                CompareSlot.Failed(
                    CompareSlot.FailReason.FrontOnly,
                    "Only the front is visible",
                    photos,
                    front = assigned.front ?: photos.first(),
                )
            } else {
                CompareSlot.Failed(CompareSlot.FailReason.NoIngredients, "No ingredients found", photos, assigned.front)
            }
        }
        suggestCategory(detected.category)
        return CompareSlot.Ready(
            name = outcome.suggestedName()?.let(::readableCase) ?: "Product ${slotLetter(slot)}",
            brand = outcome.suggestedBrand()?.let(::readableCase),
            ingredients = ingredients,
            // Roles can disagree with a successful read; the last photo is then the label.
            photos = SlotPhotos.Captured(front = assigned.front, label = assigned.label ?: photos.last()),
            scanId = null,
            fromMyList = false,
            labelDate = null,
            maybeIncomplete = detected.ingredientsComplete == false,
        )
    }

    private fun suggestCategory(category: String?) {
        if (category == null || category !in COMPARE_CATEGORIES) return
        _state.update { if (it.categoryChosen || it.category != null) it else it.copy(category = category) }
    }

    fun fillFromShelf(slot: Int, item: ShelfItemDto) {
        jobs.remove(slot)?.cancel()
        setSlot(
            slot,
            CompareSlot.Ready(
                name = item.displayName,
                brand = item.brand,
                ingredients = item.ingredients.flattenForComparison(),
                photos = SlotPhotos.Shelf(item.id),
                scanId = item.scanId,
                fromMyList = true,
                labelDate = item.createdAt,
            ),
        )
    }

    /** Products inside a saved comparison need a detail fetch for their ingredients. */
    fun fillFromSavedComparison(slot: Int, comparisonId: Int, item: ComparisonItemDto) {
        jobs.remove(slot)?.cancel()
        setSlot(slot, CompareSlot.Loading(item.displayName))
        jobs[slot] = viewModelScope.launch {
            runCatching { comparisons.detail(comparisonId) }
                .onSuccess { detail ->
                    val fresh = detail.items.firstOrNull { it.slot == item.slot }
                    setSlot(
                        slot,
                        if (fresh == null || fresh.ingredients.isEmpty()) {
                            CompareSlot.Failed(CompareSlot.FailReason.NoIngredients, "No ingredients saved", emptyList(), null)
                        } else {
                            readyFromSaved(comparisonId, fresh)
                        },
                    )
                }
                .onFailure { e ->
                    setSlot(
                        slot,
                        CompareSlot.Failed(CompareSlot.FailReason.Network, e.message ?: "Could not load", emptyList(), null),
                    )
                }
        }
    }

    private fun readyFromSaved(comparisonId: Int, item: ComparisonItemDto) = CompareSlot.Ready(
        name = item.displayName,
        brand = item.brand,
        ingredients = item.ingredients.flattenForComparison(),
        photos = SlotPhotos.Saved(comparisonId, item.slot, item.hasFront, item.hasLabel),
        scanId = item.scanId,
        fromMyList = true,
        labelDate = item.labelDate,
    )

    /**
     * "Add to compare" from a scan result: reuses that read (no new label call) in [slot], or the
     * next free slot, keeping what is already in the tray. Does nothing if the tray already holds
     * the same label or is full and no [slot] was given.
     */
    fun addFromScan(images: List<ByteArray>, outcome: ScanOutcome, slot: Int? = null) {
        if (images.isEmpty() || containsLabel(outcome)) return
        val target = slot ?: _state.value.nextEmptySlot ?: return
        jobs.remove(target)?.cancel()
        val read = slotFromOutcome(target, images, outcome)
        if (read is CompareSlot.Ready) setSlot(target, read)
    }

    /** True when a slot already holds this scan's ingredients (same pack scanned twice). */
    fun containsLabel(outcome: ScanOutcome): Boolean {
        val keys = outcome.labelIngredients().flattenForComparison().map(::comparisonKey)
        val s = _state.value
        return keys.isNotEmpty() && s.readySlots.any { (s.slots[it] as CompareSlot.Ready).ingredients.map(::comparisonKey) == keys }
    }

    fun startFromShelf(item: ShelfItemDto) {
        reset()
        fillFromShelf(0, item)
    }

    /** "Redo with one swapped": a saved comparison's products become the new session. */
    fun startFromSaved(detail: ComparisonDto) {
        reset()
        _state.update { s ->
            s.copy(
                category = detail.category,
                categoryChosen = detail.category != null,
                tags = detail.tags.toSet(),
                slots = List(COMPARE_SLOT_COUNT) { slot ->
                    detail.items.firstOrNull { it.slot == slot && it.ingredients.isNotEmpty() }
                        ?.let { readyFromSaved(detail.id, it) }
                        ?: CompareSlot.Empty
                },
            )
        }
    }

    fun remove(slot: Int) {
        jobs.remove(slot)?.cancel()
        setSlot(slot, CompareSlot.Empty)
    }

    fun rename(slot: Int, name: String, brand: String?) {
        val ready = _state.value.slots[slot] as? CompareSlot.Ready ?: return
        setSlot(
            slot,
            ready.copy(
                name = name.trim().ifBlank { "Product ${slotLetter(slot)}" },
                brand = brand?.trim()?.ifBlank { null },
            ),
        )
    }

    fun setCategory(category: String?) = _state.update { it.copy(category = category, categoryChosen = true) }

    /** Same product twice (identical label) is almost always a mis-tap. */
    fun duplicateOf(slot: Int): Int? {
        val s = _state.value
        val mine = (s.slots[slot] as? CompareSlot.Ready)?.ingredients?.map(::comparisonKey) ?: return null
        return s.readySlots.firstOrNull { other ->
            other < slot && (s.slots[other] as CompareSlot.Ready).ingredients.map(::comparisonKey) == mine
        }
    }

    /** [onSaved] gets the new id and whether this is the user's first saved comparison. */
    fun save(decision: CompareDecision, onSaved: (id: Int, first: Boolean) -> Unit) {
        val s = _state.value
        if (s.saving || !s.canCompare) return
        val draft = ComparisonDraft(
            category = s.category,
            tags = s.tags.toList(),
            decision = decision.decision,
            chosenSlot = decision.chosenSlot,
            reasons = decision.reasons,
            note = decision.note?.trim()?.ifBlank { null },
            addChosenToShelf = decision.addChosenToShelf,
            items = s.readySlots.map { slot ->
                val r = s.slots[slot] as CompareSlot.Ready
                val captured = r.photos as? SlotPhotos.Captured
                ComparisonItemDraft(
                    slot = slot,
                    displayName = r.name,
                    brand = r.brand,
                    scanId = r.scanId,
                    snapshot = r.ingredients,
                    labelImage = captured?.label,
                    frontImage = captured?.front,
                )
            },
        )
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            runCatching { comparisons.save(draft) }
                .onSuccess { id ->
                    reset()
                    // The repository has refreshed, so the new one is already in the list.
                    onSaved(id, comparisons.items.value.size <= 1)
                }
                .onFailure { e ->
                    // Session is kept so the user can retry without re-reading labels.
                    _state.update { it.copy(saving = false, message = e.message ?: "Could not save") }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun reset() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _state.value = CompareUiState()
    }

    private fun setSlot(slot: Int, value: CompareSlot) {
        _state.update { s -> s.copy(slots = s.slots.toMutableList().also { it[slot] = value }) }
    }
}
