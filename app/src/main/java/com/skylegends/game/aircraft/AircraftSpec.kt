package com.skylegends.game.aircraft

import android.graphics.Color
import com.skylegends.game.weapons.WeaponCatalog
import com.skylegends.game.weapons.WeaponSpec

/** Hull silhouette variants the Player renderer knows how to draw. */
enum class HullShape { DELTA, ARROW, HEAVY }

/** The active ability an aircraft can trigger via the in-game ability button. */
enum class AbilityType { OVERDRIVE, PHASE_DASH, SIEGE_BURST }

/**
 * Data-driven aircraft definition. Adding a ship = adding an [AircraftSpec] to
 * [AircraftCatalog]; the Player interprets stats + [shape] + colours. [cost] is the coin
 * price to unlock (0 = free starter). Each ship has one active [ability] on an
 * [abilityCooldown]; [abilityDuration] means "buff duration" for OVERDRIVE and "dash
 * invulnerability window" for PHASE_DASH, and is unused (0) for the instant SIEGE_BURST.
 */
data class AircraftSpec(
    val id: String,
    val displayName: String,
    val tagline: String,
    val cost: Int,
    val maxHp: Float,
    val followSpeed: Float,     // higher = snappier drag-follow
    val maxShield: Float,
    val shape: HullShape,
    val bodyColor: Int,
    val wingColor: Int,
    val accentColor: Int,
    val flameColor: Int,
    val defaultWeapon: WeaponSpec,
    /** Fixed wingtip guns fired automatically alongside the primary weapon, or null for none. */
    val wingWeapon: WeaponSpec? = null,
    /** Fixed missile pods fired automatically on a long cooldown, or null for none. */
    val missileWeapon: WeaponSpec? = null,
    val abilityBlurb: String,
    val ability: AbilityType,
    val abilityName: String,
    val abilitySymbol: String,
    val abilityCooldown: Float,
    val abilityDuration: Float,
    // 0..1 bars for the hangar stat display.
    val statArmor: Float,
    val statSpeed: Float,
    val statFirepower: Float
)

object AircraftCatalog {

    val VANGUARD = AircraftSpec(
        id = "vanguard", displayName = "VANGUARD", tagline = "Balanced all-rounder", cost = 0,
        maxHp = 100f, followSpeed = 22f, maxShield = 60f, shape = HullShape.DELTA,
        bodyColor = Color.rgb(90, 170, 240), wingColor = Color.rgb(40, 90, 160),
        accentColor = Color.rgb(220, 245, 255), flameColor = Color.rgb(255, 150, 40),
        defaultWeapon = WeaponCatalog.MACHINE_GUN, abilityBlurb = "Reliable hull & rapid guns",
        ability = AbilityType.OVERDRIVE, abilityName = "OVERDRIVE", abilitySymbol = "⚡",
        abilityCooldown = 14f, abilityDuration = 4f,
        statArmor = 0.6f, statSpeed = 0.6f, statFirepower = 0.55f
    )

    val NOVA = AircraftSpec(
        id = "nova", displayName = "NOVA", tagline = "Glass-cannon interceptor", cost = 500,
        maxHp = 70f, followSpeed = 31f, maxShield = 40f, shape = HullShape.ARROW,
        bodyColor = Color.rgb(120, 255, 200), wingColor = Color.rgb(30, 150, 120),
        accentColor = Color.rgb(235, 255, 248), flameColor = Color.rgb(90, 255, 210),
        defaultWeapon = WeaponCatalog.SPREAD, wingWeapon = WeaponCatalog.WING_CANNON,
        abilityBlurb = "Blazing speed, thin armor",
        ability = AbilityType.PHASE_DASH, abilityName = "PHASE DASH", abilitySymbol = "»",
        abilityCooldown = 8f, abilityDuration = 0.6f,
        statArmor = 0.3f, statSpeed = 1.0f, statFirepower = 0.7f
    )

