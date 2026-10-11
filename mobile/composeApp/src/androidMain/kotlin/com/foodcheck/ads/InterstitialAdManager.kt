package com.foodcheck.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

object InterstitialAdManager {
    private const val TAG = "LifeXAds"

    private var interstitial: InterstitialAd? = null
    private val loading = AtomicBoolean(false)

    fun preload(context: Context) {
        if (interstitial != null || !loading.compareAndSet(false, true)) return
        InterstitialAd.load(
            context.applicationContext,
            AdUnits.INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading.set(false)
                    interstitial = ad
                    Log.i(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.set(false)
                    interstitial = null
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                }
            },
        )
    }

    fun show(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitial
        if (ad == null || !FullScreenAdPacing.canShow()) {
            preload(activity)
            onDismissed()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial show failed: ${error.message}")
                preload(activity)
                onDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                FullScreenAdPacing.markShown()
                Log.i(TAG, "Interstitial shown")
            }
        }
        ad.show(activity)
    }
}
