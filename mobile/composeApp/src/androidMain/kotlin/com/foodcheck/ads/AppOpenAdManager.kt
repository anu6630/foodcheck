package com.foodcheck.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object AppOpenAdManager : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private const val TAG = "LifeXAds"
    /** Google sample default: a loaded ad expires after 4 hours. */
    private const val AD_TIMEOUT_MS = 4L * 60L * 60L * 1000L
    /** Only a real return counts: camera, gallery and permission screens take seconds. */
    private const val MIN_AWAY_MS = 30L * 60L * 1000L

    private var appOpenAd: AppOpenAd? = null
    private var loadTime: Long = 0L
    private val loading = AtomicBoolean(false)
    private var showing = false
    private var currentActivity: Activity? = null
    private var registered = false
    /** When the app last went to the background; 0 = not since launch. */
    private var backgroundedAt = 0L

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun loadAd(context: Context) {
        if (appOpenAd != null || !loading.compareAndSet(false, true)) return
        AppOpenAd.load(
            context.applicationContext,
            AdUnits.APP_OPEN,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading.set(false)
                    appOpenAd = ad
                    loadTime = Date().time
                    Log.i(TAG, "App open loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.set(false)
                    appOpenAd = null
                    Log.w(TAG, "App open failed: ${error.message}")
                }
            },
        )
    }

    private fun isAdAvailable(): Boolean {
        if (appOpenAd == null) return false
        val fresh = Date().time - loadTime < AD_TIMEOUT_MS
        if (!fresh) {
            appOpenAd = null
            return false
        }
        return true
    }

    private fun showAdIfAvailable() {
        val activity = currentActivity
        if (showing || activity == null || !FullScreenAdPacing.canShow()) return
        if (!isAdAvailable()) {
            loadAd(activity)
            return
        }
        val ad = appOpenAd ?: return
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                showing = false
                loadAd(activity)
                Log.i(TAG, "App open dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                showing = false
                loadAd(activity)
                Log.w(TAG, "App open show failed: ${error.message}")
            }

            override fun onAdShowedFullScreenContent() {
                FullScreenAdPacing.markShown()
                Log.i(TAG, "App open shown")
            }
        }
        ad.show(activity)
    }

    /**
     * Shows only when the user comes back after [MIN_AWAY_MS], with an ad already loaded. Never on a
     * short trip (camera, gallery, permission screen) and never late: an ad that pops up seconds
     * after the screen appeared covers what the user is doing and draws accidental taps.
     * A cold start has no ad loaded yet (MobileAds is still initialising), so it shows none.
     */
    override fun onStart(owner: LifecycleOwner) {
        val away = backgroundedAt
        if (away != 0L && System.currentTimeMillis() - away >= MIN_AWAY_MS) showAdIfAvailable()
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) {
        if (!showing) {
            currentActivity = activity
            AppOpenAdManagerActivityHolder.current = activity
        }
    }
    override fun onActivityResumed(activity: Activity) {
        if (!showing) {
            currentActivity = activity
            AppOpenAdManagerActivityHolder.current = activity
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
            if (AppOpenAdManagerActivityHolder.current === activity) {
                AppOpenAdManagerActivityHolder.current = null
            }
        }
    }
}
