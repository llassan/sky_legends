package com.skylegends.game.effects

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skylegends.game.utils.Constants
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * A deep-space backdrop: a rich multi-stop sky gradient, a dense faint dust field, three
 * twinkling star layers with real color-temperature variation, a handful of glowing "hero"
 * stars with sparkle flares, drifting multi-blob nebula clouds rendered with screen-blended
 * radial gradients, two shaded planets (one ringed), and rare shooting stars. Every element
 * wraps as it scrolls, so it runs forever with a fixed memory footprint. Each visual layer
 * owns a dedicated [Paint] — nothing is shared across layers, so a config change on one
 * (blend mode, mask filter, shader) can never leak into another.
 */
class Background {

    private class Star(
        var x: Float, var y: Float, val size: Float, val speed: Float,
        val warmth: Float, val baseAlpha: Int, val twinkleSpeed: Float, val twinklePhase: Float
    )

    private class HeroStar(
        var x: Float, var y: Float, val size: Float, val speed: Float,
        val color: Int, val twinkleSpeed: Float, val twinklePhase: Float
    )

    /** One soft blob; a nebula cluster is 2-4 of these drifting together. */
    private class Blob(val dx: Float, val dy: Float, val r: Float, val color: Int)
    private class NebulaCluster(var x: Float, var y: Float, val speed: Float, val blobs: List<Blob>)

    private class Planet(var x: Float, var y: Float, val r: Float, val speed: Float, val lit: Int, val shadow: Int, val ringed: Boolean)

    private class ShootingStar(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val maxLife: Float)

    private val rnd = Random(20260701)
    private var time = 0f

    private val stars = ArrayList<Star>()
    private val heroStars = ArrayList<HeroStar>()
    private val nebulae = ArrayList<NebulaCluster>()
    private val planets = ArrayList<Planet>()
    private val shootingStars = ArrayList<ShootingStar>()
    private var shootingStarTimer = 6f

    // Dense faint dust field, drawn in one batched drawPoints() call for near-zero cost.
    private val dustCount = 220
    private val dustPts = FloatArray(dustCount * 2)
    private val dustSpeed = 18f

