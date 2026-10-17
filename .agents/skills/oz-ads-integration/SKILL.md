---
name: oz-ads-integration
description: >-
  Comprehensive guide and production runbook for implementing Google AdMob ads using the Oz Ads SDK (OzAds, OzAdsManager)
  in Android applications. Use this skill whenever adding, refactoring, or debugging ads (App Open, Interstitial,
  Native, Collapsible Banner, Rewarded) in activities, fragments, recyclerviews, or viewpagers.
---

# Oz Ads SDK Integration Guide

This skill provides the authoritative implementation pattern, architecture guidelines, and production-tested patterns for integrating ads into Android applications using the **Oz Ads SDK** (`com.oz.android`).

---

## 1. Architecture Overview

The Oz Ads SDK is organized into four distinct layers:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Coordinator Layer: AdsManager / AdsExtension / Helpers   │
│    - Placement Directory, RemoteConfig & VIP Entitlements   │
│    - Timeout safeguards, loading dialogs, preloading        │
├─────────────────────────────────────────────────────────────┤
│ 2. State & Caching Layer: OzAdsManager (Singleton)          │
│    - Global AdState machine (IDLE, LOADING, LOADED, SHOWING)│
│    - Ad object store (NativeAd, Interstitial, Banner, etc.) │
│    - Reactive StateFlows (isFullScreenAdShowing, enableAd)  │
├─────────────────────────────────────────────────────────────┤
│ 3. Component & Lifecycle Layer: OzAds<AdType> (ViewGroup)   │
│    - InlineAds (Banner, Native with auto-refresh & shimmer) │
│    - OverlayAds (Interstitial, App Open, Rewarded)          │
│    - PersistentNativeAdView (RecyclerView/ViewPager safety) │
├─────────────────────────────────────────────────────────────┤
│ 4. Network Engine: AdMob GMS / Next-Gen GMA Core            │
│    - GoogleMobileAdsConsentManager & UMP Consent            │
│    - AppResumeOpenManager (Foreground resume ads)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Core Setup & Configuration

### 2.1. Gradle Setup
In `settings.gradle.kts`:
```kotlin
include(":android_ads")
```
In `app/build.gradle.kts`:
```kotlin
implementation(project(":android_ads"))
```

In `app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${ad_app_id}" />
```

### 2.2. Application Initialization
Initialize `OzAdsManager` in your `Application` class (`onCreate()`):
```kotlin
val showAds = AdsManager.isAdsEnabled()
OzAdsManager.getInstance().updateConfig {
    copy(
        isAdEnabled = showAds,
        isDebugMode = BuildConfig.DEBUG,
        testDeviceIds = if (BuildConfig.DEBUG) listOf("YOUR_TEST_DEVICE_ID") else emptyList(),
        adsCoreType = AdsCoreType.ADMOB_NEXT_GEN
    )
}
```

---

## 3. The 7 Golden Rules of Oz Ads SDK

1. **Strict Key Uniqueness per Position**:
   Never reuse the same ad placement key across different active UI components simultaneously. For banners in shared base fragments, append the host screen name:
   `fragmentName + "_" + BaseConstants.BANNER_SELECT`.
2. **View Detachment State Preservation**:
   When views are destroyed or detached (e.g. fragments or view holders), **NEVER** reset the global ad state to `IDLE` or destroy the cached ad object. Call **ONLY** `OzAdsManager.getInstance().clearPendingShow(key)` to prevent stale callbacks onto dead views.
3. **Hard Timeout Safeguards on Fullscreen Ads**:
   Every interstitial or rewarded ad load must have an atomic timeout (e.g., 4000ms–10000ms via `AtomicBoolean` and `Handler`) to ensure navigation and user experience never hang if network calls stall.
4. **Instant Reward for VIP / Ad-Free Users**:
   If a user has purchased Ad-Free VIP, rewarded ad triggers must bypass the ad load completely and immediately grant the reward in `onAdPreparationRefused()`.
5. **RecyclerView Native Ad Persistence**:
   In `RecyclerView` or `ViewPager2`, standard native views get destroyed on scroll/page swipe. Always use `PersistentNativeAdView` (which ignores `onDetachedFromWindow()` destruction) and maintain expand/collapse layout logic.
6. **R8 / Resource Shrinking Protection**:
   Native ad binding uses layout view IDs (`ad_headline`, `ad_call_to_action`, etc.). You must ensure `res/values/ids.xml` declares them or `res/raw/keep.xml` protects them from being stripped during minification.
