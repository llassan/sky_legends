package com.skylegends.game.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.skylegends.game.utils.Constants
import kotlin.math.sin

/** The kinds of floating power-up a destroyed enemy can drop. */
enum class PowerUpKind(val label: String, val bg: Int, val fg: Int) {
    WEAPON_MG("M", Color.rgb(255, 180, 40), Color.BLACK),
    WEAPON_SPREAD("S", Color.rgb(40, 220, 150), Color.BLACK),
    WEAPON_PLASMA("P", Color.rgb(150, 90, 255), Color.WHITE),
    SHIELD("◇", Color.rgb(80, 180, 255), Color.WHITE),
    HEALTH("+", Color.rgb(255, 80, 110), Color.WHITE)
}

/** A floating pickup that grants a weapon/shield/health effect on contact. */
class PowerUp(val kind: PowerUpKind) : Entity() {
    private var age = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        isFakeBoldText = true
    }
    private val textBounds = Rect()

    fun spawn(x: Float, y: Float) {
        pos.set(x, y)
        vel.set(0f, 90f)
        collisionRadius = 22f
        active = true
    }

    override fun update(dt: Float) {
        age += dt
        pos.x += vel.x * dt
        pos.y += vel.y * dt
        if (pos.y > Constants.GAME_HEIGHT + 40f) active = false
    }

    override fun render(canvas: Canvas) {
        val bob = sin(age * 4.0).toFloat()
        val r = 22f + bob * 2f
        // Glow halo.
        paint.color = kind.bg
        paint.alpha = 70
        canvas.drawCircle(pos.x, pos.y, r * 1.5f, paint)
        // Capsule.
        paint.alpha = 255
        canvas.drawCircle(pos.x, pos.y, r, paint)
        paint.color = Color.WHITE
        paint.alpha = 60
        canvas.drawCircle(pos.x - r * 0.3f, pos.y - r * 0.3f, r * 0.4f, paint)
        paint.alpha = 255
        // Symbol.
        textPaint.color = kind.fg
        textPaint.getTextBounds(kind.label, 0, kind.label.length, textBounds)
        canvas.drawText(kind.label, pos.x, pos.y - textBounds.exactCenterY(), textPaint)
    }
}
