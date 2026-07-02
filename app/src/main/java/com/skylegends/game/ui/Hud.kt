package com.skylegends.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(120, 255, 255, 255)
    }
    // Dedicated paint for the ability cooldown ring — it needs several different
    // colors/widths per frame, which previously leaked into `stroke` (shared by every
    // bordered bar) since nothing reset it afterward. Keeping it separate can't leak.
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun glowText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, glow: Int = color) {
        text.textSize = size; text.textAlign = align
        text.color = glow; text.alpha = 90
        c.drawText(s, x + 2f, y + 2f, text)
        text.alpha = 255; text.color = color
        c.drawText(s, x, y, text)
    }

    // ---------------- In-game HUD ----------------

    fun drawPlaying(
        c: Canvas, score: Int, coins: Int, hp: Float, maxHp: Float,
        shield: Float, maxShield: Float, weaponName: String, weaponLevel: Int, multiplier: Float,
        abilitySymbol: String, abilityCooldownFraction: Float, abilityReady: Boolean, overdriveActive: Boolean
    ) {
        // Score + coins (top-left).
        glowText(c, "$score", 26f, 58f, 40f, Color.WHITE, glow = Color.rgb(90, 170, 255))
        fill.color = Color.rgb(255, 200, 40)
        c.drawCircle(38f, 84f, 11f, fill)
        glowText(c, "$coins", 56f, 94f, 28f, Color.rgb(255, 220, 120))

        // Health bar (bottom-left).
        val barX = 26f; val barW = 220f; val barY = H - 44f; val barH = 20f
        drawBar(c, barX, barY, barW, barH, (hp / maxHp).coerceIn(0f, 1f),
            Color.rgb(40, 20, 24), Color.rgb(255, 70, 90), Color.rgb(255, 150, 90))
        small.textAlign = Paint.Align.LEFT; small.color = Color.WHITE
        c.drawText("HULL", barX, barY - 8f, small)

        // Shield bar (thin, above health).
        if (maxShield > 0f) {
            drawBar(c, barX, barY - 30f, barW, 10f, (shield / maxShield).coerceIn(0f, 1f),
                Color.rgb(20, 30, 44), Color.rgb(80, 180, 255), Color.rgb(150, 220, 255))
        }

        // Weapon readout (bottom-right).
        small.textAlign = Paint.Align.RIGHT; small.color = Color.rgb(200, 220, 255)
        c.drawText("$weaponName  Lv$weaponLevel", W - 26f, H - 30f, small)

        // Combo multiplier (top-center) when > 1.
        if (multiplier > 1.05f) {
            val pulse = 1f + 0.08f * sin(System.nanoTime() / 1.0e8)
            glowText(c, "x%.1f".format(multiplier), W / 2f, 60f, (36f * pulse).toFloat(),
                Color.rgb(255, 230, 120), Paint.Align.CENTER, Color.rgb(255, 140, 40))
        }

        // Overdrive banner.
        if (overdriveActive) {
            val pulse = 0.7f + 0.3f * sin(System.nanoTime() / 0.6e8).toFloat()
            glowText(c, "OVERDRIVE!", W / 2f, 110f, 30f * pulse, Color.rgb(255, 220, 80), Paint.Align.CENTER, Color.rgb(255, 120, 20))
        }

        drawAbilityButton(c, abilitySymbol, abilityCooldownFraction, abilityReady)
        drawPauseButton(c)
    }

    private fun drawAbilityButton(c: Canvas, symbol: String, cooldownFraction: Float, ready: Boolean) {
        val r = UiLayout.abilityButton
        val cx = r.centerX(); val cy = r.centerY(); val radius = r.width() / 2f

        fill.color = Color.argb(180, 20, 26, 48)
        c.drawCircle(cx, cy, radius, fill)

        // Cooldown sweep fills back in as it becomes ready.
        val oval = RectF(r)
        ring.strokeWidth = 6f
        ring.color = Color.argb(90, 90, 100, 130)
        c.drawArc(oval, -90f, 360f, false, ring)
        if (cooldownFraction < 1f) {
            ring.color = Color.rgb(120, 200, 255)
            c.drawArc(oval, -90f, 360f * cooldownFraction, false, ring)
        }

        if (ready) {
            val pulse = 0.6f + 0.4f * sin(System.nanoTime() / 0.5e8).toFloat()
            ring.color = Color.argb((120 * pulse).toInt().coerceIn(0, 255), 150, 220, 255)
            ring.strokeWidth = 3f
            c.drawCircle(cx, cy, radius + 5f, ring)
        }

        text.textAlign = Paint.Align.CENTER; text.textSize = 34f
        text.color = if (ready) Color.WHITE else Color.rgb(120, 130, 150)
        c.drawText(symbol, cx, cy + 12f, text)
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
        fill.color = Color.argb(90, 20, 30, 60)
        c.drawRoundRect(r, 10f, 10f, fill)
        fill.color = Color.WHITE
        val pw = 6f; val ph = 22f
        val cy = r.centerY()
        c.drawRect(r.centerX() - 11f, cy - ph / 2, r.centerX() - 11f + pw, cy + ph / 2, fill)
        c.drawRect(r.centerX() + 5f, cy - ph / 2, r.centerX() + 5f + pw, cy + ph / 2, fill)
    }

    fun drawBossBar(c: Canvas, name: String, frac: Float) {
        // Sits below the score/coin/pause row (which occupies roughly y 18-95) so the two
        // never overlap.
        val x = 40f; val w = W - 80f; val y = 112f
        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(255, 160, 160)
        c.drawText(name, W / 2f, y - 8f, small)
        drawBar(c, x, y, w, 16f, frac.coerceIn(0f, 1f),
            Color.rgb(40, 12, 16), Color.rgb(255, 40, 40), Color.rgb(255, 150, 60))
    }

    fun drawBossWarning(c: Canvas, t: Float) {
        // t in 0..1 fade. Red banner sweeping.
        val alpha = (sin(t * Math.PI) * 255).toInt().coerceIn(0, 255)
        fill.color = Color.argb((alpha * 0.4f).toInt(), 255, 0, 0)
        c.drawRect(0f, H * 0.42f, W, H * 0.58f, fill)
        glowText(c, "WARNING", W / 2f, H * 0.5f + 12f, 56f, Color.rgb(255, 60, 60), Paint.Align.CENTER, Color.rgb(120, 0, 0))
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

    private fun button(c: Canvas, r: RectF, label: String, accent: Int) {
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom,
            Color.argb(230, 30, 44, 88), Color.argb(230, 18, 26, 56), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18f, 18f, fill)
        fill.shader = null
        stroke.color = accent; stroke.strokeWidth = 3f
        c.drawRoundRect(r, 18f, 18f, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
        glowText(c, label, r.centerX(), r.centerY() + 12f, 34f, Color.WHITE, Paint.Align.CENTER, accent)
    }

    fun drawMenu(c: Canvas, bestScore: Int, coins: Int, musicOn: Boolean, sfxOn: Boolean, sector: Int, sectorTotal: Int, sectorName: String, time: Float) {
        val pulse = 1f + 0.03f * sin(time * 2.0).toFloat()
        glowText(c, "SKY", W / 2f, 250f, 96f * pulse, Color.rgb(120, 200, 255), Paint.Align.CENTER, Color.rgb(40, 100, 220))
        glowText(c, "LEGENDS", W / 2f, 342f, 72f * pulse, Color.WHITE, Paint.Align.CENTER, Color.rgb(255, 120, 60))

        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(180, 200, 240)
        c.drawText("BEST  $bestScore", W / 2f, 404f, small)
        small.color = Color.rgb(255, 200, 120)
        c.drawText("SECTOR $sector/$sectorTotal — $sectorName", W / 2f, 432f, small)

        // Coins (top-right) + sound toggles (top-left).
        coinPill(c, coins, W - 150f, 54f)
        toggle(c, UiLayout.musicToggle, musicOn, "♪")
        toggle(c, UiLayout.sfxToggle, sfxOn, "♫")

        button(c, UiLayout.playButton, "PLAY", Color.rgb(90, 200, 255))
        button(c, UiLayout.hangarButton, "HANGAR", Color.rgb(160, 200, 255))
        button(c, UiLayout.upgradeButton, "UPGRADE LAB", Color.rgb(255, 200, 120))

        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(120, 140, 180)
        c.drawText("Drag to fly • Auto-fire • Grab power-ups", W / 2f, H - 40f, small)
    }

    private fun coinPill(c: Canvas, coins: Int, x: Float, y: Float) {
        fill.color = Color.rgb(255, 200, 40)
        c.drawCircle(x, y, 15f, fill)
        fill.color = Color.rgb(255, 240, 150); c.drawCircle(x, y, 8f, fill)
        glowText(c, "$coins", x + 22f, y + 12f, 32f, Color.rgb(255, 225, 140), Paint.Align.LEFT, Color.rgb(180, 120, 30))
    }

    private fun toggle(c: Canvas, r: RectF, on: Boolean, glyph: String) {
        fill.color = if (on) Color.argb(200, 40, 80, 140) else Color.argb(160, 40, 44, 60)
        c.drawRoundRect(r, 12f, 12f, fill)
        stroke.color = if (on) Color.rgb(120, 200, 255) else Color.rgb(90, 90, 110)
        c.drawRoundRect(r, 12f, 12f, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
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
        fill.color = Color.argb(180, 30, 40, 70)
        c.drawRoundRect(r, 14f, 14f, fill)
        stroke.color = Color.argb(140, 200, 220, 255); c.drawRoundRect(r, 14f, 14f, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
        text.textAlign = Paint.Align.CENTER; text.textSize = 26f; text.color = Color.WHITE
        c.drawText("‹ BACK", r.centerX(), r.centerY() + 9f, text)
    }

    // ---------------- Hangar ----------------

    fun drawHangar(
        c: Canvas, spec: com.skylegends.game.aircraft.AircraftSpec, unlocked: Boolean, selected: Boolean,
        coins: Int, index: Int, count: Int
    ) {
        dim(c, 230)
        backButton(c)
        coinPill(c, coins, W - 150f, 54f)
        glowText(c, "HANGAR", W / 2f, 130f, 52f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 170, 255))

        // Ship preview (themed silhouette).
        drawShipPreview(c, spec, W / 2f, 380f)

        // Arrows.
        arrow(c, UiLayout.hangarPrev, true)
        arrow(c, UiLayout.hangarNext, false)

        glowText(c, spec.displayName, W / 2f, 510f, 46f, spec.accentColor, Paint.Align.CENTER, spec.wingColor)
        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(190, 205, 235)
        c.drawText(spec.tagline, W / 2f, 544f, small)

        // Stat bars.
        statBar(c, "ARMOR", spec.statArmor, 600f, Color.rgb(255, 120, 120))
        statBar(c, "SPEED", spec.statSpeed, 648f, Color.rgb(120, 220, 160))
        statBar(c, "POWER", spec.statFirepower, 696f, Color.rgb(255, 200, 100))
        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(150, 170, 210)
        c.drawText(spec.abilityBlurb, W / 2f, 736f, small)
        small.color = Color.rgb(255, 210, 140)
        c.drawText("${spec.defaultWeapon.displayName}  •  ${spec.abilitySymbol} ${spec.abilityName}", W / 2f, 764f, small)

        // Action button.
        val r = UiLayout.hangarAction
        when {
            selected -> { buttonDisabled(c, r, "SELECTED"); }
            unlocked -> button(c, r, "SELECT", Color.rgb(120, 255, 170))
            coins >= spec.cost -> button(c, r, "UNLOCK  ${spec.cost}", Color.rgb(255, 210, 120))
            else -> buttonDisabled(c, r, "NEED ${spec.cost}")
        }

        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(110, 130, 170)
        c.drawText("${index + 1} / $count", W / 2f, 792f, small)
    }

    private fun drawShipPreview(c: Canvas, spec: com.skylegends.game.aircraft.AircraftSpec, cx: Float, cy: Float) {
        val s = 2.2f
        fill.color = Color.argb(50, 120, 180, 255)
        c.drawCircle(cx, cy, 90f, fill)
        c.save(); c.translate(cx, cy)
        val w = 54f * s; val h = 64f * s
        val p = android.graphics.Path()
        when (spec.shape) {
            com.skylegends.game.aircraft.HullShape.DELTA -> {
                fill.color = spec.wingColor
                p.moveTo(-w / 2f, h * 0.18f); p.lineTo(-w * 0.16f, -h * 0.1f); p.lineTo(-w * 0.16f, h * 0.34f); p.close(); c.drawPath(p, fill)
                p.reset(); p.moveTo(w / 2f, h * 0.18f); p.lineTo(w * 0.16f, -h * 0.1f); p.lineTo(w * 0.16f, h * 0.34f); p.close(); c.drawPath(p, fill)
                fill.color = spec.bodyColor
                p.reset(); p.moveTo(0f, -h / 2f); p.lineTo(w * 0.2f, h * 0.3f); p.lineTo(w * 0.12f, h / 2f)
                p.lineTo(-w * 0.12f, h / 2f); p.lineTo(-w * 0.2f, h * 0.3f); p.close(); c.drawPath(p, fill)
                fill.color = spec.accentColor; c.drawCircle(0f, -h * 0.12f, w * 0.1f, fill)
                drawHardware(c, spec, w * 0.5f, h * 0.18f)
            }
            com.skylegends.game.aircraft.HullShape.ARROW -> {
                val aw = w * 0.92f; val ah = h * 1.1f
                fill.color = spec.wingColor
                p.moveTo(-aw * 0.08f, -ah * 0.10f); p.lineTo(-aw * 0.64f, ah * 0.46f)
                p.lineTo(-aw * 0.30f, ah * 0.40f); p.lineTo(-aw * 0.08f, ah * 0.14f); p.close(); c.drawPath(p, fill)
                p.reset(); p.moveTo(aw * 0.08f, -ah * 0.10f); p.lineTo(aw * 0.64f, ah * 0.46f)
                p.lineTo(aw * 0.30f, ah * 0.40f); p.lineTo(aw * 0.08f, ah * 0.14f); p.close(); c.drawPath(p, fill)
                fill.color = spec.bodyColor
                p.reset(); p.moveTo(0f, -ah * 0.58f); p.lineTo(aw * 0.09f, ah * 0.30f); p.lineTo(0f, ah * 0.5f)
                p.lineTo(-aw * 0.09f, ah * 0.30f); p.close(); c.drawPath(p, fill)
                fill.color = spec.accentColor; c.drawCircle(0f, -ah * 0.20f, aw * 0.07f, fill)
                drawHardware(c, spec, aw * 0.64f, ah * 0.44f)
            }
            com.skylegends.game.aircraft.HullShape.HEAVY -> {
                val hw = w * 1.4f; val hh = h * 0.96f
                fill.color = spec.wingColor
                c.drawRoundRect(-hw * 0.58f, -hh * 0.02f, -hw * 0.18f, hh * 0.40f, 8f, 8f, fill)
                c.drawRoundRect(hw * 0.18f, -hh * 0.02f, hw * 0.58f, hh * 0.40f, 8f, 8f, fill)
                fill.color = spec.bodyColor
                p.moveTo(0f, -hh / 2f); p.lineTo(hw * 0.24f, hh * 0.10f); p.lineTo(hw * 0.20f, hh * 0.5f)
                p.lineTo(-hw * 0.20f, hh * 0.5f); p.lineTo(-hw * 0.24f, hh * 0.10f); p.close(); c.drawPath(p, fill)
                fill.color = spec.accentColor; c.drawCircle(0f, -hh * 0.14f, hw * 0.10f, fill)
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
        small.textAlign = Paint.Align.RIGHT; small.color = Color.rgb(200, 210, 235); small.textSize = 22f
        c.drawText(label, 140f, y + 15f, small)
        drawBar(c, x, y, w, 18f, frac, Color.rgb(30, 34, 50), color, color)
        small.textSize = 24f
    }

    private fun arrow(c: Canvas, r: RectF, left: Boolean) {
        fill.color = Color.argb(150, 40, 60, 110)
        c.drawRoundRect(r, 14f, 14f, fill)
        fill.color = Color.WHITE
        val p = android.graphics.Path()
        val m = 22f
        if (left) { p.moveTo(r.centerX() + m * 0.5f, r.centerY() - m); p.lineTo(r.centerX() - m * 0.6f, r.centerY()); p.lineTo(r.centerX() + m * 0.5f, r.centerY() + m) }
        else { p.moveTo(r.centerX() - m * 0.5f, r.centerY() - m); p.lineTo(r.centerX() + m * 0.6f, r.centerY()); p.lineTo(r.centerX() - m * 0.5f, r.centerY() + m) }
        p.close(); c.drawPath(p, fill)
    }

    // ---------------- Upgrade lab ----------------

    data class UpgradeRow(val name: String, val symbol: String, val level: Int, val maxLevel: Int, val cost: Int, val maxed: Boolean, val affordable: Boolean)

    fun drawUpgrades(c: Canvas, rows: List<UpgradeRow>, coins: Int) {
        dim(c, 230)
        backButton(c)
        coinPill(c, coins, W - 150f, 54f)
        glowText(c, "UPGRADE LAB", W / 2f, 130f, 48f, Color.WHITE, Paint.Align.CENTER, Color.rgb(255, 200, 120))
        small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(150, 170, 210)
        c.drawText("Permanent boosts — spend coins", W / 2f, 200f, small)

        rows.forEachIndexed { i, row ->
            val r = UiLayout.upgradeRow(i)
            fill.color = Color.argb(180, 24, 30, 52); c.drawRoundRect(r, 14f, 14f, fill)
            stroke.color = Color.argb(90, 150, 170, 220); c.drawRoundRect(r, 14f, 14f, stroke)
            stroke.color = Color.argb(120, 255, 255, 255)

            // Symbol badge.
            fill.color = Color.argb(220, 40, 60, 110)
            c.drawCircle(r.left + 44f, r.centerY(), 26f, fill)
            text.textAlign = Paint.Align.CENTER; text.textSize = 30f; text.color = Color.WHITE
            c.drawText(row.symbol, r.left + 44f, r.centerY() + 11f, text)

            // Name + level pips.
            text.textAlign = Paint.Align.LEFT; text.textSize = 28f; text.color = Color.WHITE
            c.drawText(row.name, r.left + 84f, r.centerY() - 4f, text)
            for (p in 0 until row.maxLevel) {
                fill.color = if (p < row.level) Color.rgb(120, 220, 160) else Color.rgb(60, 66, 88)
                c.drawCircle(r.left + 88f + p * 22f, r.centerY() + 22f, 7f, fill)
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
        fill.color = Color.argb(230, 40, 60, 110); c.drawRoundRect(r, 14f, 14f, fill)
        stroke.color = accent; stroke.strokeWidth = 3f; c.drawRoundRect(r, 14f, 14f, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
        // Small coin dot + cost.
        fill.color = Color.rgb(255, 200, 40); c.drawCircle(r.left + 22f, r.centerY(), 9f, fill)
        text.textAlign = Paint.Align.CENTER; text.textSize = 26f; text.color = Color.WHITE
        c.drawText(label, r.centerX() + 12f, r.centerY() + 9f, text)
    }

    private fun buttonDisabledSmall(c: Canvas, r: RectF, label: String) {
        fill.color = Color.argb(150, 40, 44, 60); c.drawRoundRect(r, 14f, 14f, fill)
        text.textAlign = Paint.Align.CENTER; text.textSize = 24f; text.color = Color.rgb(130, 135, 155)
        c.drawText(label, r.centerX(), r.centerY() + 8f, text)
    }

    private fun buttonDisabled(c: Canvas, r: RectF, label: String) {
        fill.color = Color.argb(160, 40, 44, 60); c.drawRoundRect(r, 18f, 18f, fill)
        stroke.color = Color.argb(90, 150, 150, 170); c.drawRoundRect(r, 18f, 18f, stroke)
        stroke.color = Color.argb(120, 255, 255, 255)
        text.textAlign = Paint.Align.CENTER; text.textSize = 32f; text.color = Color.rgb(150, 155, 175)
        c.drawText(label, r.centerX(), r.centerY() + 11f, text)
    }

    fun drawPause(c: Canvas) {
        dim(c, 200)
        glowText(c, "PAUSED", W / 2f, 360f, 64f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 170, 255))
        button(c, UiLayout.resumeButton, "RESUME", Color.rgb(90, 200, 255))
        button(c, UiLayout.restartButton, "RESTART", Color.rgb(255, 140, 80))
    }

    fun drawResult(c: Canvas, victory: Boolean, score: Int, coins: Int, best: Int, newBest: Boolean, subtitle: String, retryLabel: String) {
        dim(c, 215)
        val title = if (victory) "VICTORY!" else "SHOT DOWN"
        val accent = if (victory) Color.rgb(120, 255, 170) else Color.rgb(255, 90, 90)
        glowText(c, title, W / 2f, 280f, 72f, Color.WHITE, Paint.Align.CENTER, accent)
        small.textAlign = Paint.Align.CENTER; small.color = accent
        c.drawText(subtitle, W / 2f, 320f, small)

        text.textAlign = Paint.Align.CENTER
        glowText(c, "SCORE  $score", W / 2f, 404f, 40f, Color.WHITE, Paint.Align.CENTER, Color.rgb(90, 170, 255))
        glowText(c, "COINS  $coins", W / 2f, 456f, 34f, Color.rgb(255, 220, 120), Paint.Align.CENTER, Color.rgb(255, 150, 40))
        if (newBest) glowText(c, "NEW BEST!", W / 2f, 512f, 32f, Color.rgb(255, 230, 120), Paint.Align.CENTER, Color.rgb(255, 120, 40))
        else {
            small.textAlign = Paint.Align.CENTER; small.color = Color.rgb(180, 200, 240)
            c.drawText("BEST  $best", W / 2f, 508f, small)
        }

        button(c, UiLayout.retryButton, retryLabel, Color.rgb(90, 200, 255))
        button(c, UiLayout.menuButton, "MENU", Color.rgb(200, 200, 220))
    }
}
