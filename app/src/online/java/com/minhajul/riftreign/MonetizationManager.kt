package com.minhajul.riftreign

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MonetizationManager(
    private val activity: Activity,
    private val economy: EconomyStore,
    private val onStateChanged: () -> Unit
) : MonetizationUi, PurchasesUpdatedListener {

    @Volatile private var statusText = when {
        BuildConfig.BILLING_ENABLED -> "Connecting to Play Store..."
        BuildConfig.ADS_ENABLED -> "Preparing optional ads..."
        else -> "Offline economy ready"
    }
    private val productDetails = mutableMapOf<String, ProductDetails>()
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var adsInitialized = false
    private var billingConnecting = false
    private var rewardedLoading = false
    private var interstitialLoading = false
    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

    private val billingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (BuildConfig.BILLING_ENABLED) startBilling()
        if (BuildConfig.ADS_ENABLED) requestConsentAndAds()
        changed()
    }

    fun onResume() {
        if (BuildConfig.BILLING_ENABLED) {
            if (billingClient.isReady) {
                queryPurchases(false)
                queryProducts()
            } else {
                startBilling()
            }
        }
        if (BuildConfig.ADS_ENABLED && adsInitialized) {
            loadRewarded()
            loadInterstitial()
        }
    }

    fun destroy() {
        try { billingClient.endConnection() } catch (_: Exception) { }
        rewardedAd = null
        interstitialAd = null
    }

    private fun startBilling() {
        if (!BuildConfig.BILLING_ENABLED) return
        if (billingClient.isReady || billingConnecting) return
        billingConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                billingConnecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    statusText = "Play Store connected"
                    queryProducts()
                    queryPurchases(false)
                } else {
                    statusText = "Play Store unavailable"
                    Log.w(TAG, "Billing setup: ${result.responseCode} ${result.debugMessage}")
                }
                changed()
            }

            override fun onBillingServiceDisconnected() {
                billingConnecting = false
                statusText = "Play Store reconnecting..."
                changed()
            }
        })
    }

    private fun queryProducts() {
        if (!BuildConfig.BILLING_ENABLED || !billingClient.isReady) return
        val ids = if (BuildConfig.CONSUMABLE_PURCHASES_ENABLED) StoreProducts.ALL else listOf(StoreProducts.REMOVE_ADS)
        val list = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(list).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails.clear()
                detailsResult.productDetailsList.forEach { productDetails[it.productId] = it }
                statusText = if (productDetails.isEmpty()) {
                    "Create the products in Play Console to enable purchases"
                } else {
                    "Store ready"
                }
            } else {
                statusText = "Products unavailable: ${result.debugMessage.take(48)}"
            }
            changed()
        }
    }

    override fun buy(productId: String) {
        if (!BuildConfig.BILLING_ENABLED) {
            statusText = "Purchases are not enabled in this release"
            changed()
            return
        }
        if (!BuildConfig.CONSUMABLE_PURCHASES_ENABLED &&
            productId in setOf(StoreProducts.CRYSTALS_120, StoreProducts.CRYSTALS_650, StoreProducts.CRYSTALS_1500)
        ) {
            statusText = "Paid Rift Crystal packs are not enabled in this release"
            changed()
            return
        }
        if (!billingClient.isReady) {
            statusText = "Play Store is not ready"
            startBilling()
            changed()
            return
        }
        val details = productDetails[productId]
        if (details == null) {
            statusText = "Product not configured in Play Console"
            queryProducts()
            changed()
            return
        }

        val detailsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: details.oneTimePurchaseOfferDetails
        val offerToken = offer?.offerToken
        if (!offerToken.isNullOrBlank()) detailsBuilder.setOfferToken(offerToken)

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            statusText = "Purchase could not start: ${result.debugMessage.take(48)}"
            changed()
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> statusText = "Purchase cancelled"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases(true)
            else -> statusText = "Purchase error: ${result.debugMessage.take(56)}"
        }
        changed()
    }

    override fun restorePurchases() {
        if (!BuildConfig.BILLING_ENABLED) {
            statusText = "Purchases are not enabled in this release"
            changed()
            return
        }
        statusText = "Restoring purchases..."
        changed()
        if (billingClient.isReady) queryPurchases(true) else startBilling()
    }

    private fun queryPurchases(userRequested: Boolean) {
        if (!BuildConfig.BILLING_ENABLED || !billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Reconcile the permanent entitlement with Google Play so refunds/revocations
                // do not leave Remove Ads enabled forever on this device.
                val removeAdsOwned = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        StoreProducts.REMOVE_ADS in purchase.products
                }
                economy.setAdsRemoved(removeAdsOwned)
                purchases.forEach(::processPurchase)
                if (userRequested) statusText = "Purchases restored"
            } else if (userRequested) {
                statusText = "Restore failed: ${result.debugMessage.take(50)}"
            }
            changed()
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                statusText = "Payment pending — item will unlock after payment"
            }
            return
        }
        purchase.products.forEach { productId ->
            when (productId) {
                StoreProducts.REMOVE_ADS -> grantRemoveAds(purchase)
                StoreProducts.CRYSTALS_120 -> if (BuildConfig.CONSUMABLE_PURCHASES_ENABLED) grantConsumable(purchase, 120)
                StoreProducts.CRYSTALS_650 -> if (BuildConfig.CONSUMABLE_PURCHASES_ENABLED) grantConsumable(purchase, 650)
                StoreProducts.CRYSTALS_1500 -> if (BuildConfig.CONSUMABLE_PURCHASES_ENABLED) grantConsumable(purchase, 1500)
            }
        }
    }

    private fun grantRemoveAds(purchase: Purchase) {
        economy.setAdsRemoved(true)
        statusText = "Ads removed — thank you for supporting RiftReign"
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
                }
            }
        }
        changed()
    }

    private fun grantConsumable(purchase: Purchase, crystals: Int) {
        val token = purchase.purchaseToken
        if (!economy.hasProcessedPurchase(token)) {
            economy.grantCrystals(crystals)
            economy.markProcessedPurchase(token)
            statusText = "+$crystals Rift Crystals"
            changed()
        }
        val params = ConsumeParams.newBuilder().setPurchaseToken(token).build()
        billingClient.consumeAsync(params) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK &&
                result.responseCode != BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
                Log.w(TAG, "Consume failed: ${result.debugMessage}")
            }
        }
    }

    override fun price(productId: String): String? {
        val details = productDetails[productId] ?: return null
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: details.oneTimePurchaseOfferDetails
        return offer?.formattedPrice
    }

    private fun requestConsentAndAds() {
        if (!BuildConfig.ADS_ENABLED) return
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    if (consentInformation.canRequestAds()) initializeAds()
                    changed()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update: ${requestError.message}")
                if (consentInformation.canRequestAds()) initializeAds()
                changed()
            }
        )
        if (consentInformation.canRequestAds()) initializeAds()
    }

    private fun initializeAds() {
        if (!BuildConfig.ADS_ENABLED || adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(activity) {
            loadRewarded()
            loadInterstitial()
        }
    }

    private fun loadRewarded() {
        if (!BuildConfig.ADS_ENABLED || !adsInitialized || rewardedAd != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            activity,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewardedAd = ad
                    changed()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewardedAd = null
                    Log.d(TAG, "Rewarded ad unavailable: ${error.message}")
                    changed()
                }
            }
        )
    }

    override fun isRewardedReady(): Boolean = BuildConfig.ADS_ENABLED && rewardedAd != null

    override fun showRewardedCrystals() {
        if (!BuildConfig.ADS_ENABLED) {
            statusText = "Rewarded ads are not enabled in this release"
            changed()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            statusText = "Rewarded ad is loading — try again shortly"
            loadRewarded()
            changed()
            return
        }
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                changed()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                statusText = "Ad could not be shown"
                loadRewarded()
                changed()
            }
        }
        ad.show(activity) {
            economy.grantCrystals(StoreProducts.REWARDED_CRYSTALS)
            statusText = "+${StoreProducts.REWARDED_CRYSTALS} Rift Crystals"
            changed()
        }
    }

    private fun loadInterstitial() {
        if (!BuildConfig.ADS_ENABLED || !adsInitialized || economy.adsRemoved || interstitialAd != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            activity,
            BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitialAd = null
                    Log.d(TAG, "Interstitial unavailable: ${error.message}")
                }
            }
        )
    }

    override fun maybeShowInterstitial(completedRuns: Int, onComplete: () -> Unit) {
        if (!BuildConfig.ADS_ENABLED) {
            onComplete()
            return
        }
        // Gentle monetization: no forced ad in the first two runs, then at most every third completed run.
        if (economy.adsRemoved || completedRuns < 3 || completedRuns % 3 != 0) {
            onComplete()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial()
            onComplete()
            return
        }
        interstitialAd = null
        var completed = false
        fun finishOnce() {
            if (completed) return
            completed = true
            onComplete()
            loadInterstitial()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishOnce()
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finishOnce()
        }
        ad.show(activity)
    }

    override fun isPrivacyOptionsRequired(): Boolean =
        BuildConfig.ADS_ENABLED &&
            consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions() {
        if (!BuildConfig.ADS_ENABLED) {
            statusText = "Advertising privacy controls are not active in this release"
            changed()
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) statusText = "Privacy options unavailable: ${error.message.take(40)}"
            else statusText = "Privacy choices updated"
            if (consentInformation.canRequestAds()) initializeAds()
            changed()
        }
    }

    override fun isAdsEnabled(): Boolean = BuildConfig.ADS_ENABLED

    override fun isBillingEnabled(): Boolean = BuildConfig.BILLING_ENABLED

    override fun isConsumablePurchasesEnabled(): Boolean = BuildConfig.CONSUMABLE_PURCHASES_ENABLED

    override fun status(): String = statusText

    private fun changed() {
        activity.runOnUiThread(onStateChanged)
    }
    companion object {
        private const val TAG = "RiftReignMonetization"
    }
}
