package com.skylegends.game.ui

import android.graphics.RectF
import com.skylegends.game.utils.Constants

/**
 * Virtual-space rectangles for interactive UI. Shared by [Hud] (drawing) and GameView
 * (hit-testing) so a button's look and its tap target can never drift apart.
 */
object UiLayout {
    private val W = Constants.GAME_WIDTH
    private val H = Constants.GAME_HEIGHT
    private const val BTN_W = 320f
    private const val BTN_H = 82f
    private val cx = W / 2f

    // In-game.
    val pauseButton = RectF(W - 78f, 30f, W - 26f, 82f)
    val abilityButton = RectF(W - 128f, H - 176f, W - 32f, H - 80f)

    // Pause menu.
    val resumeButton = RectF(cx - BTN_W / 2, 460f, cx + BTN_W / 2, 460f + BTN_H)
    val restartButton = RectF(cx - BTN_W / 2, 570f, cx + BTN_W / 2, 570f + BTN_H)

    // Result (victory/defeat) menu.
    val retryButton = RectF(cx - BTN_W / 2, 600f, cx + BTN_W / 2, 600f + BTN_H)
    val menuButton = RectF(cx - BTN_W / 2, 710f, cx + BTN_W / 2, 710f + BTN_H)

    // Main menu.
    val playButton = RectF(cx - BTN_W / 2, 470f, cx + BTN_W / 2, 470f + 90f)
    val hangarButton = RectF(cx - BTN_W / 2, 582f, cx + BTN_W / 2, 582f + 72f)
    val upgradeButton = RectF(cx - BTN_W / 2, 676f, cx + BTN_W / 2, 676f + 72f)
    val musicToggle = RectF(24f, 28f, 74f, 78f)
    val sfxToggle = RectF(86f, 28f, 136f, 78f)

    // Shared back button (hangar / upgrade screens).
    val backButton = RectF(24f, 30f, 132f, 84f)

    // Hangar.
    val hangarPrev = RectF(14f, 430f, 96f, 560f)
    val hangarNext = RectF(W - 96f, 430f, W - 14f, 560f)
    val hangarAction = RectF(cx - 160f, 800f, cx + 160f, 884f)

    // Upgrade lab rows.
    const val UP_TOP = 250f
    const val UP_ROW_H = 100f
    fun upgradeRow(i: Int) = RectF(28f, UP_TOP + i * UP_ROW_H, W - 28f, UP_TOP + i * UP_ROW_H + 88f)
    fun upgradeBuy(i: Int): RectF {
        val top = UP_TOP + i * UP_ROW_H
        return RectF(W - 152f, top + 16f, W - 40f, top + 72f)
    }

    fun contains(r: RectF, x: Float, y: Float) = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
}
