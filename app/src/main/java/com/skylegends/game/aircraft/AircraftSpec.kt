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
        defaultWeapon = WeaponCatalog.SPREAD, abilityBlurb = "Blazing speed, thin armor",
        ability = AbilityType.PHASE_DASH, abilityName = "PHASE DASH", abilitySymbol = "»",
        abilityCooldown = 8f, abilityDuration = 0.6f,
        statArmor = 0.3f, statSpeed = 1.0f, statFirepower = 0.7f
    )

    val TITAN = AircraftSpec(
        id = "titan", displayName = "TITAN", tagline = "Heavy plasma bomber", cost = 1200,
        maxHp = 165f, followSpeed = 15f, maxShield = 90f, shape = HullShape.HEAVY,
        bodyColor = Color.rgb(200, 150, 255), wingColor = Color.rgb(110, 70, 180),
        accentColor = Color.rgb(240, 225, 255), flameColor = Color.rgb(200, 120, 255),
        defaultWeapon = WeaponCatalog.PLASMA, abilityBlurb = "Fortress hull, heavy plasma",
        ability = AbilityType.SIEGE_BURST, abilityName = "SIEGE BURST", abilitySymbol = "✦",
        abilityCooldown = 18f, abilityDuration = 0f,
        statArmor = 1.0f, statSpeed = 0.35f, statFirepower = 0.9f
    )

    val all = listOf(VANGUARD, NOVA, TITAN)
    fun byId(id: String): AircraftSpec = all.firstOrNull { it.id == id } ?: VANGUARD
}
