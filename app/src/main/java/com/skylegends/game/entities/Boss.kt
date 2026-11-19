package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skylegends.game.bosses.BossAttackPattern
import com.skylegends.game.bosses.BossHullShape
import com.skylegends.game.bosses.BossSpec
import com.skylegends.game.utils.Constants
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The level boss: a heavy dreadnought with three escalating phases. It flies in from the
 * top (invulnerable during the cinematic entrance), then holds station and strafes while
 * cycling attack patterns whose density ramps as its health falls. Phase changes raise a
 * flag GameView turns into flash + shake + a rage roar. Identity/toughness/aggression come
 * from [spec] — the attack choreography itself stays in code.
 */
class Boss(private val spec: BossSpec) : Entity() {

    val displayName get() = spec.displayName
    val maxHp = spec.maxHp
    var hp = maxHp
    var phase = 1; private set

    var entering = true; private set
    private var entranceT = 0f
    private var age = 0f
    private var fireTimer = 1.2f
    private var spiralAngle = 0f
    private var sweepAngle = 0f
    private var hitFlash = 0f
    private var hoverY = Constants.GAME_HEIGHT * 0.24f

    /** Set true on a phase transition; GameView consumes it for FX. */
    var justChangedPhase = false

    val invulnerable get() = entering

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val detailPath = Path()
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 170 }
    private val greeblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    fun spawn() {
        pos.set(Constants.GAME_WIDTH / 2f, -160f)
        collisionRadius = 78f * spec.hullScale
        active = true
        entering = true
    }

    fun hit(damage: Float) {
        if (entering) return
        hp -= damage
        hitFlash = 1f
        val newPhase = when {
            hp <= maxHp * 0.33f -> 3
            hp <= maxHp * 0.66f -> 2
            else -> 1
        }
        if (newPhase != phase) { phase = newPhase; justChangedPhase = true }
        if (hp <= 0f) active = false
    }

    fun tick(dt: Float, playerX: Float, playerY: Float, spawner: BulletSpawner) {
        age += dt
        if (hitFlash > 0f) hitFlash = (hitFlash - dt * 6f).coerceAtLeast(0f)

        if (entering) {
            entranceT += dt
            pos.y += (hoverY - pos.y) * (1f - Math.exp(-2.2 * dt).toFloat())
            if (entranceT >= Constants.BOSS_ENTRANCE_TIME) entering = false
            return
        }

        // Strafe.
        pos.x = Constants.GAME_WIDTH / 2f + sin(age * (0.6f + 0.2f * phase)) * (Constants.GAME_WIDTH * 0.28f)

        // Attack cadence tightens with phase (and with the boss's overall aggression). SWEEP_ARC
        // fires much more often than the others — successive single shots at a slowly rotating
        // angle is what makes the sweep read as a moving wall instead of discrete volleys.
        val cadence = (when (phase) { 1 -> 1.4f; 2 -> 0.95f; else -> 0.6f }) / spec.aggression
        val effectiveCadence = if (spec.pattern == BossAttackPattern.SWEEP_ARC) cadence * 0.35f else cadence
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = effectiveCadence
            attack(playerX, playerY, spawner)
        }

        // Continuous per-frame emitters, exclusive to the pattern that owns them.
        if (spec.pattern == BossAttackPattern.SPIRAL_BURST && phase >= 2) {
            spiralAngle += dt * (2.4f + phase) * spec.aggression
            if ((age * 60f).toInt() % 4 == 0) {
                val core = Color.rgb(255, 170, 90); val glow = Color.rgb(255, 90, 40)
                val arms = if (phase == 2) 2 else 4
                for (arm in 0 until arms) {
                    val a = spiralAngle + arm * (2f * Math.PI.toFloat() / arms)
                    spawner.spawn(pos.x, pos.y + 40f, cos(a) * 240f, sin(a) * 240f, 7f, 12f, core, glow)
                }
            }
        }
        if (spec.pattern == BossAttackPattern.SWEEP_ARC) {
            sweepAngle += dt * (1.1f + 0.3f * phase)
        }
    }

    /** Dispatches to this boss's signature pattern, then layers on a universal phase-3 ring
     * (every boss's original climax) so the final stretch of every fight stays intense. */
    private fun attack(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        when (spec.pattern) {
            BossAttackPattern.AIMED_FAN -> fireAimedFan(playerX, playerY, spawner)
            BossAttackPattern.TWIN_CANNON -> fireTwinCannon(playerX, playerY, spawner)
            BossAttackPattern.BULLET_RAIN -> fireBulletRain(spawner)
            BossAttackPattern.SWEEP_ARC -> fireSweepShot(spawner)
            BossAttackPattern.SPIRAL_BURST -> fireAimedFan(playerX, playerY, spawner)
            BossAttackPattern.PINCER_AIMED -> firePincer(playerX, playerY, spawner)
            BossAttackPattern.MINEFIELD -> fireMinefield(spawner)
            BossAttackPattern.FINAL_MIX -> { fireAimedFan(playerX, playerY, spawner); fireBulletRain(spawner) }
        }
        if (phase == 3) fireRing(spawner)
    }

    /** Sentinel Mk-I's signature: a single aimed fan, widening with phase — the campaign's
     * simplest, most readable pattern, fitting for the tutorial-tier first boss. */
    private fun fireAimedFan(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        val core = Color.rgb(255, 120, 120); val glow = Color.rgb(255, 50, 50)
        val oy = pos.y + 40f
        val base = atan2(playerY - oy, playerX - pos.x)
        val count = (3 + phase * 2 + ((spec.aggression - 1f) * 3f)).toInt().coerceAtLeast(3)
        val spread = 0.5f + 0.12f * phase
        for (i in 0 until count) {
            val t = if (count == 1) 0f else i / (count - 1f) - 0.5f
            val a = base + t * spread
            spawner.spawn(pos.x, oy, cos(a) * 300f, sin(a) * 300f, 7f, 14f, core, glow)
        }
    }

    /** Iron Warden: two independent aimed streams from the wingtip cannons instead of one
     * central fan — reads as twin turrets locking on separately. */
    private fun fireTwinCannon(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        val core = Color.rgb(255, 150, 90); val glow = Color.rgb(255, 90, 40)
        val w = 200f * spec.hullScale
        val shots = 1 + phase
        for (side in intArrayOf(-1, 1)) {
            val ox = pos.x + side * w * 0.44f; val oy = pos.y + 10f
            val base = atan2(playerY - oy, playerX - ox)
            for (i in 0 until shots) {
                val a = base + (i - (shots - 1) / 2f) * 0.10f
                spawner.spawn(ox, oy, cos(a) * 320f, sin(a) * 320f, 6.5f, 13f, core, glow)
            }
        }
    }

    /** Stormbringer: a wide unaimed row falling straight down across the hull's width — a
     * storm to weave through rather than a shot to dodge. */
    private fun fireBulletRain(spawner: BulletSpawner) {
        val core = Color.rgb(120, 190, 255); val glow = Color.rgb(70, 140, 255)
        val w = 200f * spec.hullScale
        val cols = 5 + phase * 2
        for (i in 0 until cols) {
            val t = if (cols == 1) 0f else i / (cols - 1f) - 0.5f
            spawner.spawn(pos.x + t * w * 0.9f, pos.y + 30f, 0f, 260f + phase * 30f, 6f, 12f, core, glow)
        }
    }

    /** Void Reaper: a single shot at the current sweep angle, fired on a much shorter cadence
     * (see [tick]) so successive shots trace a moving scythe-like wall across the screen. */
    private fun fireSweepShot(spawner: BulletSpawner) {
        val core = Color.rgb(255, 210, 120); val glow = Color.rgb(255, 150, 40)
        val a = Math.PI.toFloat() / 2f + sin(sweepAngle) * (0.9f + 0.15f * phase)
        spawner.spawn(pos.x, pos.y + 40f, cos(a) * 320f, sin(a) * 320f, 7f, 13f, core, glow)
    }

    /** Apex Predator: twin aimed streams from both wingtips converging on the player's
     * position — visually closes in like a pincer even though each bullet travels straight. */
    private fun firePincer(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        val core = Color.rgb(200, 120, 255); val glow = Color.rgb(140, 60, 220)
        val w = 200f * spec.hullScale
        val extra = (phase - 1).coerceAtLeast(0)
        for (side in intArrayOf(-1, 1)) {
            val ox = pos.x + side * w * 0.56f; val oy = pos.y - 10f
            val a = atan2(playerY - oy, playerX - ox)
            for (i in 0..extra) {
                val speed = 300f + i * 20f
                spawner.spawn(ox, oy, cos(a) * speed, sin(a) * speed, 7f, 14f, core, glow)
            }
        }
    }

    /** Oblivion Ward: a slow, dense expanding ring the player must weave through rather than
     * dodge a discrete shot — density grows with phase. */
    private fun fireMinefield(spawner: BulletSpawner) {
        val core = Color.rgb(180, 255, 180); val glow = Color.rgb(90, 220, 120)
        val count = 10 + phase * 4
        for (i in 0 until count) {
            val a = (i / count.toFloat()) * 2f * Math.PI.toFloat() + age * 0.3f
            spawner.spawn(pos.x, pos.y, cos(a) * 110f, sin(a) * 110f, 6f, 11f, core, glow)
        }
    }

    private fun fireRing(spawner: BulletSpawner) {
        val core = Color.rgb(255, 120, 120); val glow = Color.rgb(255, 50, 50)
        for (i in 0 until 16) {
            val a = (i / 16f) * 2f * Math.PI.toFloat()
            spawner.spawn(pos.x, pos.y, cos(a) * 200f, sin(a) * 200f, 6f, 12f, core, glow)
        }
    }

    override fun update(dt: Float) { /* driven by tick() */ }

    override fun render(canvas: Canvas) {
        canvas.save()
        canvas.translate(pos.x, pos.y)

        val w = 200f * spec.hullScale; val h = 150f * spec.hullScale
        val damage = 1f - (hp / maxHp).coerceIn(0f, 1f)

        if (renderSpriteHull(canvas, w, damage)) { canvas.restore(); return }

        // Each boss keeps its OWN plating colour (so no two read alike), still shifting toward
        // rage-red as it takes damage.
        val hullBase = lerpColor(spec.hullColor, Color.rgb(200, 60, 60), damage * 0.5f)

        // A few silhouettes want a glow BEHIND the hull: ship-like hulls get rear thrusters,
        // the SOLAR corona gets a flaring halo. Drawn first so the hull sits on top.
        when (spec.hullShape) {
            BossHullShape.WEDGE, BossHullShape.MANTA, BossHullShape.TRIDENT, BossHullShape.SAUCER ->
                renderEngineGlow(canvas, w, h)
            BossHullShape.SOLAR -> {
                val flare = 0.7f + 0.3f * sin(age * 4f)
                paint.shader = RadialGradient(0f, 0f, w * 0.9f * flare, Color.argb(150, 255, 190, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawCircle(0f, 0f, w * 0.9f * flare, paint)
                paint.shader = null
            }
            else -> {}
        }

        // Build this boss's silhouette into `path` (reused by the hit-flash), then draw it as a
        // top-lit modeled plate with a dark outline.
        buildHull(spec.hullShape, w, h)
        paint.shader = LinearGradient(0f, -h * 0.6f, 0f, h * 0.6f, lighten(hullBase, 0.25f), darken(hullBase, 0.4f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        outlinePaint.color = darken(hullBase, 0.62f)
        canvas.drawPath(path, outlinePaint)

        // Shape-specific surface detail / flourishes.
        renderShapeAccents(canvas, spec.hullShape, w, h, hullBase)

        // Core weak point — pulses, brighter in later phases. Soft outer bloom + hot white
        // center reads as a glowing reactor. Sits where each hull wants its heart.
        val pulse = 0.6f + 0.4f * sin(age * (3f + phase)).toFloat()
        val coreColor = when (phase) {
            1 -> spec.coreColor
            2 -> Color.rgb(255, 200, 90)
            else -> Color.rgb(255, 90, 90)
        }
        val coreY = coreOffsetY(spec.hullShape, h)
        paint.shader = RadialGradient(0f, coreY, 42f + 8f * pulse, coreColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.alpha = (180 + 75 * pulse).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, coreY, 42f + 8f * pulse, paint)
        paint.shader = null
        paint.alpha = 255
        paint.shader = RadialGradient(0f, coreY, 26f + 6f * pulse, Color.WHITE, coreColor, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, coreY, 26f + 6f * pulse, paint)
        paint.shader = null
        paint.color = Color.WHITE
        canvas.drawCircle(0f, coreY, 11f, paint)

        // Battle damage: smoke puffs as HP drops.
        if (damage > 0.4f) {
            paint.color = Color.rgb(40, 40, 45)
            paint.alpha = 150
            canvas.drawCircle(-w * 0.25f, -h * 0.2f, 16f + 6f * sin(age * 5f), paint)
            if (damage > 0.7f) canvas.drawCircle(w * 0.22f, -h * 0.15f, 18f + 6f * sin(age * 4f + 1f), paint)
            paint.alpha = 255
        }

        // Hit flash.
        if (hitFlash > 0f) {
            paint.color = Color.WHITE
            paint.alpha = (hitFlash * 160f).toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
            paint.alpha = 255
        }
        canvas.restore()
    }

    /**
     * Sprite-composite capital ship (Kenney CC0 parts), so bosses share the art style of the
     * player and enemy ships. Each sector gets a hand-picked hull/wing/gun combo; sectors 9-16
     * add an outer wing pair and a second gun battery so later bosses read as more heavily armed.
     * Layout was prototyped offline. Returns false (-> vector fallback) if sprites are missing.
     */
    private fun renderSpriteHull(canvas: Canvas, w: Float, damage: Float): Boolean {
        val index = spec.id.removePrefix("boss_").toIntOrNull() ?: 0
        val look = BOSS_LOOKS[index.coerceIn(0, BOSS_LOOKS.size - 1)]
        val hull = com.skylegends.game.fx.Sprites.get(look.hull) ?: return false
        val wing = com.skylegends.game.fx.Sprites.get(look.wing) ?: return false
        // Bosses are under near-constant fire, so a full-strength flash would bleach the hull
        // white permanently; a light tint keeps its colours readable while still reacting.
        val flash = hitFlash * 0.35f
        val heavy = index >= 8

        // Thruster glow behind the engine nozzles (drawn first so the hull sits on top).
        val pulse = 0.7f + 0.3f * sin(age * 6f)
        for (s in SIDES) {
            val ex = s * w * 0.12f; val ey = -w * 0.34f
            paint.shader = RadialGradient(ex, ey, w * 0.11f * pulse, Color.argb(220, 150, 205, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(ex, ey, w * 0.11f * pulse, paint)
        }
        paint.shader = null

        for (s in SIDES) part(canvas, "engine1", s * w * 0.12f, -w * 0.27f, w * 0.12f, flash = flash)
        // Wings: rotated to face down, mirrored per side, height capped so tall wing shapes fit.
        val wingW = minOf(w * 0.28f, w * 0.5f * wing.width / wing.height)
        for (s in SIDES) part(canvas, look.wing, s * w * 0.34f, -w * 0.02f, wingW, flip = s < 0, flash = flash)
        if (heavy) {
            for (s in SIDES) part(canvas, look.wing, s * w * 0.50f, w * 0.06f, wingW * 0.62f, flip = s < 0, flash = flash)
            for (s in SIDES) part(canvas, look.gun, s * w * 0.48f, w * 0.22f, w * 0.08f, flash = flash)
        }
        for (s in SIDES) part(canvas, look.gun, s * w * 0.30f, w * 0.21f, w * 0.10f, flash = flash)

        // Main hull (enemy sprites already face down — no rotation).
        com.skylegends.game.fx.Sprites.drawCentered(canvas, hull, w * 0.60f, flash)

        // Battle scars accumulate as HP drops.
        if (damage > 0.33f) part(canvas, "scratch1", -w * 0.10f, w * 0.06f, w * 0.13f, rotate = false)
        if (damage > 0.66f) {
            part(canvas, "scratch2", w * 0.12f, -w * 0.05f, w * 0.13f, rotate = false)
            part(canvas, "scratch3", -w * 0.02f, w * 0.16f, w * 0.10f, rotate = false)
        }

        // Reactor core = the weak point, seated in a turret ring; colour ramps with phase.
        val coreY = -w * 0.02f
        part(canvas, "turretBase_big", 0f, coreY, w * 0.20f, rotate = false, flash = flash)
        val cp = 0.6f + 0.4f * sin(age * (3f + phase)).toFloat()
        val coreColor = when (phase) {
            1 -> spec.coreColor
            2 -> Color.rgb(255, 200, 90)
            else -> Color.rgb(255, 90, 90)
        }
        val k = w / 200f
        paint.shader = RadialGradient(0f, coreY, (30f + 6f * cp) * k, coreColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.alpha = (180 + 75 * cp).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, coreY, (30f + 6f * cp) * k, paint)
        paint.alpha = 255
        paint.shader = RadialGradient(0f, coreY, (15f + 3f * cp) * k, Color.WHITE, coreColor, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, coreY, (15f + 3f * cp) * k, paint)
        paint.shader = null

        // Smoke from the damaged hull.
        if (damage > 0.4f) {
            paint.color = Color.rgb(40, 40, 45)
            paint.alpha = 150
            canvas.drawCircle(-w * 0.2f, -w * 0.12f, 16f + 6f * sin(age * 5f), paint)
            if (damage > 0.7f) canvas.drawCircle(w * 0.18f, -w * 0.08f, 18f + 6f * sin(age * 4f + 1f), paint)
            paint.alpha = 255
        }
        return true
    }

    /** Draws one boss part centred at (x, y); parts are authored facing up, so rotate 180°. */
    private fun part(
        canvas: Canvas, name: String, x: Float, y: Float, width: Float,
        flip: Boolean = false, rotate: Boolean = true, flash: Float = 0f
    ) {
        val bmp = com.skylegends.game.fx.Sprites.get(name) ?: return
        canvas.save()
        canvas.translate(x, y)
        if (rotate) canvas.rotate(180f)
        if (flip) canvas.scale(-1f, 1f)
        com.skylegends.game.fx.Sprites.drawCentered(canvas, bmp, width, flash)
        canvas.restore()
    }

    private data class BossLook(val hull: String, val wing: String, val gun: String)

    private companion object {
        val SIDES = floatArrayOf(-1f, 1f)
        /** One per sector (index = sector-1). 0-7 standard, 8-15 heavy (extra wings + guns). */
        val BOSS_LOOKS = listOf(
            BossLook("enemyBlack1", "wingBlue_2", "gun04"),   // Sentinel Mk-I
            BossLook("enemyBlack4", "wingBlue_0", "gun02"),   // Iron Warden
            BossLook("enemyBlue3", "wingBlue_7", "gun04"),    // Stormbringer
            BossLook("enemyBlack2", "wingRed_6", "gun09"),    // Void Reaper
            BossLook("enemyRed2", "wingRed_3", "gun09"),      // Crimson Judge
            BossLook("enemyGreen4", "wingGreen_5", "gun02"),  // Apex Predator
            BossLook("enemyBlue5", "wingBlue_1", "gun09"),    // Oblivion Ward
            BossLook("enemyRed4", "wingRed_7", "gun04"),      // Dreadnought Prime
            BossLook("enemyBlack3", "wingBlue_4", "gun09"),   // Moon Breaker
            BossLook("enemyGreen2", "wingGreen_7", "gun09"),  // Grave Titan
            BossLook("enemyBlack5", "wingYellow_6", "gun09"), // Solar Tyrant
            BossLook("ufoYellow", "wingYellow_4", "gun04"),   // Ring Sovereign
            BossLook("enemyRed3", "wingRed_1", "gun02"),      // Horizon Devourer
            BossLook("enemyBlue1", "wingBlue_5", "gun09"),    // Quantum Specter
            BossLook("enemyGreen3", "wingGreen_3", "gun04"),  // Hive Overmind
            BossLook("enemyRed5", "wingYellow_2", "gun09")    // Galactic Emperor
        )
    }

    /** Where each hull carries its glowing core, in local space. */
    private fun coreOffsetY(shape: BossHullShape, h: Float): Float = when (shape) {
        BossHullShape.WEDGE -> -h * 0.08f
        BossHullShape.MANTA -> -h * 0.02f
        BossHullShape.MAW -> -h * 0.10f
        else -> 0f
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt().coerceIn(0, 255)
        )
    }

    // ---- Silhouettes: each fills `path` with a completely different hull outline. ----

    private fun buildHull(shape: BossHullShape, w: Float, h: Float) {
        path.reset()
        path.fillType = Path.FillType.WINDING
        when (shape) {
            BossHullShape.WEDGE -> hullWedge(w, h)
            BossHullShape.SAUCER -> path.addOval(-w * 0.62f, -h * 0.20f, w * 0.62f, h * 0.30f, Path.Direction.CW)
            BossHullShape.MANTA -> hullManta(w, h)
            BossHullShape.CRESCENT -> hullCrescent(w, h)
            BossHullShape.TRIDENT -> hullTrident(w, h)
            BossHullShape.SPIDER -> starInto(8, 0.30f, 0.62f, 0f, w)
            BossHullShape.RING -> hullRing(w)
            BossHullShape.ORB -> path.addCircle(0f, -h * 0.02f, w * 0.52f, Path.Direction.CW)
            BossHullShape.CRYSTAL -> starInto(6, 0.34f, 0.60f, 0.26f, w)
            BossHullShape.HIVE -> hullHive(w)
            BossHullShape.SOLAR -> starInto(16, 0.46f, 0.62f, 0f, w)
            BossHullShape.MAW -> hullMaw(w, h)
        }
    }

    /** The original layered-warship wedge — nose toward the player, swept wings, spine notch. */
    private fun hullWedge(w: Float, h: Float) {
        path.moveTo(0f, h * 0.60f)
        path.lineTo(w * 0.10f, h * 0.42f); path.lineTo(w * 0.20f, h * 0.46f)
        path.lineTo(w * 0.34f, h * 0.20f); path.lineTo(w * 0.50f, h * 0.14f)
        path.lineTo(w * 0.46f, -h * 0.02f); path.lineTo(w * 0.62f, -h * 0.10f)
        path.lineTo(w * 0.56f, -h * 0.28f); path.lineTo(w * 0.34f, -h * 0.40f)
        path.lineTo(w * 0.30f, -h * 0.56f); path.lineTo(w * 0.10f, -h * 0.50f)
        path.lineTo(0f, -h * 0.58f)
        path.lineTo(-w * 0.10f, -h * 0.50f); path.lineTo(-w * 0.30f, -h * 0.56f)
        path.lineTo(-w * 0.34f, -h * 0.40f); path.lineTo(-w * 0.56f, -h * 0.28f)
        path.lineTo(-w * 0.62f, -h * 0.10f); path.lineTo(-w * 0.46f, -h * 0.02f)
        path.lineTo(-w * 0.50f, h * 0.14f); path.lineTo(-w * 0.34f, h * 0.20f)
        path.lineTo(-w * 0.20f, h * 0.46f); path.lineTo(-w * 0.10f, h * 0.42f)
        path.close()
    }

    /** A stingray: curved swept wings sweeping back to twin tail fins. */
    private fun hullManta(w: Float, h: Float) {
        path.moveTo(0f, h * 0.56f)
        path.cubicTo(w * 0.52f, h * 0.20f, w * 0.64f, -h * 0.06f, w * 0.46f, -h * 0.34f)
        path.lineTo(w * 0.16f, -h * 0.12f); path.lineTo(w * 0.12f, -h * 0.50f)
        path.lineTo(0f, -h * 0.26f)
        path.lineTo(-w * 0.12f, -h * 0.50f); path.lineTo(-w * 0.16f, -h * 0.12f)
        path.cubicTo(-w * 0.64f, -h * 0.06f, -w * 0.52f, h * 0.20f, 0f, h * 0.56f)
        path.close()
    }

    /** A horned crescent: a disc with a bite carved out toward the player (even-odd fill). */
    private fun hullCrescent(w: Float, h: Float) {
        path.fillType = Path.FillType.EVEN_ODD
        path.addCircle(0f, -h * 0.04f, w * 0.56f, Path.Direction.CW)
        path.addCircle(0f, h * 0.42f, w * 0.50f, Path.Direction.CW)
    }

    /** Three forward-thrust prongs from a swept back plate. */
    private fun hullTrident(w: Float, h: Float) {
        path.moveTo(0f, -h * 0.42f)
        path.lineTo(-w * 0.50f, -h * 0.18f); path.lineTo(-w * 0.40f, h * 0.52f)
        path.lineTo(-w * 0.26f, -h * 0.02f); path.lineTo(-w * 0.10f, h * 0.14f)
        path.lineTo(0f, h * 0.60f)
        path.lineTo(w * 0.10f, h * 0.14f); path.lineTo(w * 0.26f, -h * 0.02f)
        path.lineTo(w * 0.40f, h * 0.52f); path.lineTo(w * 0.50f, -h * 0.18f)
        path.close()
    }

    /** A hollow ring/torus (even-odd punches out the centre — the core floats in the hole). */
    private fun hullRing(w: Float) {
        path.fillType = Path.FillType.EVEN_ODD
        path.addCircle(0f, 0f, w * 0.60f, Path.Direction.CW)
        path.addCircle(0f, 0f, w * 0.34f, Path.Direction.CW)
    }

    /** A lumpy organic blob — a circle perturbed by fixed harmonics so it reads as living mass. */
    private fun hullHive(w: Float) {
        val n = 22
        for (i in 0..n) {
            val a = (Math.PI * 2 * i / n).toFloat()
            val r = w * 0.50f + (sin(a * 3f) * 0.05f + sin(a * 5f + 1f) * 0.035f) * w
            val x = cos(a) * r; val y = sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** A gaping maw: a disc with a V-mouth carved toward the player (even-odd fill). */
    private fun hullMaw(w: Float, h: Float) {
        path.fillType = Path.FillType.EVEN_ODD
        path.addCircle(0f, -h * 0.04f, w * 0.52f, Path.Direction.CW)
        path.moveTo(0f, -h * 0.04f)
        path.lineTo(-w * 0.5f, h * 0.62f); path.lineTo(w * 0.5f, h * 0.62f)
        path.close()
    }

    /** A radial star polygon — [points] spikes alternating an outer/inner radius. Drives the
     * SPIDER, CRYSTAL and SOLAR hulls just by varying point count and spike depth. */
    private fun starInto(points: Int, innerFrac: Float, outerFrac: Float, rot: Float, w: Float) {
        val ro = w * outerFrac; val ri = w * innerFrac
        val n = points * 2
        for (i in 0 until n) {
            val a = rot + (Math.PI * i / points).toFloat()
            val r = if (i % 2 == 0) ro else ri
            val x = cos(a) * r; val y = sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // ---- Shape-specific surface detail. ----

    private fun renderShapeAccents(canvas: Canvas, shape: BossHullShape, w: Float, h: Float, hullBase: Int) {
        when (shape) {
            BossHullShape.WEDGE -> {
                renderSpinePlate(canvas, w, h)
                renderGreebles(canvas, w, h, hullBase)
                renderSidePods(canvas, w, h)
                renderNavLights(canvas, w, h)
            }
            BossHullShape.SAUCER -> {
                val dome = lighten(hullBase, 0.35f)
                paint.shader = RadialGradient(0f, -h * 0.04f, w * 0.30f, lighten(dome, 0.2f), darken(dome, 0.3f), Shader.TileMode.CLAMP)
                canvas.drawOval(-w * 0.30f, -h * 0.20f, w * 0.30f, h * 0.10f, paint)
                paint.shader = null
                lightPaint.color = Color.rgb(120, 220, 255)
                for (i in 0 until 10) {
                    val a = age * 1.5f + i * (2f * Math.PI.toFloat() / 10)
                    lightPaint.alpha = (110 + 145 * ((sin(a * 3f) + 1f) / 2f)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cos(a) * w * 0.5f, h * 0.05f + sin(a) * h * 0.10f, 3.5f, lightPaint)
                }
                lightPaint.alpha = 255
            }
            BossHullShape.MANTA -> {
                outlinePaint.color = lighten(hullBase, 0.5f)
                canvas.drawPath(path, outlinePaint)
                lightPaint.color = spec.coreColor; lightPaint.alpha = 220
                canvas.drawCircle(-w * 0.22f, h * 0.06f, 6f, lightPaint)
                canvas.drawCircle(w * 0.22f, h * 0.06f, 6f, lightPaint)
                lightPaint.alpha = 255
            }
            BossHullShape.CRESCENT -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                paint.color = lighten(hullBase, 0.55f); paint.alpha = 200
                canvas.drawCircle(0f, h * 0.42f, w * 0.50f, paint)
                paint.alpha = 255; paint.style = Paint.Style.FILL
            }
            BossHullShape.TRIDENT -> {
                lightPaint.color = spec.coreColor
                val p = (sin(age * 5f) + 1f) / 2f
                lightPaint.alpha = (150 + 105 * p).toInt().coerceIn(0, 255)
                canvas.drawCircle(-w * 0.40f, h * 0.52f, 7f, lightPaint)
                canvas.drawCircle(0f, h * 0.60f, 8f, lightPaint)
                canvas.drawCircle(w * 0.40f, h * 0.52f, 7f, lightPaint)
                lightPaint.alpha = 255
            }
            BossHullShape.SPIDER -> {
                lightPaint.color = lighten(hullBase, 0.4f)
                for (i in 0 until 8) {
                    val a = (Math.PI * 2 * i / 8).toFloat()
                    canvas.drawCircle(cos(a) * w * 0.30f, sin(a) * w * 0.30f, 4f, lightPaint)
                }
            }
            BossHullShape.RING -> {
                lightPaint.color = spec.coreColor
                for (i in 0 until 12) {
                    val a = age * 0.6f + i * (2f * Math.PI.toFloat() / 12)
                    lightPaint.alpha = (100 + 155 * ((sin(a * 2f) + 1f) / 2f)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cos(a) * w * 0.47f, sin(a) * w * 0.47f, 4f, lightPaint)
                }
                lightPaint.alpha = 255
            }
            BossHullShape.ORB -> {
                paint.color = darken(hullBase, 0.45f)
                canvas.drawRect(-w * 0.52f, -h * 0.055f, w * 0.52f, h * 0.02f, paint)
                paint.color = lighten(hullBase, 0.3f)
                canvas.drawRect(-w * 0.52f, -h * 0.055f, w * 0.52f, -h * 0.035f, paint)
            }
            BossHullShape.CRYSTAL -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
                paint.color = lighten(hullBase, 0.5f); paint.alpha = 170
                for (i in 0 until 6) {
                    val a = 0.26f + (Math.PI * (2 * i) / 6).toFloat()
                    canvas.drawLine(0f, 0f, cos(a) * w * 0.58f, sin(a) * w * 0.58f, paint)
                }
                paint.alpha = 255; paint.style = Paint.Style.FILL
            }
            BossHullShape.HIVE -> {
                val cells = arrayOf(
                    floatArrayOf(-0.18f, -0.14f), floatArrayOf(0.20f, -0.10f),
                    floatArrayOf(0.05f, 0.20f), floatArrayOf(-0.22f, 0.16f), floatArrayOf(0.26f, 0.18f)
                )
                for ((k, c) in cells.withIndex()) {
                    val p = (sin(age * 3f + k) + 1f) / 2f
                    lightPaint.color = spec.coreColor
                    lightPaint.alpha = (60 + 120 * p).toInt().coerceIn(0, 255)
                    canvas.drawCircle(c[0] * w, c[1] * w, 9f + 3f * p, lightPaint)
                }
                lightPaint.alpha = 255
            }
            BossHullShape.SOLAR -> {
                // Corona already flares behind the hull; a hot inner disc finishes the star.
                paint.shader = RadialGradient(0f, 0f, w * 0.34f, lighten(hullBase, 0.5f), darken(hullBase, 0.2f), Shader.TileMode.CLAMP)
                canvas.drawCircle(0f, 0f, w * 0.34f, paint)
                paint.shader = null
            }
            BossHullShape.MAW -> {
                paint.shader = RadialGradient(0f, h * 0.28f, w * 0.30f, spec.coreColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawCircle(0f, h * 0.28f, w * 0.30f, paint)
                paint.shader = null
                paint.color = lighten(hullBase, 0.55f)
                detailPath.reset()
                val teeth = 6
                for (i in 0 until teeth) {
                    val fx = -0.42f + 0.84f * (i / (teeth - 1f))
                    val baseX = fx * w
                    val baseY = h * 0.30f + Math.abs(fx) * h * 0.30f
                    detailPath.moveTo(baseX - w * 0.05f, baseY)
                    detailPath.lineTo(baseX, baseY + h * 0.14f)
                    detailPath.lineTo(baseX + w * 0.05f, baseY)
                    detailPath.close()
                }
                canvas.drawPath(detailPath, paint)
            }
        }
    }

    private fun renderSpinePlate(canvas: Canvas, w: Float, h: Float) {
        val armor = Color.rgb(50, 55, 75)
        detailPath.reset()
        detailPath.moveTo(0f, h * 0.30f)
        detailPath.lineTo(w * 0.14f, h * 0.06f)
        detailPath.lineTo(w * 0.11f, -h * 0.38f)
        detailPath.lineTo(0f, -h * 0.48f)
        detailPath.lineTo(-w * 0.11f, -h * 0.38f)
        detailPath.lineTo(-w * 0.14f, h * 0.06f)
        detailPath.close()
        paint.shader = LinearGradient(0f, -h * 0.48f, 0f, h * 0.3f, lighten(armor, 0.3f), darken(armor, 0.3f), Shader.TileMode.CLAMP)
        canvas.drawPath(detailPath, paint)
        paint.shader = null
    }

    private fun renderSidePods(canvas: Canvas, w: Float, h: Float) {
        val pod = Color.rgb(90, 60, 70)
        paint.shader = LinearGradient(-w * 0.52f, 0f, w * 0.52f, 0f, lighten(pod, 0.2f), darken(pod, 0.3f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-w * 0.52f, -h * 0.1f, -w * 0.3f, h * 0.28f, 8f, 8f, paint)
        canvas.drawRoundRect(w * 0.3f, -h * 0.1f, w * 0.52f, h * 0.28f, 8f, 8f, paint)
        paint.shader = null
    }

    private fun renderEngineGlow(canvas: Canvas, w: Float, h: Float) {
        val pulse = 0.7f + 0.3f * sin(age * 6f)
        val ports = floatArrayOf(-w * 0.16f, 0f, w * 0.16f)
        for (ex in ports) {
            val ey = -h * 0.60f
            paint.shader = RadialGradient(ex, ey, 24f * pulse, Color.argb(220, 150, 205, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(ex, ey, 24f * pulse, paint)
        }
        paint.shader = null
    }

    private fun renderGreebles(canvas: Canvas, w: Float, h: Float, hullBase: Int) {
        // (fx, fy) fractions of half-width/half-height, mirrored across the centerline; (fw, fh)
        // fractions of full width/height. A fixed hand-placed set, not random — deterministic
        // ship detail, not gameplay-relevant noise.
        val spots = arrayOf(
            floatArrayOf(0.20f, -0.34f, 0.09f, 0.05f),
            floatArrayOf(0.38f, -0.14f, 0.08f, 0.045f),
            floatArrayOf(0.44f, 0.06f, 0.07f, 0.05f),
            floatArrayOf(0.24f, 0.30f, 0.06f, 0.04f),
            floatArrayOf(0.14f, -0.50f, 0.08f, 0.035f)
        )
        greeblePaint.color = darken(hullBase, 0.55f)
        greeblePaint.alpha = 130
        for (s in spots) {
            val gx = s[0] * w; val gy = s[1] * h; val gw = s[2] * w; val gh = s[3] * h
            canvas.drawRect(gx - gw / 2f, gy - gh / 2f, gx + gw / 2f, gy + gh / 2f, greeblePaint)
            canvas.drawRect(-gx - gw / 2f, gy - gh / 2f, -gx + gw / 2f, gy + gh / 2f, greeblePaint)
        }
        greeblePaint.alpha = 255
    }

    private fun renderNavLights(canvas: Canvas, w: Float, h: Float) {
        // wingtip (red, slow blink), forward shoulder (green, offset blink), rear spine (white strobe).
        val wingtipOn = ((age * 1.6f).toInt() % 2) == 0
        val shoulderOn = ((age * 1.6f + 1f).toInt() % 2) == 0
        val strobe = ((sin(age * 9f) + 1f) / 2f).coerceIn(0f, 1f)

        lightPaint.color = Color.rgb(255, 50, 50)
        lightPaint.alpha = if (wingtipOn) 255 else 40
        canvas.drawCircle(w * 0.62f, -h * 0.10f, 4f, lightPaint)
        canvas.drawCircle(-w * 0.62f, -h * 0.10f, 4f, lightPaint)

        lightPaint.color = Color.rgb(70, 255, 110)
        lightPaint.alpha = if (shoulderOn) 255 else 40
        canvas.drawCircle(w * 0.50f, h * 0.14f, 3.5f, lightPaint)
        canvas.drawCircle(-w * 0.50f, h * 0.14f, 3.5f, lightPaint)

        lightPaint.color = Color.WHITE
        lightPaint.alpha = (90 + 165 * strobe).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, -h * 0.58f, 3.5f, lightPaint)
        lightPaint.alpha = 255
    }
}
