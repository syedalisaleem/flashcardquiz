package com.syedali.flashquiz.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.syedali.flashquiz.billing.SubscriptionManager

/**
 * Manages rewarded ad loading, display, and reward callbacks.
 * Checks RevenueCat subscription status before showing ads.
 */
object RewardedAdManager {

    const val REWARDED_AD_UNIT_ID = "ca-app-pub-7129470803481646/2727234050"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun loadAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        RewardedAd.load(context, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                }
            })
    }

    fun showAdIfAvailable(
        activity: Activity,
        onReward: () -> Unit,
        onDismissed: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        // Check RevenueCat — skip ad if user has ad-free subscription
        SubscriptionManager.checkAdFree { isAdFree ->
            activity.runOnUiThread {
                if (isAdFree) {
                    onReward()
                    return@runOnUiThread
                }

                val ad = rewardedAd
                if (ad == null) {
                    onFailed()
                    loadAd(activity) // Pre-load for next time
                    return@runOnUiThread
                }

                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadAd(activity)
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null
                        loadAd(activity)
                        onFailed()
                    }
                }

                ad.show(activity) {
                    onReward()
                }
            }
        }
    }

    fun isLoaded(): Boolean = rewardedAd != null
}
