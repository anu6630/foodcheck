package com.foodcheck.app

import android.app.Application
import com.foodcheck.ads.AppOpenAdManager
import com.foodcheck.ads.MobileAdsInitializer

class FoodCheckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAdsInitializer.init(this)
        AppOpenAdManager.register(this)
    }
}