    val TITAN = AircraftSpec(
        id = "titan", displayName = "TITAN", tagline = "Heavy plasma bomber", cost = 1200,
        maxHp = 165f, followSpeed = 15f, maxShield = 90f, shape = HullShape.HEAVY,
        bodyColor = Color.rgb(200, 150, 255), wingColor = Color.rgb(110, 70, 180),
        accentColor = Color.rgb(240, 225, 255), flameColor = Color.rgb(200, 120, 255),
        defaultWeapon = WeaponCatalog.PLASMA, wingWeapon = WeaponCatalog.WING_CANNON,
        missileWeapon = WeaponCatalog.MISSILE_POD, abilityBlurb = "Fortress hull, heavy plasma",
        ability = AbilityType.SIEGE_BURST, abilityName = "SIEGE BURST", abilitySymbol = "✦",
        abilityCooldown = 18f, abilityDuration = 0f,
        statArmor = 1.0f, statSpeed = 0.35f, statFirepower = 0.9f
    )

    val RAPTOR = AircraftSpec(
        id = "raptor", displayName = "RAPTOR", tagline = "Predatory strike interceptor", cost = 2000,
        maxHp = 90f, followSpeed = 34f, maxShield = 55f, shape = HullShape.ARROW,
        bodyColor = Color.rgb(255, 120, 80), wingColor = Color.rgb(150, 40, 30),
        accentColor = Color.rgb(255, 235, 220), flameColor = Color.rgb(255, 170, 60),
        defaultWeapon = WeaponCatalog.MACHINE_GUN, wingWeapon = WeaponCatalog.WING_CANNON,
        abilityBlurb = "Ferocious speed and short-cooldown dashes",
        ability = AbilityType.PHASE_DASH, abilityName = "PHASE DASH", abilitySymbol = "»",
        abilityCooldown = 7f, abilityDuration = 0.7f,
        statArmor = 0.4f, statSpeed = 0.95f, statFirepower = 0.75f
    )

    val VORTEX = AircraftSpec(
        id = "vortex", displayName = "VORTEX", tagline = "Storm-class assault fighter", cost = 2800,
        maxHp = 125f, followSpeed = 26f, maxShield = 80f, shape = HullShape.DELTA,
        bodyColor = Color.rgb(80, 220, 235), wingColor = Color.rgb(30, 120, 150),
        accentColor = Color.rgb(230, 252, 255), flameColor = Color.rgb(120, 235, 255),
        defaultWeapon = WeaponCatalog.SPREAD, wingWeapon = WeaponCatalog.WING_CANNON,
        abilityBlurb = "Tough hull, sustained overdrive bursts",
        ability = AbilityType.OVERDRIVE, abilityName = "OVERDRIVE", abilitySymbol = "⚡",
        abilityCooldown = 12f, abilityDuration = 5f,
        statArmor = 0.7f, statSpeed = 0.7f, statFirepower = 0.85f
    )

    val COLOSSUS = AircraftSpec(
        id = "colossus", displayName = "COLOSSUS", tagline = "Siege-grade war fortress", cost = 4200,
        maxHp = 215f, followSpeed = 14f, maxShield = 120f, shape = HullShape.HEAVY,
        bodyColor = Color.rgb(255, 205, 90), wingColor = Color.rgb(150, 110, 40),
        accentColor = Color.rgb(255, 245, 210), flameColor = Color.rgb(255, 190, 70),
        defaultWeapon = WeaponCatalog.PLASMA, wingWeapon = WeaponCatalog.WING_CANNON,
        missileWeapon = WeaponCatalog.MISSILE_POD, abilityBlurb = "Colossal armor, screen-clearing siege",
        ability = AbilityType.SIEGE_BURST, abilityName = "SIEGE BURST", abilitySymbol = "✦",
        abilityCooldown = 16f, abilityDuration = 0f,
        statArmor = 1.0f, statSpeed = 0.3f, statFirepower = 1.0f
    )

    val all = listOf(VANGUARD, NOVA, TITAN, RAPTOR, VORTEX, COLOSSUS)
    fun byId(id: String): AircraftSpec = all.firstOrNull { it.id == id } ?: VANGUARD
}
