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
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 170 }

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

        // Hull — colour shifts toward red with rage, top-lit gradient + dark outline instead
        // of a flat fill so a hull this large actually reads as modeled plate, not a decal.
        val r = (70 + 120 * damage).toInt().coerceIn(0, 255)
        val hullBase = Color.rgb(r, 70, 90)
        path.reset()
        path.moveTo(0f, h * 0.55f)                 // nose down
        path.lineTo(w * 0.5f, 0f)
        path.lineTo(w * 0.34f, -h * 0.5f)
        path.lineTo(-w * 0.34f, -h * 0.5f)
        path.lineTo(-w * 0.5f, 0f)
        path.close()
        paint.shader = LinearGradient(0f, -h * 0.5f, 0f, h * 0.55f, lighten(hullBase, 0.25f), darken(hullBase, 0.4f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        outlinePaint.color = darken(hullBase, 0.6f)
        canvas.drawPath(path, outlinePaint)

        // Armor plating.
        val armor = Color.rgb(50, 55, 75)
        paint.shader = LinearGradient(0f, -h * 0.42f, 0f, h * 0.2f, lighten(armor, 0.3f), darken(armor, 0.3f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-w * 0.28f, -h * 0.42f, w * 0.28f, h * 0.2f, 10f, 10f, paint)
        paint.shader = null

        // Side pods (weapon housings).
        val pod = Color.rgb(90, 60, 70)
        paint.shader = LinearGradient(-w * 0.52f, 0f, w * 0.52f, 0f, lighten(pod, 0.2f), darken(pod, 0.3f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-w * 0.52f, -h * 0.1f, -w * 0.3f, h * 0.28f, 8f, 8f, paint)
        canvas.drawRoundRect(w * 0.3f, -h * 0.1f, w * 0.52f, h * 0.28f, 8f, 8f, paint)
        paint.shader = null

        // Core weak point — pulses, brighter in later phases. Soft outer bloom + hot white
        // center reads as an actual glowing reactor, not a flat dot.
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
}
