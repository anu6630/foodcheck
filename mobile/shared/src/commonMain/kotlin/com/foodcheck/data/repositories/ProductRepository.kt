package com.foodcheck.data.repositories

import com.foodcheck.data.datasources.LocalCacheDataSource
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.dto.ScanResponse
import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.entities.ScanCandidate
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.UnifiedSearchResult
import com.foodcheck.domain.repositories.IProductRepository

class ProductRepository(
    private val networkDataSource: NetworkDataSource,
    private val localCacheDataSource: LocalCacheDataSource
) : IProductRepository {

    override suspend fun searchProducts(query: String): Result<List<SearchProductResult>> {
        return try {
            val results = networkDataSource.searchProducts(query)
            localCacheDataSource.cacheSearch(query, results)
            Result.success(results)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getSearch(query)
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to load products from server (Offline)", e))
            }
        }
    }

    override suspend fun getProductDetails(barcode: String): Result<SearchProductResult?> {
        return try {
            val result = networkDataSource.getProductDetails(barcode)
            if (result != null) {
                localCacheDataSource.cacheProduct(barcode, result)
            }
            Result.success(result)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getProduct(barcode)
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to load product details from server (Offline)", e))
            }
        }
    }

    override suspend fun unifiedSearch(query: String): Result<UnifiedSearchResult> {
        return try {
            val result = networkDataSource.unifiedSearch(query)
            localCacheDataSource.cacheUnifiedSearch(query, result)
            Result.success(result)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getUnifiedSearch(query)
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to load search results from server (Offline)", e))
            }
        }
    }

    override suspend fun getIngredientDetails(ingredientId: String): Result<IngredientDetail> {
        return try {
            val result = networkDataSource.getIngredientDetails(ingredientId)
            localCacheDataSource.cacheIngredient(ingredientId, result)
            Result.success(result)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getIngredient(ingredientId)
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to load ingredient details from server (Offline)", e))
            }
        }
    }

    override suspend fun scan(images: List<ByteArray>): Result<ScanOutcome> {
        return try {
            val outcome = networkDataSource.scan(images).toDomain()
            localCacheDataSource.cacheScan(outcome)
            Result.success(outcome)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getLastScan()
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to upload image. Please check your internet connection.", e))
            }
        }
    }

    private fun ScanResponse.toDomain(): ScanOutcome = when (matchType) {
        "confident" -> ScanOutcome.Confident(
            product ?: error("Backend returned match_type=confident with no product")
        )
        "suggestions" -> ScanOutcome.Suggestions(
            candidates.map { ScanCandidate(it.product, it.matchScore) }
        )
        else -> ScanOutcome.IngredientsOnly(ingredients)
    }
}
