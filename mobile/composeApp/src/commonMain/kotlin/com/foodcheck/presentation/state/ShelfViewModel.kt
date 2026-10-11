package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.data.repositories.ShelfRepository
import com.foodcheck.domain.entities.ShelfStance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShelfViewModel(
    private val shelfRepository: ShelfRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val items: StateFlow<List<ShelfItemDto>> = combine(
        shelfRepository.items,
        _query,
    ) { _, q ->
        shelfRepository.filtered(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncing: StateFlow<Boolean> = shelfRepository.syncing
    val error: StateFlow<String?> = shelfRepository.lastError

    init {
        viewModelScope.launch { shelfRepository.hydrateFromDisk() }
    }

    fun load(q: String = _query.value) {
        _query.value = q
        viewModelScope.launch {
            shelfRepository.hydrateFromDisk()
            shelfRepository.refresh()
        }
    }

    /** Filters the local list only (no network). */
    fun setQuery(q: String) {
        _query.value = q
    }

    fun updateStance(id: Int, stance: ShelfStance) {
        viewModelScope.launch {
            shelfRepository.updateStance(id, stance.apiValue)
        }
    }
}
