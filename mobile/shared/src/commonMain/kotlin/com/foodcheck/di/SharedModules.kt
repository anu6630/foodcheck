package com.foodcheck.di

import org.koin.dsl.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.datasources.LocalCacheDataSource
import com.foodcheck.data.repositories.ProductRepository
import com.foodcheck.domain.repositories.IProductRepository
import com.foodcheck.domain.usecases.SearchProductsUseCase
import com.foodcheck.domain.usecases.GetProductDetailsUseCase
import com.foodcheck.domain.usecases.ScanImageUseCase

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
        }
    }

    // Datasources
    single { NetworkDataSource(get()) }
    single { LocalCacheDataSource() }

    // Repositories
    single<IProductRepository> { ProductRepository(get(), get()) }

    // Use cases
    single { SearchProductsUseCase(get()) }
    single { GetProductDetailsUseCase(get()) }
    single { ScanImageUseCase(get()) }
}
