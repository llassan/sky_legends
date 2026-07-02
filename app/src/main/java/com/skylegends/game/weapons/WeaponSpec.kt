package com.skylegends.game.weapons

import android.graphics.Color

/**
 * How a weapon lays down fire. [BulletPort] describes one emitter: an x-offset from the
 * ship nose and an angle (degrees, 0 = straight up, +right). A spec is a list of ports,
 * so any pattern — single, dual, spread, fan — is pure data.
 */
data class BulletPort(val offsetX: Float, val angleDeg: Float)

/**
 * A fully data-driven weapon definition. New weapons = new [WeaponSpec] entries in
 * [WeaponCatalog]; no engine changes. Upgrade [level] scales fire-rate and damage and can
 * unlock extra ports (see [WeaponCatalog.portsFor]).
 */
data class WeaponSpec(
    val id: String,
    val displayName: String,
    val baseFireRate: Float,     // seconds between shots at level 1
    val baseDamage: Float,
    val bulletSpeed: Float,      // virtual px/s
    val bulletColor: Int,
    val glowColor: Int,
    val bulletRadius: Float,
    val muzzleColor: Int,
    /** Ports keyed by level (1-based). Index 0 = level 1, clamped for higher levels. */
    val portsByLevel: List<List<BulletPort>>
) {
    val maxLevel: Int get() = portsByLevel.size

    fun portsFor(level: Int): List<BulletPort> =
        portsByLevel[(level - 1).coerceIn(0, portsByLevel.size - 1)]

    fun fireRateFor(level: Int): Float =
        baseFireRate * Math.pow(0.90, (level - 1).toDouble()).toFloat()  // ~10% faster/level

    fun damageFor(level: Int): Float =
        baseDamage * (1f + 0.18f * (level - 1))                          // +18% dmg/level
}

/** The catalog of every weapon in the game. */
object WeaponCatalog {

    val MACHINE_GUN = WeaponSpec(
        id = "mg",
        displayName = "Vulcan MG",
        baseFireRate = 0.11f,
        baseDamage = 8f,
        bulletSpeed = 1050f,
        bulletColor = Color.rgb(255, 236, 130),
        glowColor = Color.rgb(255, 170, 40),
        bulletRadius = 5f,
        muzzleColor = Color.rgb(255, 210, 120),
        portsByLevel = listOf(
            listOf(BulletPort(0f, 0f)),
            listOf(BulletPort(-9f, 0f), BulletPort(9f, 0f)),
            listOf(BulletPort(-14f, -3f), BulletPort(0f, 0f), BulletPort(14f, 3f)),
            listOf(BulletPort(-16f, -4f), BulletPort(-6f, 0f), BulletPort(6f, 0f), BulletPort(16f, 4f))
        )
    )

    val SPREAD = WeaponSpec(
        id = "spread",
        displayName = "Scatter Blaster",
        baseFireRate = 0.20f,
        baseDamage = 11f,
        bulletSpeed = 820f,
        bulletColor = Color.rgb(120, 255, 190),
        glowColor = Color.rgb(40, 220, 150),
        bulletRadius = 6f,
        muzzleColor = Color.rgb(160, 255, 210),
        portsByLevel = listOf(
            listOf(BulletPort(0f, -10f), BulletPort(0f, 0f), BulletPort(0f, 10f)),
            listOf(BulletPort(-6f, -16f), BulletPort(-3f, -6f), BulletPort(3f, 6f), BulletPort(6f, 16f)),
            listOf(BulletPort(0f, -22f), BulletPort(0f, -11f), BulletPort(0f, 0f), BulletPort(0f, 11f), BulletPort(0f, 22f)),
            listOf(BulletPort(0f, -30f), BulletPort(0f, -18f), BulletPort(0f, -6f), BulletPort(0f, 6f), BulletPort(0f, 18f), BulletPort(0f, 30f))
        )
    )

    val PLASMA = WeaponSpec(
        id = "plasma",
        displayName = "Plasma Lance",
        baseFireRate = 0.28f,
        baseDamage = 26f,
        bulletSpeed = 1250f,
        bulletColor = Color.rgb(180, 150, 255),
        glowColor = Color.rgb(120, 80, 255),
        bulletRadius = 10f,
        muzzleColor = Color.rgb(200, 180, 255),
        portsByLevel = listOf(
            listOf(BulletPort(0f, 0f)),
            listOf(BulletPort(-11f, 0f), BulletPort(11f, 0f)),
            listOf(BulletPort(-14f, 0f), BulletPort(0f, 0f), BulletPort(14f, 0f)),
            listOf(BulletPort(-20f, -2f), BulletPort(-8f, 0f), BulletPort(8f, 0f), BulletPort(20f, 2f))
        )
    )

    /** Ordered list used for power-up cycling and the hangar. */
    val all = listOf(MACHINE_GUN, SPREAD, PLASMA)

    /**
     * Fixed auxiliary armament — not upgradeable via power-ups, always fired at level 1.
     * [com.skylegends.game.aircraft.AircraftSpec.wingWeapon]/`missileWeapon` layer these on
     * top of whatever primary weapon is equipped, so "advanced" aircraft keep their extra
     * firepower even after a weapon-swap pickup.
     */
    val WING_CANNON = WeaponSpec(
        id = "wing_cannon",
        displayName = "Wing Cannon",
        baseFireRate = 0.22f,
        baseDamage = 5f,
        bulletSpeed = 900f,
        bulletColor = Color.rgb(255, 255, 190),
        glowColor = Color.rgb(255, 210, 70),
        bulletRadius = 4f,
        muzzleColor = Color.rgb(255, 235, 150),
        portsByLevel = listOf(listOf(BulletPort(-26f, 0f), BulletPort(26f, 0f)))
    )

    val MISSILE_POD = WeaponSpec(
        id = "missile_pod",
        displayName = "Missile Pod",
        baseFireRate = 2.4f,
        baseDamage = 60f,
        bulletSpeed = 560f,
        bulletColor = Color.rgb(255, 150, 90),
        glowColor = Color.rgb(255, 80, 40),
        bulletRadius = 10f,
        muzzleColor = Color.rgb(255, 190, 130),
        portsByLevel = listOf(listOf(BulletPort(-20f, 0f), BulletPort(20f, 0f)))
    )
}
