package com.foodcheck.data.datasources

import com.foodcheck.domain.entities.IngredientDetail
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.UnifiedSearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalCacheDataSource {
    private val mutex = Mutex()
    private val productCache = mutableMapOf<String, SearchProductResult>()
    private val searchCache = mutableMapOf<String, List<SearchProductResult>>()
    private val unifiedSearchCache = mutableMapOf<String, UnifiedSearchResult>()
    private val ingredientCache = mutableMapOf<String, IngredientDetail>()
    private var lastScanResult: ScanOutcome? = null

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

    suspend fun cacheUnifiedSearch(query: String, result: UnifiedSearchResult) {
        mutex.withLock {
            unifiedSearchCache[query] = result
        }
    }

    suspend fun getUnifiedSearch(query: String): UnifiedSearchResult? {
        return mutex.withLock {
            unifiedSearchCache[query]
        }
    }

    suspend fun cacheIngredient(ingredientId: String, detail: IngredientDetail) {
        mutex.withLock {
            ingredientCache[ingredientId] = detail
        }
    }

    suspend fun getIngredient(ingredientId: String): IngredientDetail? {
        return mutex.withLock {
            ingredientCache[ingredientId]
        }
    }

    suspend fun cacheScan(result: ScanOutcome) {
        mutex.withLock {
            lastScanResult = result
        }
    }

    suspend fun getLastScan(): ScanOutcome? {
        return mutex.withLock {
            lastScanResult
        }
    }
}
