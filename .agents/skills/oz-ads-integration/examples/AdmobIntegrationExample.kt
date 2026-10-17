package com.example.ads.integration

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.oz.android.OzAdsManager
import com.oz.android.ads_core.admobs.interstitial.AdmobInterstitial
import com.oz.android.ads_core.admobs.native_advanced.AdmobNativeAdvanced
import com.oz.android.ads_core.admobs.reward.AdmobReward
import com.oz.android.oz_ads.ads_inline.admob.OzAdmobBannerAd
import com.oz.android.oz_ads.ads_inline.admob.OzAdmobNativeAd
import com.oz.android.oz_ads.ads_overlay.admob.OzAdmobIntersAd
import com.oz.android.oz_ads.ads_overlay.admob.OzAdmobRewardAds
import com.oz.android.utils.enums.AdState
import com.oz.android.utils.listener.OzAdError
import com.oz.android.utils.listener.OzAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/* ==============================================================================================
 * 1. ADS COORDINATOR & PLACEMENT DIRECTORY
 * ============================================================================================== */
object AdsManager {
    private const val TAG = "AdsManager"

    // Dev Test IDs for safe sandbox testing
    private val testIds = mapOf(
        "inter_splash" to "ca-app-pub-3940256099942544/8691691433",
        "reward_item" to "ca-app-pub-3940256099942544/5224354917",
        "scroll_native" to "ca-app-pub-3940256099942544/2247696110",
        "banner_select" to "ca-app-pub-3940256099942544/9214589741",
        "open_resume" to "ca-app-pub-3940256099942544/9257395921"
    )

    interface AdCallback {
        fun onAdHandled()
        fun onAdPreparationRefused()
    }

    fun isAdsEnabled(): Boolean {
        // Query VIP in-app purchase state or shared preferences
        return true
    }

    fun getAdUnitId(placementKey: String): String {
        return testIds[placementKey] ?: ""
    }

    fun prepare(placementKey: String, adView: View?, callback: AdCallback?) {
        if (!isAdsEnabled()) {
            adView?.visibility = View.GONE
            callback?.onAdPreparationRefused()
            return
        }

        val remoteConfigEnabled = true // Query remote config toggle if applicable
        if (remoteConfigEnabled) {
            adView?.visibility = View.VISIBLE
            callback?.onAdHandled()
        } else {
            adView?.visibility = View.GONE
            callback?.onAdPreparationRefused()
        }
    }
}

/* ==============================================================================================
 * 2. INTERSTITIAL HELPER WITH ATOMIC TIMEOUT GUARD
 * ============================================================================================== */
object AdInterstitialHelper {
    private const val TAG = "AdInterstitialHelper"

    fun showInterstitial(
        activity: Activity,
        placementKey: String,
        timeoutMs: Long = 4000L,
        onComplete: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed || !AdsManager.isAdsEnabled()) {
            onComplete()
            return
        }

        val isCompleted = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        var intersAd: OzAdmobIntersAd? = null

        val completeAction = Runnable {
            if (isCompleted.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                activity.runOnUiThread {
                    OzAdsManager.getInstance().clearPendingShow(placementKey)
                    try { intersAd?.destroy() } catch (e: Exception) {}
                    onComplete()
                }
            }
        }

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Interstitial timed out after ${timeoutMs}ms for $placementKey")
            completeAction.run()
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        AdsManager.prepare(placementKey, null, object : AdsManager.AdCallback {
            override fun onAdHandled() {
                activity.runOnUiThread {
                    if (isCompleted.get()) return@runOnUiThread
                    val adUnitId = AdsManager.getAdUnitId(placementKey)
                    if (adUnitId.isBlank()) {
                        completeAction.run()
                        return@runOnUiThread
                    }

                    val ad = OzAdmobIntersAd(activity).apply {
                        setAdUnitId(placementKey, adUnitId)
                    }
                    intersAd = ad

                    ad.listener = object : OzAdListener<AdmobInterstitial>() {
                        override fun onAdShowedFullScreenContent() {
                            handler.removeCallbacks(timeoutRunnable)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            completeAction.run()
                        }

                        override fun onAdFailedToLoad(error: OzAdError) {
                            completeAction.run()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                            completeAction.run()
                        }
                    }

                    ad.loadThenShow(activity, false)
                }
            }

            override fun onAdPreparationRefused() {
                completeAction.run()
            }
        })
    }
}

