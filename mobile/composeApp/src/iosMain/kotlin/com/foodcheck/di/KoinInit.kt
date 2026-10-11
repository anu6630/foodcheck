package com.foodcheck.di

import com.foodcheck.data.local.AppFilesBootstrap
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
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Called once from Swift (`iosAppApp.swift`) at process start, mirroring Android's
 * `MainActivity.onCreate` Koin bootstrap. */
fun initKoin() {
    val docs = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String
    if (docs != null) {
        AppFilesBootstrap.init("$docs/foodcheck-cache")
    }
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
