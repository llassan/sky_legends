package com.skylegends.game.level

import com.skylegends.game.bosses.BossCatalog
import com.skylegends.game.bosses.BossSpec
import kotlin.random.Random

/** Enemy formations a wave can spawn in. */
enum class Formation { LINE, VEE, STREAM, LEFT_SWOOP, RIGHT_SWOOP }

/** One authored step of a level timeline. Levels are pure data — see [LevelLibrary]. */
sealed class Step {
    /** Pause the timeline for [seconds] before the next step. */
    data class Delay(val seconds: Float) : Step()
    /** Spawn [count] of [specId] in [formation]. Instantaneous. */
    data class Spawn(val specId: String, val count: Int, val formation: Formation) : Step()
    /** Block until every non-boss enemy is destroyed or off-screen. */
    object WaitClear : Step()
    /** Spawn [spec] and block until it dies — completes the level. */
    data class Boss(val spec: BossSpec) : Step()
}

data class Level(
    val index: Int,
    val name: String,
    val steps: List<Step>
)

/**
 * The campaign: 8 sectors × 5 stages (4 enemy waves + 1 boss) = 40 stages total. Sectors are
 * *generated* from a difficulty curve rather than hand-authored one by one — enemy types
 * unlock progressively, spawn density and pacing tighten, and each sector culminates in its
 * own [BossSpec] (see [BossCatalog]). Adding a 9th sector is a one-line change to [sectorNames].
 */
object LevelLibrary {

    private val sectorNames = listOf(
        "ORBITAL APPROACH", "CLOUD CITY SIEGE", "MONSOON FRONT", "IRON COASTLINE",
        "VOLCANIC RIDGE", "FROZEN ABYSS", "STRATOSPHERE BREACH", "THE LAST SKY"
    )

    val levels: List<Level> = sectorNames.indices.map { tier -> buildSector(tier) }

    fun level(index: Int): Level = levels[(index - 1).coerceIn(0, levels.size - 1)]

    private fun buildSector(tier: Int): Level {
        val rnd = Random(4200 + tier)

        // Enemy roster unlocks progressively across the campaign.
        val roster = buildList {
            add("scout"); add("dart")
            if (tier >= 1) { add("weaver"); add("kamikaze") }
            if (tier >= 2) add("gunship")
            if (tier >= 3) add("turret")
        }

        val waveCount = 3 + (tier / 2)                            // 3 waves early → 6 late
        val density = 1f + tier * 0.22f                           // scales spawn counts
        val pace = (1.5f - tier * 0.09f).coerceAtLeast(0.55f)     // delay multiplier, shrinks

        val steps = mutableListOf<Step>(Step.Delay(1.2f * pace))
        repeat(waveCount) { wave ->
            steps += buildWave(roster, tier, wave, density, rnd)
            steps += Step.WaitClear
            steps += Step.Delay((0.8f + wave * 0.15f) * pace)
        }
        steps += Step.Boss(BossCatalog.forSector(tier))

        return Level(index = tier + 1, name = sectorNames[tier], steps = steps)
    }

    private fun buildWave(roster: List<String>, tier: Int, wave: Int, density: Float, rnd: Random): List<Step> {
        val out = mutableListOf<Step>()
        val early = roster.take(2)                                // scout/dart, always present
        val late = roster.drop(2)                                 // unlocked as tier increases
        fun n(base: Int) = (base * density).toInt().coerceAtLeast(base)

        when (wave % 3) {
            0 -> {
                out += Step.Spawn(early[0], n(5), Formation.LINE)
                if (late.isNotEmpty()) out += Step.Spawn(late.random(rnd), n(2), Formation.VEE)
            }
            1 -> {
                out += Step.Spawn(early[1], n(4), Formation.RIGHT_SWOOP)
                out += Step.Spawn(early[0], n(4), Formation.LEFT_SWOOP)
                if (tier >= 1) out += Step.Spawn("kamikaze", n(3), Formation.STREAM)
            }
            else -> {
                out += Step.Spawn(early[0], n(6), Formation.VEE)
                if (late.isNotEmpty()) out += Step.Spawn(late[wave % late.size], n(2), Formation.LINE)
                if (tier >= 3) out += Step.Spawn("turret", 1, Formation.LINE)
            }
        }
        return out
    }
}
