package com.foodcheck.domain.usecases

import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.repositories.IProductRepository

class GetIngredientDetailsUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(ingredientId: String): Result<IngredientDetail> {
        if (ingredientId.isBlank()) return Result.failure(IllegalArgumentException("Ingredient id cannot be empty"))
        return repository.getIngredientDetails(ingredientId)
    }
}
