package com.skylegends.game.upgrade

import com.skylegends.game.utils.SaveManager

/**
 * A permanent, coin-bought upgrade. Effect per level is computed by the [Upgrades] helper.
 * Cost for the next level scales linearly: baseCost * (currentLevel + 1).
 */
data class UpgradeDef(
    val id: String,
    val displayName: String,
    val symbol: String,
    val maxLevel: Int,
    val baseCost: Int,
    val blurb: String
)

object UpgradeCatalog {
    val DAMAGE = UpgradeDef("damage", "DAMAGE", "⚔", 6, 90, "+10% weapon damage / level")
    val FIRE_RATE = UpgradeDef("firerate", "FIRE RATE", "↯", 6, 90, "+6% fire rate / level")
    val ARMOR = UpgradeDef("armor", "ARMOR", "◆", 6, 80, "+25 max hull / level")
    val SHIELD = UpgradeDef("shield", "SHIELD", "◯", 5, 110, "+20 shield capacity / level")
    val MAGNET = UpgradeDef("magnet", "MAGNET", "⊕", 5, 70, "+45 coin magnet range / level")
    val COIN = UpgradeDef("coin", "PAYLOAD", "●", 5, 130, "+15% coins earned / level")

    val all = listOf(DAMAGE, FIRE_RATE, ARMOR, SHIELD, MAGNET, COIN)
}

/**
 * Reads upgrade levels from [SaveManager] and exposes the derived combat bonuses that
 * GameView applies to the player at the start of each run. One place owns the tuning math.
 */
class Upgrades(private val save: SaveManager) {

    fun level(def: UpgradeDef): Int = save.upgradeLevel(def.id).coerceIn(0, def.maxLevel)

    fun isMaxed(def: UpgradeDef): Boolean = level(def) >= def.maxLevel

    /** Coin cost to buy the next level, or -1 if already maxed. */
    fun costFor(def: UpgradeDef): Int {
        val lvl = level(def)
        return if (lvl >= def.maxLevel) -1 else def.baseCost * (lvl + 1)
    }

    /** Attempts to buy the next level of [def]; returns true on success. */
    fun buy(def: UpgradeDef): Boolean {
        val cost = costFor(def)
        if (cost < 0) return false
        if (!save.spend(cost)) return false
        save.setUpgradeLevel(def.id, level(def) + 1)
        return true
    }

    // --- Derived combat bonuses ---
    val damageMult: Float get() = 1f + 0.10f * level(UpgradeCatalog.DAMAGE)
    val fireRateMult: Float get() = (1f - 0.06f * level(UpgradeCatalog.FIRE_RATE)).coerceAtLeast(0.55f)
    val armorBonus: Float get() = 25f * level(UpgradeCatalog.ARMOR)
    val shieldBonus: Float get() = 20f * level(UpgradeCatalog.SHIELD)
    val magnetRange: Float get() = 130f + 45f * level(UpgradeCatalog.MAGNET)
    val coinMult: Float get() = 1f + 0.15f * level(UpgradeCatalog.COIN)
}
