package com.skylegends.game.bosses

import android.graphics.Color

/** Each boss's signature attack — [Boss] dispatches on this instead of every boss sharing one
 * formula. Ordered roughly simplest → most complex, matching the campaign's difficulty curve;
 * [FINAL_MIX] (the last boss) layers two other patterns into one volley. */
enum class BossAttackPattern { AIMED_FAN, TWIN_CANNON, BULLET_RAIN, SWEEP_ARC, SPIRAL_BURST, PINCER_AIMED, MINEFIELD, FINAL_MIX }

/** The hull silhouette a boss is drawn as. [Boss] builds a completely different procedural
 * shape (and shape-specific surface detail) per value, so no two bosses read as the same
 * craft — a capital-ship WEDGE, a flying-SAUCER, a stingray MANTA, a horned CRESCENT, a
 * three-pronged TRIDENT, a legged SPIDER star, a hollow RING station, a spherical ORB
 * battlestation, a faceted CRYSTAL, a lumpy organic HIVE, a spiked SOLAR corona, and a
 * gaping MAW. */
enum class BossHullShape { WEDGE, SAUCER, MANTA, CRESCENT, TRIDENT, SPIDER, RING, ORB, CRYSTAL, HIVE, SOLAR, MAW }

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
    val hullScale: Float,
    /** Which procedural silhouette [Boss] draws this boss as. */
    val hullShape: BossHullShape,
    /** The boss's own base plating colour (it still shifts toward rage-red as HP drops),
     * so each hull reads with a distinct palette instead of every boss being reddish. */
    val hullColor: Int
)

object BossCatalog {
    private val names = listOf(
        "SENTINEL MK-I", "IRON WARDEN", "STORMBRINGER", "VOID REAPER",
        "CRIMSON JUDGE", "APEX PREDATOR", "OBLIVION WARD", "DREADNOUGHT PRIME",
        "MOON BREAKER", "GRAVE TITAN", "SOLAR TYRANT", "RING SOVEREIGN",
        "HORIZON DEVOURER", "QUANTUM SPECTER", "HIVE OVERMIND", "GALACTIC EMPEROR"
    )
    private val coreColors = listOf(
        Color.rgb(120, 200, 255), Color.rgb(120, 255, 190), Color.rgb(200, 160, 255),
        Color.rgb(255, 190, 90), Color.rgb(255, 120, 160), Color.rgb(150, 255, 120),
        Color.rgb(255, 140, 220), Color.rgb(255, 90, 90),
        Color.rgb(200, 210, 220), Color.rgb(160, 120, 255), Color.rgb(255, 210, 70),
        Color.rgb(255, 230, 140), Color.rgb(120, 90, 255), Color.rgb(90, 255, 240),
        Color.rgb(160, 255, 90), Color.rgb(255, 180, 60)
    )
    private val patterns = listOf(
        BossAttackPattern.AIMED_FAN, BossAttackPattern.TWIN_CANNON, BossAttackPattern.BULLET_RAIN,
        BossAttackPattern.SWEEP_ARC, BossAttackPattern.SPIRAL_BURST, BossAttackPattern.PINCER_AIMED,
        BossAttackPattern.MINEFIELD, BossAttackPattern.FINAL_MIX
    )
    private val hullScales = listOf(
        0.88f, 0.95f, 1.0f, 1.04f, 1.08f, 1.1f, 1.14f, 1.2f,
        1.22f, 1.25f, 1.28f, 1.31f, 1.34f, 1.38f, 1.42f, 1.48f
    )

    // One distinct silhouette per sector. Where a shape repeats (16 sectors, 12 shapes) the
    // reappearances are far apart and wear a very different colour, so no two encounters read
    // as the same craft.
    private val hullShapes = listOf(
        BossHullShape.WEDGE, BossHullShape.SAUCER, BossHullShape.MANTA, BossHullShape.CRESCENT,
        BossHullShape.TRIDENT, BossHullShape.SPIDER, BossHullShape.MAW, BossHullShape.ORB,
        BossHullShape.CRYSTAL, BossHullShape.HIVE, BossHullShape.SOLAR, BossHullShape.RING,
        BossHullShape.MAW, BossHullShape.CRYSTAL, BossHullShape.HIVE, BossHullShape.ORB
    )
    private val hullColors = listOf(
        Color.rgb(70, 90, 120), Color.rgb(95, 100, 110), Color.rgb(60, 80, 120), Color.rgb(80, 70, 105),
        Color.rgb(130, 60, 70), Color.rgb(80, 95, 65), Color.rgb(120, 120, 110), Color.rgb(70, 75, 85),
        Color.rgb(120, 140, 160), Color.rgb(90, 70, 110), Color.rgb(150, 80, 40), Color.rgb(160, 130, 60),
        Color.rgb(35, 35, 45), Color.rgb(90, 80, 160), Color.rgb(70, 120, 70), Color.rgb(180, 140, 50)
    )

    /** [tier] is the 0-based sector index. Hp, aggression, hull size, and pattern complexity
     * all escalate across the campaign, so later bosses are tougher in more than one way. The
     * eight attack patterns cycle for sectors beyond the eighth (the finale's [FINAL_MIX] lands
     * on the true last sector), while names, colours and hull sizes stay unique per sector. */
    fun forSector(tier: Int): BossSpec {
        val t = tier.coerceIn(0, names.size - 1)
        return BossSpec(
            id = "boss_$t",
            displayName = names[t],
            maxHp = 1600f + t * 480f,
            aggression = 0.75f + t * 0.11f,
            coreColor = coreColors[t % coreColors.size],
            pattern = patterns[t % patterns.size],
            hullScale = hullScales[t % hullScales.size],
            hullShape = hullShapes[t % hullShapes.size],
            hullColor = hullColors[t % hullColors.size]
        )
    }
}
