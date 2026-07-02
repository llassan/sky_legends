package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.skylegends.game.aircraft.AbilityType
import com.skylegends.game.aircraft.AircraftCatalog
import com.skylegends.game.aircraft.AircraftSpec
import com.skylegends.game.aircraft.HullShape
import com.skylegends.game.utils.Constants
import com.skylegends.game.weapons.WeaponSpec
import kotlin.math.cos
import kotlin.math.sin

/** Emits a single bullet. Implemented by GameView so the Player never touches the pool. */
fun interface BulletSpawner {
    fun spawn(x: Float, y: Float, vx: Float, vy: Float, radius: Float, damage: Float, core: Int, glow: Int)
}

/** Screen-wide ability effects the Player can't reach on its own. Implemented by GameView. */
fun interface AbilityContext {
    fun nukeScreen(damage: Float, originX: Float, originY: Float)
}

/**
 * The player ship. Movement is a critically-damped follow toward the finger target, giving
 * the buttery "chase my thumb" feel. Stats, colours and hull silhouette come from the
 * equipped [AircraftSpec]; [damageMult]/[fireRateMult] fold in the permanent upgrade bonuses.
 */
class Player : Entity() {

    var spec: AircraftSpec = AircraftCatalog.VANGUARD
    var maxHp = spec.maxHp
    var hp = maxHp
    var shield = 0f
    var maxShield = spec.maxShield

    var weapon: WeaponSpec = spec.defaultWeapon
    var weaponLevel = 1

    var damageMult = 1f
    var fireRateMult = 1f

    var firing = true
    val alive get() = hp > 0f

    private var fireCooldown = 0f
    private var invuln = 0f
    private var tilt = 0f
    private var lastX = 0f
    private var flameFlicker = 0f

    private var abilityCooldownRemaining = 0f
    private var overdriveTimer = 0f
    val overdriveActive get() = overdriveTimer > 0f
    val abilityReady get() = abilityCooldownRemaining <= 0f
    val abilityCooldownFraction get() = if (spec.abilityCooldown <= 0f) 1f else (1f - abilityCooldownRemaining / spec.abilityCooldown).coerceIn(0f, 1f)

    val target = com.skylegends.game.utils.Vector2()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /** Configure hull + stats for a run (upgrades already folded into the passed values). */
    fun configure(
        spec: AircraftSpec, maxHp: Float, maxShield: Float,
        damageMult: Float, fireRateMult: Float
    ) {
        this.spec = spec
        this.maxHp = maxHp
        this.maxShield = maxShield
        this.damageMult = damageMult
        this.fireRateMult = fireRateMult
        this.weapon = spec.defaultWeapon
        this.weaponLevel = 1
        this.abilityCooldownRemaining = 0f
        this.overdriveTimer = 0f
    }

    fun spawnAtStart() {
        pos.set(Constants.GAME_WIDTH / 2f, Constants.GAME_HEIGHT * Constants.PLAYER_START_Y_FRAC)
        target.set(pos)
        lastX = pos.x
        collisionRadius = 12f          // deliberately small hitbox (shmup convention)
        hp = maxHp
        shield = 0f
        invuln = 0f
        fireCooldown = 0f
        tilt = 0f
        firing = true
        active = true
        abilityCooldownRemaining = 0f
        overdriveTimer = 0f
    }

    val invulnerable get() = invuln > 0f

    fun setTarget(x: Float, y: Float) {
        target.set(
            x.coerceIn(Constants.PLAYER_WIDTH / 2f, Constants.GAME_WIDTH - Constants.PLAYER_WIDTH / 2f),
            (y - 90f).coerceIn(Constants.PLAYER_HEIGHT / 2f, Constants.GAME_HEIGHT - Constants.PLAYER_HEIGHT / 2f)
        )
    }

    override fun update(dt: Float) {
        lastX = pos.x
        val a = 1f - Math.exp(-spec.followSpeed.toDouble() * dt).toFloat()
        pos.x += (target.x - pos.x) * a
        pos.y += (target.y - pos.y) * a

        val vx = (pos.x - lastX) / dt
        val targetTilt = (vx / 900f).coerceIn(-1f, 1f) * Constants.PLAYER_MAX_TILT
        tilt += (targetTilt - tilt) * (1f - Math.exp(-14.0 * dt).toFloat())

        if (invuln > 0f) invuln -= dt
        if (fireCooldown > 0f) fireCooldown -= dt
        if (abilityCooldownRemaining > 0f) abilityCooldownRemaining -= dt
        if (overdriveTimer > 0f) overdriveTimer -= dt
        flameFlicker += dt * 40f
    }

