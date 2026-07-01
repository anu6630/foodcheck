package com.foodcheck.domain.repositories

import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult

interface IProductRepository {
    suspend fun searchProducts(query: String, countries: List<String>): Result<List<SearchProductResult>>
    suspend fun getProductDetails(barcode: String, countries: List<String>): Result<SearchProductResult?>
    suspend fun scanImage(imageBytes: ByteArray, countries: List<String>): Result<ScanResult>
}
