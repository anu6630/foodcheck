package com.foodcheck.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

class AndroidFirebaseAnalyticsTracker(
    context: Context
) : AnalyticsTracker {

    private val firebaseAnalytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun logEvent(name: String, params: Map<String, Any?>) {
        try {
            val bundle = Bundle()
            for ((key, value) in params) {
                if (value == null) continue
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putFloat(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
            firebaseAnalytics.logEvent(name, bundle)
            Log.d("Analytics", "Logged event: $name -> $params")
        } catch (e: Exception) {
            Log.w("Analytics", "Failed to log event $name", e)
        }
    }

    override fun setUserProperty(name: String, value: String) {
        try {
            firebaseAnalytics.setUserProperty(name, value)
        } catch (e: Exception) {
            Log.w("Analytics", "Failed to set user property $name", e)
        }
    }
}

private var instance: AndroidFirebaseAnalyticsTracker? = null

fun initAndroidAnalyticsTracker(context: Context): AnalyticsTracker {
    if (instance == null) {
        instance = AndroidFirebaseAnalyticsTracker(context.applicationContext)
    }
    return instance!!
}

actual fun getAnalyticsTracker(): AnalyticsTracker {
    return instance ?: NoOpAnalyticsTracker
}

private object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, Any?>) {}
    override fun setUserProperty(name: String, value: String) {}
}
