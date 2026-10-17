# Native Ad Resource Binding, Layouts & R8 ProGuard Optimization

This document covers how native ads are laid out and bound in the Oz Ads SDK, how to prevent release-build shrinking bugs, and how to write clean, reusable XML templates.

---

## 1. Native Ad View Structure

Every native ad layout requires Google AdMob's `NativeAdView` as the root element containing child views for standard ad assets:
- Headline (`TextView`)
- Body / Secondary text (`TextView`)
- Call to Action button (`MaterialButton` / `TextView` / `Button`)
- App Icon (`ImageView`)
- Media Content (`com.google.android.gms.ads.nativead.MediaView`)
- Ad Attribution badge / AdChoices (`TextView` / `com.google.android.gms.ads.nativead.AdChoicesView`)

### Standard XML Layout Example (`custom_native_admob_medium.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.gms.ads.nativead.NativeAdView 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="8dp">

    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:cardCornerRadius="16dp"
        app:cardElevation="2dp"
        app:strokeColor="#ECEFF1"
        app:strokeWidth="1dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <!-- Top Row: Icon + Headline + Ad Badge -->
            <RelativeLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

                <ImageView
                    android:id="@+id/ad_app_icon"
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:layout_alignParentStart="true"
                    android:contentDescription="Ad icon" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_toEndOf="@id/ad_app_icon"
                    android:layout_marginStart="10dp"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/ad_headline"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:fontFamily="@font/quicksand_bold"
                        android:textSize="15sp"
                        android:textColor="#263238" />

                    <TextView
                        android:id="@+id/ad_body"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="12sp"
                        android:textColor="#78909C" />
                </LinearLayout>
            </RelativeLayout>

            <!-- Media Container -->
            <com.google.android.gms.ads.nativead.MediaView
                android:id="@+id/ad_media"
                android:layout_width="match_parent"
                android:layout_height="180dp"
                android:layout_marginTop="8dp" />

            <!-- Call to Action Button -->
            <com.google.android.material.button.MaterialButton
                android:id="@+id/ad_call_to_action"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:layout_marginTop="8dp"
                android:fontFamily="@font/quicksand_bold"
                android:textColor="#FFFFFF"
                app:backgroundTint="#E65100"
                app:cornerRadius="22dp" />

        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</com.google.android.gms.ads.nativead.NativeAdView>
```

---

## 2. R8 / Resource Shrinking Hazard & Protection

### The Problem
In `OzAdmobNativeAd`, view components are dynamically resolved at runtime by looking up string identifiers via:
```kotlin
context.resources.getIdentifier(idName, "id", packageName)
```
When building release APKs with `minifyEnabled true` and `shrinkResources true`, R8's resource shrinker looks for direct static compile-time references (`R.id.ad_headline`). Because string literals passed to `getIdentifier()` are invisible to static analysis, R8 will **strip or rename** the view IDs from compiled resources.

At runtime, `getIdentifier()` returns `0`, and the ad displays with empty headlines, missing CTA buttons, or unclickable areas, severely degrading eCPM and fill rates.

### The Solution: Keep Configuration

1. **Option 1: Declare standard IDs in `res/values/ids.xml`**
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources>
       <item name="ad_headline" type="id" />
       <item name="ad_body" type="id" />
       <item name="ad_call_to_action" type="id" />
       <item name="ad_app_icon" type="id" />
       <item name="ad_media" type="id" />
       <item name="ad_price" type="id" />
       <item name="ad_stars" type="id" />
       <item name="ad_store" type="id" />
       <item name="ad_advertiser" type="id" />
       <item name="ad_choices" type="id" />
   </resources>
   ```

2. **Option 2: Add `res/raw/keep.xml`**
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources xmlns:tools="http://schemas.android.com/tools"
       tools:keep="@id/ad_headline,@id/ad_body,@id/ad_call_to_action,@id/ad_app_icon,@id/ad_media,@id/ad_choices" />
   ```

3. **ProGuard Rules in `proguard-rules.pro`**:
   ```proguard
   # Keep Google Play Ads classes
   -keep class com.google.android.gms.ads.** { *; }
   -keep interface com.google.android.gms.ads.** { *; }

   # Keep Oz Ads SDK classes
   -keep class com.oz.android.** { *; }
   ```
