package com.skylegends.game

import android.graphics.Canvas
import android.view.SurfaceHolder
import com.skylegends.game.utils.Constants

/**
 * Dedicated render/update thread with a fixed-timestep accumulator. Updates run at a
 * constant 60 Hz regardless of render rate; rendering happens once per loop. A frame-skip
 * cap prevents the "spiral of death" if the device stalls (e.g. app resume).
 */
class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    @Volatile var running = false
        private set

    override fun run() {
        var lastTime = System.nanoTime()
        var accumulator = 0.0
        val frameTimeNs = Constants.FRAME_TIME_MS * 1_000_000.0

        while (running) {
            val now = System.nanoTime()
            accumulator += (now - lastTime)
            lastTime = now

            var steps = 0
            while (accumulator >= frameTimeNs && steps < Constants.MAX_FRAME_SKIP) {
                gameView.update(Constants.DELTA_TIME)
                accumulator -= frameTimeNs
                steps++
            }
            // Drop backlog we couldn't catch up on rather than snowballing.
            if (accumulator > frameTimeNs * Constants.MAX_FRAME_SKIP) accumulator = 0.0

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) { gameView.render(canvas) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas) } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    fun startThread() { running = true; start() }

    fun stopThread() {
        running = false
        try { join(1000) } catch (e: InterruptedException) { e.printStackTrace() }
    }
}
