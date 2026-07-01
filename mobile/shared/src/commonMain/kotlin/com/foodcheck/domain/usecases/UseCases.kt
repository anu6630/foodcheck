package com.foodcheck.domain.usecases

import com.foodcheck.domain.repositories.IProductRepository
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult

class SearchProductsUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(query: String, countries: List<String>): Result<List<SearchProductResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchProducts(query, countries)
    }
}

class GetProductDetailsUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(barcode: String, countries: List<String>): Result<SearchProductResult?> {
        if (barcode.isBlank()) return Result.failure(IllegalArgumentException("Barcode cannot be empty"))
        return repository.getProductDetails(barcode, countries)
    }
}

class ScanImageUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(imageBytes: ByteArray, countries: List<String>): Result<ScanResult> {
        if (imageBytes.isEmpty()) return Result.failure(IllegalArgumentException("Image bytes cannot be empty"))
        return repository.scanImage(imageBytes, countries)
    }
}
