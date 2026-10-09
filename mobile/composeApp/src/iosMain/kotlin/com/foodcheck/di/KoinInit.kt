package com.foodcheck.di

import com.foodcheck.data.network.ApiHealthMonitor
import com.foodcheck.presentation.state.IngredientDetailViewModel
import com.foodcheck.presentation.state.ProductDetailViewModel
import com.foodcheck.presentation.state.FeedViewModel
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.state.SearchViewModel
import com.foodcheck.presentation.state.ShelfViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

/** Called once from Swift (`iosAppApp.swift`) at process start, mirroring Android's
 * `MainActivity.onCreate` Koin bootstrap. */
fun initKoin() {
    val koinApp = startKoin {
        modules(sharedModule, appModule)
    }
    koinApp.koin.get<ApiHealthMonitor>().start()
}

object ViewModelProvider : KoinComponent {
    fun getSearchViewModel(): SearchViewModel = get()
    fun getProductDetailViewModel(): ProductDetailViewModel = get()
    fun getIngredientDetailViewModel(): IngredientDetailViewModel = get()
    fun getScanViewModel(): ScanViewModel = get()
    fun getFeedViewModel(): FeedViewModel = get()
    fun getShelfViewModel(): ShelfViewModel = get()
}
