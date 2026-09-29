package com.syedali.flashquiz.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreProduct

/**
 * RevenueCat subscription manager.
 * Handles ad-free premium subscription.
 *
 * Setup:
 * 1. Create RevenueCat account at app.revenuecat.com
 * 2. Create app with package name com.syedali.flashquiz
 * 3. Create offerings: "monthly" ($2.99/mo) and "yearly" ($19.99/yr)
 * 4. Replace REVENUECAT_API_KEY below with your public SDK key
 */
object SubscriptionManager {

    // RevenueCat public SDK key (from app.revenuecat.com > Project Settings > API Keys)
    private const val REVENUECAT_API_KEY = "test_PLZYtkruffqAiygwlPbtiRIrlha"

    // Entitlement ID (must match what you set in RevenueCat dashboard)
    const val ENTITLEMENT_AD_FREE = "com_syedali_flashquiz_pro"

    fun initialize(context: Context) {
        val config = PurchasesConfiguration.Builder(context, REVENUECAT_API_KEY)
            .diagnosticsEnabled(false)
            .build()
        Purchases.configure(config)
    }

    fun checkAdFree(callback: (Boolean) -> Unit) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val isActive = customerInfo.entitlements[ENTITLEMENT_AD_FREE]?.isActive == true
                callback(isActive)
            }

            override fun onError(error: PurchasesError) {
                Log.w("SubscriptionManager", describe(error))
                callback(false)
            }
        })
    }

    fun purchaseMonthly(activity: Activity, callback: (Boolean, String?) -> Unit) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val pkg = offerings.current?.monthly
                if (pkg == null) {
                    callback(false, "Monthly plan not available")
                    return
                }
                makePurchase(activity, pkg.product, callback)
            }

            override fun onError(error: PurchasesError) {
                callback(false, describe(error))
            }
        })
    }

    fun purchaseYearly(activity: Activity, callback: (Boolean, String?) -> Unit) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val pkg = offerings.current?.annual
                if (pkg == null) {
                    callback(false, "Yearly plan not available")
                    return
                }
                makePurchase(activity, pkg.product, callback)
            }

            override fun onError(error: PurchasesError) {
                callback(false, describe(error))
            }
        })
    }

    private fun describe(error: PurchasesError): String {
        val underlying = error.underlyingErrorMessage
        return if (underlying.isNullOrBlank()) error.message else "${error.message} (${underlying})"
    }

    private fun makePurchase(activity: Activity, product: StoreProduct, callback: (Boolean, String?) -> Unit) {
        val purchaseParams = PurchaseParams.Builder(activity, product).build()

        Purchases.sharedInstance.purchase(
            purchaseParams,
            object : com.revenuecat.purchases.interfaces.PurchaseCallback {
                override fun onCompleted(storeTransaction: com.revenuecat.purchases.models.StoreTransaction, customerInfo: CustomerInfo) {
                    val isActive = customerInfo.entitlements[ENTITLEMENT_AD_FREE]?.isActive == true
                    callback(isActive, null)
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    if (userCancelled) {
                        callback(false, "Cancelled")
                    } else {
                        callback(false, describe(error))
                    }
                }
            }
        )
    }

    fun restorePurchases(callback: (Boolean) -> Unit) {
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val isActive = customerInfo.entitlements[ENTITLEMENT_AD_FREE]?.isActive == true
                callback(isActive)
            }

            override fun onError(error: PurchasesError) {
                Log.w("SubscriptionManager", describe(error))
                callback(false)
            }
        })
    }

    fun logout() {
        Purchases.sharedInstance.logOut()
    }
}
