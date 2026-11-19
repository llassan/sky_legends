package com.skylegends.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.skylegends.game.utils.Constants
import kotlin.math.sin

/**
 * All on-screen UI: in-game HUD (score, health, shield, weapon, combo), the boss bar,
 * the low-health vignette, and the full-screen menu/pause/result overlays. Pure drawing —
 * it reads values passed in and never mutates game state.
 */
class Hud {
    private val W = Constants.GAME_WIDTH
    private val H = Constants.GAME_HEIGHT

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; isFakeBoldText = true
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 220, 255); textSize = 24f
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val panelPath = android.graphics.Path()
    private var fontsApplied = false

    /** Hud is constructed before assets load, so the CC0 sci-fi font is applied lazily. */
    private fun fonts() {
        if (fontsApplied) return
        val bold = com.skylegends.game.fx.Sprites.fontBold ?: return
        text.typeface = bold; text.isFakeBoldText = false; text.letterSpacing = 0.04f
        small.typeface = com.skylegends.game.fx.Sprites.fontThin ?: bold; small.letterSpacing = 0.06f
        fontsApplied = true
    }

    /** Scales [p]'s text size down so [s] fits in [maxW] (0 = no limit). */
    private fun fit(p: Paint, s: String, size: Float, maxW: Float) {
        p.textSize = size
        if (maxW > 0f) { val w = p.measureText(s); if (w > maxW) p.textSize = size * maxW / w }
    }

    /** Small spaced-out caption (thin font). */
    private fun caption(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
        small.textAlign = align; small.color = color
        fit(small, s, size * 1.3f, maxW)   // Rajdhani is condensed — scale up to match the visual size
        c.drawText(s, x, y, small)
    }

    /**
     * Sci-fi plate: cut corners (top-left + bottom-right), glass gradient, lit top edge, accent
     * border with a soft outer halo, and bright corner notches. Every button and panel uses it,
     * so the whole UI reads as one designed set.
     */
    private fun panel(c: Canvas, r: RectF, accent: Int, alpha: Int = 235, cut: Float = 14f, glow: Boolean = true) {
        panelPath.reset()
        panelPath.moveTo(r.left + cut, r.top); panelPath.lineTo(r.right, r.top)
        panelPath.lineTo(r.right, r.bottom - cut); panelPath.lineTo(r.right - cut, r.bottom)
        panelPath.lineTo(r.left, r.bottom); panelPath.lineTo(r.left, r.top + cut); panelPath.close()
        if (glow) {
            halo.color = accent; halo.alpha = 55; halo.strokeWidth = 9f
            c.drawPath(panelPath, halo)
        }
        fill.shader = LinearGradient(0f, r.top, 0f, r.bottom,
            Color.argb(alpha, 28, 40, 82), Color.argb(alpha, 8, 12, 30), Shader.TileMode.CLAMP)
        c.drawPath(panelPath, fill)
        fill.shader = null
        fill.color = Color.argb(70, 255, 255, 255)
        c.drawRect(r.left + cut + 2f, r.top + 3f, r.right - 4f, r.top + 4.5f, fill)
        border.color = accent; border.alpha = 230; border.strokeWidth = 2.5f
        c.drawPath(panelPath, border)
        // Corner notches on the two cut corners.
        fill.color = accent
        panelPath.reset(); panelPath.moveTo(r.left, r.top + cut + 8f); panelPath.lineTo(r.left, r.top + cut)
        panelPath.lineTo(r.left + cut, r.top); panelPath.lineTo(r.left + cut + 8f, r.top)
        panelPath.lineTo(r.left + cut + 8f, r.top + 3f); panelPath.lineTo(r.left + cut + 1f, r.top + 3f)
        panelPath.lineTo(r.left + 3f, r.top + cut + 1f); panelPath.lineTo(r.left + 3f, r.top + cut + 8f); panelPath.close()
        c.drawPath(panelPath, fill)
        panelPath.reset(); panelPath.moveTo(r.right, r.bottom - cut - 8f); panelPath.lineTo(r.right, r.bottom - cut)
        panelPath.lineTo(r.right - cut, r.bottom); panelPath.lineTo(r.right - cut - 8f, r.bottom)
        panelPath.lineTo(r.right - cut - 8f, r.bottom - 3f); panelPath.lineTo(r.right - cut - 1f, r.bottom - 3f)
        panelPath.lineTo(r.right - 3f, r.bottom - cut - 1f); panelPath.lineTo(r.right - 3f, r.bottom - cut - 8f); panelPath.close()
        c.drawPath(panelPath, fill)
    }

    /** Segmented gauge: dark well, gradient fill, 10% tick marks, glossy top line, frame. */
    private fun gauge(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, c1: Int, c2: Int, segments: Int = 10) {
        val f = frac.coerceIn(0f, 1f)
        fill.color = Color.argb(200, 6, 8, 18)
        c.drawRect(x, y, x + w, y + h, fill)
        if (f > 0f) {
            fill.shader = LinearGradient(x, y, x + w, y, c1, c2, Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w * f, y + h, fill)
            fill.shader = null
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawRect(x, y, x + w * f, y + h * 0.3f, fill)
        }
        fill.color = Color.argb(150, 6, 8, 18)
        for (i in 1 until segments) { val sx = x + w * i / segments; c.drawRect(sx - 1f, y, sx + 1f, y + h, fill) }
        border.color = Color.argb(150, 190, 210, 255); border.strokeWidth = 1.5f
        c.drawRect(x, y, x + w, y + h, border)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(120, 255, 255, 255)
    }
    // Dedicated paint for the ability cooldown ring — it needs several different
    // colors/widths per frame, which previously leaked into `stroke` (shared by every
    // bordered bar) since nothing reset it afterward. Keeping it separate can't leak.
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Neon text: a soft coloured glow behind a crisp face, shrunk to [maxW] if needed. */
    private fun glowText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, glow: Int = color, maxW: Float = 0f) {
        text.textAlign = align
        fit(text, s, size, maxW)
        text.color = color
        text.setShadowLayer(size * 0.28f, 0f, 0f, glow)
        c.drawText(s, x, y, text)
        text.clearShadowLayer()
    }

    // ---------------- In-game HUD ----------------

    fun drawPlaying(
        c: Canvas, score: Int, coins: Int, hp: Float, maxHp: Float,
        shield: Float, maxShield: Float, weaponName: String, weaponLevel: Int, multiplier: Float,
        abilitySymbol: String, abilityCooldownFraction: Float, abilityReady: Boolean, overdriveActive: Boolean
    ) {
        fonts()
        // Score + coins (top-left).
        caption(c, "SCORE", 28f, 36f, 13f, Color.rgb(140, 170, 230))
        glowText(c, "$score", 26f, 72f, 34f, Color.WHITE, glow = Color.rgb(60, 140, 255))
        drawCoin(c, 38f, 94f, 9f)
        glowText(c, "$coins", 54f, 102f, 22f, Color.rgb(255, 222, 120), glow = Color.rgb(200, 120, 20))

        // Status panel (bottom-left): shield over hull, labels in their own column so they
        // never overlap the gauges.
        val px = 16f; val pr = 300f; val pb = H - 18f; val pt = H - 102f
        panel(c, RectF(px, pt, pr, pb), Color.rgb(90, 150, 255), alpha = 170, cut = 12f, glow = false)
        caption(c, "SHIELD", px + 14f, pt + 30f, 13f, Color.rgb(140, 200, 255))
        gauge(c, px + 100f, pt + 18f, pr - px - 116f, 13f, if (maxShield > 0f) shield / maxShield else 0f,
            Color.rgb(60, 150, 255), Color.rgb(150, 230, 255))
        caption(c, "HULL", px + 14f, pt + 66f, 13f, Color.rgb(255, 170, 170))
        gauge(c, px + 100f, pt + 46f, pr - px - 116f, 24f, hp / maxHp,
            Color.rgb(255, 60, 90), Color.rgb(255, 170, 80))

        // Weapon chip (bottom-right, below the ability button).
        val wr = RectF(W - 236f, H - 64f, W - 16f, H - 18f)
        panel(c, wr, Color.rgb(255, 190, 90), alpha = 170, cut = 10f, glow = false)
        caption(c, weaponName.uppercase(), wr.left + 14f, wr.centerY() + 6f, 15f, Color.WHITE, maxW = wr.width() - 70f)
        glowText(c, "LV$weaponLevel", wr.right - 12f, wr.centerY() + 7f, 18f, Color.rgb(255, 210, 110), Paint.Align.RIGHT, Color.rgb(255, 120, 20))

        // Combo multiplier (top-center) when > 1.
        if (multiplier > 1.05f) {
            val pulse = 1f + 0.08f * sin(System.nanoTime() / 1.0e8)
            caption(c, "COMBO", W / 2f, 32f, 12f, Color.rgb(255, 200, 120), Paint.Align.CENTER)
            glowText(c, "x%.1f".format(multiplier), W / 2f, 70f, (34f * pulse).toFloat(),
                Color.rgb(255, 235, 140), Paint.Align.CENTER, Color.rgb(255, 120, 20))
        }

        // Overdrive banner.
        if (overdriveActive) {
            val pulse = 0.7f + 0.3f * sin(System.nanoTime() / 0.6e8).toFloat()
            glowText(c, "OVERDRIVE", W / 2f, 150f, 28f * pulse, Color.rgb(255, 225, 90), Paint.Align.CENTER, Color.rgb(255, 110, 10))
        }

        drawAbilityButton(c, abilitySymbol, abilityCooldownFraction, abilityReady)
        drawPauseButton(c)
    }

    private fun drawAbilityButton(c: Canvas, symbol: String, cooldownFraction: Float, ready: Boolean) {
        val r = UiLayout.abilityButton
        val cx = r.centerX(); val cy = r.centerY(); val radius = r.width() / 2f

        // Glass core with a lit centre.
        fill.shader = RadialGradient(cx, cy - radius * 0.3f, radius,
            if (ready) Color.argb(235, 40, 80, 150) else Color.argb(220, 26, 32, 56),
            Color.argb(235, 8, 12, 28), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, radius, fill)
        fill.shader = null

        // Cooldown sweep fills back in as it becomes ready; tick marks every 30°.
        val oval = RectF(r)
        ring.strokeWidth = 6f
        ring.color = Color.argb(110, 70, 85, 120)
        c.drawArc(oval, -90f, 360f, false, ring)
        ring.color = if (ready) Color.rgb(140, 220, 255) else Color.rgb(90, 170, 255)
        c.drawArc(oval, -90f, 360f * cooldownFraction.coerceIn(0f, 1f), false, ring)
        fill.color = Color.argb(160, 8, 12, 28)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            c.drawCircle(cx + (radius * kotlin.math.cos(a)).toFloat(), cy + (radius * sin(a)).toFloat(), 2f, fill)
        }

        if (ready) {
            val pulse = 0.6f + 0.4f * sin(System.nanoTime() / 0.5e8).toFloat()
            ring.color = Color.argb((120 * pulse).toInt().coerceIn(0, 255), 150, 220, 255)
            ring.strokeWidth = 3f
            c.drawCircle(cx, cy, radius + 5f, ring)
        }

        if (ready) glowText(c, symbol, cx, cy + 13f, 38f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 180, 255))
        else {
            text.textAlign = Paint.Align.CENTER; text.textSize = 38f; text.color = Color.rgb(110, 125, 155)
            c.drawText(symbol, cx, cy + 13f, text)
        }
    }

    private fun drawCoin(c: Canvas, x: Float, y: Float, r: Float) {
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, Color.rgb(255, 245, 170), Color.rgb(230, 150, 20), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill)
        fill.shader = null
        border.color = Color.rgb(170, 100, 10); border.strokeWidth = 1.5f
        c.drawCircle(x, y, r, border)
        fill.color = Color.argb(140, 255, 255, 220)
        c.drawCircle(x, y, r * 0.45f, fill)
    }

    private fun drawBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, bg: Int, c1: Int, c2: Int) {
        fill.shader = null
        fill.color = bg
        c.drawRoundRect(x, y, x + w, y + h, h / 2f, h / 2f, fill)
        if (frac > 0f) {
            fill.shader = LinearGradient(x, y, x + w, y, c1, c2, Shader.TileMode.CLAMP)
            c.drawRoundRect(x, y, x + w * frac, y + h, h / 2f, h / 2f, fill)
            fill.shader = null
        }
        c.drawRoundRect(x, y, x + w, y + h, h / 2f, h / 2f, stroke)
    }

    private fun drawPauseButton(c: Canvas) {
        val r = UiLayout.pauseButton
        panel(c, r, Color.rgb(120, 180, 255), alpha = 190, cut = 10f, glow = false)
        fill.color = Color.WHITE
        val pw = 6f; val ph = 22f
        val cy = r.centerY()
        c.drawRect(r.centerX() - 11f, cy - ph / 2, r.centerX() - 11f + pw, cy + ph / 2, fill)
        c.drawRect(r.centerX() + 5f, cy - ph / 2, r.centerX() + 5f + pw, cy + ph / 2, fill)
    }

    fun drawBossBar(c: Canvas, name: String, frac: Float) {
        // Sits below the score/coin/pause row (which occupies roughly y 18-95) so the two
        // never overlap.
        fonts()
        val x = 40f; val w = W - 80f; val y = 116f
        glowText(c, name, W / 2f, y - 10f, 17f, Color.rgb(255, 190, 180), Paint.Align.CENTER, Color.rgb(255, 40, 40), maxW = w)
        // Angled end-caps frame the gauge like a boss nameplate.
        fill.color = Color.rgb(255, 80, 70)
        panelPath.reset(); panelPath.moveTo(x - 14f, y + 7f); panelPath.lineTo(x - 4f, y - 3f); panelPath.lineTo(x - 4f, y + 17f); panelPath.close(); c.drawPath(panelPath, fill)
        panelPath.reset(); panelPath.moveTo(x + w + 14f, y + 7f); panelPath.lineTo(x + w + 4f, y - 3f); panelPath.lineTo(x + w + 4f, y + 17f); panelPath.close(); c.drawPath(panelPath, fill)
        gauge(c, x, y, w, 14f, frac, Color.rgb(255, 30, 50), Color.rgb(255, 150, 60), segments = 20)
    }

    fun drawBossWarning(c: Canvas, t: Float) {
        fonts()
        // t in 0..1 fade. Hazard band with chevrons + flashing WARNING.
        val alpha = (sin(t * Math.PI) * 255).toInt().coerceIn(0, 255)
        fill.color = Color.argb((alpha * 0.45f).toInt(), 120, 0, 0)
        c.drawRect(0f, H * 0.42f, W, H * 0.58f, fill)
        fill.color = Color.argb(alpha, 255, 60, 50)
        c.drawRect(0f, H * 0.42f, W, H * 0.42f + 3f, fill)
        c.drawRect(0f, H * 0.58f - 3f, W, H * 0.58f, fill)
        val a = (alpha * 0.9f).toInt()
        glowText(c, "WARNING", W / 2f, H * 0.5f + 8f, 52f, Color.argb(a, 255, 90, 80), Paint.Align.CENTER, Color.argb(a, 255, 0, 0), maxW = W - 60f)
        caption(c, "HOSTILE CAPITAL SHIP INBOUND", W / 2f, H * 0.5f + 40f, 14f, Color.argb(a, 255, 190, 180), Paint.Align.CENTER, maxW = W - 60f)
    }

    fun drawLowHealthVignette(c: Canvas, intensity: Float) {
        val a = (intensity * 120f).toInt().coerceIn(0, 160)
        fill.shader = android.graphics.RadialGradient(
            W / 2f, H / 2f, H * 0.7f,
            intArrayOf(Color.TRANSPARENT, Color.argb(a, 200, 0, 0)),
            floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W, H, fill)
        fill.shader = null
    }

    fun drawFlash(c: Canvas, intensity: Float, r: Int, g: Int, b: Int) {
        fill.color = Color.argb((intensity * 200f).toInt().coerceIn(0, 255), r, g, b)
        c.drawRect(0f, 0f, W, H, fill)
    }

    // ---------------- Overlays ----------------

    private fun dim(c: Canvas, alpha: Int) {
        fill.color = Color.argb(alpha, 6, 8, 20)
        c.drawRect(0f, 0f, W, H, fill)
    }

    private fun button(c: Canvas, r: RectF, label: String, accent: Int, primary: Boolean = false) {
        fonts()
        panel(c, r, accent, cut = 16f)
        if (primary) {
            // Animated sheen sweeping across the main call-to-action.
            val t = ((System.nanoTime() / 1_000_000L) % 2600L) / 2600f
            val sx = r.left - 80f + (r.width() + 160f) * t
            c.save(); c.clipRect(r)
            fill.shader = LinearGradient(sx - 50f, 0f, sx + 50f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(60, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawRect(r, fill); fill.shader = null
            c.restore()
        }
        glowText(c, label, r.centerX(), r.centerY() + r.height() * 0.15f, r.height() * 0.36f, Color.WHITE, Paint.Align.CENTER, accent, maxW = r.width() - 40f)
    }

    fun drawMenu(c: Canvas, bestScore: Int, coins: Int, musicOn: Boolean, sfxOn: Boolean, sector: Int, sectorTotal: Int, sectorName: String, time: Float) {
        fonts()
        val pulse = 1f + 0.025f * sin(time * 2.0).toFloat()
        glowText(c, "SKY", W / 2f, 248f, 100f * pulse, Color.rgb(150, 215, 255), Paint.Align.CENTER, Color.rgb(30, 110, 255), maxW = W - 80f)
        glowText(c, "LEGENDS", W / 2f, 326f, 58f * pulse, Color.WHITE, Paint.Align.CENTER, Color.rgb(255, 110, 40), maxW = W - 60f)
        // Thin divider with a centre diamond under the logo.
        fill.color = Color.argb(150, 150, 200, 255)
        c.drawRect(W / 2f - 150f, 350f, W / 2f - 14f, 351.5f, fill)
        c.drawRect(W / 2f + 14f, 350f, W / 2f + 150f, 351.5f, fill)
        panelPath.reset(); panelPath.moveTo(W / 2f, 344f); panelPath.lineTo(W / 2f + 7f, 351f); panelPath.lineTo(W / 2f, 358f); panelPath.lineTo(W / 2f - 7f, 351f); panelPath.close()
        fill.color = Color.rgb(255, 170, 80); c.drawPath(panelPath, fill)

        caption(c, "BEST  $bestScore", W / 2f, 398f, 17f, Color.rgb(180, 205, 245), Paint.Align.CENTER)
        caption(c, "SECTOR $sector/$sectorTotal — $sectorName", W / 2f, 430f, 17f, Color.rgb(255, 205, 130), Paint.Align.CENTER, maxW = W - 40f)

        // Coins (top-right) + sound toggles (top-left).
        coinPill(c, coins, W - 150f, 54f)
        toggle(c, UiLayout.musicToggle, musicOn, "♪")
        toggle(c, UiLayout.sfxToggle, sfxOn, "♫")

        button(c, UiLayout.playButton, "PLAY", Color.rgb(90, 200, 255), primary = true)
        button(c, UiLayout.hangarButton, "HANGAR", Color.rgb(150, 190, 255))
        button(c, UiLayout.upgradeButton, "UPGRADE LAB", Color.rgb(255, 190, 100))

        caption(c, "DRAG TO FLY • AUTO-FIRE • GRAB POWER-UPS", W / 2f, H - 40f, 13f, Color.rgb(130, 150, 195), Paint.Align.CENTER, maxW = W - 40f)
    }

    private fun coinPill(c: Canvas, coins: Int, x: Float, y: Float) {
        fonts()
        panel(c, RectF(x - 26f, y - 24f, W - 22f, y + 24f), Color.rgb(255, 190, 70), alpha = 190, cut = 10f, glow = false)
        drawCoin(c, x, y, 13f)
        glowText(c, "$coins", x + 24f, y + 10f, 26f, Color.rgb(255, 228, 140), Paint.Align.LEFT, Color.rgb(200, 110, 20), maxW = W - 22f - x - 34f)
    }

    private fun toggle(c: Canvas, r: RectF, on: Boolean, glyph: String) {
        panel(c, r, if (on) Color.rgb(110, 190, 255) else Color.rgb(90, 95, 120), alpha = if (on) 220 else 170, cut = 9f, glow = false)
        text.textAlign = Paint.Align.CENTER; text.textSize = 30f
        text.color = if (on) Color.WHITE else Color.rgb(110, 110, 130)
        c.drawText(glyph, r.centerX(), r.centerY() + 11f, text)
        if (!on) { // strike-through when muted
            fill.color = Color.rgb(220, 80, 80)
            c.drawRect(r.left + 8f, r.centerY() - 2f, r.right - 8f, r.centerY() + 2f, fill)
        }
    }

    private fun backButton(c: Canvas) {
        val r = UiLayout.backButton
        panel(c, r, Color.rgb(150, 190, 255), alpha = 210, cut = 10f, glow = false)
        glowText(c, "‹ BACK", r.centerX(), r.centerY() + 8f, 20f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 160, 255), maxW = r.width() - 16f)
    }

    // ---------------- Hangar ----------------

    fun drawHangar(
        c: Canvas, spec: com.skylegends.game.aircraft.AircraftSpec, unlocked: Boolean, selected: Boolean,
        coins: Int, index: Int, count: Int
    ) {
        fonts()
        dim(c, 200)
        backButton(c)
        coinPill(c, coins, W - 150f, 54f)
        glowText(c, "HANGAR", W / 2f, 140f, 44f, Color.WHITE, Paint.Align.CENTER, Color.rgb(60, 140, 255), maxW = W - 80f)

        // Ship preview (themed silhouette).
        drawShipPreview(c, spec, W / 2f, 380f)

        // Arrows.
        arrow(c, UiLayout.hangarPrev, true)
        arrow(c, UiLayout.hangarNext, false)

        glowText(c, spec.displayName, W / 2f, 510f, 40f, Color.WHITE, Paint.Align.CENTER, spec.bodyColor, maxW = W - 220f)
        caption(c, spec.tagline.uppercase(), W / 2f, 544f, 15f, Color.rgb(190, 205, 235), Paint.Align.CENTER, maxW = W - 220f)

        // Stat bars.
        statBar(c, "ARMOR", spec.statArmor, 600f, Color.rgb(255, 120, 120))
        statBar(c, "SPEED", spec.statSpeed, 648f, Color.rgb(120, 220, 160))
        statBar(c, "POWER", spec.statFirepower, 696f, Color.rgb(255, 200, 100))
        caption(c, spec.abilityBlurb.uppercase(), W / 2f, 740f, 14f, Color.rgb(150, 175, 220), Paint.Align.CENTER, maxW = W - 40f)
        caption(c, "${spec.defaultWeapon.displayName.uppercase()}  •  ${spec.abilitySymbol} ${spec.abilityName}", W / 2f, 768f, 15f, Color.rgb(255, 210, 140), Paint.Align.CENTER, maxW = W - 40f)

        // Action button.
        val r = UiLayout.hangarAction
        when {
            selected -> { buttonDisabled(c, r, "SELECTED"); }
            unlocked -> button(c, r, "SELECT", Color.rgb(120, 255, 170))
            coins >= spec.cost -> button(c, r, "UNLOCK  ${spec.cost}", Color.rgb(255, 210, 120))
            else -> buttonDisabled(c, r, "NEED ${spec.cost}")
        }

        // Page dots instead of "1 / 6".
        for (i in 0 until count) {
            val dx = W / 2f + (i - (count - 1) / 2f) * 20f
            fill.color = if (i == index) Color.rgb(140, 210, 255) else Color.argb(120, 120, 140, 180)
            c.drawCircle(dx, 786f, if (i == index) 5f else 3.5f, fill)
        }
    }

    private fun previewLighten(color: Int, amt: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amt).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * amt).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amt).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun previewDarken(color: Int, amt: Float): Int {
        val f = 1f - amt
        return Color.rgb((Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt())
    }

    /** Same top-lit gradient + dark-outline treatment as the in-game ship, so the Hangar
     * preview matches what you actually fly instead of looking like a flatter mock-up. */
    private fun previewShadedFill(c: Canvas, p: android.graphics.Path, base: Int, topY: Float, bottomY: Float) {
        fill.shader = LinearGradient(0f, topY, 0f, bottomY, previewLighten(base, 0.3f), previewDarken(base, 0.35f), Shader.TileMode.CLAMP)
        c.drawPath(p, fill)
        fill.shader = null
        stroke.color = previewDarken(base, 0.55f)
        stroke.strokeWidth = 2.2f
        c.drawPath(p, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
        stroke.strokeWidth = 3f
    }

    private fun previewCanopy(c: Canvas, cx: Float, cy: Float, r: Float, tint: Int) {
        fill.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f, Color.WHITE, tint, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
    }

    private fun drawShipPreview(c: Canvas, spec: com.skylegends.game.aircraft.AircraftSpec, cx: Float, cy: Float) {
        val s = 2.2f
        fill.color = Color.argb(50, 120, 180, 255)
        c.drawCircle(cx, cy, 90f, fill)
        c.save(); c.translate(cx, cy)
        val w = 54f * s; val h = 64f * s
        // Same sprite the player flies in-game, so the hangar can never drift from gameplay.
        val sprite = com.skylegends.game.fx.Sprites.forAircraft(spec.id)
        if (sprite != null) {
            val scale = if (spec.shape == com.skylegends.game.aircraft.HullShape.HEAVY) 1.55f else 1.35f
            com.skylegends.game.fx.Sprites.drawCentered(c, sprite, w * scale)
            c.restore()
            return
        }
        val p = android.graphics.Path()
        when (spec.shape) {
            com.skylegends.game.aircraft.HullShape.DELTA -> {
                p.moveTo(-w / 2f, h * 0.18f); p.lineTo(-w * 0.16f, -h * 0.1f); p.lineTo(-w * 0.16f, h * 0.34f); p.close()
                previewShadedFill(c, p, spec.wingColor, -h * 0.1f, h * 0.34f)
                p.reset(); p.moveTo(w / 2f, h * 0.18f); p.lineTo(w * 0.16f, -h * 0.1f); p.lineTo(w * 0.16f, h * 0.34f); p.close()
                previewShadedFill(c, p, spec.wingColor, -h * 0.1f, h * 0.34f)
                p.reset(); p.moveTo(0f, -h / 2f); p.lineTo(w * 0.2f, h * 0.3f); p.lineTo(w * 0.12f, h / 2f)
                p.lineTo(-w * 0.12f, h / 2f); p.lineTo(-w * 0.2f, h * 0.3f); p.close()
                previewShadedFill(c, p, spec.bodyColor, -h / 2f, h / 2f)
                previewCanopy(c, 0f, -h * 0.12f, w * 0.1f, spec.accentColor)
                drawHardware(c, spec, w * 0.5f, h * 0.18f)
            }
            com.skylegends.game.aircraft.HullShape.ARROW -> {
                val aw = w * 0.92f; val ah = h * 1.1f
                p.moveTo(-aw * 0.08f, -ah * 0.10f); p.lineTo(-aw * 0.64f, ah * 0.46f)
                p.lineTo(-aw * 0.30f, ah * 0.40f); p.lineTo(-aw * 0.08f, ah * 0.14f); p.close()
                previewShadedFill(c, p, spec.wingColor, -ah * 0.1f, ah * 0.46f)
                p.reset(); p.moveTo(aw * 0.08f, -ah * 0.10f); p.lineTo(aw * 0.64f, ah * 0.46f)
                p.lineTo(aw * 0.30f, ah * 0.40f); p.lineTo(aw * 0.08f, ah * 0.14f); p.close()
                previewShadedFill(c, p, spec.wingColor, -ah * 0.1f, ah * 0.46f)
                p.reset(); p.moveTo(0f, -ah * 0.58f); p.lineTo(aw * 0.09f, ah * 0.30f); p.lineTo(0f, ah * 0.5f)
                p.lineTo(-aw * 0.09f, ah * 0.30f); p.close()
                previewShadedFill(c, p, spec.bodyColor, -ah * 0.58f, ah * 0.5f)
                previewCanopy(c, 0f, -ah * 0.20f, aw * 0.07f, spec.accentColor)
                drawHardware(c, spec, aw * 0.64f, ah * 0.44f)
            }
            com.skylegends.game.aircraft.HullShape.HEAVY -> {
                val hw = w * 1.4f; val hh = h * 0.96f
                p.reset(); p.addRoundRect(-hw * 0.58f, -hh * 0.02f, -hw * 0.18f, hh * 0.40f, 8f, 8f, android.graphics.Path.Direction.CW)
                previewShadedFill(c, p, spec.wingColor, -hh * 0.02f, hh * 0.40f)
                p.reset(); p.addRoundRect(hw * 0.18f, -hh * 0.02f, hw * 0.58f, hh * 0.40f, 8f, 8f, android.graphics.Path.Direction.CW)
                previewShadedFill(c, p, spec.wingColor, -hh * 0.02f, hh * 0.40f)
                p.reset(); p.moveTo(0f, -hh / 2f); p.lineTo(hw * 0.24f, hh * 0.10f); p.lineTo(hw * 0.20f, hh * 0.5f)
                p.lineTo(-hw * 0.20f, hh * 0.5f); p.lineTo(-hw * 0.24f, hh * 0.10f); p.close()
                previewShadedFill(c, p, spec.bodyColor, -hh / 2f, hh * 0.5f)
                previewCanopy(c, 0f, -hh * 0.14f, hw * 0.10f, spec.accentColor)
                fill.color = spec.wingColor; c.drawRect(-hw * 0.14f, hh * 0.16f, hw * 0.14f, hh * 0.22f, fill)
                drawHardware(c, spec, hw * 0.42f, hh * 0.22f)
            }
        }
        c.restore()
    }

    /** Mirrors [com.skylegends.game.entities.Player]'s wing-gun/missile-pod hardware so the
     * hangar preview matches what you actually see in flight. */
    private fun drawHardware(c: Canvas, spec: com.skylegends.game.aircraft.AircraftSpec, wingX: Float, wingY: Float) {
        val wing = spec.wingWeapon
        if (wing != null) {
            fill.color = Color.rgb(60, 62, 74)
            c.drawRoundRect(-wingX - 4f, wingY - 12f, -wingX + 4f, wingY + 8f, 2f, 2f, fill)
            c.drawRoundRect(wingX - 4f, wingY - 12f, wingX + 4f, wingY + 8f, 2f, 2f, fill)
            fill.color = wing.glowColor
            c.drawCircle(-wingX, wingY - 12f, 2.6f, fill)
            c.drawCircle(wingX, wingY - 12f, 2.6f, fill)
        }
        val missile = spec.missileWeapon
        if (missile != null) {
            val podX = wingX * 0.62f
            fill.color = Color.rgb(80, 82, 92)
            c.drawRoundRect(-podX - 7f, wingY + 4f, -podX + 7f, wingY + 24f, 4f, 4f, fill)
            c.drawRoundRect(podX - 7f, wingY + 4f, podX + 7f, wingY + 24f, 4f, 4f, fill)
            fill.color = missile.glowColor
            c.drawCircle(-podX, wingY + 4f, 4.5f, fill)
            c.drawCircle(podX, wingY + 4f, 4.5f, fill)
        }
    }

    private fun statBar(c: Canvas, label: String, frac: Float, y: Float, color: Int) {
        val x = 150f; val w = W - 190f
        caption(c, label, 136f, y + 15f, 15f, Color.rgb(200, 210, 235), Paint.Align.RIGHT)
        gauge(c, x, y, w, 16f, frac, darkerOf(color), color)
    }

    private fun darkerOf(c: Int) = Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10)

    private fun arrow(c: Canvas, r: RectF, left: Boolean) {
        panel(c, r, Color.rgb(120, 180, 255), alpha = 190, cut = 12f, glow = false)
        fill.color = Color.WHITE
        text.setShadowLayer(10f, 0f, 0f, Color.rgb(90, 170, 255))
        val p = android.graphics.Path()
        val m = 22f
        if (left) { p.moveTo(r.centerX() + m * 0.5f, r.centerY() - m); p.lineTo(r.centerX() - m * 0.6f, r.centerY()); p.lineTo(r.centerX() + m * 0.5f, r.centerY() + m) }
        else { p.moveTo(r.centerX() - m * 0.5f, r.centerY() - m); p.lineTo(r.centerX() + m * 0.6f, r.centerY()); p.lineTo(r.centerX() - m * 0.5f, r.centerY() + m) }
        p.close(); c.drawPath(p, fill)
        text.clearShadowLayer()
    }

    // ---------------- Upgrade lab ----------------

    data class UpgradeRow(val name: String, val symbol: String, val level: Int, val maxLevel: Int, val cost: Int, val maxed: Boolean, val affordable: Boolean)

    fun drawUpgrades(c: Canvas, rows: List<UpgradeRow>, coins: Int) {
        fonts()
        dim(c, 200)
        backButton(c)
        coinPill(c, coins, W - 150f, 54f)
        glowText(c, "UPGRADE LAB", W / 2f, 140f, 40f, Color.WHITE, Paint.Align.CENTER, Color.rgb(255, 150, 40), maxW = W - 60f)
        caption(c, "PERMANENT BOOSTS — SPEND COINS", W / 2f, 200f, 15f, Color.rgb(150, 175, 220), Paint.Align.CENTER, maxW = W - 40f)

        rows.forEachIndexed { i, row ->
            val r = UiLayout.upgradeRow(i)
            panel(c, r, if (row.maxed) Color.rgb(120, 220, 160) else Color.rgb(110, 140, 210), alpha = 200, cut = 12f, glow = false)

            // Symbol badge: hexagon.
            val bx = r.left + 44f; val by = r.centerY(); val br = 26f
            panelPath.reset()
            for (k in 0 until 6) {
                val a = Math.toRadians(60.0 * k + 30.0)
                val hx = bx + (br * kotlin.math.cos(a)).toFloat(); val hy = by + (br * sin(a)).toFloat()
                if (k == 0) panelPath.moveTo(hx, hy) else panelPath.lineTo(hx, hy)
            }
            panelPath.close()
            fill.shader = RadialGradient(bx, by - 8f, br * 1.2f, Color.rgb(60, 100, 180), Color.rgb(18, 26, 56), Shader.TileMode.CLAMP)
            c.drawPath(panelPath, fill); fill.shader = null
            border.color = Color.rgb(130, 190, 255); border.strokeWidth = 2f; c.drawPath(panelPath, border)
            glowText(c, row.symbol, bx, by + 11f, 28f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 170, 255))

            // Name + segmented level meter.
            glowText(c, row.name, r.left + 84f, r.centerY() - 6f, 22f, Color.WHITE, Paint.Align.LEFT, Color.rgb(60, 120, 255), maxW = 190f)
            for (p in 0 until row.maxLevel) {
                val sx = r.left + 84f + p * 26f
                fill.color = if (p < row.level) Color.rgb(110, 230, 160) else Color.argb(200, 40, 48, 74)
                c.drawRect(sx, r.centerY() + 12f, sx + 21f, r.centerY() + 22f, fill)
                if (p < row.level) { fill.color = Color.argb(110, 255, 255, 255); c.drawRect(sx, r.centerY() + 12f, sx + 21f, r.centerY() + 14.5f, fill) }
            }

            // Buy button.
            val b = UiLayout.upgradeBuy(i)
            when {
                row.maxed -> buttonDisabledSmall(c, b, "MAX")
                row.affordable -> buttonSmall(c, b, "${row.cost}", Color.rgb(255, 210, 120))
                else -> buttonDisabledSmall(c, b, "${row.cost}")
            }
        }
    }

    private fun buttonSmall(c: Canvas, r: RectF, label: String, accent: Int) {
        panel(c, r, accent, alpha = 230, cut = 10f, glow = true)
        drawCoin(c, r.left + 22f, r.centerY(), 9f)
        glowText(c, label, r.centerX() + 12f, r.centerY() + 8f, 22f, Color.WHITE, Paint.Align.CENTER, accent, maxW = r.width() - 44f)
    }

    private fun buttonDisabledSmall(c: Canvas, r: RectF, label: String) {
        panel(c, r, Color.rgb(80, 86, 108), alpha = 160, cut = 10f, glow = false)
        text.textAlign = Paint.Align.CENTER; fit(text, label, 20f, r.width() - 20f); text.color = Color.rgb(130, 138, 160)
        c.drawText(label, r.centerX(), r.centerY() + 7f, text)
    }

    private fun buttonDisabled(c: Canvas, r: RectF, label: String) {
        fonts()
        panel(c, r, Color.rgb(90, 96, 118), alpha = 170, cut = 16f, glow = false)
        text.textAlign = Paint.Align.CENTER; fit(text, label, r.height() * 0.34f, r.width() - 40f); text.color = Color.rgb(150, 158, 182)
        c.drawText(label, r.centerX(), r.centerY() + r.height() * 0.14f, text)
    }

    fun drawPause(c: Canvas) {
        fonts()
        dim(c, 190)
        glowText(c, "PAUSED", W / 2f, 360f, 56f, Color.WHITE, Paint.Align.CENTER, Color.rgb(60, 140, 255), maxW = W - 80f)
        button(c, UiLayout.resumeButton, "RESUME", Color.rgb(90, 200, 255), primary = true)
        button(c, UiLayout.restartButton, "RESTART", Color.rgb(255, 140, 80))
    }

    fun drawResult(c: Canvas, victory: Boolean, score: Int, coins: Int, best: Int, newBest: Boolean, subtitle: String, retryLabel: String) {
        fonts()
        dim(c, 205)
        val title = if (victory) "VICTORY" else "SHOT DOWN"
        val accent = if (victory) Color.rgb(120, 255, 170) else Color.rgb(255, 90, 90)
        glowText(c, title, W / 2f, 270f, 56f, Color.WHITE, Paint.Align.CENTER, accent, maxW = W - 60f)
        caption(c, subtitle.uppercase(), W / 2f, 312f, 16f, accent, Paint.Align.CENTER, maxW = W - 60f)

        // Stats card.
        val card = RectF(70f, 350f, W - 70f, 540f)
        panel(c, card, accent, alpha = 200, cut = 16f, glow = false)
        caption(c, "SCORE", card.left + 26f, 398f, 15f, Color.rgb(150, 180, 230))
        glowText(c, "$score", card.right - 26f, 400f, 30f, Color.WHITE, Paint.Align.RIGHT, Color.rgb(60, 140, 255), maxW = card.width() - 150f)
        caption(c, "COINS", card.left + 26f, 450f, 15f, Color.rgb(255, 210, 140))
        text.textSize = 26f
        drawCoin(c, card.right - 26f - text.measureText("$coins") - 18f, 443f, 10f)
        glowText(c, "$coins", card.right - 26f, 452f, 26f, Color.rgb(255, 225, 130), Paint.Align.RIGHT, Color.rgb(220, 120, 20))
        if (newBest) glowText(c, "NEW BEST!", W / 2f, 510f, 24f, Color.rgb(255, 235, 130), Paint.Align.CENTER, Color.rgb(255, 120, 30), maxW = card.width() - 40f)
        else {
            caption(c, "BEST", card.left + 26f, 502f, 15f, Color.rgb(170, 190, 230))
            glowText(c, "$best", card.right - 26f, 504f, 22f, Color.rgb(200, 215, 245), Paint.Align.RIGHT, Color.rgb(60, 120, 220))
        }

        button(c, UiLayout.retryButton, retryLabel, Color.rgb(90, 200, 255), primary = true)
        button(c, UiLayout.menuButton, "MENU", Color.rgb(170, 180, 210))
    }
}
