package com.skylegends.game.entities

import android.graphics.Canvas
import com.skylegends.game.utils.Vector2

/**
 * Base for everything in the world. Positions are **center-based** (natural for a shmup)
 * and collision is circle-vs-circle against [collisionRadius] — cheap, forgiving, and the
 * norm for the genre (tiny player hitbox, generous bullet grazing).
 *
 * All rendering is in virtual coordinates; [com.skylegends.game.GameView] applies the
 * single global scale + shake transform before drawing the world.
 */
abstract class Entity {
    val pos = Vector2()
    val vel = Vector2()
    var collisionRadius = 0f
    var active = true

    open fun update(dt: Float) {
        pos.x += vel.x * dt
        pos.y += vel.y * dt
    }

    abstract fun render(canvas: Canvas)

    fun overlaps(other: Entity): Boolean {
        if (!active || !other.active) return false
        val r = collisionRadius + other.collisionRadius
        val dx = pos.x - other.pos.x
        val dy = pos.y - other.pos.y
        return dx * dx + dy * dy <= r * r
    }
}