7. **Fast-Path Consent for Non-EU Users**:
   Do not block the splash screen with UMP consent checks for users outside the EU/EEA. Use `RegionHelper.isEuRegion(context)` to immediately initialize ads and sync UMP in the background.

---

## 4. Implementation by Ad Format

### 4.1. Splash Screen & Consent Flow (Cold Boot)
1. Check EU region via `RegionHelper.isEuRegion(this)`.
2. If non-EU:
   - Mark `CAN_REQUEST_ADS = true`.
   - Set consent flags for mediation networks (`VunglePrivacySettings`, `AppLovinPrivacySettings`).
   - Call `initAds()`.
   - Query UMP asynchronously in background to keep Google telemetry happy without stalling splash.
3. If EU:
   - Request UMP consent using `UserMessagingPlatform`.
   - Show consent form if `REQUIRED`.
   - On consent granted, call `initAds()`. If denied, set `isAdEnabled = false` and navigate forward.
4. In `initAds()`:
   - Initialize `AppResumeOpenManager.getInstance().init(application)`. Set splash class exclusion: `setSplashActivity(SplashActivity::class.java)`.
   - Await `OzAdsManager.getInstance().awaitInitialization(activity)`.
   - Load `OzAdmobIntersAd` with key `INTER_SPLASH` and `setTimeGap(0)`.
   - Bind to a multi-stage splash progress bar (crawl 0% -> 70% normal, 70% -> 99% crawl, fast finish on `onAdLoaded`). Show ad when 100% is reached or navigate on fallback timeout (12s).
   - Preload upcoming onboarding or home native ads via `preloadNativeAd(key)`.

### 4.2. Collapsible Banner Ads (`OzAdmobBannerAd`)
Implement in `BaseFragment`:
```kotlin
protected open fun setupBannerAds(view: View) {
    val banner = view.findViewById<OzAdmobBannerAd>(R.id.bannerView) ?: return
    if (!AdsManager.isAdsEnabled()) {
        banner.visibility = View.GONE
        return
    }

    val placementKey = BaseConstants.BANNER_SELECT
    AdsManager.prepare(placementKey, banner, object : AdsManager.AdCallback {
        override fun onAdHandled() {
            banner.apply {
                // Unique key per fragment prevents cross-tab collision
                setAdUnitId("${fragmentName}_$placementKey", AdsManager.getAdUnitId(placementKey))
                setCollapsibleBottom()
                setRefreshTime(120_000L) // 120s refresh

                // Wait until any fullscreen overlay ad closes before showing
                var bannerJob: Job? = null
                bannerJob = viewLifecycleOwner.lifecycleScope.launch {
                    OzAdsManager.getInstance().isFullScreenAdShowing.collect { isShowing ->
                        if (!isShowing) {
                            loadThenShow()
                            bannerJob?.cancel()
                        }
                    }
                }
            }
        }

        override fun onAdPreparationRefused() {
            banner.visibility = View.GONE
        }
    })
}
```

### 4.3. Interstitial Ads (`OzAdmobIntersAd`)
Use a centralized helper (`AdsExtension.kt` / `AdInterstitialHelper`):
```kotlin
fun Activity.showInterstitialAd(
    placementKey: String,
    showOverlay: Boolean = true,
    timeoutMs: Long = 4000L,
    onComplete: () -> Unit
) {
    if (isFinishing || isDestroyed || !AdsManager.isAdsEnabled()) {
        onComplete()
        return
    }

    val isCompleted = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    var loadingDialog: DialogLoading? = null

    if (showOverlay) {
        loadingDialog = DialogLoading.showLoadingAd(this, "Loading Ad...", "Please wait a moment...")
    }

    var intersAd: OzAdmobIntersAd? = null

    fun dismissAll() {
        runOnUiThread {
            loadingDialog?.safeDismiss()
            loadingDialog = null
            intersAd?.hideLoading()
        }
    }

    val completeAction = Runnable {
        if (isCompleted.compareAndSet(false, true)) {
            handler.removeCallbacksAndMessages(null)
            runOnUiThread {
                dismissAll()
                OzAdsManager.getInstance().clearPendingShow(placementKey)
                try { intersAd?.destroy() } catch (e: Exception) {}
                onComplete()
            }
        }
    }

    // Safety timeout
    val timeoutRunnable = Runnable { completeAction.run() }
    handler.postDelayed(timeoutRunnable, timeoutMs)

    AdsManager.prepare(placementKey, null, object : AdsManager.AdCallback {
        override fun onAdHandled() {
            runOnUiThread {
                if (isCompleted.get()) return@runOnUiThread
                val adUnitId = AdsManager.getAdUnitId(placementKey)
                if (adUnitId.isBlank()) {
                    completeAction.run()
                    return@runOnUiThread
                }

                val adInstance = OzAdmobIntersAd(this@showInterstitialAd).apply {
                    setAdUnitId(placementKey, adUnitId)
                }
                intersAd = adInstance

                adInstance.listener = object : OzAdListener<AdmobInterstitial>() {
                    override fun onAdShowedFullScreenContent() {
                        handler.removeCallbacks(timeoutRunnable)
                        dismissAll()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        dismissAll()
                        completeAction.run()
                    }

                    override fun onAdFailedToLoad(error: OzAdError) {
                        dismissAll()
                        completeAction.run()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: OzAdError) {
                        dismissAll()
                        completeAction.run()
                    }
                }

                adInstance.loadThenShow(this@showInterstitialAd, false)
            }
        }

        override fun onAdPreparationRefused() {
            completeAction.run()
        }
    })
}
```

