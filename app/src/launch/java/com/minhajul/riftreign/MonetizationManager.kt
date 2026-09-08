package com.minhajul.riftreign

import android.app.Activity

/**
 * First-upload / closed-test implementation. No ad, billing, consent or network SDK is
 * linked into the launch flavor, so testers can validate the complete offline game
 * without external-service configuration.
 */
class MonetizationManager(
    private val activity: Activity,
    private val economy: EconomyStore,
    private val onStateChanged: () -> Unit
) : MonetizationUi {
    fun start() = changed()
    fun onResume() = Unit
    fun destroy() = Unit

    override fun buy(productId: String) = Unit
    override fun restorePurchases() = changed()
    override fun showRewardedCrystals() = Unit
    override fun maybeShowInterstitial(completedRuns: Int, onComplete: () -> Unit) = onComplete()
    override fun price(productId: String): String? = null
    override fun isRewardedReady(): Boolean = false
    override fun isPrivacyOptionsRequired(): Boolean = false
    override fun showPrivacyOptions() = Unit
    override fun isAdsEnabled(): Boolean = false
    override fun isBillingEnabled(): Boolean = false
    override fun isConsumablePurchasesEnabled(): Boolean = false
    override fun status(): String = "Offline launch build // no ads or purchases"

    private fun changed() = activity.runOnUiThread(onStateChanged)
}
