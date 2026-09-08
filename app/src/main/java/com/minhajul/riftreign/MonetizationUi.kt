package com.minhajul.riftreign

interface MonetizationUi {
    fun buy(productId: String)
    fun restorePurchases()
    fun showRewardedCrystals()
    fun maybeShowInterstitial(completedRuns: Int, onComplete: () -> Unit)
    fun price(productId: String): String?
    fun isRewardedReady(): Boolean
    fun isPrivacyOptionsRequired(): Boolean
    fun showPrivacyOptions()
    fun isAdsEnabled(): Boolean
    fun isBillingEnabled(): Boolean
    fun isConsumablePurchasesEnabled(): Boolean
    fun status(): String
}

object StoreProducts {
    const val REMOVE_ADS = "remove_ads"
    const val CRYSTALS_120 = "rift_crystals_120"
    const val CRYSTALS_650 = "rift_crystals_650"
    const val CRYSTALS_1500 = "rift_crystals_1500"
    val ALL = listOf(REMOVE_ADS, CRYSTALS_120, CRYSTALS_650, CRYSTALS_1500)
    const val REWARDED_CRYSTALS = 20
}