### 4.4. Unified Rewarded Ads (`RewardAdHelper`)
All rewarded touchpoints (coins, hints, double streak, unlock item) should route through a single placement key (`BaseConstants.REWARD_ITEM`):
- **Preloading**: Preload on entry to Home/Main Activity via `RewardAdHelper.preload(context)`.
- **Auto-Reload**: Automatically call `preload()` immediately in `onAdDismissedFullScreenContent()`.
- **VIP Handling**: If ads are disabled by in-app purchase, `AdsManager.prepare()` triggers `onAdPreparationRefused()`. In this branch, immediately invoke `onRewarded()` and grant the item!
- **Debounce**: Use an `AtomicBoolean isAdRequestInProgress` to prevent double clicks.

### 4.5. Native Ads in Feeds & Lists (`PersistentNativeAdView`)
Standard `OzAdmobNativeAd` extends `InlineAds`, whose default `onDetachedFromWindow()` calls `destroy()` and nulls `adKey`. When scrolled off-screen in a `RecyclerView`, this destroys the ad and leaves a blank space.
Use `PersistentNativeAdView`:
1. Subclass `OzAdmobNativeAd` and override `onDetachedFromWindow()` to do nothing.
2. In `showAds(key)`: If `currentState == AdState.SHOWING`, retrieve existing ad object via `OzAdsManager.getInstance().getAd(key)` and re-bind.
3. In `AdsRecyclerAdapter`:
   - Inject ad view holder at desired position (e.g., position 2).
   - Collapse view (`itemView.visibility = GONE`, width = 0, height = 0) until ad is loaded or verified in cache.
   - When loaded or impression is triggered, expand view (`itemView.visibility = VISIBLE`, width = MATCH_PARENT, height = WRAP_CONTENT).

### 4.6. Native Ads in ViewPager2 / Onboarding
1. Set `viewPager.offscreenPageLimit = pages.size` to prevent view destruction during swipes.
2. Maintain a map of instances: `val bottomAdViews = mutableMapOf<Int, OzAdmobNativeAd>()`.
3. Preload all slide ads in advance (e.g. from Splash or language screen) with small delays between each preload call.

### 4.7. App Resume / Open Ads (`AppResumeOpenManager`)
1. In `SplashActivity.initAds()` or `MainActivity.onCreate()`:
   ```kotlin
   AppResumeOpenManager.getInstance().apply {
       init(application)
       setAdUnitId(AdsManager.getAdUnitId(BaseConstants.OPEN_RESUME))
       setSplashActivity(SplashActivity::class.java)
       fetchAd()
   }
   ```
2. The manager automatically handles `ActivityLifecycleCallbacks` and shows the open ad when the app transitions from background to foreground, while automatically respecting fullscreen ad flags (`isFullScreenAdShowing`).

---

## 5. References & Examples
For complete implementations and deep-dive technical references:
- [Lifecycle & Race Conditions Reference](./references/lifecycle_and_race_conditions.md)
- [Native Ad Binding & R8 ProGuard Reference](./references/native_ads_binding_and_r8.md)
- [Complete Admob Integration Reference Code](./examples/AdmobIntegrationExample.kt)
