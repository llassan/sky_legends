package com.skylegends.game.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.skylegends.game.utils.Pool

/**
 * One pooled floating label — a "+120" that pops at a kill point, drifts up and fades.
 * Zero per-frame allocation (configured and recycled by [FloatingTextSystem]).
 */
class FloatingText {
    var x = 0f; var y = 0f
    var vy = 0f
    var life = 0f; var maxLife = 0.9f
    var text = ""
    var color = Color.WHITE
    var size = 22f
    var active = false

    fun configure(x: Float, y: Float, text: String, color: Int, size: Float) {
        this.x = x; this.y = y
        this.vy = -80f
        this.life = 0.9f; this.maxLife = 0.9f
        this.text = text; this.color = color; this.size = size
        active = true
    }

    fun update(dt: Float) {
        life -= dt
        if (life <= 0f) { active = false; return }
        y += vy * dt
        vy *= 0.90f        // ease the rise so it decelerates as it fades
    }

    fun render(canvas: Canvas, paint: Paint) {
        val t = (life / maxLife).coerceIn(0f, 1f)   // 1 -> new, 0 -> gone
        // Quick pop-in: 30% bigger at birth, settling to its size.
        paint.textSize = size * (1f + 0.3f * (1f - t))
        val alpha = (255 * t).toInt().coerceIn(0, 255)
        // Drop shadow for legibility over bright explosions.
        paint.color = Color.argb((alpha * 0.55f).toInt(), 0, 0, 0)
        canvas.drawText(text, x + 1.6f, y + 1.6f, paint)
        paint.color = color
        paint.alpha = alpha
        canvas.drawText(text, x, y, paint)
        paint.alpha = 255
    }
}

/**
 * Owns the floating-label pool. GameView spawns a "+score" at each kill; these read as
 * reward feedback the silent score counter can't convey.
 */
class FloatingTextSystem {
    private val pool = Pool(64) { FloatingText() }
    private val live = ArrayList<FloatingText>(64)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    fun spawn(x: Float, y: Float, text: String, color: Int, size: Float = 22f) {
        val f = pool.obtain() ?: return
        f.configure(x, y, text, color, size)
        live.add(f)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < live.size) {
            val f = live[i]
            f.update(dt)
            if (!f.active) {
                pool.release(f)
                live[i] = live[live.size - 1]
                live.removeAt(live.size - 1)
            } else i++
        }
    }

    fun render(canvas: Canvas) {
        com.skylegends.game.fx.Sprites.fontBold?.let { if (paint.typeface !== it) paint.typeface = it }
        for (i in live.indices) live[i].render(canvas, paint)
    }

    fun clear() {
        for (f in live) pool.release(f)
        live.clear()
    }
}
