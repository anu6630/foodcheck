package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.repositories.ShelfRepository
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.UnifiedSearchResult
import com.foodcheck.domain.usecases.UnifiedSearchUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val unifiedSearchUseCase: UnifiedSearchUseCase,
    private val shelfRepository: ShelfRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(UnifiedSearchResult())
    val results: StateFlow<UnifiedSearchResult> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _shelf = MutableStateFlow<List<ShelfItemDto>>(emptyList())
    val shelf: StateFlow<List<ShelfItemDto>> = _shelf.asStateFlow()

    init {
        // collectLatest cancels the in-flight request for a stale keystroke as soon as a newer
        // one arrives, so a slow response to an earlier partial query (e.g. "s") can never land
        // after and overwrite the result of a later, more specific one (e.g. "sodium").
        viewModelScope.launch {
            _query
                .debounce(250)
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _results.value = UnifiedSearchResult()
                        _shelf.value = emptyList()
                        _error.value = null
                        com.foodcheck.analytics.getAnalyticsTracker().logSearchCleared()
                        return@collectLatest
                    }
                    _loading.value = true
                    _error.value = null
                    unifiedSearchUseCase(q)
                        .onSuccess { _results.value = it }
                        .onFailure { _error.value = it.message ?: "Failed to search" }
                    if (shelfRepository.items.value.isEmpty()) {
                        runCatching {
                            shelfRepository.hydrateFromDisk()
                            shelfRepository.refresh()
                        }
                    }
                    val matchedIngredientIds = buildSet {
                        fun collectIds(ing: AnnotatedIngredient) {
                            ing.id?.let { add(it) }
                            ing.eNumber?.let { add(it) }
                            ing.codes.forEach { add(it.code) }
                            ing.members.forEach { collectIds(it) }
                        }
                        _results.value.ingredients.forEach { collectIds(it) }
                    }
                    val filteredShelf = shelfRepository.filtered(q, matchedIngredientIds)
                    _shelf.value = filteredShelf
                    com.foodcheck.analytics.getAnalyticsTracker().logSearchPerformed(
                        query = q,
                        resultCount = _results.value.ingredients.size,
                        shelfCount = filteredShelf.size
                    )
                    _loading.value = false
                }
        }
        viewModelScope.launch {
            shelfRepository.items.collect {
                val q = _query.value
                if (q.isNotBlank()) {
                    val matchedIngredientIds = buildSet {
                        fun collectIds(ing: AnnotatedIngredient) {
                            ing.id?.let { add(it) }
                            ing.eNumber?.let { add(it) }
                            ing.codes.forEach { add(it.code) }
                            ing.members.forEach { collectIds(it) }
                        }
                        _results.value.ingredients.forEach { collectIds(it) }
                    }
                    _shelf.value = shelfRepository.filtered(q, matchedIngredientIds)
                }
            }
        }
    }

    fun search(query: String) {
        _query.value = query
    }
}
