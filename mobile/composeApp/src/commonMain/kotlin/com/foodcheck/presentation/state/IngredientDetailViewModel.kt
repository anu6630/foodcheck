package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.usecases.GetIngredientDetailsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IngredientDetailViewModel(
    private val getIngredientDetailsUseCase: GetIngredientDetailsUseCase
) : ViewModel() {

    private val _detail = MutableStateFlow<IngredientDetail?>(null)
    val detail: StateFlow<IngredientDetail?> = _detail.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedId: String? = null

    fun load(ingredientId: String) {
        if (loadedId == ingredientId) return
        loadedId = ingredientId
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            getIngredientDetailsUseCase(ingredientId)
                .onSuccess {
                    _detail.value = it
                    _loading.value = false
                }
                .onFailure {
                    _error.value = it.message ?: "Failed to get ingredient details"
                    _loading.value = false
                }
        }
    }
}
