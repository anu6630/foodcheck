package com.foodcheck.di

import org.koin.dsl.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.datasources.LocalCacheDataSource
import com.foodcheck.data.datasources.ShelfLocalStore
import com.foodcheck.data.datasources.ComparisonLocalStore
import com.foodcheck.data.network.ApiHealthMonitor
import com.foodcheck.data.network.installFallbackInterceptor
import com.foodcheck.data.repositories.ProductRepository
import com.foodcheck.data.repositories.ShelfRepository
import com.foodcheck.data.repositories.ComparisonRepository
import com.foodcheck.domain.repositories.IProductRepository
import com.foodcheck.domain.usecases.SearchProductsUseCase
import com.foodcheck.domain.usecases.GetProductDetailsUseCase
import com.foodcheck.domain.usecases.ScanUseCase
import com.foodcheck.domain.usecases.UnifiedSearchUseCase
import com.foodcheck.domain.usecases.GetIngredientDetailsUseCase
import com.foodcheck.domain.usecases.SubmitIngredientRequestUseCase
import com.foodcheck.domain.usecases.SubmitProductRequestUseCase

val sharedModule = module {
    // HttpClient
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }.apply { installFallbackInterceptor() }
    }

    // Datasources
    single { NetworkDataSource(get()) }
    single { LocalCacheDataSource() }
    single { ShelfLocalStore() }
    single { ComparisonLocalStore() }

    // Proactively steers the active backend tier back to primary once healthy again.
    single { ApiHealthMonitor() }

    // Repositories — concrete type registered so submit use cases can resolve it;
    // interface binding reuses the same instance.
    single { ProductRepository(get(), get()) }
    single<IProductRepository> { get<ProductRepository>() }
    single { ShelfRepository(get(), get()) }
    single { ComparisonRepository(get(), get(), get()) }

    // Use cases
    single { SearchProductsUseCase(get()) }
    single { GetProductDetailsUseCase(get()) }
    single { ScanUseCase(get()) }
    single { UnifiedSearchUseCase(get()) }
    single { GetIngredientDetailsUseCase(get()) }
    single { SubmitProductRequestUseCase(get()) }
    single { SubmitIngredientRequestUseCase(get()) }
}
