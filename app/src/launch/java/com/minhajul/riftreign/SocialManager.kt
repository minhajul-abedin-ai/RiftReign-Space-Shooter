package com.minhajul.riftreign

import android.app.Activity
import android.content.Context

/** Local-only achievement implementation for the first Play closed-test build. */
class SocialManager(
    private val activity: Activity,
    private val onStateChanged: () -> Unit
) : SocialUi {
    private val prefs = activity.getSharedPreferences("rift_reign_social", Context.MODE_PRIVATE)

    fun start() = changed()
    fun onResume() = Unit

    override fun status(): String = "Local achievements active // online services planned for a later update"
    override fun isOnlineFeaturesAvailable(): Boolean = false
    override fun isConnected(): Boolean = false
    override fun signIn() = changed()
    override fun showLeaderboard() = changed()
    override fun showAchievements() = changed()
    override fun saveCloud(payload: String, onResult: (String) -> Unit) = onResult("Cloud save is not enabled in this launch build")
    override fun loadCloud(onResult: (String?, String) -> Unit) = onResult(null, "Cloud save is not enabled in this launch build")
    override fun deleteCloud(onResult: (String) -> Unit) = onResult("No RiftReign cloud save exists in this launch build")

    override fun onRunFinished(
        score: Long,
        wave: Int,
        runKills: Int,
        totalRuns: Int,
        totalKills: Long,
        overdrive: Boolean,
        shipCount: Int
    ) {
        setLocal("first_rift", totalRuns >= 1)
        setLocal("wave_5", wave >= 5)
        setLocal("wave_10", wave >= 10)
        setLocal("hunter_100", totalKills >= 100)
        setLocal("hunter_1000", totalKills >= 1000)
        setLocal("score_10000", score >= 10_000)
        setLocal("overdrive_5", overdrive && wave >= 5)
        setLocal("collector", shipCount >= 3)
        changed()
    }

    override fun localAchievements(): List<SocialAchievement> = listOf(
        SocialAchievement("first_rift", "First Rift", "Complete your first run", isLocal("first_rift")),
        SocialAchievement("wave_5", "Rift Walker", "Reach wave 5", isLocal("wave_5")),
        SocialAchievement("wave_10", "Deep Space", "Reach wave 10", isLocal("wave_10")),
        SocialAchievement("hunter_100", "Hunter", "Destroy 100 enemies", isLocal("hunter_100")),
        SocialAchievement("hunter_1000", "Exterminator", "Destroy 1,000 enemies", isLocal("hunter_1000")),
        SocialAchievement("score_10000", "Five Digits", "Score 10,000 in one run", isLocal("score_10000")),
        SocialAchievement("overdrive_5", "Overdrive", "Reach wave 5 on Overdrive", isLocal("overdrive_5")),
        SocialAchievement("collector", "Collector", "Own 3 aircraft", isLocal("collector"))
    )

    private fun setLocal(key: String, value: Boolean) {
        if (value && !isLocal(key)) prefs.edit().putBoolean("ach_$key", true).apply()
    }

    private fun isLocal(key: String): Boolean = prefs.getBoolean("ach_$key", false)
    private fun changed() = activity.runOnUiThread(onStateChanged)
}
