# Lifecycle & Race Conditions Reference Guide

This document explains the internal state machine of `OzAdsManager` and how to avoid the most common asynchronous bugs in production Android ad integrations.

---

## 1. The AdState State Machine

Ad placements progress through four primary states:

```
         +---------------------------------------+
         |                                       | (Load Failed /
         v                                       |  Dismissed)
     +-------+             +---------+           |
     | IDLE  | ----------> | LOADING | ----------+
     +-------+  (loadAd)   +---------+
         ^                      |
         |                      | (onAdLoaded)
         |                      v
         |                 +---------+
         +---------------- | LOADED  |
         |  (destroy/      +---------+
         |   failure)           |
         |                      | (showAds)
         |                      v
         |                 +---------+
         +---------------- | SHOWING |
                           +---------+
```

1. **`IDLE`**: No ad loaded or loading for this key.
2. **`LOADING`**: An asynchronous network request has been triggered.
3. **`LOADED`**: The ad network returned the ad object; cached in `adStore`.
4. **`SHOWING`**: The ad is actively displayed to the user.

---

## 2. Five Critical Pitfalls & How to Avoid Them

### Pitfall 1: Shared Key View Destruction Clearing Global Pending State
**Problem**:
If Screen A and Screen B share the same placement key (e.g. `"banner_key"` or `"native_key"`), when Screen A detaches, its cleanup code clears the pending show callback or resets the state to `IDLE`. If Screen B was waiting for that ad to load, Screen B's callback is erased and the ad is never displayed.

**Remediation**:
- Always enforce **strictly unique keys per position**.
- For shared views (like `BaseFragment`), construct composite keys:
  ```kotlin
  val uniqueKey = "${fragmentName}_${BaseConstants.BANNER_SELECT}"
  ```

---

### Pitfall 2: Overwriting `pendingShows` Map
**Problem**:
`OzAdsManager` stores pending shows in `pendingShows: ConcurrentHashMap<String, () -> Unit>`. If `loadThenShow(key)` is invoked multiple times while state is `LOADING`, each call replaces the runnable. If a view is recreated quickly, the destroyed view's cleanup might call `clearPendingShow(key)` and wipe the *new* view's callback.

**Remediation**:
- In view destruction, call **only** `clearPendingShow(key)`. Never call `onDestroyAd(key)` or `setAdState(key, IDLE)`.
- Global ad state preservation allows the recreated view to find the ad in `LOADED` state immediately upon attachment and display without extra network delay.

---

### Pitfall 3: Showing-State Corruption (Preloading During Show)
**Problem**:
Preloading an ad while an active ad is showing (state = `SHOWING`) can overwrite the global state of the key to `LOADING`, losing track of the fact that an ad is currently visible.

**Remediation**:
- Separate preload keys from showing keys, or use dedicated helper classes (e.g., `RewardAdHelper`) which handle auto-reload strictly inside the dismissal callback (`onAdDismissedFullScreenContent`).

---

### Pitfall 4: RecyclerView & ViewPager2 Detachment Trashing
**Problem**:
Standard `InlineAds` (and `OzAdmobNativeAd`) invokes `destroy()` inside `onDetachedFromWindow()`. In scrolling lists or view pagers, views detach when scrolled off screen. This destroys the native ad and nulls out the internal `adKey`, causing blank slots or crashes when scrolling back.

**Remediation**:
- Use `PersistentNativeAdView`:
  ```kotlin
  override fun onDetachedFromWindow() {
      // Do NOT call super.onDetachedFromWindow()!
      // This preserves the loaded ad object in memory while scrolled off-screen.
  }
  ```
- If the global state is `SHOWING`, re-populate the container using `OzAdsManager.getInstance().getAd(key)`.

---

### Pitfall 5: Deadlock on Fullscreen Ad Callbacks
**Problem**:
If an ad request fails silently (network timeout, no fill, ad blocker), or if an error listener is missing, the calling screen never receives an `onComplete()` callback, locking the user out of the app.

**Remediation**:
- Always wrap interstitial and rewarded ad invocations in an atomic timeout guard:
  ```kotlin
  val isCompleted = AtomicBoolean(false)
  val handler = Handler(Looper.getMainLooper())
  val timeout = Runnable {
      if (isCompleted.compareAndSet(false, true)) {
          dismissLoading()
          onComplete()
      }
  }
  handler.postDelayed(timeout, 4000L) // 4 seconds max wait
  ```
- Clear the timeout as soon as `onAdShowedFullScreenContent()` fires.
