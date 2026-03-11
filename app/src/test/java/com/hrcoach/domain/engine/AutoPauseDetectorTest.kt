package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoPauseDetectorTest {

    private lateinit var detector: AutoPauseDetector

    @Before
    fun setUp() {
        // Default thresholds: stop=0.5 m/s, resume=1.0 m/s, confirmWindow=3000ms
        detector = AutoPauseDetector()
    }

    // --- null speed ---

    @Test
    fun `null speed returns NONE and does not change state`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(null, 0L))
        assertEquals(AutoPauseEvent.NONE, detector.update(null, 10_000L))
    }

    // --- speed above stop threshold ---

    @Test
    fun `speed above stop threshold returns NONE`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(2.0f, 0L))
    }

    // --- confirmation window not yet elapsed ---

    @Test
    fun `speed below threshold but within window returns NONE`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 0L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 1_000L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 2_999L))
    }

    // --- confirmation window elapsed → PAUSED ---

    @Test
    fun `speed below threshold for full window fires PAUSED`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 1_000L)
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 3_000L))
    }

    // --- after PAUSED, continued stops return NONE ---

    @Test
    fun `after PAUSED further stopped ticks return NONE`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 4_000L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 5_000L))
    }

    // --- Schmitt trigger: resume requires higher threshold ---

    @Test
    fun `speed below resume threshold while paused does not resume`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        // 0.8 m/s is above stop (0.5) but below resume (1.0) — should NOT resume
        assertEquals(AutoPauseEvent.NONE, detector.update(0.8f, 4_000L))
    }

    @Test
    fun `speed at or above resume threshold while paused fires RESUMED`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        assertEquals(AutoPauseEvent.RESUMED, detector.update(1.0f, 4_000L))
    }

    // --- after RESUMED, returns NONE while moving ---

    @Test
    fun `after RESUMED further moving ticks return NONE`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.update(1.0f, 4_000L) // RESUMED
        assertEquals(AutoPauseEvent.NONE, detector.update(2.0f, 5_000L))
    }

    // --- can re-pause after resuming ---

    @Test
    fun `can stop again after resuming and fire PAUSED a second time`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.update(1.0f, 4_000L) // RESUMED
        detector.update(0.0f, 5_000L)
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 8_000L))
    }

    // --- speed spike resets window ---

    @Test
    fun `brief speed above stop threshold resets the confirmation window`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 1_500L)  // 1.5s stopped
        detector.update(2.0f, 2_000L)  // moved — clears stoppedSinceMs
        // Window restarts from when we stop again (4_499). 7_498 - 4_499 = 2999 < 3000
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 4_499L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 7_498L))
        // 4_499 + 3_000 = 7_499 → fires
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 7_499L))
    }

    // --- reset() ---

    @Test
    fun `reset clears paused state so stopped ticks need new confirmation window`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.reset()
        // After reset, need a full 3s window from first stopped tick
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 3_001L))  // starts window at 3_001
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 5_999L))  // only 2998ms elapsed
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 6_001L)) // 3000ms elapsed
    }
}
