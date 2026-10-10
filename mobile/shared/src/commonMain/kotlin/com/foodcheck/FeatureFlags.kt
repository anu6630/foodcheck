package com.foodcheck

/**
 * Temporary product switches. Flip [socialFeedEnabled] back to true to restore
 * Feed tab + "Share as story" without deleting community code or APIs.
 */
object FeatureFlags {
    /** Community stories feed + publish-from-scan. My list / shelf stay on. */
    const val socialFeedEnabled = false
}
