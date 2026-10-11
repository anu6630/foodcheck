package com.foodcheck.ads

actual object AdsController {
    actual fun preloadInterstitial() = Unit
    actual fun showInterstitial(onDismissed: () -> Unit) = onDismissed()
}
