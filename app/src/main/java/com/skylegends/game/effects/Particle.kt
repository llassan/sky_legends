package com.skylegends.game.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

enum class ParticleKind { SPARK, SMOKE, DEBRIS, SHOCKWAVE, GLOW }

/**
 * One pooled visual particle. A single class covers sparks, smoke, debris, expanding
 * shockwaves and soft glows via [kind]; [ParticleSystem] configures and recycles them so
 * thousands can live at once with zero per-frame allocation.
 */
class Particle {
    val kind get() = _kind
    private var _kind = ParticleKind.SPARK

    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var life = 0f; var maxLife = 1f
    var size = 0f
    var color = Color.WHITE
    var rot = 0f; var rotVel = 0f
    var drag = 1f; var grav = 0f
    var active = false

    fun configure(
        kind: ParticleKind, x: Float, y: Float, vx: Float, vy: Float,
        life: Float, size: Float, color: Int, drag: Float = 1f, grav: Float = 0f,
        rot: Float = 0f, rotVel: Float = 0f
    ) {
        this._kind = kind
        this.x = x; this.y = y; this.vx = vx; this.vy = vy
        this.life = life; this.maxLife = life; this.size = size; this.color = color
        this.drag = drag; this.grav = grav; this.rot = rot; this.rotVel = rotVel
        active = true
    }

    fun update(dt: Float) {
        life -= dt
        if (life <= 0f) { active = false; return }
        vx *= drag; vy *= drag
        vy += grav * dt
        x += vx * dt; y += vy * dt
        rot += rotVel * dt
    }

    fun render(canvas: Canvas, paint: Paint) {
        val t = (life / maxLife).coerceIn(0f, 1f)   // 1 -> new, 0 -> dead
        when (_kind) {
            ParticleKind.SPARK -> {
                paint.color = color
                paint.alpha = (255 * t).toInt()
                paint.strokeWidth = size * t
                canvas.drawLine(x, y, x - vx * 0.02f, y - vy * 0.02f, paint)
            }
            ParticleKind.SMOKE -> {
                paint.color = color
                paint.alpha = (110 * t).toInt()
                canvas.drawCircle(x, y, size * (1.6f - t), paint)
            }
            ParticleKind.DEBRIS -> {
                paint.color = color
                paint.alpha = (255 * t).toInt()
                canvas.save()
                canvas.rotate(Math.toDegrees(rot.toDouble()).toFloat(), x, y)
                canvas.drawRect(x - size, y - size * 0.5f, x + size, y + size * 0.5f, paint)
                canvas.restore()
            }
            ParticleKind.SHOCKWAVE -> {
                // size = max radius; ring expands as life drains.
                val radius = size * (1f - t)
                paint.color = color
                paint.alpha = (200 * t).toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f * t + 1f
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.FILL
            }
            ParticleKind.GLOW -> {
                paint.color = color
                paint.alpha = (160 * t).toInt()
                canvas.drawCircle(x, y, size * (0.6f + 0.4f * t), paint)
            }
        }
        paint.alpha = 255
    }
}
