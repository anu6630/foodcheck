package com.foodcheck.domain.usecases

import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.repositories.IProductRepository

class GetProductDetailsUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(barcode: String): Result<SearchProductResult?> {
        if (barcode.isBlank()) return Result.failure(IllegalArgumentException("Barcode cannot be empty"))
        return repository.getProductDetails(barcode)
    }
}
