package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skylegends.game.enemies.EnemySpec
import com.skylegends.game.enemies.EnemyShape
import com.skylegends.game.enemies.FirePattern
import com.skylegends.game.enemies.MovementPattern
import com.skylegends.game.utils.Constants
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A live enemy. All behaviour is driven by its [EnemySpec]; this class is the interpreter
 * for the movement + fire patterns. GameView calls [tick] each frame with the player
 * position and a bullet spawner, then reads [active]/[hp] to resolve death.
 */
class Enemy(val spec: EnemySpec) : Entity() {

    var hp = spec.maxHp
    private var age = 0f
    private var fireTimer = spec.fireRate * 0.6f   // small initial delay so they don't all fire on frame 1
    private var hitFlash = 0f
    private var spawnX = 0f
    private var settled = false
    private var kamikazeDir = com.skylegends.game.utils.Vector2(0f, 1f)
    private var swoopTargetX = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 150 }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    /** Top-lit gradient fill + dark outline instead of a flat color — matches the player
     * ship's shading so enemies read as modeled hardware, not flat cutouts. */
    private fun shadedFill(c: Canvas, p: Path, base: Int, topY: Float, bottomY: Float) {
        paint.shader = LinearGradient(0f, topY, 0f, bottomY, lighten(base, 0.28f), darken(base, 0.35f), Shader.TileMode.CLAMP)
        c.drawPath(p, paint)
        paint.shader = null
        outlinePaint.color = darken(base, 0.5f)
        c.drawPath(p, outlinePaint)
    }

    fun spawn(x: Float, y: Float) {
        pos.set(x, y)
        spawnX = x
        collisionRadius = minOf(spec.width, spec.height) * 0.42f
        hp = spec.maxHp
        age = 0f
        settled = false
        active = true
        when (spec.movement) {
            MovementPattern.STRAIGHT, MovementPattern.SINE, MovementPattern.HOVER ->
                vel.set(0f, spec.speed)
            MovementPattern.SWOOP -> {
                // Enter from whichever side we're nearer, arc toward the opposite third.
                val fromLeft = x < Constants.GAME_WIDTH / 2f
                swoopTargetX = if (fromLeft) Constants.GAME_WIDTH * 0.72f else Constants.GAME_WIDTH * 0.28f
                vel.set(0f, spec.speed)
            }
            MovementPattern.KAMIKAZE -> vel.set(0f, spec.speed * 0.5f)
        }
    }

    fun hit(damage: Float) {
        hp -= damage
        hitFlash = 1f
    }

    fun tick(dt: Float, playerX: Float, playerY: Float, spawner: BulletSpawner) {
        age += dt
        if (hitFlash > 0f) hitFlash = (hitFlash - dt * 6f).coerceAtLeast(0f)

        when (spec.movement) {
            MovementPattern.STRAIGHT -> {}
            MovementPattern.SINE -> {
                pos.x = spawnX + sin(age * spec.weaveFrequency * 2f * Math.PI).toFloat() * spec.weaveAmplitude
            }
            MovementPattern.SWOOP -> {
                pos.x += (swoopTargetX - pos.x) * (1f - Math.exp(-1.6 * dt).toFloat())
            }
            MovementPattern.HOVER -> {
                if (!settled && pos.y >= spec.hoverY) {
                    settled = true; vel.set(0f, 0f)
                }
                if (settled) {
                    // Gentle strafe while holding station.
                    pos.x = (Constants.GAME_WIDTH / 2f) +
                        sin(age * 1.1f) * (Constants.GAME_WIDTH * 0.30f)
                }
            }
            MovementPattern.KAMIKAZE -> {
                if (age < 0.5f) {
                    // brief telegraph descent, then lock onto player
                } else {
                    kamikazeDir.set(playerX - pos.x, playerY - pos.y).normalize()
                    vel.set(kamikazeDir.x * spec.speed, kamikazeDir.y * spec.speed)
                }
            }
        }

        pos.x += vel.x * dt
        pos.y += vel.y * dt

        // Firing.
        if (spec.fire != FirePattern.NONE) {
            fireTimer -= dt
            if (fireTimer <= 0f && pos.y < Constants.GAME_HEIGHT * 0.85f) {
                fireTimer = spec.fireRate
                fire(playerX, playerY, spawner)
            }
        }

        // Cull once fully past the bottom.
        if (pos.y - spec.height > Constants.GAME_HEIGHT + 40f) active = false
    }

    private fun fire(playerX: Float, playerY: Float, spawner: BulletSpawner) {
        val core = Color.rgb(255, 120, 120)
        val glow = Color.rgb(255, 60, 60)
        val sp = spec.bulletSpeed
        val ox = pos.x; val oy = pos.y + spec.height * 0.3f
        when (spec.fire) {
            FirePattern.AIMED -> {
                val a = atan2(playerY - oy, playerX - ox)
                spawner.spawn(ox, oy, cos(a) * sp, sin(a) * sp, 6f, spec.bulletDamage, core, glow)
            }
            FirePattern.SPREAD3 -> {
                val base = atan2(playerY - oy, playerX - ox)
                for (d in -1..1) {
                    val a = base + d * 0.22f
                    spawner.spawn(ox, oy, cos(a) * sp, sin(a) * sp, 6f, spec.bulletDamage, core, glow)
                }
            }
            FirePattern.RADIAL8 -> {
                for (i in 0 until 8) {
                    val a = (i / 8f) * 2f * Math.PI.toFloat() + age * 0.6f
                    spawner.spawn(ox, oy, cos(a) * sp, sin(a) * sp, 6f, spec.bulletDamage, core, glow)
                }
            }
            FirePattern.NONE -> {}
        }
    }

    override fun update(dt: Float) { /* driven by tick() */ }

    override fun render(canvas: Canvas) {
        canvas.save()
        canvas.translate(pos.x, pos.y)
        val w = spec.width; val h = spec.height
        when (spec.shape) {
            EnemyShape.DRONE -> renderDrone(canvas, w, h)
            EnemyShape.DART -> renderDart(canvas, w, h)
            EnemyShape.GUNSHIP -> renderGunship(canvas, w, h)
            EnemyShape.TURRET -> renderTurret(canvas, w, h)
        }
        // Hit flash overlay.
        if (hitFlash > 0f) {
            paint.color = Color.WHITE
            paint.alpha = (hitFlash * 200f).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, maxOf(w, h) * 0.5f, paint)
            paint.alpha = 255
        }
        canvas.restore()

        // Health bar for beefy enemies.
        if (spec.maxHp > 50f && hp < spec.maxHp) {
            val bw = spec.width
            val frac = (hp / spec.maxHp).coerceIn(0f, 1f)
            paint.color = Color.rgb(30, 30, 40)
            canvas.drawRect(pos.x - bw / 2f, pos.y - spec.height / 2f - 12f, pos.x + bw / 2f, pos.y - spec.height / 2f - 6f, paint)
            paint.color = Color.rgb(255, 80, 80)
            canvas.drawRect(pos.x - bw / 2f, pos.y - spec.height / 2f - 12f, pos.x - bw / 2f + bw * frac, pos.y - spec.height / 2f - 6f, paint)
        }
    }

    // These enemies point DOWN (toward the player), so nose is +y.
    private fun renderDrone(c: Canvas, w: Float, h: Float) {
        path.reset(); path.addOval(-w / 2f, -h * 0.32f, w / 2f, h * 0.32f, Path.Direction.CW)
        shadedFill(c, path, spec.bodyColor, -h * 0.32f, h * 0.32f)
        // Fins.
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(-w / 2f, 0f); path.lineTo(-w * 0.72f, -h * 0.18f); path.lineTo(-w * 0.3f, -h * 0.2f); path.close()
        c.drawPath(path, paint)
        path.reset()
        path.moveTo(w / 2f, 0f); path.lineTo(w * 0.72f, -h * 0.18f); path.lineTo(w * 0.3f, -h * 0.2f); path.close()
        c.drawPath(path, paint)
        // Saucer rim ring — a thin stroked skirt, the alien-scout silhouette cue.
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = 1.6f
        detailPaint.color = spec.accentColor
        detailPaint.alpha = 140
        c.drawOval(-w * 0.5f, -h * 0.32f, w * 0.5f, h * 0.32f, detailPaint)
        detailPaint.style = Paint.Style.FILL
        // Twin rivet lights, softly pulsing.
        detailPaint.color = Color.WHITE
        detailPaint.alpha = (110 + 100f * ((sin(age * 5f) + 1f) / 2f)).toInt().coerceIn(0, 255)
        c.drawCircle(-w * 0.30f, 0f, 2.2f, detailPaint)
        c.drawCircle(w * 0.30f, 0f, 2.2f, detailPaint)
        detailPaint.alpha = 255
        // Core eye — glowing radial "iris" instead of a flat dot.
        paint.shader = RadialGradient(0f, 0f, w * 0.14f, Color.rgb(255, 200, 190), Color.rgb(200, 40, 40), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, w * 0.14f, paint)
        paint.shader = null
    }

    private fun renderDart(c: Canvas, w: Float, h: Float) {
        path.reset()
        path.moveTo(0f, h / 2f)               // nose points down
        path.lineTo(w / 2f, -h * 0.3f)
        path.lineTo(w * 0.18f, -h / 2f)
        path.lineTo(-w * 0.18f, -h / 2f)
        path.lineTo(-w / 2f, -h * 0.3f)
        path.close()
        shadedFill(c, path, spec.bodyColor, -h / 2f, h / 2f)
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(0f, h / 2f); path.lineTo(w * 0.14f, -h * 0.1f); path.lineTo(-w * 0.14f, -h * 0.1f); path.close()
        c.drawPath(path, paint)
        // Spine panel line for surface detail.
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = 1.2f
        detailPaint.color = darken(spec.bodyColor, 0.4f)
        detailPaint.alpha = 120
        c.drawLine(0f, -h * 0.42f, 0f, h * 0.32f, detailPaint)
        detailPaint.style = Paint.Style.FILL
        detailPaint.alpha = 255
        // Tail thruster flicker — the rear (tail) is -y since the nose points down at +y.
        val flicker = 0.6f + 0.4f * sin(age * 14f)
        paint.shader = RadialGradient(0f, -h * 0.46f, 8f * flicker, Color.argb(220, 255, 210, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(0f, -h * 0.46f, 8f * flicker, paint)
        paint.shader = null
    }

    private fun renderGunship(c: Canvas, w: Float, h: Float) {
        path.reset(); path.addRoundRect(-w / 2f, -h * 0.3f, w / 2f, h * 0.36f, 12f, 12f, Path.Direction.CW)
        shadedFill(c, path, spec.bodyColor, -h * 0.3f, h * 0.36f)
        // Cannons.
        paint.color = spec.accentColor
        c.drawRect(-w * 0.34f, h * 0.2f, -w * 0.2f, h * 0.5f, paint)
        c.drawRect(w * 0.2f, h * 0.2f, w * 0.34f, h * 0.5f, paint)
        // Greebled panel lines flanking the bridge.
        detailPaint.color = darken(spec.bodyColor, 0.4f)
        detailPaint.alpha = 120
        c.drawRect(-w * 0.12f, -h * 0.2f, -w * 0.02f, -h * 0.1f, detailPaint)
        c.drawRect(w * 0.02f, -h * 0.2f, w * 0.12f, -h * 0.1f, detailPaint)
        detailPaint.alpha = 255
        // Bridge — glassy highlight.
        paint.shader = RadialGradient(-w * 0.03f, -h * 0.08f, w * 0.14f, Color.WHITE, Color.rgb(255, 190, 110), Shader.TileMode.CLAMP)
        c.drawCircle(0f, -h * 0.05f, w * 0.12f, paint)
        paint.shader = null
        // Blinking hazard light beneath the bridge.
        val on = ((age * 3f).toInt() % 2) == 0
        detailPaint.color = Color.rgb(255, 60, 60)
        detailPaint.alpha = if (on) 255 else 60
        c.drawCircle(0f, h * 0.28f, 2.5f, detailPaint)
        detailPaint.alpha = 255
    }

    private fun renderTurret(c: Canvas, w: Float, @Suppress("UNUSED_PARAMETER") h: Float) {
        // Octagon base.
        path.reset()
        val r = w / 2f
        for (i in 0 until 8) {
            val a = (i / 8f) * 2f * Math.PI + Math.PI / 8
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        shadedFill(c, path, spec.bodyColor, -r, r)
        // Pulsing warning ring around the platform rim.
        val pulse = (sin(age * 4f) + 1f) / 2f
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = 2f
        detailPaint.color = Color.rgb(255, 80, 80)
        detailPaint.alpha = (70 + 110 * pulse).toInt().coerceIn(0, 255)
        c.drawCircle(0f, 0f, w * 0.46f, detailPaint)
        detailPaint.style = Paint.Style.FILL
        detailPaint.alpha = 255
        // Rotating barrel ring.
        paint.color = spec.accentColor
        c.drawCircle(0f, 0f, w * 0.24f, paint)
        c.save()
        c.rotate((age * 90f) % 360f)
        paint.color = Color.rgb(30, 30, 40)
        c.drawRect(-w * 0.06f, -w * 0.42f, w * 0.06f, w * 0.42f, paint)
        c.restore()
    }
}
