package com.oz.android.wrapper

import android.content.Context
import android.util.AttributeSet
import com.oz.android.ads.oz_ads.ads_component.ads_inline.admob.OzAdmobBannerAd
import com.oz.android.ads.oz_ads.ads_component.ads_inline.admob.OzAdmobNativeAd
import com.oz.android.ads.oz_ads.ads_component.ads_overlay.admob.OzAdmobIntersAd
import com.oz.android.ads.oz_ads.ads_component.ads_overlay.admob.OzAdmobRewardAds
import com.oz.android.ads.oz_ads.ads_component.ads_overlay.admob.OzAdmobOpenAd

class OzAdmobRewardedAds @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : OzAdmobRewardAds(context, attrs)

class OzAdmobIntersAd @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : OzAdmobIntersAd(context, attrs)

class OzAdmobOpenAd @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : OzAdmobOpenAd(context, attrs)

class OzAdmobNativeAd @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : OzAdmobNativeAd(context, attrs)

class OzAdmobBannerAd @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : OzAdmobBannerAd(context, attrs)
