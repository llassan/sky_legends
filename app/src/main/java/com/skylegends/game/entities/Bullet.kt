package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.skylegends.game.utils.Constants
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pooled projectile — the single most common entity, so it is allocation-free after
 * warm-up. One [Bullet] type serves both the player and enemies; [fromPlayer] decides
 * who it can hit. Rendered as a glowing, motion-stretched capsule.
 */
class Bullet : Entity() {
    var damage = 0f
    var fromPlayer = false
    var coreColor = 0
    var glowColor = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun configure(
        x: Float, y: Float, vx: Float, vy: Float,
        radius: Float, damage: Float, fromPlayer: Boolean, core: Int, glow: Int
    ) {
        pos.set(x, y); vel.set(vx, vy)
        collisionRadius = radius
        this.damage = damage
        this.fromPlayer = fromPlayer
        this.coreColor = core
        this.glowColor = glow
        active = true
    }

    override fun update(dt: Float) {
        super.update(dt)
        // Cull once well off-screen (any edge).
        val m = 60f
        if (pos.y < -m || pos.y > Constants.GAME_HEIGHT + m ||
            pos.x < -m || pos.x > Constants.GAME_WIDTH + m
        ) active = false
    }

    override fun render(canvas: Canvas) {
        val r = collisionRadius
        val speed = hypot(vel.x, vel.y)
        val angle = atan2(vel.y, vel.x)
        canvas.save()
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat() + 90f, pos.x, pos.y)

        // Outer glow.
        paint.color = glowColor
        paint.alpha = 90
        canvas.drawCircle(pos.x, pos.y, r * 2.1f, paint)

        // Motion-stretched core: an elongated round-rect along travel.
        val stretch = if (speed > 1f) (r * 1.6f) else r
        paint.alpha = 255
        paint.color = glowColor
        canvas.drawRoundRect(pos.x - r, pos.y - r - stretch, pos.x + r, pos.y + r, r, r, paint)
        paint.color = coreColor
        canvas.drawRoundRect(
            pos.x - r * 0.55f, pos.y - r - stretch, pos.x + r * 0.55f, pos.y + r * 0.4f,
            r * 0.55f, r * 0.55f, paint
        )
        canvas.restore()
    }
}
