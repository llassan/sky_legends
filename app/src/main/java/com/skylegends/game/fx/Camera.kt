package com.skylegends.game.fx

import com.skylegends.game.utils.Constants
import kotlin.math.min
import kotlin.random.Random

/**
 * Trauma-based screen shake (Squirrel Eiserloh model): callers add *trauma*, and the
 * per-frame offset is proportional to trauma² so small hits barely register while big
 * explosions kick hard. Trauma decays linearly so the screen always settles.
 *
 * Also owns a global "hit flash" (white/colored full-screen alpha) and a hitstop timer
 * used for the brief freeze on impactful kills.
 */
class Camera {
    private var trauma = 0f
    var offsetX = 0f; private set
    var offsetY = 0f; private set

    // Full-screen flash (0..1 intensity, decays).
    var flash = 0f; private set
    var flashR = 255; private set
    var flashG = 255; private set
    var flashB = 255; private set

    // Hitstop: seconds of frozen gameplay for punchy impacts.
    private var hitstop = 0f

    fun addTrauma(amount: Float) {
        trauma = min(1f, trauma + amount)
    }

    fun addFlash(intensity: Float, r: Int = 255, g: Int = 255, b: Int = 255) {
        if (intensity > flash) { flash = intensity; flashR = r; flashG = g; flashB = b }
    }

    fun addHitstop(seconds: Float) {
        if (seconds > hitstop) hitstop = seconds
    }

    /** True while gameplay should be frozen this frame (still consumes the timer). */
    fun consumeHitstop(dt: Float): Boolean {
        if (hitstop > 0f) { hitstop -= dt; return true }
        return false
    }

    fun update(dt: Float) {
        if (trauma > 0f) {
            val shake = trauma * trauma
            val mag = shake * Constants.MAX_SHAKE
            offsetX = (Random.nextFloat() * 2f - 1f) * mag
            offsetY = (Random.nextFloat() * 2f - 1f) * mag
            trauma = (trauma - dt * 1.4f).coerceAtLeast(0f)
        } else {
            offsetX = 0f; offsetY = 0f
        }
        if (flash > 0f) flash = (flash - dt * 3.2f).coerceAtLeast(0f)
    }

    fun reset() {
        trauma = 0f; offsetX = 0f; offsetY = 0f; flash = 0f; hitstop = 0f
    }
}
