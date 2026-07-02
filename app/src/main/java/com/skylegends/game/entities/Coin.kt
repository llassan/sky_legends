package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.skylegends.game.utils.Constants
import kotlin.math.abs
import kotlin.math.sin

/**
 * Dropped currency. Drifts down, then homes toward the player once within magnet range
 * (the "magnet" upgrade widens this). Rendered as a spinning gold coin (x-scale wobble).
 */
class Coin : Entity() {
    var value = 1
    private var spin = 0f
    private var magnetRange = 120f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun configure(x: Float, y: Float, value: Int, magnetRange: Float) {
        pos.set(x, y)
        vel.set((Math.random().toFloat() - 0.5f) * 60f, 80f + Math.random().toFloat() * 40f)
        collisionRadius = 14f
        this.value = value
        this.magnetRange = magnetRange
        spin = Math.random().toFloat() * 6f
        active = true
    }

    fun tickToward(dt: Float, playerX: Float, playerY: Float) {
        spin += dt * 6f
        val d = Vectors.dist(pos.x, pos.y, playerX, playerY)
        if (d < magnetRange) {
            val pull = (1f - d / magnetRange) * 900f
            val dx = playerX - pos.x; val dy = playerY - pos.y
            val inv = if (d > 0.001f) 1f / d else 0f
            vel.x += dx * inv * pull * dt
            vel.y += dy * inv * pull * dt
        } else {
            vel.y += 20f * dt
        }
        vel.x *= 0.98f
        pos.x += vel.x * dt
        pos.y += vel.y * dt
        if (pos.y > Constants.GAME_HEIGHT + 30f) active = false
    }

    override fun update(dt: Float) { /* driven by tickToward() */ }

    override fun render(canvas: Canvas) {
        val wobble = abs(sin(spin.toDouble())).toFloat()
        val rx = 14f * (0.3f + 0.7f * wobble)
        paint.color = Color.rgb(255, 200, 40)
        canvas.drawOval(pos.x - rx, pos.y - 14f, pos.x + rx, pos.y + 14f, paint)
        paint.color = Color.rgb(255, 240, 150)
        canvas.drawOval(pos.x - rx * 0.5f, pos.y - 8f, pos.x + rx * 0.5f, pos.y + 8f, paint)
    }
}

/** Tiny static-distance helper kept off the hot Vector2 API. */
object Vectors {
    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
