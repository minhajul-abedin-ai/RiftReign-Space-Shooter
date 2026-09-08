package com.minhajul.riftreign

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import java.nio.charset.StandardCharsets

class SocialManager(
    private val activity: Activity,
    private val onStateChanged: () -> Unit
) : SocialUi {
    private val prefs = activity.getSharedPreferences("rift_reign_social", Context.MODE_PRIVATE)
    private var connected = false
    @Volatile private var statusText = if (BuildConfig.PLAY_GAMES_CONFIGURED) "Play Games connecting..." else "Local achievements active // online services not enabled"

    fun start() {
        if (!BuildConfig.PLAY_GAMES_CONFIGURED) {
            changed()
            return
        }
        try {
            PlayGamesSdk.initialize(activity)
            refreshAuth(false)
        } catch (t: Throwable) {
            statusText = "Play Games unavailable"
            Log.w(TAG, "Play Games initialization failed", t)
            changed()
        }
    }

    fun onResume() {
        if (BuildConfig.PLAY_GAMES_CONFIGURED) refreshAuth(false)
    }

    private fun refreshAuth(forceSignIn: Boolean) {
        try {
            val client = PlayGames.getGamesSignInClient(activity)
            val task = if (forceSignIn) client.signIn() else client.isAuthenticated
            task.addOnCompleteListener { result ->
                connected = result.isSuccessful && result.result?.isAuthenticated == true
                statusText = if (connected) "Play Games connected // cloud ready" else "Play Games not connected"
                changed()
            }
        } catch (t: Throwable) {
            connected = false
            statusText = "Play Games unavailable"
            Log.w(TAG, "Play Games auth failed", t)
            changed()
        }
    }

    override fun status(): String = statusText
    override fun isOnlineFeaturesAvailable(): Boolean = BuildConfig.PLAY_GAMES_CONFIGURED
    override fun isConnected(): Boolean = connected

    override fun signIn() {
        if (!BuildConfig.PLAY_GAMES_CONFIGURED) {
            statusText = "Online Play Games features are not enabled in this release"
            changed()
            return
        }
        statusText = "Opening Play Games..."
        changed()
        refreshAuth(true)
    }

    override fun showLeaderboard() {
        if (!connected) {
            statusText = "Connect Play Games to open the global leaderboard"
            changed()
            return
        }
        PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(activity.getString(R.string.leaderboard_high_score))
            .addOnSuccessListener { activity.startActivityForResult(it, RC_LEADERBOARD) }
            .addOnFailureListener {
                statusText = "Leaderboard unavailable"
                Log.w(TAG, "Leaderboard UI failed", it)
                changed()
            }
    }

    override fun showAchievements() {
        if (!connected) {
            statusText = "Connect Play Games to open platform achievements"
            changed()
            return
        }
        PlayGames.getAchievementsClient(activity)
            .achievementsIntent
            .addOnSuccessListener { activity.startActivityForResult(it, RC_ACHIEVEMENTS) }
            .addOnFailureListener {
                statusText = "Achievements unavailable"
                Log.w(TAG, "Achievements UI failed", it)
                changed()
            }
    }

    override fun saveCloud(payload: String, onResult: (String) -> Unit) {
        if (!connected) {
            onResult("Connect Play Games before cloud saving")
            return
        }
        try {
            val client = PlayGames.getSnapshotsClient(activity)
            client.open(SAVE_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                .addOnSuccessListener { opened ->
                    val snapshot = opened.data
                    if (snapshot == null) {
                        onResult("Cloud save could not be opened")
                        return@addOnSuccessListener
                    }
                    try {
                        snapshot.snapshotContents.writeBytes(payload.toByteArray(StandardCharsets.UTF_8))
                        val changes = SnapshotMetadataChange.Builder()
                            .setDescription("RiftReign pilot progress")
                            .build()
                        client.commitAndClose(snapshot, changes)
                            .addOnSuccessListener {
                                statusText = "Cloud save complete"
                                onResult(statusText)
                                changed()
                            }
                            .addOnFailureListener {
                                statusText = "Cloud save failed"
                                Log.w(TAG, "Cloud commit failed", it)
                                onResult(statusText)
                                changed()
                            }
                    } catch (t: Throwable) {
                        statusText = "Cloud save failed"
                        Log.w(TAG, "Cloud write failed", t)
                        onResult(statusText)
                        changed()
                    }
                }
                .addOnFailureListener {
                    statusText = "Cloud save unavailable"
                    Log.w(TAG, "Snapshot open failed", it)
                    onResult(statusText)
                    changed()
                }
        } catch (t: Throwable) {
            statusText = "Cloud save unavailable"
            Log.w(TAG, "Cloud save failed", t)
            onResult(statusText)
            changed()
        }
    }

    override fun loadCloud(onResult: (String?, String) -> Unit) {
        if (!connected) {
            onResult(null, "Connect Play Games before cloud loading")
            return
        }
        val client = PlayGames.getSnapshotsClient(activity)
        client.open(SAVE_NAME, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnSuccessListener { opened ->
                val snapshot = opened.data
                if (snapshot == null) {
                    onResult(null, "No cloud save found")
                    return@addOnSuccessListener
                }
                try {
                    val bytes = snapshot.snapshotContents.readFully()
                    val payload = String(bytes, StandardCharsets.UTF_8)
                    statusText = if (payload.isBlank()) "Cloud save is empty" else "Cloud progress loaded"
                    onResult(payload.takeIf { it.isNotBlank() }, statusText)
                    changed()
                } catch (t: Throwable) {
                    statusText = "Cloud load failed"
                    Log.w(TAG, "Cloud read failed", t)
                    onResult(null, statusText)
                    changed()
                } finally {
                    try { client.discardAndClose(snapshot) } catch (_: Throwable) { }
                }
            }
            .addOnFailureListener {
                statusText = "No cloud save found"
                onResult(null, statusText)
                changed()
            }
    }

    override fun deleteCloud(onResult: (String) -> Unit) {
        if (!connected) {
            onResult("Connect Play Games before deleting cloud progress")
            return
        }
        val client = PlayGames.getSnapshotsClient(activity)
        client.open(SAVE_NAME, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnSuccessListener { opened ->
                val snapshot = opened.data
                if (snapshot == null) {
                    onResult("No cloud save found")
                    return@addOnSuccessListener
                }
                val metadata = snapshot.metadata
                client.discardAndClose(snapshot)
                    .addOnCompleteListener {
                        client.delete(metadata)
                            .addOnSuccessListener {
                                statusText = "Cloud save deleted"
                                onResult(statusText)
                                changed()
                            }
                            .addOnFailureListener { error ->
                                statusText = "Cloud delete failed"
                                Log.w(TAG, "Cloud delete failed", error)
                                onResult(statusText)
                                changed()
                            }
                    }
            }
            .addOnFailureListener {
                statusText = "No cloud save found"
                onResult(statusText)
                changed()
            }
    }

    override fun onRunFinished(
        score: Long,
        wave: Int,
        runKills: Int,
        totalRuns: Int,
        totalKills: Long,
        overdrive: Boolean,
        shipCount: Int
    ) {
        // Local achievements always work offline.
        setLocal("first_rift", totalRuns >= 1)
        setLocal("wave_5", wave >= 5)
        setLocal("wave_10", wave >= 10)
        setLocal("hunter_100", totalKills >= 100)
        setLocal("hunter_1000", totalKills >= 1000)
        setLocal("score_10000", score >= 10_000)
        setLocal("overdrive_5", overdrive && wave >= 5)
        setLocal("collector", shipCount >= 3)

        if (!connected) {
            changed()
            return
        }
        val leaderboards = PlayGames.getLeaderboardsClient(activity)
        leaderboards.submitScore(activity.getString(R.string.leaderboard_high_score), score)
        val a = PlayGames.getAchievementsClient(activity)
        if (totalRuns >= 1) a.unlock(activity.getString(R.string.achievement_first_rift))
        if (wave >= 5) a.unlock(activity.getString(R.string.achievement_wave_5))
        if (wave >= 10) a.unlock(activity.getString(R.string.achievement_wave_10))
        if (totalKills >= 100) a.unlock(activity.getString(R.string.achievement_hunter_100))
        if (totalKills >= 1000) a.unlock(activity.getString(R.string.achievement_hunter_1000))
        if (score >= 10_000) a.unlock(activity.getString(R.string.achievement_score_10000))
        if (overdrive && wave >= 5) a.unlock(activity.getString(R.string.achievement_overdrive_5))
        if (shipCount >= 3) a.unlock(activity.getString(R.string.achievement_collector))
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

    companion object {
        private const val TAG = "RiftReignSocial"
        private const val SAVE_NAME = "rift_reign_progress_v1"
        private const val RC_ACHIEVEMENTS = 9103
        private const val RC_LEADERBOARD = 9104
    }
}
