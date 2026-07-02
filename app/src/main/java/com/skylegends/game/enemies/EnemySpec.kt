package com.skylegends.game.enemies

import android.graphics.Color

/** How an enemy moves once spawned. Parameters live on [EnemySpec]. */
enum class MovementPattern {
    STRAIGHT,     // straight down at speed
    SINE,         // drift down while weaving horizontally
    SWOOP,        // arc in from a side, then straighten
    HOVER,        // descend to a hold line, hover and strafe
    KAMIKAZE      // accelerate toward the player's last position
}

/** How an enemy shoots. NONE = unarmed rammer. */
enum class FirePattern {
    NONE,
    AIMED,        // single shot toward the player
    SPREAD3,      // 3-way fan aimed at player
    RADIAL8       // 8-way ring (turret / elite)
}

/** How an enemy is drawn (kept simple + readable — vector silhouettes). */
enum class EnemyShape { DRONE, DART, GUNSHIP, TURRET }

/**
 * Fully data-driven enemy definition. Adding an enemy is adding an [EnemySpec] to
 * [EnemyCatalog]; the [com.skylegends.game.entities.Enemy] class interprets these fields.
 */
data class EnemySpec(
    val id: String,
    val shape: EnemyShape,
    val maxHp: Float,
    val width: Float,
    val height: Float,
    val bodyColor: Int,
    val accentColor: Int,
    val speed: Float,
    val movement: MovementPattern,
    val fire: FirePattern,
    val fireRate: Float,          // seconds between volleys (ignored if NONE)
    val bulletSpeed: Float,
    val bulletDamage: Float,
    val contactDamage: Float,
    val score: Int,
    val coinValue: Int,
    // Movement tuning.
    val weaveAmplitude: Float = 0f,
    val weaveFrequency: Float = 0f,
    val hoverY: Float = 0f        // for HOVER: virtual-y to settle at
)

object EnemyCatalog {

    val SCOUT = EnemySpec(
        id = "scout", shape = EnemyShape.DRONE,
        maxHp = 20f, width = 46f, height = 42f,
        bodyColor = Color.rgb(120, 200, 255), accentColor = Color.rgb(230, 250, 255),
        speed = 190f, movement = MovementPattern.STRAIGHT,
        fire = FirePattern.NONE, fireRate = 0f, bulletSpeed = 0f, bulletDamage = 0f,
        contactDamage = 18f, score = 100, coinValue = 2
    )

    val WEAVER = EnemySpec(
        id = "weaver", shape = EnemyShape.DRONE,
        maxHp = 28f, width = 48f, height = 44f,
        bodyColor = Color.rgb(180, 140, 255), accentColor = Color.rgb(240, 230, 255),
        speed = 150f, movement = MovementPattern.SINE,
        fire = FirePattern.AIMED, fireRate = 1.6f, bulletSpeed = 300f, bulletDamage = 12f,
        contactDamage = 18f, score = 150, coinValue = 3,
        weaveAmplitude = 120f, weaveFrequency = 1.4f
    )

    val DART = EnemySpec(
        id = "dart", shape = EnemyShape.DART,
        maxHp = 16f, width = 40f, height = 56f,
        bodyColor = Color.rgb(255, 120, 90), accentColor = Color.rgb(255, 220, 120),
        speed = 150f, movement = MovementPattern.SWOOP,
        fire = FirePattern.NONE, fireRate = 0f, bulletSpeed = 0f, bulletDamage = 0f,
        contactDamage = 22f, score = 120, coinValue = 2
    )

    val KAMIKAZE = EnemySpec(
        id = "kamikaze", shape = EnemyShape.DART,
        maxHp = 22f, width = 42f, height = 52f,
        bodyColor = Color.rgb(255, 70, 70), accentColor = Color.rgb(255, 200, 60),
        speed = 300f, movement = MovementPattern.KAMIKAZE,
        fire = FirePattern.NONE, fireRate = 0f, bulletSpeed = 0f, bulletDamage = 0f,
        contactDamage = 30f, score = 180, coinValue = 4
    )

    val GUNSHIP = EnemySpec(
        id = "gunship", shape = EnemyShape.GUNSHIP,
        maxHp = 70f, width = 78f, height = 64f,
        bodyColor = Color.rgb(90, 110, 140), accentColor = Color.rgb(255, 180, 80),
        speed = 90f, movement = MovementPattern.HOVER,
        fire = FirePattern.SPREAD3, fireRate = 1.3f, bulletSpeed = 320f, bulletDamage = 14f,
        contactDamage = 26f, score = 300, coinValue = 8, hoverY = 220f
    )

    val TURRET = EnemySpec(
        id = "turret", shape = EnemyShape.TURRET,
        maxHp = 120f, width = 72f, height = 72f,
        bodyColor = Color.rgb(70, 80, 95), accentColor = Color.rgb(255, 90, 70),
        speed = 70f, movement = MovementPattern.HOVER,
        fire = FirePattern.RADIAL8, fireRate = 1.9f, bulletSpeed = 260f, bulletDamage = 14f,
        contactDamage = 30f, score = 400, coinValue = 12, hoverY = 170f
    )

    val all = listOf(SCOUT, WEAVER, DART, KAMIKAZE, GUNSHIP, TURRET)
    fun byId(id: String): EnemySpec = all.first { it.id == id }
}
