package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.usecases.SearchProductsUseCase
import com.foodcheck.domain.usecases.GetProductDetailsUseCase
import com.foodcheck.domain.usecases.ScanImageUseCase
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val searchProductsUseCase: SearchProductsUseCase,
    private val getProductDetailsUseCase: GetProductDetailsUseCase,
    private val scanImageUseCase: ScanImageUseCase
) : ViewModel() {

    private val _selectedCountry = MutableStateFlow("IN")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchProductResult>>(emptyList())
    val searchResults: StateFlow<List<SearchProductResult>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedProduct = MutableStateFlow<SearchProductResult?>(null)
    val selectedProduct: StateFlow<SearchProductResult?> = _selectedProduct.asStateFlow()

    private val _detailsLoading = MutableStateFlow(false)
    val detailsLoading: StateFlow<Boolean> = _detailsLoading.asStateFlow()

    private val _detailsError = MutableStateFlow<String?>(null)
    val detailsError: StateFlow<String?> = _detailsError.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    fun setCountry(country: String) {
        _selectedCountry.value = country
        if (_searchQuery.value.isNotBlank()) {
            searchProducts(_searchQuery.value)
        }
        _selectedProduct.value?.product?.barcode?.let {
            getProductDetails(it)
        }
    }

    fun searchProducts(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            val countries = listOf(_selectedCountry.value)
            searchProductsUseCase(query, countries)
                .onSuccess {
                    _searchResults.value = it
                    _searchLoading.value = false
                }
                .onFailure {
                    _searchError.value = it.message ?: "Failed to search products"
                    _searchLoading.value = false
                }
        }
    }

    fun getProductDetails(barcode: String) {
        viewModelScope.launch {
            _detailsLoading.value = true
            _detailsError.value = null
            val countries = listOf(_selectedCountry.value)
            getProductDetailsUseCase(barcode, countries)
                .onSuccess {
                    _selectedProduct.value = it
                    _detailsLoading.value = false
                }
                .onFailure {
                    _detailsError.value = it.message ?: "Failed to get product details"
                    _detailsLoading.value = false
                }
        }
    }

    fun scanImage(imageBytes: ByteArray) {
        viewModelScope.launch {
            _scanLoading.value = true
            _scanError.value = null
            _scanResult.value = null
            val countries = listOf(_selectedCountry.value)
            scanImageUseCase(imageBytes, countries)
                .onSuccess {
                    _scanResult.value = it
                    _scanLoading.value = false
                }
                .onFailure {
                    _scanError.value = it.message ?: "Scan failed"
                    _scanLoading.value = false
                }
        }
    }

    fun clearDetails() {
        _selectedProduct.value = null
    }

    fun clearScan() {
        _scanResult.value = null
        _scanError.value = null
    }
}
