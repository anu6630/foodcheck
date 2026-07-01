package com.foodcheck.data.datasources

import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalCacheDataSource {
    private val mutex = Mutex()
    private val productCache = mutableMapOf<String, SearchProductResult>()
    private val searchCache = mutableMapOf<String, List<SearchProductResult>>()
    private var lastScanResult: ScanResult? = null

    suspend fun cacheProduct(barcode: String, product: SearchProductResult) {
        mutex.withLock {
            productCache[barcode] = product
        }
    }

    suspend fun getProduct(barcode: String): SearchProductResult? {
        return mutex.withLock {
            productCache[barcode]
        }
    }

    suspend fun cacheSearch(query: String, results: List<SearchProductResult>) {
        mutex.withLock {
            searchCache[query] = results
        }
    }

    suspend fun getSearch(query: String): List<SearchProductResult>? {
        return mutex.withLock {
            searchCache[query]
        }
    }

    suspend fun cacheScan(result: ScanResult) {
        mutex.withLock {
            lastScanResult = result
        }
    }

    suspend fun getLastScan(): ScanResult? {
        return mutex.withLock {
            lastScanResult
        }
    }
}
