package com.syedali.flashquiz.ads

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.syedali.flashquiz.billing.SubscriptionManager

/**
 * Manages banner ad loading and display.
 * Checks RevenueCat subscription status before showing ads.
 */
object BannerAdManager {

    private const val TAG = "BannerAd"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-7129470803481646/2894755016"

    private var currentAdView: AdView? = null

    fun showBanner(activity: Activity, container: FrameLayout) {
        // If already showing, don't reload
        if (currentAdView != null && container.childCount > 0) return

        // Check RevenueCat for ad-free status — show ads if check fails
        try {
            SubscriptionManager.checkAdFree { isAdFree ->
                activity.runOnUiThread {
                    if (isAdFree) {
                        container.visibility = android.view.View.GONE
                        return@runOnUiThread
                    }
                    loadAndShowAd(activity, container)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat check failed, showing ads anyway: ${e.message}")
            loadAndShowAd(activity, container)
        }
    }

    private fun loadAndShowAd(activity: Activity, container: FrameLayout) {
        try {
            val adView = AdView(activity)
            adView.adUnitId = BANNER_AD_UNIT_ID
            adView.setAdSize(AdSize.BANNER)

            container.removeAllViews()
            container.addView(adView)
            container.visibility = android.view.View.VISIBLE
            currentAdView = adView

            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner ad failed: ${error.message}")
                    container.visibility = android.view.View.GONE
                    currentAdView = null
                }

                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad loaded successfully")
                }
            }

            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading banner: ${e.message}")
            container.visibility = android.view.View.GONE
        }
    }

    fun hideBanner(container: FrameLayout) {
        currentAdView?.destroy()
        currentAdView = null
        container.removeAllViews()
        container.visibility = android.view.View.GONE
    }
}