    /** Fire the equipped weapon if off cooldown. Returns true if a volley was emitted. */
    fun tryFire(spawner: BulletSpawner): Boolean {
        if (!firing || !alive || fireCooldown > 0f) return false
        val noseX = pos.x
        val noseY = pos.y - Constants.PLAYER_HEIGHT / 2f
        val speed = weapon.bulletSpeed
        val overdriveDmg = if (overdriveActive) 1.6f else 1f
        val overdriveRate = if (overdriveActive) 0.55f else 1f
        val dmg = weapon.damageFor(weaponLevel) * damageMult * overdriveDmg
        for (port in weapon.portsFor(weaponLevel)) {
            val a = Math.toRadians(port.angleDeg.toDouble())
            val vx = (sin(a) * speed).toFloat()
            val vy = (-cos(a) * speed).toFloat()
            spawner.spawn(noseX + port.offsetX, noseY, vx, vy, weapon.bulletRadius, dmg, weapon.bulletColor, weapon.glowColor)
        }
        fireCooldown = weapon.fireRateFor(weaponLevel) * fireRateMult * overdriveRate
        return true
    }

    /**
     * Trigger the equipped aircraft's active ability if off cooldown. [SIEGE_BURST] needs to
     * reach outside the Player (all on-screen enemies), hence [ctx]. Returns true if it fired.
     */
    fun tryActivateAbility(ctx: AbilityContext): Boolean {
        if (!alive || abilityCooldownRemaining > 0f) return false
        when (spec.ability) {
            AbilityType.OVERDRIVE -> overdriveTimer = spec.abilityDuration
            AbilityType.PHASE_DASH -> {
                pos.y = (pos.y - 150f).coerceAtLeast(Constants.PLAYER_HEIGHT)
                target.y = pos.y
                invuln = maxOf(invuln, spec.abilityDuration)
            }
            AbilityType.SIEGE_BURST -> {
                ctx.nukeScreen(130f, pos.x, pos.y)
                addShield(maxShield * 0.5f)
            }
        }
        abilityCooldownRemaining = spec.abilityCooldown
        return true
    }

    /** Apply damage through shield then hull. Returns true if the hit landed (not i-framed). */
    fun takeDamage(amount: Float): Boolean {
        if (invuln > 0f) return false
        var dmg = amount
        if (shield > 0f) {
            val absorbed = minOf(shield, dmg)
            shield -= absorbed
            dmg -= absorbed
        }
        if (dmg > 0f) hp = (hp - dmg).coerceAtLeast(0f)
        invuln = Constants.PLAYER_INVULN_TIME
        return true
    }

    fun heal(amount: Float) { hp = (hp + amount).coerceAtMost(maxHp) }
    fun addShield(amount: Float) { shield = (shield + amount).coerceAtMost(maxShield) }

    fun upgradeWeapon() { if (weaponLevel < weapon.maxLevel) weaponLevel++ }
    fun equip(spec: WeaponSpec) {
        if (spec.id == weapon.id) upgradeWeapon() else { weapon = spec; weaponLevel = 1 }
    }

    override fun render(canvas: Canvas) {
        if (invuln > 0f && ((invuln * 20f).toInt() % 2 == 0)) return

        canvas.save()
        canvas.translate(pos.x, pos.y)
        canvas.rotate(Math.toDegrees(tilt.toDouble()).toFloat())

        val h = Constants.PLAYER_HEIGHT
        renderFlame(canvas, h)
        when (spec.shape) {
            HullShape.DELTA -> renderDelta(canvas)
            HullShape.ARROW -> renderArrow(canvas)
            HullShape.HEAVY -> renderHeavy(canvas)
        }
        canvas.restore()

        if (shield > 0f) {
            paint.color = Color.rgb(120, 200, 255)
            paint.alpha = (60 + 90 * (shield / maxShield)).toInt().coerceIn(0, 200)
            canvas.drawCircle(pos.x, pos.y, Constants.PLAYER_WIDTH * 0.85f, paint)
            paint.alpha = 255
        }
    }

