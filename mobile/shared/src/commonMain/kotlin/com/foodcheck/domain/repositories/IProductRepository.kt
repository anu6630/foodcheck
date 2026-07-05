package com.foodcheck.domain.repositories

import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.UnifiedSearchResult

interface IProductRepository {
    suspend fun searchProducts(query: String): Result<List<SearchProductResult>>
    suspend fun getProductDetails(barcode: String): Result<SearchProductResult?>
    suspend fun scan(images: List<ByteArray>): Result<ScanOutcome>
    suspend fun unifiedSearch(query: String): Result<UnifiedSearchResult>
    suspend fun getIngredientDetails(ingredientId: String): Result<IngredientDetail>
}
