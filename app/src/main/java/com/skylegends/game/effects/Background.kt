package com.skylegends.game.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.skylegends.game.utils.Constants
import kotlin.random.Random

/**
 * Multi-layer parallax backdrop: a vertical sky gradient, three star fields scrolling at
 * different speeds (depth cue), and slow drifting nebula blobs. Everything wraps, so it
 * scrolls forever with a fixed memory footprint.
 */
class Background {

    private class Star(var x: Float, var y: Float, val size: Float, val speed: Float, val bright: Int)
    private class Nebula(var x: Float, var y: Float, val r: Float, val color: Int, val speed: Float)

    private val rnd = Random(20260701)
    private val stars = ArrayList<Star>()
    private val nebulae = ArrayList<Nebula>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val gradient = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, Constants.GAME_HEIGHT,
            intArrayOf(
                Color.rgb(10, 14, 39),
                Color.rgb(26, 20, 58),
                Color.rgb(12, 22, 52)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    init {
        // Three depth layers.
        repeat(40) { stars.add(makeStar(0.25f, 1f, 90)) }   // far, slow, dim
        repeat(28) { stars.add(makeStar(0.6f, 1.8f, 150)) } // mid
        repeat(16) { stars.add(makeStar(1.1f, 2.8f, 230)) } // near, fast, bright
        repeat(4) {
            nebulae.add(
                Nebula(
                    rnd.nextFloat() * Constants.GAME_WIDTH,
                    rnd.nextFloat() * Constants.GAME_HEIGHT,
                    120f + rnd.nextFloat() * 160f,
                    listOf(
                        Color.argb(40, 90, 60, 160),
                        Color.argb(36, 40, 90, 160),
                        Color.argb(34, 150, 50, 110)
                    ).random(rnd),
                    12f + rnd.nextFloat() * 18f
                )
            )
        }
    }

    private fun makeStar(speedScale: Float, size: Float, bright: Int) = Star(
        rnd.nextFloat() * Constants.GAME_WIDTH,
        rnd.nextFloat() * Constants.GAME_HEIGHT,
        size,
        Constants.SCROLL_SPEED * speedScale,
        bright
    )

    fun update(dt: Float) {
        for (n in nebulae) {
            n.y += n.speed * dt
            if (n.y - n.r > Constants.GAME_HEIGHT) { n.y = -n.r; n.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
        for (s in stars) {
            s.y += s.speed * dt
            if (s.y > Constants.GAME_HEIGHT) { s.y = 0f; s.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
    }

    fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, gradient)
        for (n in nebulae) {
            paint.color = n.color
            canvas.drawCircle(n.x, n.y, n.r, paint)
            canvas.drawCircle(n.x + n.r * 0.4f, n.y + n.r * 0.3f, n.r * 0.7f, paint)
        }
        for (s in stars) {
            paint.color = Color.argb(s.bright, 255, 255, 255)
            canvas.drawCircle(s.x, s.y, s.size, paint)
        }
        paint.alpha = 255
    }
}
