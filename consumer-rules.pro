# Add project specific ProGuard rules here.
# This file is used for library modules to specify rules that will be
# applied to consumers of this library.

# Preserve GMS Ads classes/methods invoked reflectively in OzAds
-keep class com.google.android.gms.ads.MobileAds {
    public static void initialize(android.content.Context, com.google.android.gms.ads.initialization.OnInitializationCompleteListener);
    public static void setRequestConfiguration(com.google.android.gms.ads.RequestConfiguration);
}
-keep class com.google.android.gms.ads.RequestConfiguration { *; }
-keep class com.google.android.gms.ads.RequestConfiguration$Builder { *; }
-keep interface com.google.android.gms.ads.initialization.OnInitializationCompleteListener { *; }
-keep interface com.google.android.gms.ads.initialization.InitializationStatus { *; }
-keep interface com.google.android.gms.ads.initialization.AdapterStatus { *; }
-keep interface com.google.android.gms.ads.nativead.NativeAd$OnNativeAdLoadedListener { *; }
-keep class com.google.android.gms.ads.VideoOptions { *; }
-keep class com.google.android.gms.ads.VideoOptions$Builder { *; }
-keep class com.google.android.gms.ads.nativead.NativeAdOptions { *; }
-keep class com.google.android.gms.ads.nativead.NativeAdOptions$Builder { *; }
