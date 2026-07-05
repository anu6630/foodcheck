package com.foodcheck.domain.usecases

import com.foodcheck.domain.entities.UnifiedSearchResult
import com.foodcheck.domain.repositories.IProductRepository

/** Matches a query against both product titles and ingredient names/synonyms, grouped (not interleaved). */
class UnifiedSearchUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(query: String): Result<UnifiedSearchResult> {
        if (query.isBlank()) return Result.success(UnifiedSearchResult())
        return repository.unifiedSearch(query)
    }
}
