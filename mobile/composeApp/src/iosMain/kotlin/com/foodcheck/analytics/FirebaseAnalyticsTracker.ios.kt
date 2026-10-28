package com.foodcheck.analytics

class IosAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, Any?>) {
        // iOS logging stub / os_log
    }

    override fun setUserProperty(name: String, value: String) {
        // iOS user property stub
    }
}

private val instance = IosAnalyticsTracker()

actual fun getAnalyticsTracker(): AnalyticsTracker = instance
