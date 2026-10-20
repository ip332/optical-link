package com.automotivesystemsconsulting.opticallink.time

import android.os.SystemClock

/**
 * Interface representing a monotonic clock source for precise symbol timing schedules.
 */
interface MonotonicClock {
    /**
     * Returns current monotonic time in nanoseconds.
     */
    fun nanoTime(): Long

    /**
     * Suspends for the specified duration in milliseconds, advancing clock time in test implementations.
     */
    suspend fun delay(millis: Long)
}

/**
 * Default production monotonic clock using [SystemClock.elapsedRealtimeNanos] and [kotlinx.coroutines.delay].
 */
object SystemMonotonicClock : MonotonicClock {
    override fun nanoTime(): Long = SystemClock.elapsedRealtimeNanos()

    override suspend fun delay(millis: Long) {
        kotlinx.coroutines.delay(millis)
    }
}

/**
 * Test monotonic clock implementation allowing deterministic time advance in unit tests.
 */
class TestMonotonicClock(
    initialNanos: Long = 0L
) : MonotonicClock {
    private var currentNanos: Long = initialNanos

    override fun nanoTime(): Long = currentNanos

    override suspend fun delay(millis: Long) {
        currentNanos += millis * 1_000_000L
        kotlinx.coroutines.delay(millis)
    }

    fun advanceByNanos(nanos: Long) {
        require(nanos >= 0) { "Cannot advance clock backwards" }
        currentNanos += nanos
    }

    fun advanceByMillis(millis: Long) {
        advanceByNanos(millis * 1_000_000L)
    }
}
