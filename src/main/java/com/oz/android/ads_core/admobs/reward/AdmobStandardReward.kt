package com.oz.android.ads_core.admobs.reward

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oz.android.utils.OzLog
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oz.android.ads_core.admobs.AdmobBase
import com.oz.android.ads_core.admobs.toOzError
import com.oz.android.utils.listener.OzAdListener

/**
 * Standard SDK implementation of Rewarded ads.
 */
class AdmobStandardReward(
    context: Context,
    adUnitId: String,
    listener: OzAdListener<AdmobReward>? = null
) : AdmobBase<AdmobReward>(context, adUnitId, listener), AdmobReward {
    private var rewardedAd: RewardedAd? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AdmobStandardReward"
    }

    override fun load() {
        if (rewardedAd != null) {
            OzLog.d(TAG, "Ad already loaded")
            return
        }

        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    OzLog.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    
                    rewardedAd?.onPaidEventListener = getOnPaidListener(rewardedAd!!.responseInfo)
                    setupFullScreenContentCallback(ad)
                    mainHandler.post {
                        listener?.onAdLoaded(this@AdmobStandardReward)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    OzLog.e(TAG, "Rewarded ad failed to load: ${adError.message}")
                    rewardedAd = null
                    val ozError = adError.toOzError()
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
                OzLog.w(TAG, "Activity is finishing or destroyed. Cannot show reward ad")
                return@Runnable
            }

            val currentAd = rewardedAd
            if (currentAd == null) {
                OzLog.w(TAG, "RewardedAd is null. Call load() first")
                return@Runnable
            }

            setupFullScreenContentCallback(currentAd)

            currentAd.show(activity) { rewardItem ->
                mainHandler.post {
                    rewardCallback.onUserEarnedReward(rewardItem.type, rewardItem.amount)
                }
            }
            OzLog.d(TAG, "Rewarded ad displayed")
        }

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

    private fun setupFullScreenContentCallback(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                OzLog.d(TAG, "Ad was dismissed")
                rewardedAd = null
                mainHandler.post {
                    listener?.onAdDismissedFullScreenContent()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                OzLog.e(TAG, "Ad failed to show: ${adError.message}")
                rewardedAd = null
                val ozError = adError.toOzError()
                mainHandler.post {
                    listener?.onAdFailedToShowFullScreenContent(ozError)
                }
            }

            override fun onAdShowedFullScreenContent() {
                OzLog.d(TAG, "Ad showed fullscreen content")
                mainHandler.post {
                    listener?.onAdShowedFullScreenContent()
                }
            }

            override fun onAdImpression() {
                OzLog.d(TAG, "Ad recorded an impression")
                mainHandler.post {
                    listener?.onAdImpression()
                }
            }

            override fun onAdClicked() {
                OzLog.d(TAG, "Ad was clicked")
                mainHandler.post {
                    listener?.onAdClicked()
                }
            }
        }
    }

    override fun isAdLoaded(): Boolean {
        return rewardedAd != null
    }
}
