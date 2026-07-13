package com.skylegends.game.bosses

import android.graphics.Color

/** Each boss's signature attack — [Boss] dispatches on this instead of every boss sharing one
 * formula. Ordered roughly simplest → most complex, matching the campaign's difficulty curve;
 * [FINAL_MIX] (the last boss) layers two other patterns into one volley. */
enum class BossAttackPattern { AIMED_FAN, TWIN_CANNON, BULLET_RAIN, SWEEP_ARC, SPIRAL_BURST, PINCER_AIMED, MINEFIELD, FINAL_MIX }

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
    val coreColor: Int,
    val pattern: BossAttackPattern,
    /** Scales the whole hull (and hitbox) — later bosses read as physically larger/more
     * imposing, not just tougher on a health bar. */
    val hullScale: Float
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
    private val patterns = listOf(
        BossAttackPattern.AIMED_FAN, BossAttackPattern.TWIN_CANNON, BossAttackPattern.BULLET_RAIN,
        BossAttackPattern.SWEEP_ARC, BossAttackPattern.SPIRAL_BURST, BossAttackPattern.PINCER_AIMED,
        BossAttackPattern.MINEFIELD, BossAttackPattern.FINAL_MIX
    )
    private val hullScales = listOf(0.88f, 0.95f, 1.0f, 1.04f, 1.08f, 1.1f, 1.14f, 1.2f)

    /** [tier] is 0-based sector index (0..7). Hp, aggression, hull size, and pattern complexity
     * all escalate across the campaign, so later bosses are tougher in more than one way. */
    fun forSector(tier: Int): BossSpec {
        val t = tier.coerceIn(0, names.size - 1)
        return BossSpec(
            id = "boss_$t",
            displayName = names[t],
            maxHp = 1600f + t * 480f,
            aggression = 0.75f + t * 0.11f,
            coreColor = coreColors[t],
            pattern = patterns[t],
            hullScale = hullScales[t]
        )
    }
}
