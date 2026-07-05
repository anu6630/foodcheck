package com.foodcheck.domain.usecases

import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.repositories.IProductRepository

class SearchProductsUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(query: String): Result<List<SearchProductResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchProducts(query)
    }
}
