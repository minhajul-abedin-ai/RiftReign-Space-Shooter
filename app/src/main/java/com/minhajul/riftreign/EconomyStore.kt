package com.minhajul.riftreign

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class EconomyStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rift_reign", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_CRYSTALS)) {
            // Welcome balance: enough to unlock one low-cost cosmetic and understand the economy.
            prefs.edit().putInt(KEY_CRYSTALS, 220).apply()
        }
    }

    val crystals: Int get() = prefs.getInt(KEY_CRYSTALS, 220).coerceAtLeast(0)
    val adsRemoved: Boolean get() = prefs.getBoolean(KEY_ADS_REMOVED, false)
    val selectedShip: String get() = prefs.getString(KEY_SELECTED_SHIP, "nova") ?: "nova"
    val selectedWeapon: String get() = prefs.getString(KEY_SELECTED_WEAPON, "pulse") ?: "pulse"
    val selectedTarget: String get() = prefs.getString(KEY_SELECTED_TARGET, "neon") ?: "neon"

    @Synchronized fun grantCrystals(amount: Int) {
        if (amount <= 0) return
        prefs.edit().putInt(KEY_CRYSTALS, (crystals + amount).coerceAtMost(9_999_999)).apply()
    }

    @Synchronized fun spendCrystals(amount: Int): Boolean {
        if (amount < 0 || crystals < amount) return false
        prefs.edit().putInt(KEY_CRYSTALS, crystals - amount).apply()
        return true
    }

    fun isShipUnlocked(id: String): Boolean = id == "nova" || getUnlocked(KEY_UNLOCKED_SHIPS).contains(id)
    fun isWeaponUnlocked(id: String): Boolean = id == "pulse" || getUnlocked(KEY_UNLOCKED_WEAPONS).contains(id)
    fun isTargetUnlocked(id: String): Boolean = id == "neon" || getUnlocked(KEY_UNLOCKED_TARGETS).contains(id)

    @Synchronized fun unlockAndSelectShip(skin: ShipSkin): Boolean {
        if (!isShipUnlocked(skin.id)) {
            if (!spendCrystals(skin.cost)) return false
            addUnlocked(KEY_UNLOCKED_SHIPS, skin.id)
        }
        prefs.edit().putString(KEY_SELECTED_SHIP, skin.id).apply()
        return true
    }

    @Synchronized fun unlockAndSelectWeapon(skin: WeaponSkin): Boolean {
        if (!isWeaponUnlocked(skin.id)) {
            if (!spendCrystals(skin.cost)) return false
            addUnlocked(KEY_UNLOCKED_WEAPONS, skin.id)
        }
        prefs.edit().putString(KEY_SELECTED_WEAPON, skin.id).apply()
        return true
    }

    @Synchronized fun unlockAndSelectTarget(theme: TargetTheme): Boolean {
        if (!isTargetUnlocked(theme.id)) {
            if (!spendCrystals(theme.cost)) return false
            addUnlocked(KEY_UNLOCKED_TARGETS, theme.id)
        }
        prefs.edit().putString(KEY_SELECTED_TARGET, theme.id).apply()
        return true
    }

    @Synchronized fun setAdsRemoved(value: Boolean) {
        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
    }

    fun unlockedShipCount(): Int = 1 + getUnlocked(KEY_UNLOCKED_SHIPS).size

    /**
     * Cloud progress intentionally excludes Rift Crystal balance, processed purchase tokens,
     * and Remove Ads. Those are monetization entitlements and should eventually be protected
     * by a server-authoritative account backend rather than a client-editable save file.
     */
    fun exportCloudProgress(): JSONObject = JSONObject().apply {
        put("selectedShip", selectedShip)
        put("selectedWeapon", selectedWeapon)
        put("selectedTarget", selectedTarget)
        put("ships", JSONArray(getUnlocked(KEY_UNLOCKED_SHIPS).toList()))
        put("weapons", JSONArray(getUnlocked(KEY_UNLOCKED_WEAPONS).toList()))
        put("targets", JSONArray(getUnlocked(KEY_UNLOCKED_TARGETS).toList()))
    }

    @Synchronized fun mergeCloudProgress(json: JSONObject) {
        fun arraySet(name: String): Set<String> {
            val out = mutableSetOf<String>()
            val arr = json.optJSONArray(name) ?: return out
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
            return out
        }
        fun mergeSet(key: String, remote: Set<String>) {
            if (remote.isEmpty()) return
            prefs.edit().putStringSet(key, (getUnlocked(key) + remote).toSet()).apply()
        }
        mergeSet(KEY_UNLOCKED_SHIPS, arraySet("ships"))
        mergeSet(KEY_UNLOCKED_WEAPONS, arraySet("weapons"))
        mergeSet(KEY_UNLOCKED_TARGETS, arraySet("targets"))

        val ship = json.optString("selectedShip", selectedShip)
        val weapon = json.optString("selectedWeapon", selectedWeapon)
        val target = json.optString("selectedTarget", selectedTarget)
        val editor = prefs.edit()
        if (isShipUnlocked(ship)) editor.putString(KEY_SELECTED_SHIP, ship)
        if (isWeaponUnlocked(weapon)) editor.putString(KEY_SELECTED_WEAPON, weapon)
        if (isTargetUnlocked(target)) editor.putString(KEY_SELECTED_TARGET, target)
        editor.apply()
    }

    fun hasProcessedPurchase(token: String): Boolean =
        prefs.getStringSet(KEY_PROCESSED_TOKENS, emptySet())?.contains(token) == true

    @Synchronized fun markProcessedPurchase(token: String) {
        val copy = prefs.getStringSet(KEY_PROCESSED_TOKENS, emptySet())?.toMutableSet() ?: mutableSetOf()
        copy.add(token)
        // Keep the set bounded. Purchase tokens are only used as local duplicate protection.
        if (copy.size > 80) {
            val trimmed = copy.toList().takeLast(60).toMutableSet()
            prefs.edit().putStringSet(KEY_PROCESSED_TOKENS, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY_PROCESSED_TOKENS, copy).apply()
        }
    }

    private fun getUnlocked(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    private fun addUnlocked(key: String, id: String) {
        val copy = getUnlocked(key).toMutableSet()
        copy.add(id)
        prefs.edit().putStringSet(key, copy).apply()
    }

    companion object {
        private const val KEY_CRYSTALS = "rift_crystals"
        private const val KEY_ADS_REMOVED = "remove_ads_owned"
        private const val KEY_SELECTED_SHIP = "selected_ship"
        private const val KEY_SELECTED_WEAPON = "selected_weapon"
        private const val KEY_SELECTED_TARGET = "selected_target"
        private const val KEY_UNLOCKED_SHIPS = "unlocked_ships"
        private const val KEY_UNLOCKED_WEAPONS = "unlocked_weapons"
        private const val KEY_UNLOCKED_TARGETS = "unlocked_targets"
        private const val KEY_PROCESSED_TOKENS = "processed_purchase_tokens"
    }
}
