package com.skylegends.game.utils

import android.content.Context

/**
 * Persistent save via SharedPreferences. Holds meta-progression: spendable coins, best
 * score/level, which aircraft are unlocked & selected, per-upgrade levels, and audio
 * prefs. Versioned keys so the schema can migrate later.
 */
class SaveManager(context: Context) {
    private val prefs = context.getSharedPreferences("sky_legends_save", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(v) { prefs.edit().putInt(KEY_BEST, v).apply() }

    /** Spendable currency (also the lifetime bank — spent in hangar/upgrades). */
    var coins: Int
        get() = prefs.getInt(KEY_COINS, 0)
        set(v) { prefs.edit().putInt(KEY_COINS, v.coerceAtLeast(0)).apply() }

    /** 0-based index of the next campaign sector to play (0..sectorCount-1). */
    var campaignProgress: Int
        get() = prefs.getInt(KEY_CAMPAIGN, 0)
        set(v) { prefs.edit().putInt(KEY_CAMPAIGN, v).apply() }

    fun addCoins(n: Int) { coins += n }
    /** Spend [n] coins if affordable; returns true on success. */
    fun spend(n: Int): Boolean {
        if (coins < n) return false
        coins -= n
        return true
    }

    // --- Aircraft ---
    var selectedAircraft: String
        get() = prefs.getString(KEY_SELECTED, "vanguard") ?: "vanguard"
        set(v) { prefs.edit().putString(KEY_SELECTED, v).apply() }

    private fun unlockedSet(): MutableSet<String> {
        val csv = prefs.getString(KEY_UNLOCKED, "vanguard") ?: "vanguard"
        return csv.split(",").filter { it.isNotBlank() }.toMutableSet()
    }

    fun isUnlocked(id: String): Boolean = id == "vanguard" || unlockedSet().contains(id)

    fun unlock(id: String) {
        val s = unlockedSet(); s.add(id)
        prefs.edit().putString(KEY_UNLOCKED, s.joinToString(",")).apply()
    }

    // --- Upgrades ---
    fun upgradeLevel(id: String): Int = prefs.getInt(KEY_UP_PREFIX + id, 0)
    fun setUpgradeLevel(id: String, level: Int) {
        prefs.edit().putInt(KEY_UP_PREFIX + id, level).apply()
    }

    // --- Audio prefs ---
    var musicOn: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(v) { prefs.edit().putBoolean(KEY_MUSIC, v).apply() }
    var sfxOn: Boolean
        get() = prefs.getBoolean(KEY_SFX, true)
        set(v) { prefs.edit().putBoolean(KEY_SFX, v).apply() }

    companion object {
        private const val KEY_BEST = "best_score_v1"
        private const val KEY_COINS = "coins_v1"
        private const val KEY_CAMPAIGN = "campaign_progress_v1"
        private const val KEY_SELECTED = "aircraft_selected_v1"
        private const val KEY_UNLOCKED = "aircraft_unlocked_v1"
        private const val KEY_UP_PREFIX = "upgrade_v1_"
        private const val KEY_MUSIC = "music_on_v1"
        private const val KEY_SFX = "sfx_on_v1"
    }
}
