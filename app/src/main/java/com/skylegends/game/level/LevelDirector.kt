package com.skylegends.game.level

import com.skylegends.game.bosses.BossSpec
import com.skylegends.game.utils.Constants
import kotlin.random.Random

/**
 * Plays a [Level] timeline against the running game. GameView implements [Context]; the
 * director never touches entities directly. Spawns can be staggered over time (streams,
 * swoops), so it keeps its own pending queue that [Step.WaitClear] also waits on.
 */
class LevelDirector(private val level: Level) {

    interface Context {
        fun spawnEnemy(specId: String, x: Float, y: Float)
        fun aliveEnemyCount(): Int
        fun spawnBoss(spec: BossSpec)
        fun bossActive(): Boolean
    }

    private class Pending(val specId: String, val x: Float, val y: Float, var timer: Float)

    private val pending = ArrayList<Pending>()
    private var stepIndex = 0
    private var delayRemaining = 0f
    private var bossSpawned = false
    private val rnd = Random(12345)

    var complete = false; private set
    val bossPhaseReached get() = bossSpawned

    fun update(dt: Float, ctx: Context) {
        if (complete) return

        // Drain staggered spawns.
        var i = 0
        while (i < pending.size) {
            val p = pending[i]
            p.timer -= dt
            if (p.timer <= 0f) {
                ctx.spawnEnemy(p.specId, p.x, p.y)
                pending.removeAt(i)
            } else i++
        }

        if (delayRemaining > 0f) { delayRemaining -= dt; return }

        // Process instantaneous steps until we hit a blocking one.
        while (stepIndex < level.steps.size) {
            when (val step = level.steps[stepIndex]) {
                is Step.Delay -> { delayRemaining = step.seconds; stepIndex++; return }
                is Step.Spawn -> { enqueue(step); stepIndex++ /* continue */ }
                is Step.WaitClear -> {
                    if (pending.isEmpty() && ctx.aliveEnemyCount() == 0) stepIndex++ else return
                }
                is Step.Boss -> {
                    if (!bossSpawned) { ctx.spawnBoss(step.spec); bossSpawned = true }
                    if (!ctx.bossActive()) complete = true
                    return
                }
            }
        }
        // Timeline exhausted (a level with no explicit Boss step): finish once clear.
        if (pending.isEmpty() && ctx.aliveEnemyCount() == 0) complete = true
    }

    private fun enqueue(step: Step.Spawn) {
        val w = Constants.GAME_WIDTH
        val n = step.count
        when (step.formation) {
            Formation.LINE -> for (k in 0 until n) {
                val x = w * (k + 1) / (n + 1)
                pending.add(Pending(step.specId, x, -50f, 0f))
            }
            Formation.VEE -> {
                val mid = (n - 1) / 2f
                for (k in 0 until n) {
                    val x = w * (0.15f + 0.7f * (if (n == 1) 0.5f else k / (n - 1f)))
                    val depth = kotlin.math.abs(k - mid)
                    pending.add(Pending(step.specId, x, -50f - depth * 34f, 0f))
                }
            }
            Formation.STREAM -> for (k in 0 until n) {
                val x = w * (0.32f + 0.36f * rnd.nextFloat())
                pending.add(Pending(step.specId, x, -50f, k * 0.34f))
            }
            Formation.LEFT_SWOOP -> for (k in 0 until n) {
                pending.add(Pending(step.specId, w * 0.12f, -40f, k * 0.22f))
            }
            Formation.RIGHT_SWOOP -> for (k in 0 until n) {
                pending.add(Pending(step.specId, w * 0.88f, -40f, k * 0.22f))
            }
        }
    }
}
