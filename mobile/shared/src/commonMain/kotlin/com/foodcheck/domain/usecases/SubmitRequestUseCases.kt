package com.foodcheck.domain.usecases

import com.foodcheck.data.repositories.ProductRepository

/**
 * Submits a community product-addition request (Flow 1).
 *
 * @return [Result] containing (requestId, status) on success.
 */
class SubmitProductRequestUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(
        productName: String,
        countryCode: String,
        brand: String? = null,
        barcode: String? = null,
        category: String? = null,
        ingredientsText: String? = null,
        requesterNotes: String? = null,
        images: List<ByteArray> = emptyList(),
    ): Result<Pair<Int, String>> {
        if (productName.isBlank()) return Result.failure(IllegalArgumentException("Product name is required"))
        if (countryCode.isBlank()) return Result.failure(IllegalArgumentException("Country is required"))
        if (images.size > 3) return Result.failure(IllegalArgumentException("At most 3 images are allowed"))
        return repository.submitProductRequest(
            productName = productName.trim(),
            countryCode = countryCode.trim(),
            brand = brand?.trim(),
            barcode = barcode?.trim(),
            category = category?.trim(),
            ingredientsText = ingredientsText?.trim(),
            requesterNotes = requesterNotes?.trim(),
            images = images,
        )
    }
}

/**
 * Submits a community ingredient-check request (Flow 2).
 *
 * @return [Result] containing (requestId, status) on success.
 */
class SubmitIngredientRequestUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(
        ingredientName: String,
        eNumber: String? = null,
        category: String? = null,
        requesterNotes: String? = null,
    ): Result<Pair<Int, String>> {
        if (ingredientName.isBlank()) return Result.failure(IllegalArgumentException("Ingredient name is required"))
        return repository.submitIngredientRequest(
            ingredientName = ingredientName.trim(),
            eNumber = eNumber?.trim(),
            category = category?.trim(),
            requesterNotes = requesterNotes?.trim(),
        )
    }
}
