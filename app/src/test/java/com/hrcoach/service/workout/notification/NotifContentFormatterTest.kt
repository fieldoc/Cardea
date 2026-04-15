package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifContentFormatterTest {

    private fun snapshot(
        currentHr: Int = 152,
        targetHr: Int = 145,
        zoneStatus: ZoneStatus = ZoneStatus.ABOVE_ZONE,
        elapsedSeconds: Long = 1110L, // 18:30
        isRunning: Boolean = true,
        isPaused: Boolean = false,
        isAutoPaused: Boolean = false,
        isFreeRun: Boolean = false,
        countdownSecondsRemaining: Int? = null,
    ) = WorkoutSnapshot(
        isRunning = isRunning,
        isPaused = isPaused,
        currentHr = currentHr,
        targetHr = targetHr,
        zoneStatus = zoneStatus,
        hrConnected = currentHr > 0,
        isFreeRun = isFreeRun,
        isAutoPaused = isAutoPaused,
        countdownSecondsRemaining = countdownSecondsRemaining,
        elapsedSeconds = elapsedSeconds,
    )

    private fun config(
        sessionLabel: String? = "Aerobic Tempo",
        mode: WorkoutMode = WorkoutMode.DISTANCE_PROFILE,
    ) = WorkoutConfig(mode = mode, sessionLabel = sessionLabel)

    // ----- Title -----

    @Test fun `title uses session label when present`() {
        val p = NotifContentFormatter.format(snapshot(), config(), totalSeconds = 2700L)
        assertEquals("Aerobic Tempo · Target 145", p.titleText)
    }

    @Test fun `title falls back to 'Workout' when sessionLabel is null`() {
        val p = NotifContentFormatter.format(
            snapshot(), config(sessionLabel = null), totalSeconds = 2700L
        )
        assertEquals("Workout · Target 145", p.titleText)
    }

    @Test fun `title is 'Free Run' for free runs`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true), config(sessionLabel = null), totalSeconds = 0L
        )
        assertEquals("Free Run", p.titleText)
    }

    @Test fun `title is 'Get HR signal…' when NO_DATA`() {
        val p = NotifContentFormatter.format(
            snapshot(zoneStatus = ZoneStatus.NO_DATA, currentHr = 0),
            config(),
            totalSeconds = 2700L
        )
        assertEquals("Get HR signal…", p.titleText)
    }

    @Test fun `title shows 'Paused' suffix when manually paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isPaused = true), config(), totalSeconds = 2700L
        )
        assertEquals("Aerobic Tempo · Paused", p.titleText)
    }

    @Test fun `title shows 'Auto-paused' suffix when auto-paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isAutoPaused = true), config(), totalSeconds = 2700L
        )
        assertEquals("Aerobic Tempo · Auto-paused", p.titleText)
    }

    @Test fun `title shows countdown during startup`() {
        val p = NotifContentFormatter.format(
            snapshot(countdownSecondsRemaining = 3), config(), totalSeconds = 2700L
        )
        assertEquals("Starting in 3…", p.titleText)
    }

    // ----- Subtitle -----

    @Test fun `subtitle shows elapsed slash total with positive delta when ABOVE`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 158, targetHr = 145, zoneStatus = ZoneStatus.ABOVE_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · +13 BPM", p.subtitleText)
    }

    @Test fun `subtitle shows negative delta when BELOW`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 138, targetHr = 145, zoneStatus = ZoneStatus.BELOW_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · -7 BPM", p.subtitleText)
    }

    @Test fun `subtitle shows 'ON TARGET' when IN_ZONE`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 145, targetHr = 145, zoneStatus = ZoneStatus.IN_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · ON TARGET", p.subtitleText)
    }

    @Test fun `subtitle shows em-dash when NO_DATA`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 0, zoneStatus = ZoneStatus.NO_DATA),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · —", p.subtitleText)
    }

    @Test fun `subtitle uses infinity symbol when free run`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true, zoneStatus = ZoneStatus.NO_DATA, currentHr = 0),
            config(sessionLabel = null), totalSeconds = 0L
        )
        assertEquals("18:30 / ∞ · —", p.subtitleText)
    }

    @Test fun `subtitle handles zero elapsed`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 0L, currentHr = 0, zoneStatus = ZoneStatus.NO_DATA),
            config(), totalSeconds = 2700L
        )
        assertEquals("00:00 / 45:00 · —", p.subtitleText)
    }

    @Test fun `subtitle handles overtime (elapsed greater than total)`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 3000L, currentHr = 145, zoneStatus = ZoneStatus.IN_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("50:00 / 45:00 · ON TARGET", p.subtitleText)
    }

    // ----- Payload flags -----

    @Test fun `payload is indeterminate when totalSeconds is zero`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true), config(sessionLabel = null), totalSeconds = 0L
        )
        assertTrue(p.isIndeterminate)
    }

    @Test fun `payload is determinate when totalSeconds greater than zero`() {
        val p = NotifContentFormatter.format(snapshot(), config(), totalSeconds = 2700L)
        assertFalse(p.isIndeterminate)
    }

    @Test fun `payload isPaused is true when snapshot isPaused`() {
        val p = NotifContentFormatter.format(
            snapshot(isPaused = true), config(), totalSeconds = 2700L
        )
        assertTrue(p.isPaused)
    }

    @Test fun `payload isPaused is true when auto-paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isAutoPaused = true), config(), totalSeconds = 2700L
        )
        assertTrue(p.isPaused)
    }

    @Test fun `payload carries elapsed and total seconds through unchanged`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 1110L), config(), totalSeconds = 2700L
        )
        assertEquals(1110L, p.elapsedSeconds)
        assertEquals(2700L, p.totalSeconds)
    }
}
