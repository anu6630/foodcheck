package com.foodcheck.ads

actual object AdsController {
    actual var adsSuppressed: Boolean = false

    actual fun preloadInterstitial() = Unit
    actual fun showInterstitial(onDismissed: () -> Unit) = onDismissed()
}