/* ==============================================================================================
 * 3. PERSISTENT NATIVE AD VIEW FOR RECYCLERVIEW / VIEWPAGER2
 * ============================================================================================== */
open class PersistentNativeAdView(context: Context) : OzAdmobNativeAd(context) {
    var isAdRendered: Boolean = false
        private set

    override fun onDetachedFromWindow() {
        // Crucial: Suppress super call to prevent tearing down NativeAd on scroll detachment
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAdRendered && isAdEnable()) {
            visibility = View.VISIBLE
        }
    }

    override fun showAds(key: String) {
        val currentState = OzAdsManager.getInstance().getAdState(key)
        if (currentState == AdState.SHOWING && !isAdRendered) {
            val cachedAd = OzAdsManager.getInstance().getAd(key) as? AdmobNativeAdvanced
            if (cachedAd != null) {
                isAdRendered = true
                visibility = View.VISIBLE
                onShowAds(key, cachedAd)
                return
            }
        }
        super.showAds(key)
    }

    override fun onShowAds(key: String, ad: AdmobNativeAdvanced) {
        isAdRendered = true
        visibility = View.VISIBLE
        super.onShowAds(key, ad)
    }
}

/* ==============================================================================================
 * 4. UNIFIED REWARDED AD HELPER (SINGLE PLACEMENT KEY)
 * ============================================================================================== */
object RewardAdHelper {
    private const val PLACEMENT_KEY = "reward_item"
    private var preloadedReward: OzAdmobRewardAds? = null
    private val isRequesting = AtomicBoolean(false)

    fun preload(context: Context) {
        if (!AdsManager.isAdsEnabled()) return
        val adUnitId = AdsManager.getAdUnitId(PLACEMENT_KEY)
        if (adUnitId.isBlank()) return

        val state = OzAdsManager.getInstance().getAdState(PLACEMENT_KEY)
        if (state == AdState.LOADED || state == AdState.LOADING) return

        if (preloadedReward == null) {
            preloadedReward = OzAdmobRewardAds(context.applicationContext).apply {
                setAdUnitId(PLACEMENT_KEY, adUnitId)
            }
        }
        preloadedReward?.loadAd()
    }

    fun showRewardAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onClosed: ((Boolean) -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!isRequesting.compareAndSet(false, true)) return

        AdsManager.prepare(PLACEMENT_KEY, null, object : AdsManager.AdCallback {
            override fun onAdHandled() {
                var isEarned = false
                val rewardAd = preloadedReward ?: OzAdmobRewardAds(activity.applicationContext).apply {
                    setAdUnitId(PLACEMENT_KEY, AdsManager.getAdUnitId(PLACEMENT_KEY))
                }

                rewardAd.listener = object : OzAdListener<AdmobReward>() {
                    override fun onAdDismissedFullScreenContent() {
                        isRequesting.set(false)
                        onClosed?.invoke(isEarned)
                        preload(activity.applicationContext)
                    }

                    override fun onAdFailedToLoad(error: OzAdError) {
                        isRequesting.set(false)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                        isRequesting.set(false)
                        preload(activity.applicationContext)
                    }
                }

                val onEarnedCallback: (String, Int) -> Unit = { _, _ ->
                    isEarned = true
                    onRewarded()
                }

                if (OzAdsManager.getInstance().getAdState(PLACEMENT_KEY) == AdState.LOADED) {
                    rewardAd.show(activity, onRewardEarned = onEarnedCallback)
                } else {
                    rewardAd.loadThenShow(activity, showOverlay = false, onRewardEarned = onEarnedCallback)
                }
            }

            override fun onAdPreparationRefused() {
                isRequesting.set(false)
                // VIP / Ad-Free users instantly receive the reward
                onRewarded()
                onClosed?.invoke(true)
            }
        })
    }
}
