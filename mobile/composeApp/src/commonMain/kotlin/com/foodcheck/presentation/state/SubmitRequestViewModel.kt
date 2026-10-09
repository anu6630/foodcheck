package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.usecases.SubmitIngredientRequestUseCase
import com.foodcheck.domain.usecases.SubmitProductRequestUseCase
import com.foodcheck.domain.usecases.UnifiedSearchUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubmitRequestState {
    data object Idle : SubmitRequestState
    data object Loading : SubmitRequestState
    data class Success(val requestId: Int, val status: String) : SubmitRequestState
    data class Error(val message: String) : SubmitRequestState
}

class SubmitRequestViewModel(
    private val submitProductRequestUseCase: SubmitProductRequestUseCase,
    private val submitIngredientRequestUseCase: SubmitIngredientRequestUseCase,
    private val unifiedSearchUseCase: UnifiedSearchUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<SubmitRequestState>(SubmitRequestState.Idle)
    val state: StateFlow<SubmitRequestState> = _state.asStateFlow()

    // Real-time ingredient search suggestions state
    private val _ingredientSuggestions = MutableStateFlow<List<AnnotatedIngredient>>(emptyList())
    val ingredientSuggestions: StateFlow<List<AnnotatedIngredient>> = _ingredientSuggestions.asStateFlow()

    private val _isSearchingIngredients = MutableStateFlow(false)
    val isSearchingIngredients: StateFlow<Boolean> = _isSearchingIngredients.asStateFlow()

    private var searchJob: Job? = null

    fun searchIngredientRealtime(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _ingredientSuggestions.value = emptyList()
            _isSearchingIngredients.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearchingIngredients.value = true
            delay(300) // 300ms debounce
            unifiedSearchUseCase(trimmed)
                .onSuccess { result ->
                    _ingredientSuggestions.value = result.ingredients
                }
                .onFailure {
                    _ingredientSuggestions.value = emptyList()
                }
            _isSearchingIngredients.value = false
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        _ingredientSuggestions.value = emptyList()
        _isSearchingIngredients.value = false
    }

    fun submitProduct(
        productName: String,
        countryCode: String,
        brand: String?,
        barcode: String?,
        category: String?,
        ingredientsText: String?,
        notes: String?,
        images: List<ByteArray>,
    ) {
        viewModelScope.launch {
            _state.value = SubmitRequestState.Loading
            submitProductRequestUseCase(
                productName = productName,
                countryCode = countryCode,
                brand = brand?.takeIf { it.isNotBlank() },
                barcode = barcode?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                ingredientsText = ingredientsText?.takeIf { it.isNotBlank() },
                requesterNotes = notes?.takeIf { it.isNotBlank() },
                images = images,
            )
                .onSuccess { (id, status) -> _state.value = SubmitRequestState.Success(id, status) }
                .onFailure { _state.value = SubmitRequestState.Error(it.message ?: "Submission failed") }
        }
    }

    fun submitIngredient(
        ingredientName: String,
        eNumber: String?,
        category: String?,
        notes: String?,
    ) {
        viewModelScope.launch {
            _state.value = SubmitRequestState.Loading
            submitIngredientRequestUseCase(
                ingredientName = ingredientName,
                eNumber = eNumber?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                requesterNotes = notes?.takeIf { it.isNotBlank() },
            )
                .onSuccess { (id, status) -> _state.value = SubmitRequestState.Success(id, status) }
                .onFailure { _state.value = SubmitRequestState.Error(it.message ?: "Submission failed") }
        }
    }

    fun resetState() {
        _state.value = SubmitRequestState.Idle
        clearSuggestions()
    }
}
