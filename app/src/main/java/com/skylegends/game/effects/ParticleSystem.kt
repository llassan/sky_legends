package com.skylegends.game.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.skylegends.game.utils.Constants
import com.skylegends.game.utils.Pool
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns the particle pool and the list of live particles, and exposes high-level emitters
 * (muzzle flash, hit sparks, multi-layer explosions). Layered explosions = shockwave ring
 * + fireball glow + sparks + smoke + debris, the "Hollywood" stack the brief asks for.
 */
class ParticleSystem {
    private val pool = Pool(Constants.MAX_PARTICLES) { Particle() }
    private val live = ArrayList<Particle>(Constants.MAX_PARTICLES)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val count get() = live.size

    private fun emit(configure: (Particle) -> Unit) {
        val p = pool.obtain() ?: return
        configure(p)
        live.add(p)
    }

    fun muzzleFlash(x: Float, y: Float, color: Int) {
        emit { it.configure(ParticleKind.GLOW, x, y, 0f, -40f, 0.10f, 16f, color) }
        repeat(3) {
            val a = -Math.PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 1.0f
            val s = 260f + Random.nextFloat() * 220f
            emit { it.configure(ParticleKind.SPARK, x, y, cos(a) * s, sin(a) * s, 0.12f, 7f, color, drag = 0.9f) }
        }
    }

    /** A single soft smoke wisp, left behind by missiles as they fly. */
    fun smokeTrail(x: Float, y: Float) {
        emit {
            it.configure(
                ParticleKind.SMOKE, x, y, (Random.nextFloat() - 0.5f) * 20f, 30f + Random.nextFloat() * 20f,
                0.4f + Random.nextFloat() * 0.2f, 7f + Random.nextFloat() * 4f, Color.rgb(120, 120, 120), drag = 0.95f
            )
        }
    }

    fun hitSparks(x: Float, y: Float, color: Int) {
        repeat(6) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = 120f + Random.nextFloat() * 220f
            emit { it.configure(ParticleKind.SPARK, x, y, cos(a) * s, sin(a) * s, 0.22f, 6f, color, drag = 0.90f) }
        }
    }

    /** Full-fat explosion. [scale] ~1 for a fighter, ~3+ for a boss. */
    fun explosion(x: Float, y: Float, scale: Float = 1f, tint: Int = Color.rgb(255, 160, 60)) {
        // Shockwave ring.
        emit { it.configure(ParticleKind.SHOCKWAVE, x, y, 0f, 0f, 0.45f, 60f * scale, Color.WHITE) }
        // Fireball core glows.
        repeat((3 * scale).toInt().coerceAtLeast(2)) {
            emit { it.configure(ParticleKind.GLOW, x, y, 0f, 0f, 0.35f + Random.nextFloat() * 0.2f, (30f + Random.nextFloat() * 26f) * scale, tint) }
        }
        // Sparks.
        repeat((14 * scale).toInt()) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = (160f + Random.nextFloat() * 360f) * scale.coerceAtMost(2f)
            emit { it.configure(ParticleKind.SPARK, x, y, cos(a) * s, sin(a) * s, 0.3f + Random.nextFloat() * 0.3f, 8f, Color.rgb(255, 230, 150), drag = 0.90f) }
        }
        // Smoke.
        repeat((6 * scale).toInt()) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = 40f + Random.nextFloat() * 90f
            emit { it.configure(ParticleKind.SMOKE, x, y, cos(a) * s, sin(a) * s, 0.7f + Random.nextFloat() * 0.5f, (18f + Random.nextFloat() * 18f) * scale, Color.rgb(60, 60, 66), drag = 0.94f) }
        }
        // Debris.
        repeat((5 * scale).toInt()) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = (140f + Random.nextFloat() * 260f) * scale.coerceAtMost(2f)
            emit {
                it.configure(
                    ParticleKind.DEBRIS, x, y, cos(a) * s, sin(a) * s,
                    0.6f + Random.nextFloat() * 0.5f, 4f + Random.nextFloat() * 4f,
                    Color.rgb(90, 90, 96), drag = 0.96f, grav = 500f,
                    rot = Random.nextFloat() * 6f, rotVel = (Random.nextFloat() - 0.5f) * 20f
                )
            }
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < live.size) {
            val p = live[i]
            p.update(dt)
            if (!p.active) {
                pool.release(p)
                live[i] = live[live.size - 1]
                live.removeAt(live.size - 1)
            } else i++
        }
    }

    fun render(canvas: Canvas) {
        for (i in live.indices) live[i].render(canvas, paint)
    }

    fun clear() {
        for (p in live) pool.release(p)
        live.clear()
    }
}
