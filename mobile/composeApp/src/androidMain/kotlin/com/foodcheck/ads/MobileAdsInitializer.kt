package com.foodcheck.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

object MobileAdsInitializer {
    private val started = AtomicBoolean(false)

    /** Hashed device IDs from logcat ("Use RequestConfiguration…setTestDeviceIds"). */
    private val TEST_DEVICE_IDS = listOf(
        "78F7ABE253B3FBC1ECBB353CD34E4721",
    )

    @Volatile
    var ready: Boolean = false
        private set

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(TEST_DEVICE_IDS)
                .build(),
        )
        MobileAds.initialize(app) {
            ready = true
            Log.i("LifeXAds", "MobileAds initialized (test devices configured)")
            InterstitialAdManager.preload(app)
            AppOpenAdManager.loadAd(app)
        }
    }
}
