package com.skylegends.game.bosses

import android.graphics.Color

/**
 * Data-driven boss definition. [Boss] interprets these — attack *patterns* stay in code
 * (they're the interesting part), but identity, toughness and aggression are pure data so
 * each campaign sector gets a distinct, escalating encounter without new classes.
 */
data class BossSpec(
    val id: String,
    val displayName: String,
    val maxHp: Float,
    /** Multiplies attack cadence and bullet counts — higher = more aggressive. */
    val aggression: Float,
    /** Phase-1 core/weak-point colour; later phases still ramp amber → red as today. */
    val coreColor: Int
)

object BossCatalog {
    private val names = listOf(
        "SENTINEL MK-I", "IRON WARDEN", "STORMBRINGER", "VOID REAPER",
        "CRIMSON JUDGE", "APEX PREDATOR", "OBLIVION WARD", "DREADNOUGHT PRIME"
    )
    private val coreColors = listOf(
        Color.rgb(120, 200, 255), Color.rgb(120, 255, 190), Color.rgb(200, 160, 255),
        Color.rgb(255, 190, 90), Color.rgb(255, 120, 160), Color.rgb(150, 255, 120),
        Color.rgb(255, 140, 220), Color.rgb(255, 90, 90)
    )

    /** [tier] is 0-based sector index (0..7). Hp and aggression escalate across the campaign. */
    fun forSector(tier: Int): BossSpec {
        val t = tier.coerceIn(0, names.size - 1)
        return BossSpec(
            id = "boss_$t",
            displayName = names[t],
            maxHp = 1600f + t * 480f,
            aggression = 0.75f + t * 0.11f,
            coreColor = coreColors[t]
        )
    }
}
