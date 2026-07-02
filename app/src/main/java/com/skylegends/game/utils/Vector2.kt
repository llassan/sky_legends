package com.skylegends.game.utils

import kotlin.math.sqrt

/** Lightweight mutable 2D vector. Mutating methods return `this` for chaining. */
data class Vector2(var x: Float = 0f, var y: Float = 0f) {

    fun set(x: Float, y: Float): Vector2 { this.x = x; this.y = y; return this }
    fun set(o: Vector2): Vector2 { x = o.x; y = o.y; return this }

    fun add(o: Vector2): Vector2 { x += o.x; y += o.y; return this }
    fun add(dx: Float, dy: Float): Vector2 { x += dx; y += dy; return this }
    fun scale(s: Float): Vector2 { x *= s; y *= s; return this }

    fun length(): Float = sqrt(x * x + y * y)

    fun normalize(): Vector2 {
        val len = length()
        if (len > 0f) { x /= len; y /= len }
        return this
    }

    fun copy(): Vector2 = Vector2(x, y)

    companion object {
        fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = ax - bx; val dy = ay - by
            return sqrt(dx * dx + dy * dy)
        }
    }
}
