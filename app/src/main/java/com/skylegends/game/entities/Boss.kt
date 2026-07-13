package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
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
        collisionRadius = 78f
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

        // Attack cadence tightens with phase (and with the boss's overall aggression).
        val cadence = (when (phase) { 1 -> 1.4f; 2 -> 0.95f; else -> 0.6f }) / spec.aggression
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = cadence
            attack(playerX, playerY, spawner)
        }
        // Continuous spiral in later phases.
        if (phase >= 2) {
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
    }

    private fun attack(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        val core = Color.rgb(255, 120, 120); val glow = Color.rgb(255, 50, 50)
        val oy = pos.y + 40f
        // Aimed fan; wider + denser each phase, scaled by aggression.
        val base = atan2(playerY - oy, playerX - pos.x)
        val count = (3 + phase * 2 + ((spec.aggression - 1f) * 3f)).toInt().coerceAtLeast(3)
        val spread = 0.5f + 0.12f * phase
        for (i in 0 until count) {
            val t = if (count == 1) 0f else i / (count - 1f) - 0.5f
            val a = base + t * spread
            spawner.spawn(pos.x, oy, cos(a) * 300f, sin(a) * 300f, 7f, 14f, core, glow)
        }
        // Phase 3 adds a full ring on every volley.
        if (phase == 3) {
            for (i in 0 until 16) {
                val a = (i / 16f) * 2f * Math.PI.toFloat()
                spawner.spawn(pos.x, pos.y, cos(a) * 200f, sin(a) * 200f, 6f, 12f, core, glow)
            }
        }
    }

    override fun update(dt: Float) { /* driven by tick() */ }

    override fun render(canvas: Canvas) {
        canvas.save()
        canvas.translate(pos.x, pos.y)

        val w = 200f; val h = 150f
        val damage = 1f - (hp / maxHp).coerceIn(0f, 1f)

        // Hull colour shifts toward red with rage.
        val r = (70 + 120 * damage).toInt().coerceIn(0, 255)
        val hullBase = Color.rgb(r, 70, 90)

        // Rear engine thrusters, drawn BEHIND the hull so the glow reads as escaping from
        // past the ship's silhouette rather than sitting on top of it — the "just arrived
        // from deep space" cue movie capital ships lead with.
        renderEngineGlow(canvas, w, h)

        // Hull — an asymmetric-looking jagged wedge (still bilaterally symmetric for readable
        // gameplay) instead of a plain hexagon, so it reads as a layered alien warship rather
        // than a geometric placeholder. Top-lit gradient + dark outline so a hull this large
        // reads as modeled plate, not a flat decal.
        path.reset()
        path.moveTo(0f, h * 0.60f)                    // nose tip
        path.lineTo(w * 0.10f, h * 0.42f)
        path.lineTo(w * 0.20f, h * 0.46f)
        path.lineTo(w * 0.34f, h * 0.20f)
        path.lineTo(w * 0.50f, h * 0.14f)
        path.lineTo(w * 0.46f, -h * 0.02f)
        path.lineTo(w * 0.62f, -h * 0.10f)             // wingtip — widest point
        path.lineTo(w * 0.56f, -h * 0.28f)
        path.lineTo(w * 0.34f, -h * 0.40f)
        path.lineTo(w * 0.30f, -h * 0.56f)
        path.lineTo(w * 0.10f, -h * 0.50f)
        path.lineTo(0f, -h * 0.58f)                    // rear spine notch
        path.lineTo(-w * 0.10f, -h * 0.50f)
        path.lineTo(-w * 0.30f, -h * 0.56f)
        path.lineTo(-w * 0.34f, -h * 0.40f)
        path.lineTo(-w * 0.56f, -h * 0.28f)
        path.lineTo(-w * 0.62f, -h * 0.10f)             // wingtip — widest point
        path.lineTo(-w * 0.46f, -h * 0.02f)
        path.lineTo(-w * 0.50f, h * 0.14f)
        path.lineTo(-w * 0.34f, h * 0.20f)
        path.lineTo(-w * 0.20f, h * 0.46f)
        path.lineTo(-w * 0.10f, h * 0.42f)
        path.close()
        paint.shader = LinearGradient(0f, -h * 0.58f, 0f, h * 0.60f, lighten(hullBase, 0.25f), darken(hullBase, 0.4f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        outlinePaint.color = darken(hullBase, 0.6f)
        canvas.drawPath(path, outlinePaint)

        // Raised centerline spine plate — reads as a bridge/keel structure running the
        // length of the hull, like a Star-Destroyer-style command spine.
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

        // Greebled panel lines scattered across both wings — small dark rects read as access
        // hatches/vents at this scale, the detail movie model-ships lean on heavily.
        renderGreebles(canvas, w, h, hullBase)

        // Side pods (weapon housings).
        val pod = Color.rgb(90, 60, 70)
        paint.shader = LinearGradient(-w * 0.52f, 0f, w * 0.52f, 0f, lighten(pod, 0.2f), darken(pod, 0.3f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-w * 0.52f, -h * 0.1f, -w * 0.3f, h * 0.28f, 8f, 8f, paint)
        canvas.drawRoundRect(w * 0.3f, -h * 0.1f, w * 0.52f, h * 0.28f, 8f, 8f, paint)
        paint.shader = null

        // Anti-collision nav lights along the wingtips/shoulders — the blinking red/green/
        // white strobe convention every movie/TV spacecraft uses, and cheap to fake with a
        // couple of sine-timed dots.
        renderNavLights(canvas, w, h)

        // Core weak point — pulses, brighter in later phases. Soft outer bloom + hot white
        // center reads as an actual glowing reactor (a Death-Star-exhaust-port beat), not a
        // flat dot.
        val pulse = 0.6f + 0.4f * sin(age * (3f + phase)).toFloat()
        val coreColor = when (phase) {
            1 -> spec.coreColor
            2 -> Color.rgb(255, 200, 90)
            else -> Color.rgb(255, 90, 90)
        }
        val coreY = -h * 0.08f
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
