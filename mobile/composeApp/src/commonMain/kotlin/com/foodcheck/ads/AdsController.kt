package com.foodcheck.ads

/**
 * Platform ads entry points. Android loads/shows AdMob; iOS is a no-op.
 */
expect object AdsController {
    var adsSuppressed: Boolean

    /** Prefetch interstitial so a later show is instant. */
    fun preloadInterstitial()

    /**
     * Show interstitial if loaded and the shared frequency cap allows. [onDismissed] always runs
     * (shown or not), so navigation can continue safely.
     *
     * Call only at a break after a finished task (comparison saved, scan saved, leaving a product
     * page) — never while the user is waiting for an answer or mid-flow.
     */
    fun showInterstitial(onDismissed: () -> Unit = {})
}
