package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        paint.color = spec.bodyColor
        c.drawOval(-w / 2f, -h * 0.32f, w / 2f, h * 0.32f, paint)
        // Fins.
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(-w / 2f, 0f); path.lineTo(-w * 0.72f, -h * 0.18f); path.lineTo(-w * 0.3f, -h * 0.2f); path.close()
        c.drawPath(path, paint)
        path.reset()
        path.moveTo(w / 2f, 0f); path.lineTo(w * 0.72f, -h * 0.18f); path.lineTo(w * 0.3f, -h * 0.2f); path.close()
        c.drawPath(path, paint)
        // Core eye.
        paint.color = Color.rgb(255, 90, 90)
        c.drawCircle(0f, 0f, w * 0.14f, paint)
    }

    private fun renderDart(c: Canvas, w: Float, h: Float) {
        paint.color = spec.bodyColor
        path.reset()
        path.moveTo(0f, h / 2f)               // nose points down
        path.lineTo(w / 2f, -h * 0.3f)
        path.lineTo(w * 0.18f, -h / 2f)
        path.lineTo(-w * 0.18f, -h / 2f)
        path.lineTo(-w / 2f, -h * 0.3f)
        path.close()
        c.drawPath(path, paint)
        paint.color = spec.accentColor
        path.reset()
        path.moveTo(0f, h / 2f); path.lineTo(w * 0.14f, -h * 0.1f); path.lineTo(-w * 0.14f, -h * 0.1f); path.close()
        c.drawPath(path, paint)
    }

    private fun renderGunship(c: Canvas, w: Float, h: Float) {
        paint.color = spec.bodyColor
        c.drawRoundRect(-w / 2f, -h * 0.3f, w / 2f, h * 0.36f, 12f, 12f, paint)
        // Cannons.
        paint.color = spec.accentColor
        c.drawRect(-w * 0.34f, h * 0.2f, -w * 0.2f, h * 0.5f, paint)
        c.drawRect(w * 0.2f, h * 0.2f, w * 0.34f, h * 0.5f, paint)
        // Bridge.
        paint.color = Color.rgb(255, 210, 130)
        c.drawCircle(0f, -h * 0.05f, w * 0.12f, paint)
    }

    private fun renderTurret(c: Canvas, w: Float, @Suppress("UNUSED_PARAMETER") h: Float) {
        // Octagon base.
        paint.color = spec.bodyColor
        path.reset()
        val r = w / 2f
        for (i in 0 until 8) {
            val a = (i / 8f) * 2f * Math.PI + Math.PI / 8
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, paint)
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
