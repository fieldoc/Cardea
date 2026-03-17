package com.hrcoach.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPauseElapsedTest {

    private fun calcElapsed(startMs: Long, nowMs: Long, totalPausedMs: Long): Long =
        if (startMs > 0L) ((nowMs - startMs - totalPausedMs).coerceAtLeast(0L)) / 1000L else 0L

    @Test
    fun elapsedIgnoresPausedWallTime() {
        val start = 1L
        val now = 300_001L
        val paused = 60_000L

        assertEquals(240L, calcElapsed(start, now, paused))
    }

    @Test
    fun elapsedWithoutAnyPauseEqualsWallTime() {
        assertEquals(300L, calcElapsed(1L, 300_001L, 0L))
    }

    @Test
    fun elapsedBeforeStartIsZero() {
        assertEquals(0L, calcElapsed(0L, 100_000L, 0L))
    }
}
