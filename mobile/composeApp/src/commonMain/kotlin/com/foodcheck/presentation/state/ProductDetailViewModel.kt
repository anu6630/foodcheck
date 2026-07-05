package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.usecases.GetProductDetailsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    private val getProductDetailsUseCase: GetProductDetailsUseCase
) : ViewModel() {

    private val _product = MutableStateFlow<SearchProductResult?>(null)
    val product: StateFlow<SearchProductResult?> = _product.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedBarcode: String? = null

    fun load(barcode: String) {
        if (loadedBarcode == barcode) return // avoid re-fetching on recomposition
        loadedBarcode = barcode
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            getProductDetailsUseCase(barcode)
                .onSuccess {
                    _product.value = it
                    _loading.value = false
                }
                .onFailure {
                    _error.value = it.message ?: "Failed to get product details"
                    _loading.value = false
                }
        }
    }
}
