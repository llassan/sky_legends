package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
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
    private var wingFireCooldown = 0f
    private var missileFireCooldown = 0f
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
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; alpha = 160 }

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
        wingFireCooldown = 0f
        missileFireCooldown = 0f
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
        if (wingFireCooldown > 0f) wingFireCooldown -= dt
        if (missileFireCooldown > 0f) missileFireCooldown -= dt
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
     * Fixed wingtip guns — "advanced" aircraft only ([AircraftSpec.wingWeapon]). Independent
     * cooldown from the primary weapon so a weapon-swap pickup never removes it.
     */
    fun tryFireWingCannon(spawner: BulletSpawner): Boolean {
        val wing = spec.wingWeapon ?: return false
        if (!firing || !alive || wingFireCooldown > 0f) return false
        val noseY = pos.y - Constants.PLAYER_HEIGHT * 0.15f
        val dmg = wing.damageFor(1) * damageMult
        for (port in wing.portsFor(1)) {
            spawner.spawn(pos.x + port.offsetX, noseY, 0f, -wing.bulletSpeed, wing.bulletRadius, dmg, wing.bulletColor, wing.glowColor)
        }
        wingFireCooldown = wing.fireRateFor(1) * fireRateMult
        return true
    }

    /** Fixed missile pods — "most advanced" aircraft only ([AircraftSpec.missileWeapon]). */
    fun tryFireMissile(spawner: BulletSpawner): Boolean {
        val missile = spec.missileWeapon ?: return false
        if (!firing || !alive || missileFireCooldown > 0f) return false
        val noseY = pos.y + Constants.PLAYER_HEIGHT * 0.1f
        val dmg = missile.damageFor(1) * damageMult
        for (port in missile.portsFor(1)) {
            spawner.spawn(pos.x + port.offsetX, noseY, 0f, -missile.bulletSpeed, missile.bulletRadius, dmg, missile.bulletColor, missile.glowColor)
        }
        missileFireCooldown = missile.fireRateFor(1) * fireRateMult
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
            val frac = shield / maxShield
            paint.shader = RadialGradient(
                pos.x, pos.y, Constants.PLAYER_WIDTH * 0.85f,
                Color.argb((40 + 60 * frac).toInt(), 170, 220, 255), Color.argb((90 + 90 * frac).toInt(), 90, 170, 255),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(pos.x, pos.y, Constants.PLAYER_WIDTH * 0.85f, paint)
            paint.shader = null
        }
    }

    private fun renderFlame(canvas: Canvas, h: Float) {
        val flame = 0.7f + 0.3f * sin(flameFlicker.toDouble()).toFloat()
        paint.shader = LinearGradient(0f, h / 2f - 6f, 0f, h / 2f + 22f * flame, spec.flameColor, Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(-8f, h / 2f - 6f); path.lineTo(0f, h / 2f + 22f * flame); path.lineTo(8f, h / 2f - 6f); path.close()
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 240, 200)
        paint.alpha = 230
        path.reset()
        path.moveTo(-4f, h / 2f - 6f); path.lineTo(0f, h / 2f + 12f * flame); path.lineTo(4f, h / 2f - 6f); path.close()
        canvas.drawPath(path, paint)
        paint.alpha = 255
    }

    /** Fills [path] with a lit-to-shadow gradient of [base] instead of a flat color, then
     * strokes a dark outline — the single biggest lever for reading as modeled hardware
     * rather than a flat cutout. [topY]/[bottomY] set the light direction (lighter at top). */
    private fun shadedFill(canvas: Canvas, path: Path, base: Int, topY: Float, bottomY: Float) {
        paint.shader = LinearGradient(0f, topY, 0f, bottomY, lighten(base, 0.30f), darken(base, 0.35f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        outlinePaint.color = darken(base, 0.55f)
        canvas.drawPath(path, outlinePaint)
    }

    private fun lighten(c: Int, amt: Float): Int {
        val r = (Color.red(c) + (255 - Color.red(c)) * amt).toInt().coerceIn(0, 255)
        val g = (Color.green(c) + (255 - Color.green(c)) * amt).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) + (255 - Color.blue(c)) * amt).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darken(c: Int, amt: Float): Int {
        val f = 1f - amt
        return Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    }

    /** Radial "glass" highlight for a cockpit/canopy instead of a flat-filled circle. */
    private fun canopyGlass(canvas: Canvas, cx: Float, cy: Float, r: Float, tint: Int) {
        paint.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f, Color.WHITE, tint, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        outlinePaint.strokeWidth = 1.4f
        outlinePaint.color = darken(tint, 0.4f)
        canvas.drawCircle(cx, cy, r, outlinePaint)
        outlinePaint.strokeWidth = 2.2f
    }

    private fun renderDelta(canvas: Canvas) {
        // Vanguard — clean, balanced fighter. No bolt-on hardware: the plain baseline
        // silhouette the other two visibly add weapons pods onto.
        val w = Constants.PLAYER_WIDTH; val h = Constants.PLAYER_HEIGHT
        wingPath(path, w, h, -1f); shadedFill(canvas, path, spec.wingColor, -h * 0.1f, h * 0.34f)
        wingPath(path, w, h, 1f); shadedFill(canvas, path, spec.wingColor, -h * 0.1f, h * 0.34f)
        path.reset()
        path.moveTo(0f, -h / 2f)
        path.lineTo(w * 0.20f, h * 0.30f); path.lineTo(w * 0.12f, h / 2f)
        path.lineTo(-w * 0.12f, h / 2f); path.lineTo(-w * 0.20f, h * 0.30f)
        path.close(); shadedFill(canvas, path, spec.bodyColor, -h / 2f, h / 2f)
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(0f, -h / 2f); path.lineTo(w * 0.06f, h * 0.1f); path.lineTo(-w * 0.06f, h * 0.1f); path.close()
        canvas.drawPath(path, paint)
        canopyGlass(canvas, 0f, -h * 0.12f, w * 0.10f, spec.accentColor)
        renderHardware(canvas, w * 0.5f, h * 0.18f)
    }

    private fun wingPath(p: Path, w: Float, h: Float, s: Float): Path {
        p.reset()
        p.moveTo(s * w / 2f, h * 0.18f)
        p.lineTo(s * w * 0.16f, -h * 0.1f)
        p.lineTo(s * w * 0.16f, h * 0.34f)
        p.close()
        return p
    }

    private fun renderArrow(canvas: Canvas) {
        // Nova — sleek, narrow interceptor: long needle nose, sharply swept thin wings,
        // twin engine strakes. Visibly slimmer than the other two, not just recolored.
        val w = Constants.PLAYER_WIDTH * 0.92f; val h = Constants.PLAYER_HEIGHT * 1.1f
        path.reset()
        path.moveTo(-w * 0.08f, -h * 0.10f); path.lineTo(-w * 0.64f, h * 0.46f)
        path.lineTo(-w * 0.30f, h * 0.40f); path.lineTo(-w * 0.08f, h * 0.14f)
        path.close(); shadedFill(canvas, path, spec.wingColor, -h * 0.1f, h * 0.46f)
        path.reset()
        path.moveTo(w * 0.08f, -h * 0.10f); path.lineTo(w * 0.64f, h * 0.46f)
        path.lineTo(w * 0.30f, h * 0.40f); path.lineTo(w * 0.08f, h * 0.14f)
        path.close(); shadedFill(canvas, path, spec.wingColor, -h * 0.1f, h * 0.46f)
        path.reset()
        path.moveTo(0f, -h * 0.58f)
        path.lineTo(w * 0.09f, h * 0.30f); path.lineTo(0f, h * 0.5f)
        path.lineTo(-w * 0.09f, h * 0.30f)
        path.close(); shadedFill(canvas, path, spec.bodyColor, -h * 0.58f, h * 0.5f)
        canopyGlass(canvas, 0f, -h * 0.20f, w * 0.07f, spec.accentColor)
        paint.color = spec.flameColor
        paint.alpha = 140
        canvas.drawRect(-w * 0.10f, h * 0.32f, -w * 0.04f, h * 0.48f, paint)
        canvas.drawRect(w * 0.04f, h * 0.32f, w * 0.10f, h * 0.48f, paint)
        paint.alpha = 255
        renderHardware(canvas, w * 0.64f, h * 0.44f)
    }

    private fun renderHeavy(canvas: Canvas) {
        // Titan — broad, heavy bomber: wide integrated wing slabs, boxy central hull,
        // armor plating band. Noticeably wider and bulkier than Vanguard/Nova.
        val w = Constants.PLAYER_WIDTH * 1.4f; val h = Constants.PLAYER_HEIGHT * 0.96f
        path.reset(); path.addRoundRect(-w * 0.58f, -h * 0.02f, -w * 0.18f, h * 0.40f, 8f, 8f, Path.Direction.CW)
        shadedFill(canvas, path, spec.wingColor, -h * 0.02f, h * 0.40f)
        path.reset(); path.addRoundRect(w * 0.18f, -h * 0.02f, w * 0.58f, h * 0.40f, 8f, 8f, Path.Direction.CW)
        shadedFill(canvas, path, spec.wingColor, -h * 0.02f, h * 0.40f)
        path.reset()
        path.moveTo(0f, -h / 2f)
        path.lineTo(w * 0.24f, h * 0.10f); path.lineTo(w * 0.20f, h * 0.5f)
        path.lineTo(-w * 0.20f, h * 0.5f); path.lineTo(-w * 0.24f, h * 0.10f)
        path.close(); shadedFill(canvas, path, spec.bodyColor, -h / 2f, h * 0.5f)
        canopyGlass(canvas, 0f, -h * 0.14f, w * 0.10f, spec.accentColor)
        paint.color = spec.wingColor
        canvas.drawRect(-w * 0.14f, h * 0.16f, w * 0.14f, h * 0.22f, paint)
        renderHardware(canvas, w * 0.42f, h * 0.22f)
    }

    /**
     * Wing-mounted gun barrels and/or missile pods, drawn purely from what [spec] actually
     * carries — so the visual affordance can never drift from the real loadout. [wingX]/[wingY]
     * is roughly where each hull's wingtip sits, passed in by the caller.
     */
    private fun renderHardware(canvas: Canvas, wingX: Float, wingY: Float) {
        if (spec.wingWeapon != null) {
            paint.color = Color.rgb(60, 62, 74)
            canvas.drawRoundRect(-wingX - 4f, wingY - 12f, -wingX + 4f, wingY + 8f, 2f, 2f, paint)
            canvas.drawRoundRect(wingX - 4f, wingY - 12f, wingX + 4f, wingY + 8f, 2f, 2f, paint)
            paint.color = spec.wingWeapon!!.glowColor
            canvas.drawCircle(-wingX, wingY - 12f, 2.6f, paint)
            canvas.drawCircle(wingX, wingY - 12f, 2.6f, paint)
        }
        if (spec.missileWeapon != null) {
            val podX = wingX * 0.62f
            paint.color = Color.rgb(80, 82, 92)
            canvas.drawRoundRect(-podX - 7f, wingY + 4f, -podX + 7f, wingY + 24f, 4f, 4f, paint)
            canvas.drawRoundRect(podX - 7f, wingY + 4f, podX + 7f, wingY + 24f, 4f, 4f, paint)
            paint.color = spec.missileWeapon!!.glowColor
            canvas.drawCircle(-podX, wingY + 4f, 4.5f, paint)
            canvas.drawCircle(podX, wingY + 4f, 4.5f, paint)
        }
    }
}
