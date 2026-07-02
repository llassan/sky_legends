package com.skylegends.game.effects

import android.graphics.Color

/** How the ambient particle layer behaves for a theme — same engine, different motion/feel. */
enum class DustMode { SPARKLE, MIST, RAIN, EMBER, SNOW }

/**
 * A backdrop mood: sky gradient, star visibility, nebula/planet palettes, ambient particle
 * style, an optional horizon glow, and whether storms flash. One per campaign sector, so the
 * scene visibly evolves as the player advances instead of reusing one static vista forever.
 */
data class SpaceTheme(
    val sky: IntArray,
    val starVisibility: Float,
    val nebulaColors: List<Int>,
    val nebulaCount: Int,
    val planetColors: List<Pair<Int, Int>>,
    val planetCount: Int,
    val dustColor: Int,
    val dustMode: DustMode,
    val horizonGlow: Int?,
    val lightning: Boolean
)

/** One theme per campaign sector (see [com.skylegends.game.level.LevelLibrary]'s sector names). */
object SpaceThemeCatalog {

    private val themes = listOf(
        // 0 — ORBITAL APPROACH: deep space, calm.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(3, 4, 14), Color.rgb(10, 12, 34), Color.rgb(22, 16, 46),
                Color.rgb(14, 20, 44), Color.rgb(4, 6, 18)
            ),
            starVisibility = 1f,
            nebulaColors = listOf(Color.argb(90, 90, 130, 255), Color.argb(60, 130, 80, 220), Color.argb(70, 90, 160, 255)),
            nebulaCount = 4,
            planetColors = listOf(Color.rgb(255, 210, 150) to Color.rgb(120, 60, 40), Color.rgb(150, 200, 255) to Color.rgb(40, 60, 110)),
            planetCount = 2,
            dustColor = Color.argb(70, 200, 210, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = null,
            lightning = false
        ),
        // 1 — CLOUD CITY SIEGE: high above a lit city at dusk.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(30, 18, 42), Color.rgb(70, 42, 70), Color.rgb(120, 70, 90),
                Color.rgb(150, 100, 90), Color.rgb(90, 60, 80)
            ),
            starVisibility = 0.35f,
            nebulaColors = listOf(Color.argb(70, 255, 190, 140), Color.argb(55, 255, 150, 190), Color.argb(60, 255, 210, 160)),
            nebulaCount = 3,
            planetColors = emptyList(),
            planetCount = 0,
            dustColor = Color.argb(60, 255, 220, 190),
            dustMode = DustMode.MIST,
            horizonGlow = Color.argb(110, 255, 190, 120),
            lightning = false
        ),
        // 2 — MONSOON FRONT: overcast storm, rain, lightning.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(10, 12, 18), Color.rgb(24, 28, 38), Color.rgb(34, 40, 52),
                Color.rgb(26, 32, 44), Color.rgb(12, 14, 20)
            ),
            starVisibility = 0f,
            nebulaColors = listOf(Color.argb(70, 80, 95, 120), Color.argb(55, 60, 75, 100)),
            nebulaCount = 3,
            planetColors = emptyList(),
            planetCount = 0,
            dustColor = Color.argb(150, 205, 220, 240),
            dustMode = DustMode.RAIN,
            horizonGlow = null,
            lightning = true
        ),
        // 3 — IRON COASTLINE: cool teal skies over dark water.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(6, 14, 18), Color.rgb(10, 26, 32), Color.rgb(16, 38, 44),
                Color.rgb(10, 28, 34), Color.rgb(4, 10, 14)
            ),
            starVisibility = 0.6f,
            nebulaColors = listOf(Color.argb(75, 70, 180, 190), Color.argb(55, 50, 140, 170), Color.argb(60, 90, 160, 180)),
            nebulaCount = 3,
            planetColors = listOf(Color.rgb(190, 210, 215) to Color.rgb(70, 90, 95)),
            planetCount = 1,
            dustColor = Color.argb(70, 170, 220, 225),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(90, 60, 170, 180),
            lightning = false
        ),
        // 4 — VOLCANIC RIDGE: ash-hazed, ember-lit.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(10, 4, 4), Color.rgb(30, 10, 8), Color.rgb(46, 16, 10),
                Color.rgb(28, 10, 8), Color.rgb(8, 3, 3)
            ),
            starVisibility = 0.3f,
            nebulaColors = listOf(Color.argb(80, 255, 110, 60), Color.argb(65, 255, 60, 90), Color.argb(70, 220, 90, 50)),
            nebulaCount = 3,
            planetColors = listOf(Color.rgb(255, 150, 80) to Color.rgb(60, 20, 15)),
            planetCount = 1,
            dustColor = Color.argb(170, 255, 150, 70),
            dustMode = DustMode.EMBER,
            horizonGlow = Color.argb(150, 255, 110, 40),
            lightning = false
        ),
        // 5 — FROZEN ABYSS: icy, aurora-lit.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(3, 8, 18), Color.rgb(8, 20, 38), Color.rgb(14, 34, 56),
                Color.rgb(10, 26, 44), Color.rgb(4, 10, 20)
            ),
            starVisibility = 0.9f,
            nebulaColors = listOf(Color.argb(80, 110, 255, 210), Color.argb(60, 120, 200, 255), Color.argb(65, 180, 140, 255)),
            nebulaCount = 3,
            planetColors = listOf(Color.rgb(220, 240, 255) to Color.rgb(90, 130, 170)),
            planetCount = 1,
            dustColor = Color.argb(160, 230, 245, 255),
            dustMode = DustMode.SNOW,
            horizonGlow = Color.argb(90, 120, 255, 220),
            lightning = false
        ),
        // 6 — STRATOSPHERE BREACH: atmosphere thinning into space.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(4, 6, 20), Color.rgb(10, 16, 42), Color.rgb(30, 50, 90),
                Color.rgb(60, 100, 150), Color.rgb(120, 170, 210)
            ),
            starVisibility = 0.6f,
            nebulaColors = listOf(Color.argb(75, 140, 190, 255), Color.argb(55, 200, 220, 255)),
            nebulaCount = 3,
            planetColors = listOf(Color.rgb(200, 225, 255) to Color.rgb(70, 100, 150), Color.rgb(255, 235, 210) to Color.rgb(140, 110, 80)),
            planetCount = 2,
            dustColor = Color.argb(85, 220, 235, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(130, 150, 200, 255),
            lightning = false
        ),
        // 7 — THE LAST SKY: dramatic crimson finale.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(6, 2, 4), Color.rgb(24, 4, 10), Color.rgb(40, 8, 16),
                Color.rgb(22, 4, 10), Color.rgb(6, 2, 4)
            ),
            starVisibility = 0.8f,
            nebulaColors = listOf(Color.argb(85, 220, 40, 60), Color.argb(60, 120, 20, 60), Color.argb(65, 180, 50, 90)),
            nebulaCount = 4,
            planetColors = listOf(Color.rgb(200, 80, 80) to Color.rgb(40, 10, 10), Color.rgb(120, 60, 90) to Color.rgb(20, 8, 16)),
            planetCount = 2,
            dustColor = Color.argb(95, 255, 160, 160),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(100, 200, 40, 60),
            lightning = false
        )
    )

    fun forSector(tier: Int): SpaceTheme = themes[tier.coerceIn(0, themes.size - 1)]
}
