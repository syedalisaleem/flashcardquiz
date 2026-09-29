# FlashcardQuiz ProGuard rules

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep Material
-keep class com.google.android.material.** { *; }

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.android.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.**

# General
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# AdMob / Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**
