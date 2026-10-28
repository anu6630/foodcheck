package com.foodcheck.ads

import android.app.Activity
import android.content.Context

actual object AdsController {
    private var appContext: Context? = null
    actual var adsSuppressed: Boolean = false

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    actual fun preloadInterstitial() {
        if (adsSuppressed) return
        val ctx = appContext ?: return
        InterstitialAdManager.preload(ctx)
    }

    actual fun showInterstitial(onDismissed: () -> Unit) {
        if (adsSuppressed) {
            onDismissed()
            return
        }
        val activity = AppOpenAdManagerActivityHolder.current
        if (activity == null) {
            onDismissed()
            return
        }
        InterstitialAdManager.show(activity, onDismissed)
    }
}

/** Bridge so AdsController can show interstitials without a Compose Local. */
internal object AppOpenAdManagerActivityHolder {
    @Volatile
    var current: Activity? = null
}
