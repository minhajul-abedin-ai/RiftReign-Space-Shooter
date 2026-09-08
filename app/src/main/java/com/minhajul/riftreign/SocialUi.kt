package com.minhajul.riftreign

data class SocialAchievement(
    val key: String,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

interface SocialUi {
    fun status(): String
    fun isOnlineFeaturesAvailable(): Boolean
    fun isConnected(): Boolean
    fun signIn()
    fun showLeaderboard()
    fun showAchievements()
    fun saveCloud(payload: String, onResult: (String) -> Unit)
    fun loadCloud(onResult: (String?, String) -> Unit)
    fun deleteCloud(onResult: (String) -> Unit)
    fun onRunFinished(score: Long, wave: Int, runKills: Int, totalRuns: Int, totalKills: Long, overdrive: Boolean, shipCount: Int)
    fun localAchievements(): List<SocialAchievement>
}
