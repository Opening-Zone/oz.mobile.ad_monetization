package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener
import com.oz.android.utils.event.OzEventLogger

class AdmobNextReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener), AdmobReward {

    private var nextGenAd: RewardedAd? = null

    // Used only for dispatching library-owned UI operations (show/hide views) to main thread.
    // Listener callbacks are intentionally called on whatever thread they fire — callers decide their own thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobNextReward"
    }

    override fun load() {
        if (nextGenAd != null) {
            OzLog.d(TAG, "Ad already loaded (Next-Gen)")
            return
        }

        RewardedAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    OzLog.d(TAG, "Rewarded ad loaded successfully (Next-Gen)")
                    nextGenAd = ad
                    setupNextGenFullScreenCallback(ad)
                    mainHandler.post {
                        listener?.onAdLoaded(this@AdmobNextReward)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    OzLog.e(TAG, "Rewarded ad failed to load: ${error.message} (Next-Gen)")
                    nextGenAd = null
                    val ozError = error.toOzError()
                    mainHandler.post {
                        listener?.onAdFailedToLoad(ozError)
                    }
                }
            }
        )
    }

    override fun show() {
        OzLog.w(
            TAG,
            "show() called without activity and callback. Use show(activity: Activity, callback: OnUserEarnedRewardListener) for reward ads"
        )
    }

    override fun show(activity: Activity, rewardCallback: OzRewardCallback) {
        val showRunnable = Runnable {
            if (activity.isFinishing || activity.isDestroyed) {
                OzLog.w(TAG, "Activity is finishing or destroyed (Next-Gen). Cannot show reward ad")
                return@Runnable
            }

            val currentAd = nextGenAd
            if (currentAd == null) {
                OzLog.w(TAG, "RewardedAd is null (Next-Gen). Call load() first")
                return@Runnable
            }

            setupNextGenFullScreenCallback(currentAd)

            currentAd.show(activity, object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    OzEventLogger.logAdRewardEarned(context, adUnitId, reward.type, reward.amount)
                    mainHandler.post {
                        rewardCallback.onUserEarnedReward(reward.type, reward.amount)
                    }
                }
            })
            OzLog.d(TAG, "Rewarded ad displayed (Next-Gen)")
        }

        // Self-dispatches to main — safe to call from any thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run()
        } else {
            mainHandler.post(showRunnable)
        }
    }

    override fun loadThenShow() {
        OzLog.w(
            TAG,
            "loadThenShow() called without activity and callback. Use loadThenShow(activity: Activity, callback: OzRewardCallback) for reward ads"
        )
    }

    override fun loadThenShow(activity: Activity, rewardCallback: OzRewardCallback) {
        load()
    }

    private fun setupNextGenFullScreenCallback(ad: RewardedAd) {
        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                OzLog.d(TAG, "Ad was dismissed (Next-Gen)")
                nextGenAd = null
                mainHandler.post {
                    listener?.onAdDismissedFullScreenContent()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                OzLog.e(TAG, "Ad failed to show: ${error.message} (Next-Gen)")
                nextGenAd = null
                val ozError = error.toOzError()
                mainHandler.post {
                    listener?.onAdFailedToShowFullScreenContent(ozError)
                }
            }

            override fun onAdShowedFullScreenContent() {
                OzLog.d(TAG, "Ad showed fullscreen content (Next-Gen)")
                mainHandler.post {
                    listener?.onAdShowedFullScreenContent()
                }
            }

            override fun onAdImpression() {
                OzLog.d(TAG, "Ad recorded an impression (Next-Gen)")
                mainHandler.post {
                    listener?.onAdImpression()
                }
            }

            override fun onAdClicked() {
                OzLog.d(TAG, "Ad was clicked (Next-Gen)")
                mainHandler.post {
                    listener?.onAdClicked()
                }
            }

            override fun onAdPaid(value: AdValue) {
                OzEventLogger.logPaidAdImpressionNextGen(
                    context,
                    value.valueMicros,
                    value.currencyCode,
                    adUnitId,
                    ad.getResponseInfo().adapterClassName ?: "unknown"
                )
            }
        }
    }

    override fun isAdLoaded(): Boolean {
        return nextGenAd != null
    }
}
