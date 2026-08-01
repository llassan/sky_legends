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
    val lightning: Boolean,
    /** Color of a single, near-static distant galaxy silhouette; null where the sky is too
     * hazy/overcast/close for one to plausibly be visible. */
    val galaxyColor: Int? = null
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
            planetColors = listOf(
                Color.rgb(255, 210, 150) to Color.rgb(120, 60, 40),
                Color.rgb(150, 200, 255) to Color.rgb(40, 60, 110),
                Color.rgb(200, 190, 180) to Color.rgb(70, 65, 60),
                Color.rgb(210, 140, 255) to Color.rgb(60, 30, 90)
            ),
            planetCount = 2,
            dustColor = Color.argb(70, 200, 210, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = null,
            lightning = false,
            galaxyColor = Color.argb(120, 150, 170, 255)
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
            planetColors = listOf(
                Color.rgb(190, 210, 215) to Color.rgb(70, 90, 95),
                Color.rgb(140, 220, 200) to Color.rgb(30, 80, 70)
            ),
            planetCount = 1,
            dustColor = Color.argb(70, 170, 220, 225),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(90, 60, 170, 180),
            lightning = false,
            galaxyColor = Color.argb(100, 120, 220, 210)
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
            planetColors = listOf(
                Color.rgb(220, 240, 255) to Color.rgb(90, 130, 170),
                Color.rgb(180, 200, 255) to Color.rgb(60, 70, 140),
                Color.rgb(255, 250, 240) to Color.rgb(140, 150, 170)
            ),
            planetCount = 1,
            dustColor = Color.argb(160, 230, 245, 255),
            dustMode = DustMode.SNOW,
            horizonGlow = Color.argb(90, 120, 255, 220),
            lightning = false,
            galaxyColor = Color.argb(130, 160, 220, 255)
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
            planetColors = listOf(
                Color.rgb(200, 225, 255) to Color.rgb(70, 100, 150),
                Color.rgb(255, 235, 210) to Color.rgb(140, 110, 80),
                Color.rgb(210, 255, 230) to Color.rgb(60, 130, 100)
            ),
            planetCount = 2,
            dustColor = Color.argb(85, 220, 235, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(130, 150, 200, 255),
            lightning = false,
            galaxyColor = Color.argb(110, 200, 220, 255)
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
            planetColors = listOf(
                Color.rgb(200, 80, 80) to Color.rgb(40, 10, 10),
                Color.rgb(120, 60, 90) to Color.rgb(20, 8, 16),
                Color.rgb(230, 150, 90) to Color.rgb(60, 20, 10)
            ),
            planetCount = 2,
            dustColor = Color.argb(95, 255, 160, 160),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(100, 200, 40, 60),
            lightning = false,
            galaxyColor = Color.argb(130, 220, 70, 90)
        ),
        // 8 — SHATTERED MOON: cold grey debris field over a broken moon.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(6, 8, 12), Color.rgb(16, 20, 28), Color.rgb(30, 34, 44),
                Color.rgb(20, 24, 32), Color.rgb(6, 8, 12)
            ),
            starVisibility = 0.95f,
            nebulaColors = listOf(Color.argb(60, 150, 165, 185), Color.argb(45, 120, 140, 170)),
            nebulaCount = 3,
            planetColors = listOf(
                Color.rgb(210, 215, 220) to Color.rgb(70, 75, 85),
                Color.rgb(180, 185, 195) to Color.rgb(55, 60, 70)
            ),
            planetCount = 2,
            dustColor = Color.argb(90, 200, 210, 225),
            dustMode = DustMode.SPARKLE,
            horizonGlow = null,
            lightning = false,
            galaxyColor = Color.argb(110, 180, 195, 220)
        ),
        // 9 — NEBULA GRAVEYARD: violet-green nebula strewn with wreckage.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(8, 4, 16), Color.rgb(22, 10, 34), Color.rgb(30, 20, 40),
                Color.rgb(16, 24, 30), Color.rgb(4, 8, 12)
            ),
            starVisibility = 0.85f,
            nebulaColors = listOf(Color.argb(90, 150, 90, 230), Color.argb(70, 90, 220, 160), Color.argb(65, 190, 110, 255)),
            nebulaCount = 5,
            planetColors = listOf(Color.rgb(160, 120, 200) to Color.rgb(40, 25, 70)),
            planetCount = 1,
            dustColor = Color.argb(80, 190, 160, 230),
            dustMode = DustMode.MIST,
            horizonGlow = null,
            lightning = false,
            galaxyColor = Color.argb(130, 170, 130, 240)
        ),
        // 10 — SOLAR FURNACE: searing corona, blinding light.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(30, 10, 2), Color.rgb(70, 26, 4), Color.rgb(140, 70, 10),
                Color.rgb(200, 120, 20), Color.rgb(90, 40, 6)
            ),
            starVisibility = 0.2f,
            nebulaColors = listOf(Color.argb(90, 255, 170, 50), Color.argb(75, 255, 90, 30), Color.argb(70, 255, 210, 90)),
            nebulaCount = 4,
            planetColors = listOf(Color.rgb(255, 180, 60) to Color.rgb(80, 30, 5)),
            planetCount = 1,
            dustColor = Color.argb(170, 255, 190, 90),
            dustMode = DustMode.EMBER,
            horizonGlow = Color.argb(170, 255, 150, 40),
            lightning = false
        ),
        // 11 — THE RING WORLD: golden megastructure arcing across the sky.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(10, 10, 6), Color.rgb(26, 24, 14), Color.rgb(46, 40, 22),
                Color.rgb(70, 60, 34), Color.rgb(30, 26, 16)
            ),
            starVisibility = 0.7f,
            nebulaColors = listOf(Color.argb(75, 255, 220, 120), Color.argb(55, 230, 190, 90)),
            nebulaCount = 3,
            planetColors = listOf(
                Color.rgb(240, 215, 150) to Color.rgb(90, 70, 30),
                Color.rgb(255, 235, 180) to Color.rgb(120, 95, 45)
            ),
            planetCount = 2,
            dustColor = Color.argb(90, 255, 235, 170),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(120, 255, 210, 110),
            lightning = false,
            galaxyColor = Color.argb(110, 255, 225, 150)
        ),
        // 12 — EVENT HORIZON: a black hole's accretion glow in the dark.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(2, 2, 4), Color.rgb(8, 4, 12), Color.rgb(18, 8, 8),
                Color.rgb(8, 4, 10), Color.rgb(1, 1, 2)
            ),
            starVisibility = 1f,
            nebulaColors = listOf(Color.argb(80, 255, 130, 60), Color.argb(60, 120, 60, 200), Color.argb(70, 255, 90, 120)),
            nebulaCount = 4,
            planetColors = listOf(Color.rgb(40, 30, 40) to Color.rgb(255, 140, 70)),
            planetCount = 1,
            dustColor = Color.argb(70, 200, 160, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = Color.argb(140, 255, 120, 50),
            lightning = false,
            galaxyColor = Color.argb(120, 255, 150, 90)
        ),
        // 13 — QUANTUM RIFT: unstable cyan-magenta fracture, lightning arcs.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(6, 10, 20), Color.rgb(20, 8, 34), Color.rgb(10, 30, 46),
                Color.rgb(30, 12, 44), Color.rgb(6, 10, 20)
            ),
            starVisibility = 0.6f,
            nebulaColors = listOf(Color.argb(90, 80, 240, 255), Color.argb(80, 255, 90, 220), Color.argb(70, 150, 120, 255)),
            nebulaCount = 5,
            planetColors = emptyList(),
            planetCount = 0,
            dustColor = Color.argb(120, 160, 240, 255),
            dustMode = DustMode.SPARKLE,
            horizonGlow = null,
            lightning = true,
            galaxyColor = Color.argb(120, 200, 130, 255)
        ),
        // 14 — THE HIVE CORE: sickly bioluminescent green, close and organic.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(4, 12, 6), Color.rgb(10, 26, 12), Color.rgb(18, 40, 20),
                Color.rgb(12, 30, 16), Color.rgb(4, 12, 6)
            ),
            starVisibility = 0.15f,
            nebulaColors = listOf(Color.argb(90, 120, 255, 110), Color.argb(70, 90, 200, 120), Color.argb(65, 170, 255, 90)),
            nebulaCount = 4,
            planetColors = listOf(Color.rgb(120, 200, 90) to Color.rgb(20, 50, 20)),
            planetCount = 1,
            dustColor = Color.argb(120, 150, 255, 130),
            dustMode = DustMode.MIST,
            horizonGlow = Color.argb(120, 110, 230, 90),
            lightning = false
        ),
        // 15 — GALACTIC THRONE: regal gold-and-crimson final stand.
        SpaceTheme(
            sky = intArrayOf(
                Color.rgb(12, 4, 8), Color.rgb(34, 8, 14), Color.rgb(60, 20, 20),
                Color.rgb(90, 60, 24), Color.rgb(20, 6, 10)
            ),
            starVisibility = 0.9f,
            nebulaColors = listOf(Color.argb(90, 255, 200, 90), Color.argb(75, 230, 50, 70), Color.argb(70, 255, 150, 90)),
            nebulaCount = 5,
            planetColors = listOf(
                Color.rgb(255, 210, 120) to Color.rgb(90, 40, 20),
                Color.rgb(220, 90, 90) to Color.rgb(50, 12, 12),
                Color.rgb(255, 235, 170) to Color.rgb(120, 80, 40)
            ),
            planetCount = 3,
            dustColor = Color.argb(100, 255, 220, 150),
            dustMode = DustMode.EMBER,
            horizonGlow = Color.argb(140, 255, 180, 80),
            lightning = false,
            galaxyColor = Color.argb(140, 255, 200, 120)
        )
    )

    fun forSector(tier: Int): SpaceTheme = themes[tier.coerceIn(0, themes.size - 1)]
}
