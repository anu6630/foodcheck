package com.foodcheck.data.repositories

import com.foodcheck.domain.repositories.IProductRepository
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.datasources.LocalCacheDataSource

class ProductRepository(
    private val networkDataSource: NetworkDataSource,
    private val localCacheDataSource: LocalCacheDataSource
) : IProductRepository {

    override suspend fun searchProducts(query: String, countries: List<String>): Result<List<SearchProductResult>> {
        return try {
            val results = networkDataSource.searchProducts(query, countries)
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

    override suspend fun getProductDetails(barcode: String, countries: List<String>): Result<SearchProductResult?> {
        return try {
            val result = networkDataSource.getProductDetails(barcode, countries)
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

    override suspend fun scanImage(imageBytes: ByteArray, countries: List<String>): Result<ScanResult> {
        return try {
            val result = networkDataSource.scanImage(imageBytes, countries)
            localCacheDataSource.cacheScan(result)
            Result.success(result)
        } catch (e: Exception) {
            val cached = localCacheDataSource.getLastScan()
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(Exception("Failed to upload image. Please check your internet connection.", e))
            }
        }
    }
}
