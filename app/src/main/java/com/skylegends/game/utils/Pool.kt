package com.skylegends.game.utils

/**
 * Fixed-capacity object pool. Objects are pre-allocated once; [obtain] hands back a
 * recycled instance (or null if exhausted) and [free] returns it. This is the core of
 * the game's zero-allocation hot loop — bullets and particles never trigger GC mid-fight.
 */
class Pool<T>(capacity: Int, factory: () -> T) {
    private val free = ArrayList<T>(capacity)

    init {
        repeat(capacity) { free.add(factory()) }
    }

    /** Returns a recycled instance, or null if the pool is exhausted (caller drops it). */
    fun obtain(): T? = if (free.isEmpty()) null else free.removeAt(free.size - 1)

    fun release(item: T) { free.add(item) }
}
