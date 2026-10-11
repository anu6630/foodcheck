package com.foodcheck.data.repositories

import com.foodcheck.data.datasources.ComparisonLocalStore
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.dto.ComparisonDraft
import com.foodcheck.data.dto.ComparisonDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Saved comparisons. The list (stored counts only) is cached on disk and revalidated;
 * a detail is always fetched fresh because the server re-checks labels against current rules.
 */
class ComparisonRepository(
    private val network: NetworkDataSource,
    private val local: ComparisonLocalStore,
    private val shelf: ShelfRepository,
) {
    private val _items = MutableStateFlow<List<ComparisonDto>>(emptyList())
    val items: StateFlow<List<ComparisonDto>> = _items.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun hydrateFromDisk() {
        if (_items.value.isEmpty()) local.load()?.let { _items.value = it }
    }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        try {
            val remote = network.listComparisons().comparisons
            _items.value = remote
            local.save(remote)
            _lastError.value = null
        } catch (e: Exception) {
            if (_items.value.isEmpty()) _lastError.value = e.message ?: "Could not load comparisons"
        } finally {
            refreshMutex.unlock()
        }
    }

    suspend fun detail(id: Int): ComparisonDto = network.getComparison(id)

    /** Returns the new id. Refreshes My list too when the pick was added there. */
    suspend fun save(draft: ComparisonDraft): Int {
        val id = network.createComparison(draft).id
        refresh()
        if (draft.addChosenToShelf) shelf.invalidateAndRefresh()
        return id
    }

    suspend fun updateDecision(
        id: Int,
        decision: String,
        chosenSlot: Int?,
        reasons: List<String>,
        note: String?,
    ) {
        network.updateComparisonDecision(id, decision, chosenSlot, reasons, note)
        _items.value = _items.value.map {
            if (it.id == id) it.copy(decision = decision, chosenSlot = chosenSlot, reasons = reasons, note = note) else it
        }
        local.save(_items.value)
    }

    suspend fun delete(id: Int) {
        network.deleteComparison(id)
        _items.value = _items.value.filterNot { it.id == id }
        local.save(_items.value)
    }

    suspend fun photo(id: Int, slot: Int, kind: String): ByteArray? = network.comparisonPhoto(id, slot, kind)

    suspend fun shelfPhoto(shelfItemId: Int): ByteArray? = network.shelfPhoto(shelfItemId)
}
