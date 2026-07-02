package com.skylegends.game.utils

/**
 * Global tuning for Sky Legends.
 *
 * The game is rendered at a fixed virtual *width*; height adapts to the device's real
 * aspect ratio (see [com.skylegends.game.MainActivity], which sets [GAME_HEIGHT] once at
 * startup before anything else is constructed) so the play field fills the whole screen —
 * taller devices simply get more vertical play space — instead of letterboxing to a fixed
 * 9:16 canvas. All gameplay math is in virtual units, so behaviour is still resolution-
 * independent; only the *amount* of vertical world varies.
 */
object Constants {
    const val GAME_WIDTH = 540f
    /** Set once from the real screen aspect ratio before [GameView] is constructed. */
    var GAME_HEIGHT = 960f

    // Timing — fixed timestep.
    const val TARGET_FPS = 60
    const val FRAME_TIME_MS = 1000.0 / TARGET_FPS
    const val DELTA_TIME = 1f / TARGET_FPS
    // Guard against spiral-of-death after a long stall (e.g. app resume).
    const val MAX_FRAME_SKIP = 5

    // Player.
    const val PLAYER_WIDTH = 54f
    const val PLAYER_HEIGHT = 64f
    const val PLAYER_MAX_HP = 100f
    const val PLAYER_START_Y_FRAC = 0.78f      // resting height as fraction of screen
    const val PLAYER_FOLLOW = 22f              // higher = snappier drag-follow
    const val PLAYER_MAX_TILT = 0.42f          // radians of bank at full lateral speed
    const val PLAYER_INVULN_TIME = 1.4f        // i-frames after taking a hit (s)

    // Bullets.
    const val MAX_BULLETS = 512
    const val MAX_PARTICLES = 2048

    // Scrolling / world.
    const val SCROLL_SPEED = 130f              // background scroll (virtual px/s)

    // Combo.
    const val COMBO_WINDOW = 2.2f              // seconds before combo decays

    // Boss.
    const val BOSS_ENTRANCE_TIME = 2.6f

    // Screen shake caps.
    const val MAX_SHAKE = 26f
}
