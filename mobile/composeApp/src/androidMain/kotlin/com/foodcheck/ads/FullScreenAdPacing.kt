package com.foodcheck.ads

/**
 * One frequency cap shared by every full-screen ad (interstitial and app open), so two formats
 * can never land back to back.
 */
internal object FullScreenAdPacing {
    private const val MIN_GAP_MS = 3L * 60L * 1000L

    @Volatile
    private var lastShownAt = 0L

    fun canShow(now: Long = System.currentTimeMillis()): Boolean = now - lastShownAt >= MIN_GAP_MS

    fun markShown(now: Long = System.currentTimeMillis()) {
        lastShownAt = now
    }
}
