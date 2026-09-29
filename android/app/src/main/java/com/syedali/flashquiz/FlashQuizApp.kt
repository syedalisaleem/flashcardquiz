package com.syedali.flashquiz

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.syedali.flashquiz.billing.SubscriptionManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlashQuizApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        MobileAds.initialize(this) { }

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("EMULATOR"))
                .build()
        )

        SubscriptionManager.initialize(this)
    }
}
