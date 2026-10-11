package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.dto.ComparisonDto
import com.foodcheck.data.repositories.ComparisonRepository
import com.foodcheck.domain.entities.ComparisonMatrix
import com.foodcheck.domain.entities.buildComparisonMatrix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComparisonDetailState(
    val loading: Boolean = true,
    val detail: ComparisonDto? = null,
    val matrix: ComparisonMatrix? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

/** Saved comparisons: My list › Comparisons and the saved-comparison screen. */
class ComparisonsViewModel(
    private val repository: ComparisonRepository,
) : ViewModel() {
    val items: StateFlow<List<ComparisonDto>> = repository.items
    val error: StateFlow<String?> = repository.lastError

    private val _detail = MutableStateFlow(ComparisonDetailState())
    val detail: StateFlow<ComparisonDetailState> = _detail.asStateFlow()

    fun load() {
        viewModelScope.launch {
            repository.hydrateFromDisk()
            repository.refresh()
        }
    }

    fun open(id: Int) {
        if (_detail.value.detail?.id != id) _detail.value = ComparisonDetailState()
        viewModelScope.launch {
            runCatching { repository.detail(id) }
                .onSuccess { d ->
                    val labels = d.items.sortedBy { it.slot }.map { it.ingredients }
                    _detail.value = ComparisonDetailState(
                        loading = false,
                        detail = d,
                        matrix = if (labels.size >= COMPARE_MIN_READY) buildComparisonMatrix(labels) else null,
                    )
                }
                .onFailure { e ->
                    _detail.value = _detail.value.copy(loading = false, error = e.message ?: "Could not load")
                }
        }
    }

    fun updateDecision(decision: CompareDecision) {
        val current = _detail.value.detail ?: return
        _detail.value = _detail.value.copy(busy = true, message = null)
        viewModelScope.launch {
            runCatching {
                repository.updateDecision(
                    current.id,
                    decision.decision,
                    decision.chosenSlot,
                    decision.reasons,
                    decision.note?.trim()?.ifBlank { null },
                )
            }.onSuccess {
                _detail.value = _detail.value.copy(
                    busy = false,
                    message = "Saved",
                    detail = current.copy(
                        decision = decision.decision,
                        chosenSlot = decision.chosenSlot,
                        reasons = decision.reasons,
                        note = decision.note?.trim()?.ifBlank { null },
                    ),
                )
            }.onFailure { e ->
                _detail.value = _detail.value.copy(busy = false, message = e.message ?: "Could not save")
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = _detail.value.detail ?: return
        _detail.value = _detail.value.copy(busy = true, message = null)
        viewModelScope.launch {
            runCatching { repository.delete(current.id) }
                .onSuccess {
                    _detail.value = ComparisonDetailState()
                    onDeleted()
                }
                .onFailure { e ->
                    _detail.value = _detail.value.copy(busy = false, message = e.message ?: "Could not delete")
                }
        }
    }

    fun clearMessage() {
        _detail.value = _detail.value.copy(message = null)
    }

    suspend fun photo(photos: SlotPhotos, kind: String): ByteArray? = when (photos) {
        is SlotPhotos.Captured -> if (kind == "front") photos.front else photos.label
        is SlotPhotos.Shelf -> if (kind == "label") runCatching { repository.shelfPhoto(photos.shelfItemId) }.getOrNull() else null
        is SlotPhotos.Saved -> {
            val has = if (kind == "front") photos.hasFront else photos.hasLabel
            if (has) runCatching { repository.photo(photos.comparisonId, photos.slot, kind) }.getOrNull() else null
        }
    }
}
