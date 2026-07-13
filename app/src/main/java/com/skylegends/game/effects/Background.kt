package com.skylegends.game.effects

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * A deep-space backdrop, re-skinned per campaign sector via [SpaceTheme] so the scene visibly
 * evolves as the player advances — [configure] is called once at the start of every sector,
 * rebuilding the sky gradient, nebula/planet palettes, star visibility, and ambient particle
 * style ([DustMode]) for that sector's mood. Every layer is built from several passes (soft
 * glow + sharp core, atmosphere rim + terminator + surface bands, tapered diffraction spikes)
 * rather than one flat shape, plus a closing vignette, so the scene reads with real depth
 * instead of flat cutout shapes. Each visual layer owns a dedicated [Paint] — nothing is
 * shared across layers, so a config change on one (blend mode, mask filter, shader) can never
 * leak into another.
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

    /** One soft blob; a nebula cluster is several of these layered together for a turbulent,
     * wispy-cloud look instead of a couple of visibly separate circles. */
    private class Blob(val dx: Float, val dy: Float, val r: Float, val color: Int, val alphaScale: Float)
    private class NebulaCluster(var x: Float, var y: Float, val speed: Float, val blobs: List<Blob>, val coreColor: Int)

    private class Planet(var x: Float, var y: Float, val r: Float, val speed: Float, val lit: Int, val shadow: Int, val ringed: Boolean)

    private class ShootingStar(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val maxLife: Float)

    private var rnd = Random(20260701)
    private var time = 0f
    private var theme = SpaceThemeCatalog.forSector(0)

    private val stars = ArrayList<Star>()
    private val heroStars = ArrayList<HeroStar>()
    private val nebulae = ArrayList<NebulaCluster>()
    private val planets = ArrayList<Planet>()
    private val shootingStars = ArrayList<ShootingStar>()
    private var shootingStarTimer = 6f

    // Dense ambient particle field, drawn in one batched call. Position storage is reused
    // across themes; only color/motion/render style change with [DustMode].
    private val dustCount = 220
    private val dustPts = FloatArray(dustCount * 2)
    private val rainLines = FloatArray(dustCount * 4)

    private var lightningTimer = 8f
    private var lightningFlash = 0f

    // Dedicated paints per layer — never shared, never leaked (see [[feedback_play_before_claiming_verified]]).
    private val skyPaint = Paint()
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val heroCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroFlarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetClipPath = Path()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint()
    private val vignettePaint = Paint()

    init {
        configure(0)
    }

    /** Re-skin the whole backdrop for campaign sector [tier] (0-based). Called once per run start. */
    fun configure(tier: Int) {
        theme = SpaceThemeCatalog.forSector(tier)
        rnd = Random(20260701L.toInt() + tier * 7919)
        time = 0f
        shootingStars.clear()
        shootingStarTimer = 4f + rnd.nextFloat() * 4f
        lightningTimer = 6f + rnd.nextFloat() * 6f
        lightningFlash = 0f

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, Constants.GAME_HEIGHT,
            theme.sky, floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP
        )

        repeat(dustCount) { i ->
            dustPts[i * 2] = rnd.nextFloat() * Constants.GAME_WIDTH
            dustPts[i * 2 + 1] = rnd.nextFloat() * Constants.GAME_HEIGHT
        }

        stars.clear()
        repeat(45) { stars.add(makeStar(0.22f, 1.0f, 130)) }
        repeat(30) { stars.add(makeStar(0.55f, 1.7f, 190)) }
        repeat(18) { stars.add(makeStar(1.0f, 2.6f, 245)) }

        heroStars.clear()
        repeat(6) {
            heroStars.add(
                HeroStar(
                    rnd.nextFloat() * Constants.GAME_WIDTH, rnd.nextFloat() * Constants.GAME_HEIGHT,
                    4.5f + rnd.nextFloat() * 2.5f, Constants.SCROLL_SPEED * (0.4f + rnd.nextFloat() * 0.5f),
                    tintFor(rnd.nextFloat()), 1.2f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f
                )
            )
        }

        nebulae.clear()
        val palette = theme.nebulaColors
        repeat(theme.nebulaCount) {
            // Several overlapping blobs of varying size/density read as a turbulent wisp of
            // gas rather than a couple of visibly distinct circles.
            val blobs = List(5 + rnd.nextInt(4)) { j ->
                Blob(
                    (rnd.nextFloat() - 0.5f) * 260f, (rnd.nextFloat() - 0.5f) * 190f,
                    60f + rnd.nextFloat() * 160f, palette[j % palette.size],
                    0.5f + rnd.nextFloat() * 0.6f
                )
            }
            nebulae.add(
                NebulaCluster(
                    rnd.nextFloat() * Constants.GAME_WIDTH, rnd.nextFloat() * Constants.GAME_HEIGHT,
                    10f + rnd.nextFloat() * 14f, blobs, palette[0]
                )
            )
        }

        planets.clear()
        repeat(theme.planetCount) { i ->
            val (lit, shadow) = theme.planetColors[i % theme.planetColors.size]
            planets.add(
                Planet(
                    Constants.GAME_WIDTH * (0.15f + rnd.nextFloat() * 0.7f),
                    Constants.GAME_HEIGHT * (0.1f + rnd.nextFloat() * 0.6f),
                    24f + rnd.nextFloat() * 24f, Constants.SCROLL_SPEED * (0.35f + rnd.nextFloat() * 0.45f),
                    lit, shadow, ringed = i == 0
                )
            )
        }
    }

    private fun tintFor(warmth: Float): Int {
        val cool = Triple(190, 215, 255)
        val warm = Triple(255, 225, 180)
        val r = (cool.first + (warm.first - cool.first) * warmth).toInt()
        val g = (cool.second + (warm.second - cool.second) * warmth).toInt()
        val b = (cool.third + (warm.third - cool.third) * warmth).toInt()
        return Color.rgb(r, g, b)
    }

    /** Blends [color] toward black by [amount] (0..1) — used for band/terminator shading. */
    private fun darken(color: Int, amount: Float): Int {
        val f = 1f - amount
        return Color.rgb(
            (Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt()
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
        updateDust(dt)

        if (theme.starVisibility > 0.4f) {
            shootingStarTimer -= dt
            if (shootingStarTimer <= 0f && shootingStars.size < 2) {
                shootingStarTimer = 5f + rnd.nextFloat() * 9f
                val startX = rnd.nextFloat() * Constants.GAME_WIDTH * 0.7f
                val speed = 700f + rnd.nextFloat() * 400f
                val angle = Math.toRadians((25 + rnd.nextFloat() * 30).toDouble())
                shootingStars.add(
                    ShootingStar(startX, -20f, (sin(angle) * speed).toFloat(), (cos(angle) * speed).toFloat(), 0.9f, 0.9f)
                )
            }
        }
        var i = 0
        while (i < shootingStars.size) {
            val s = shootingStars[i]
            s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt
            if (s.life <= 0f || s.y > Constants.GAME_HEIGHT + 40f || s.x > Constants.GAME_WIDTH + 40f) {
                shootingStars.removeAt(i)
            } else i++
        }

        if (theme.lightning) {
            lightningTimer -= dt
            if (lightningTimer <= 0f) { lightningTimer = 5f + rnd.nextFloat() * 10f; lightningFlash = 1f }
            if (lightningFlash > 0f) lightningFlash = (lightningFlash - dt * 3.2f).coerceAtLeast(0f)
        }
    }

    private fun updateDust(dt: Float) {
        val speed = when (theme.dustMode) {
            DustMode.SPARKLE -> 18f
            DustMode.MIST -> 10f
            DustMode.RAIN -> 950f
            DustMode.EMBER -> -55f
            DustMode.SNOW -> 34f
        }
        val sway = if (theme.dustMode == DustMode.MIST || theme.dustMode == DustMode.SNOW) {
            sin(time * 0.6f) * 0.6f
        } else 0f
        for (i in 0 until dustCount) {
            val xi = i * 2; val yi = xi + 1
            dustPts[yi] += speed * dt
            dustPts[xi] += sway
            if (speed >= 0f && dustPts[yi] > Constants.GAME_HEIGHT) {
                dustPts[yi] = 0f; dustPts[xi] = rnd.nextFloat() * Constants.GAME_WIDTH
            } else if (speed < 0f && dustPts[yi] < 0f) {
                dustPts[yi] = Constants.GAME_HEIGHT; dustPts[xi] = rnd.nextFloat() * Constants.GAME_WIDTH
            }
        }
    }

    fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, skyPaint)

        renderNebulae(canvas)
        renderHorizonGlow(canvas)
        if (theme.starVisibility > 0.02f) renderStars(canvas)
        renderPlanets(canvas)
        if (theme.starVisibility > 0.4f) {
            renderHeroStars(canvas)
            renderShootingStars(canvas)
        }
        renderDust(canvas)
        renderVignette(canvas)

        if (theme.lightning && lightningFlash > 0f) {
            flashPaint.color = Color.argb((lightningFlash * 130f).toInt().coerceIn(0, 255), 210, 225, 255)
            canvas.drawRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, flashPaint)
        }
    }

    private fun renderNebulae(canvas: Canvas) {
        for (n in nebulae) {
            for (b in n.blobs) {
                val cx = n.x + b.dx; val cy = n.y + b.dy
                nebulaPaint.shader = RadialGradient(cx, cy, b.r, b.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                val alpha = Color.alpha(b.color)
                nebulaPaint.alpha = (alpha * b.alphaScale).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, b.r, nebulaPaint)
            }
            // Brighter, tighter core glow so each cluster reads as one coherent cloud with an
            // internal hot spot, not just a pile of equal blobs.
            nebulaPaint.shader = RadialGradient(n.x, n.y, 70f, n.coreColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            nebulaPaint.alpha = 255
            canvas.drawCircle(n.x, n.y, 70f, nebulaPaint)
        }
        nebulaPaint.shader = null
        nebulaPaint.alpha = 255
    }

    private fun renderHorizonGlow(canvas: Canvas) {
        val color = theme.horizonGlow ?: return
        val cy = Constants.GAME_HEIGHT * 1.02f
        glowPaint.shader = RadialGradient(
            Constants.GAME_WIDTH / 2f, cy, Constants.GAME_HEIGHT * 0.5f,
            color, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, Constants.GAME_HEIGHT * 0.4f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, glowPaint)
        glowPaint.shader = null
    }

    private fun renderStars(canvas: Canvas) {
        for (s in stars) {
            val twinkle = 0.55f + 0.45f * sin(time * s.twinkleSpeed + s.twinklePhase)
            val tint = tintFor(s.warmth)
            val alpha = (s.baseAlpha * twinkle * theme.starVisibility).toInt().coerceIn(0, 255)
            // Soft halo under the sharp core on the brighter/nearer stars — cheap two-circle
            // bloom approximation, no blur filter needed at this volume.
            if (s.size > 1.5f) {
                starGlowPaint.color = tint
                starGlowPaint.alpha = (alpha * 0.3f).toInt()
                canvas.drawCircle(s.x, s.y, s.size * 2.4f, starGlowPaint)
            }
            starPaint.color = tint
            starPaint.alpha = alpha
            canvas.drawCircle(s.x, s.y, s.size, starPaint)
        }
    }

    private fun renderHeroStars(canvas: Canvas) {
        for (h in heroStars) {
            val twinkle = 0.5f + 0.5f * sin(time * h.twinkleSpeed + h.twinklePhase)
            val v = theme.starVisibility
            heroGlowPaint.color = h.color
            heroGlowPaint.alpha = (110 * twinkle * v).toInt().coerceIn(0, 255)
            canvas.drawCircle(h.x, h.y, h.size * 2.6f, heroGlowPaint)

            heroCorePaint.color = Color.WHITE
            heroCorePaint.alpha = ((200 + 55 * twinkle) * v).toInt().coerceIn(0, 255)
            canvas.drawCircle(h.x, h.y, h.size * 0.55f, heroCorePaint)

            // Diffraction-spike flares, tapered to transparent at both tips via a gradient
            // shader instead of a flat-alpha line — reads as a glinting star, not a cross.
            val flareLen = h.size * (1.6f + twinkle * 1.4f)
            heroFlarePaint.alpha = (170 * twinkle * v).toInt().coerceIn(0, 255)
            heroFlarePaint.strokeWidth = max(1f, h.size * 0.22f)
            heroFlarePaint.shader = LinearGradient(
                h.x - flareLen, h.y, h.x + flareLen, h.y,
                intArrayOf(Color.TRANSPARENT, h.color, Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawLine(h.x - flareLen, h.y, h.x + flareLen, h.y, heroFlarePaint)
            heroFlarePaint.shader = LinearGradient(
                h.x, h.y - flareLen, h.x, h.y + flareLen,
                intArrayOf(Color.TRANSPARENT, h.color, Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawLine(h.x, h.y - flareLen, h.x, h.y + flareLen, heroFlarePaint)
        }
        heroFlarePaint.shader = null
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

            // Soft atmosphere rim glow, just outside the sphere.
            planetPaint.shader = RadialGradient(
                p.x, p.y, p.r * 1.35f, Color.argb(85, Color.red(p.lit), Color.green(p.lit), Color.blue(p.lit)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(p.x, p.y, p.r * 1.35f, planetPaint)

            // Base sphere shading (lit hemisphere -> shadow).
            planetPaint.shader = RadialGradient(p.x - p.r * 0.35f, p.y - p.r * 0.35f, p.r * 1.6f, p.lit, p.shadow, Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x, p.y, p.r, planetPaint)

            // Surface bands + terminator shadow, clipped to the sphere for gas-giant-style detail.
            canvas.save()
            planetClipPath.reset()
            planetClipPath.addCircle(p.x, p.y, p.r, Path.Direction.CW)
            canvas.clipPath(planetClipPath)
            planetDetailPaint.shader = null
            for (b in 0 until 3) {
                val bandY = p.y + (b - 1) * p.r * 0.42f
                planetDetailPaint.color = if (b % 2 == 0) darken(p.shadow, 0.2f) else darken(p.lit, 0.1f)
                planetDetailPaint.alpha = 55
                canvas.drawRect(p.x - p.r, bandY - p.r * 0.12f, p.x + p.r, bandY + p.r * 0.12f, planetDetailPaint)
            }
            planetDetailPaint.alpha = 255
            planetPaint.shader = RadialGradient(
                p.x + p.r * 0.5f, p.y + p.r * 0.5f, p.r * 1.3f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(p.x, p.y, p.r, planetPaint)
            canvas.restore()
        }
        planetPaint.shader = null
    }

    private fun renderDust(canvas: Canvas) {
        dustPaint.color = theme.dustColor
        when (theme.dustMode) {
            DustMode.RAIN -> {
                dustPaint.strokeWidth = 2.6f
                for (i in 0 until dustCount) {
                    val xi = i * 2; val yi = xi + 1
                    val li = i * 4
                    rainLines[li] = dustPts[xi]; rainLines[li + 1] = dustPts[yi]
                    rainLines[li + 2] = dustPts[xi] - 8f; rainLines[li + 3] = dustPts[yi] - 34f
                }
                canvas.drawLines(rainLines, dustPaint)
            }
            DustMode.SNOW -> { dustPaint.strokeWidth = 4.5f; canvas.drawPoints(dustPts, dustPaint) }
            DustMode.EMBER -> { dustPaint.strokeWidth = 3f; canvas.drawPoints(dustPts, dustPaint) }
            else -> { dustPaint.strokeWidth = 2.4f; canvas.drawPoints(dustPts, dustPaint) }
        }
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

    /** Soft cinematic darkening toward the edges — the finishing touch that ties every
     * layer together into one frame instead of a stack of flat shapes. */
    private fun renderVignette(canvas: Canvas) {
        vignettePaint.shader = RadialGradient(
            Constants.GAME_WIDTH / 2f, Constants.GAME_HEIGHT / 2f, Constants.GAME_HEIGHT * 0.72f,
            Color.TRANSPARENT, Color.argb(115, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT, vignettePaint)
    }
}