    private fun renderFlame(canvas: Canvas, h: Float) {
        val flame = 0.7f + 0.3f * sin(flameFlicker.toDouble()).toFloat()
        paint.color = spec.flameColor
        paint.alpha = 200
        path.reset()
        path.moveTo(-8f, h / 2f - 6f); path.lineTo(0f, h / 2f + 22f * flame); path.lineTo(8f, h / 2f - 6f); path.close()
        canvas.drawPath(path, paint)
        paint.color = Color.rgb(255, 240, 200)
        paint.alpha = 230
        path.reset()
        path.moveTo(-4f, h / 2f - 6f); path.lineTo(0f, h / 2f + 12f * flame); path.lineTo(4f, h / 2f - 6f); path.close()
        canvas.drawPath(path, paint)
        paint.alpha = 255
    }

    private fun renderDelta(canvas: Canvas) {
        val w = Constants.PLAYER_WIDTH; val h = Constants.PLAYER_HEIGHT
        paint.color = spec.wingColor
        wing(canvas, w, h, -1f); wing(canvas, w, h, 1f)
        paint.color = spec.bodyColor
        path.reset()
        path.moveTo(0f, -h / 2f)
        path.lineTo(w * 0.20f, h * 0.30f); path.lineTo(w * 0.12f, h / 2f)
        path.lineTo(-w * 0.12f, h / 2f); path.lineTo(-w * 0.20f, h * 0.30f)
        path.close(); canvas.drawPath(path, paint)
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(0f, -h / 2f); path.lineTo(w * 0.06f, h * 0.1f); path.lineTo(-w * 0.06f, h * 0.1f); path.close()
        canvas.drawPath(path, paint)
        canvas.drawCircle(0f, -h * 0.12f, w * 0.10f, paint)
    }

    private fun wing(canvas: Canvas, w: Float, h: Float, s: Float) {
        path.reset()
        path.moveTo(s * w / 2f, h * 0.18f)
        path.lineTo(s * w * 0.16f, -h * 0.1f)
        path.lineTo(s * w * 0.16f, h * 0.34f)
        path.close(); canvas.drawPath(path, paint)
    }

    private fun renderArrow(canvas: Canvas) {
        // Narrow, swept-back interceptor.
        val w = Constants.PLAYER_WIDTH; val h = Constants.PLAYER_HEIGHT
        paint.color = spec.wingColor
        path.reset()
        path.moveTo(-w * 0.10f, -h * 0.05f); path.lineTo(-w * 0.55f, h * 0.42f)
        path.lineTo(-w * 0.10f, h * 0.30f); path.close(); canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(w * 0.10f, -h * 0.05f); path.lineTo(w * 0.55f, h * 0.42f)
        path.lineTo(w * 0.10f, h * 0.30f); path.close(); canvas.drawPath(path, paint)
        paint.color = spec.bodyColor
        path.reset()
        path.moveTo(0f, -h * 0.56f)
        path.lineTo(w * 0.14f, h * 0.36f); path.lineTo(0f, h * 0.5f)
        path.lineTo(-w * 0.14f, h * 0.36f); path.close(); canvas.drawPath(path, paint)
        paint.color = spec.accentColor
        canvas.drawCircle(0f, -h * 0.16f, w * 0.09f, paint)
    }

    private fun renderHeavy(canvas: Canvas) {
        // Wide twin-hull bomber.
        val w = Constants.PLAYER_WIDTH * 1.12f; val h = Constants.PLAYER_HEIGHT
        paint.color = spec.wingColor
        canvas.drawRoundRect(-w * 0.5f, -h * 0.06f, -w * 0.24f, h * 0.46f, 6f, 6f, paint)
        canvas.drawRoundRect(w * 0.24f, -h * 0.06f, w * 0.5f, h * 0.46f, 6f, 6f, paint)
        paint.color = spec.bodyColor
        path.reset()
        path.moveTo(0f, -h / 2f)
        path.lineTo(w * 0.30f, h * 0.28f); path.lineTo(w * 0.22f, h * 0.5f)
        path.lineTo(-w * 0.22f, h * 0.5f); path.lineTo(-w * 0.30f, h * 0.28f)
        path.close(); canvas.drawPath(path, paint)
        paint.color = spec.accentColor
        canvas.drawCircle(0f, -h * 0.1f, w * 0.12f, paint)
    }
}