    // Dedicated paints per layer — never shared, never leaked (see [[feedback_play_before_claiming_verified]]).
    private val skyPaint = Paint()
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 200, 210, 255); strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val heroCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroFlarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    init {
        buildSky()
        repeat(dustCount) { i ->
            dustPts[i * 2] = rnd.nextFloat() * Constants.GAME_WIDTH
            dustPts[i * 2 + 1] = rnd.nextFloat() * Constants.GAME_HEIGHT
        }
        repeat(45) { stars.add(makeStar(0.22f, 1.0f, 130)) }   // far, slow, dim
        repeat(30) { stars.add(makeStar(0.55f, 1.7f, 190)) }   // mid
        repeat(18) { stars.add(makeStar(1.0f, 2.6f, 245)) }    // near, fast, bright
        repeat(6) {
            heroStars.add(
                HeroStar(
                    rnd.nextFloat() * Constants.GAME_WIDTH, rnd.nextFloat() * Constants.GAME_HEIGHT,
                    4.5f + rnd.nextFloat() * 2.5f, Constants.SCROLL_SPEED * (0.4f + rnd.nextFloat() * 0.5f),
                    tintFor(rnd.nextFloat()), 1.2f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f
                )
            )
        }
        val palettes = listOf(
            intArrayOf(Color.argb(90, 90, 130, 255), Color.argb(60, 130, 80, 220)),   // blue -> violet
            intArrayOf(Color.argb(85, 200, 90, 200), Color.argb(55, 90, 60, 200)),     // magenta -> indigo
            intArrayOf(Color.argb(80, 255, 130, 90), Color.argb(50, 200, 70, 120)),    // ember -> rose
            intArrayOf(Color.argb(85, 80, 220, 210), Color.argb(55, 70, 100, 220))     // teal -> blue
        )
        repeat(4) { i ->
            val palette = palettes[i % palettes.size]
            val blobs = List(2 + rnd.nextInt(3)) { j ->
                Blob(
                    (rnd.nextFloat() - 0.5f) * 220f, (rnd.nextFloat() - 0.5f) * 160f,
                    90f + rnd.nextFloat() * 150f, palette[j % palette.size]
                )
            }
            nebulae.add(
                NebulaCluster(
                    rnd.nextFloat() * Constants.GAME_WIDTH, rnd.nextFloat() * Constants.GAME_HEIGHT,
                    10f + rnd.nextFloat() * 14f, blobs
                )
            )
        }
        planets.add(
            Planet(
                Constants.GAME_WIDTH * 0.78f, Constants.GAME_HEIGHT * 0.2f, 46f, 6f,
                Color.rgb(255, 210, 150), Color.rgb(120, 60, 40), ringed = true
            )
        )
        planets.add(
            Planet(
                Constants.GAME_WIDTH * 0.16f, Constants.GAME_HEIGHT * 0.62f, 26f, 9f,
                Color.rgb(150, 200, 255), Color.rgb(40, 60, 110), ringed = false
            )
        )
    }

    private fun tintFor(warmth: Float): Int {
        val cool = Triple(190, 215, 255)
        val warm = Triple(255, 225, 180)
        val r = (cool.first + (warm.first - cool.first) * warmth).toInt()
        val g = (cool.second + (warm.second - cool.second) * warmth).toInt()
        val b = (cool.third + (warm.third - cool.third) * warmth).toInt()
        return Color.rgb(r, g, b)
    }

    private fun buildSky() {
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, Constants.GAME_HEIGHT,
            intArrayOf(
                Color.rgb(3, 4, 14),
                Color.rgb(10, 12, 34),
                Color.rgb(22, 16, 46),
                Color.rgb(14, 20, 44),
                Color.rgb(4, 6, 18)
            ),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun makeStar(speedScale: Float, size: Float, bright: Int) = Star(
        rnd.nextFloat() * Constants.GAME_WIDTH, rnd.nextFloat() * Constants.GAME_HEIGHT,
        size, Constants.SCROLL_SPEED * speedScale, rnd.nextFloat(), bright,
        0.6f + rnd.nextFloat() * 1.8f, rnd.nextFloat() * 6.28f
    )

    fun update(dt: Float) {
        time += dt

        for (n in nebulae) {
            n.y += n.speed * dt
            if (n.y - 260f > Constants.GAME_HEIGHT) { n.y = -260f; n.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
        for (s in stars) {
            s.y += s.speed * dt
            if (s.y > Constants.GAME_HEIGHT) { s.y = 0f; s.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
        for (h in heroStars) {
            h.y += h.speed * dt
            if (h.y > Constants.GAME_HEIGHT) { h.y = 0f; h.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
        for (p in planets) {
            p.y += p.speed * dt
            if (p.y - p.r > Constants.GAME_HEIGHT) { p.y = -p.r; p.x = rnd.nextFloat() * Constants.GAME_WIDTH }
        }
        for (i in 0 until dustCount) {
            val yi = i * 2 + 1
            dustPts[yi] += dustSpeed * dt
            if (dustPts[yi] > Constants.GAME_HEIGHT) {
                dustPts[yi] = 0f
                dustPts[i * 2] = rnd.nextFloat() * Constants.GAME_WIDTH
            }
        }

        shootingStarTimer -= dt
        if (shootingStarTimer <= 0f && shootingStars.size < 2) {
            shootingStarTimer = 5f + rnd.nextFloat() * 9f
            val startX = rnd.nextFloat() * Constants.GAME_WIDTH * 0.7f
            val speed = 700f + rnd.nextFloat() * 400f
            val angle = Math.toRadians((25 + rnd.nextFloat() * 30).toDouble())
            shootingStars.add(
                ShootingStar(
                    startX, -20f,
                    (sin(angle) * speed).toFloat(), (cos(angle) * speed).toFloat(),
                    0.9f, 0.9f
                )
            )
        }
        var i = 0
        while (i < shootingStars.size) {
            val s = shootingStars[i]
            s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt
            if (s.life <= 0f || s.y > Constants.GAME_HEIGHT + 40f || s.x > Constants.GAME_WIDTH + 40f) {
                shootingStars.removeAt(i)
            } else i++
        }
    }

    fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, skyPaint)

        dustPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPoints(dustPts, dustPaint)

        renderNebulae(canvas)
        renderStars(canvas)
        renderPlanets(canvas)
        renderHeroStars(canvas)
        renderShootingStars(canvas)
    }

    private fun renderNebulae(canvas: Canvas) {
        for (n in nebulae) {
            for (b in n.blobs) {
                val cx = n.x + b.dx; val cy = n.y + b.dy
                nebulaPaint.shader = RadialGradient(
                    cx, cy, b.r, b.color, Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, b.r, nebulaPaint)
            }
        }
        nebulaPaint.shader = null
    }

    private fun renderStars(canvas: Canvas) {
        for (s in stars) {
            val twinkle = 0.55f + 0.45f * sin(time * s.twinkleSpeed + s.twinklePhase)
            val tint = tintFor(s.warmth)
            starPaint.color = tint
            starPaint.alpha = (s.baseAlpha * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.size, starPaint)
        }
    }

    private fun renderHeroStars(canvas: Canvas) {
        for (h in heroStars) {
            val twinkle = 0.5f + 0.5f * sin(time * h.twinkleSpeed + h.twinklePhase)
            val glowAlpha = (110 * twinkle).toInt().coerceIn(0, 255)
            heroGlowPaint.color = h.color
            heroGlowPaint.alpha = glowAlpha
            canvas.drawCircle(h.x, h.y, h.size * 2.6f, heroGlowPaint)

            heroCorePaint.color = Color.WHITE
            heroCorePaint.alpha = (200 + 55 * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(h.x, h.y, h.size * 0.55f, heroCorePaint)

            val flareLen = h.size * (1.6f + twinkle * 1.4f)
            heroFlarePaint.color = h.color
            heroFlarePaint.alpha = (140 * twinkle).toInt().coerceIn(0, 255)
            heroFlarePaint.strokeWidth = max(1f, h.size * 0.22f)
            canvas.drawLine(h.x - flareLen, h.y, h.x + flareLen, h.y, heroFlarePaint)
            canvas.drawLine(h.x, h.y - flareLen, h.x, h.y + flareLen, heroFlarePaint)
        }
    }

    private fun renderPlanets(canvas: Canvas) {
        for (p in planets) {
            if (p.ringed) {
                ringPaint.color = Color.argb(120, 230, 200, 170)
                ringPaint.strokeWidth = 5f
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(-18f)
                canvas.scale(1f, 0.38f)
                canvas.drawCircle(0f, 0f, p.r * 1.85f, ringPaint)
                canvas.restore()
            }
            planetPaint.shader = RadialGradient(
                p.x - p.r * 0.35f, p.y - p.r * 0.35f, p.r * 1.6f,
                p.lit, p.shadow, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(p.x, p.y, p.r, planetPaint)
        }
        planetPaint.shader = null
    }

    private fun renderShootingStars(canvas: Canvas) {
        for (s in shootingStars) {
            val t = (s.life / s.maxLife).coerceIn(0f, 1f)
            val tailX = s.x - s.vx * 0.05f; val tailY = s.y - s.vy * 0.05f
            shootPaint.color = Color.WHITE
            shootPaint.alpha = (255 * t).toInt().coerceIn(0, 255)
            shootPaint.strokeWidth = 3.5f
            canvas.drawLine(s.x, s.y, tailX, tailY, shootPaint)
            shootPaint.alpha = (120 * t).toInt().coerceIn(0, 255)
            shootPaint.strokeWidth = 1.5f
            canvas.drawLine(tailX, tailY, s.x - s.vx * 0.14f, s.y - s.vy * 0.14f, shootPaint)
        }
    }
}
