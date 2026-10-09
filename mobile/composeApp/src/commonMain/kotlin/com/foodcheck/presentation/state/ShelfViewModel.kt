package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.dto.ShelfItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShelfViewModel(private val network: NetworkDataSource) : ViewModel() {
    private val _items = MutableStateFlow<List<ShelfItemDto>>(emptyList())
    val items = _items.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load(q: String = _query.value) {
        _query.value = q
        viewModelScope.launch {
            try {
                _items.value = network.listShelf(q).items
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
