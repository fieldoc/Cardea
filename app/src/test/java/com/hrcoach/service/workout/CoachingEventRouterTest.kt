package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEventRouterTest {

    @Test
    fun `emits connection and segment events`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 1_000f, targetHr = 140),
                HrSegment(distanceMeters = 2_000f, targetHr = 145)
            )
        )

        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 0L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = null,
            guidance = "hold",
            nowMs = 0L,
            emitEvent = { event, _ -> events += event }
        )
        router.route(
            workoutConfig = config,
            connected = false,
            distanceMeters = 1_500f,
            elapsedSeconds = 0L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = null,
            guidance = "hold",
            nowMs = 5_000L,
            emitEvent = { event, _ -> events += event }
        )

        assertTrue(events.contains(CoachingEvent.SIGNAL_LOST))
        assertTrue(events.contains(CoachingEvent.SEGMENT_CHANGE))
    }

    @Test
    fun `emits predictive warning when projected drift is confident after warmup`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val adaptiveResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off",
            hasProjectionConfidence = true
        )

        // First tick establishes zone entry; the entry reset absorbs the 60s predictive cooldown.
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 120L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = null,
            guidance = "ease off",
            nowMs = 100_000L,
            emitEvent = { event, _ -> events += event }
        )

        // After 60s in zone with drift projection — warning should fire.
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 180L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 160_001L,
            emitEvent = { event, _ -> events += event }
        )

        assertTrue(events.contains(CoachingEvent.PREDICTIVE_WARNING))
    }

    @Test
    fun `suppresses predictive warning during warmup period`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val adaptiveResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off",
            hasProjectionConfidence = true
        )

        // First 90s warmup gate — warning must NOT fire even with drift projection
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 30L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 100_000L,
            emitEvent = { event, _ -> events += event }
        )

        assertFalse(events.contains(CoachingEvent.PREDICTIVE_WARNING))
    }

    @Test
    fun `PREDICTIVE_WARNING does not fire on first zone entry for returning runners`() {
        // Regression: returning runners have adaptive projection confidence from prior sessions.
        // Without the fix, PREDICTIVE_WARNING fires on the very first IN_ZONE tick (lastPredictiveWarningTime
        // starts at 0L, so the 60s cooldown is immediately satisfied). The entry reset now absorbs the
        // cooldown on any zone entry where it was already expired — including first entry.
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val driftResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 152,
            currentPaceMinPerKm = 5.8f,
            guidance = "ease off",
            hasProjectionConfidence = true
        )

        // Runner warms up below zone for 2 minutes (past warmup gate), then enters zone for
        // the first time with drift projection confidence from prior session data.
        router.route(workoutConfig = config, connected = true, distanceMeters = 0f,
            elapsedSeconds = 120L, zoneStatus = ZoneStatus.BELOW_ZONE,
            adaptiveResult = null, guidance = "speed up", nowMs = 120_000L,
            emitEvent = { e, _ -> events += e })

        router.route(workoutConfig = config, connected = true, distanceMeters = 200f,
            elapsedSeconds = 150L, zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = driftResult, guidance = "ease off", nowMs = 150_000L,
            emitEvent = { e, _ -> events += e })

        assertFalse(
            "PREDICTIVE_WARNING must not fire on the first-ever zone entry tick",
            events.contains(CoachingEvent.PREDICTIVE_WARNING)
        )
    }

    @Test
    fun `PREDICTIVE_WARNING does not fire on zone re-entry even when projection shows drift`() {
        // Regression: the adaptive engine projects drift immediately on re-entry (the runner keeps
        // overshooting). Without the fix, PREDICTIVE_WARNING fires simultaneously with RETURN_TO_ZONE
        // on the same tick — the user hears the guidance description every time they step back in zone.
        // Fix: when the 60s cooldown was already expired on re-entry, lastPredictiveWarningTime is
        // reset to nowMs, imposing a fresh 60s grace before the next warning can fire.
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 0L)
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val driftResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off slightly",
            hasProjectionConfidence = true
        )

        // Initial zone entry at T=0 (warmup not done, cooldown not expired) — no warning.
        routeTick(router, ZoneStatus.IN_ZONE, 0L, config, events,
            elapsedSeconds = 0L)

        // Step 1: fire a PREDICTIVE_WARNING while stably in zone (past warmup + 60s cooldown).
        // lastPredictiveWarningTime = 100_000.
        routeTick(router, ZoneStatus.IN_ZONE, 100_000L, config, events,
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 120L)
        assertTrue("setup: PREDICTIVE_WARNING must fire while stably in zone",
            events.any { it.first == CoachingEvent.PREDICTIVE_WARNING })

        // Step 2: leave zone. Then return 63s later (cooldown of 60s has elapsed).
        // On re-entry the fix resets lastPredictiveWarningTime = nowMs, so PREDICTIVE_WARNING
        // must NOT fire this tick even though the 60s cooldown has expired.
        routeTick(router, ZoneStatus.ABOVE_ZONE, 101_000L, config, events, elapsedSeconds = 180L)
        val countBeforeReturn = events.count { it.first == CoachingEvent.PREDICTIVE_WARNING }

        routeTick(router, ZoneStatus.IN_ZONE, 163_000L, config, events,  // 63s after last warning
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 200L)

        assertEquals(
            "PREDICTIVE_WARNING must not fire on the zone re-entry tick — 60s grace applies",
            countBeforeReturn,
            events.count { it.first == CoachingEvent.PREDICTIVE_WARNING }
        )
    }

    @Test
    fun `PREDICTIVE_WARNING still fires for rapid oscillators after 60s elapses in zone`() {
        // The entry reset only fires when the 60s cooldown is already expired. For rapid
        // oscillators (< 60s cycles), the cooldown hasn't expired on re-entry, so the reset
        // is skipped and the timer continues running. Once the runner has been in zone for
        // 60s without the cooldown being reset, the warning fires normally.
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 0L)
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val driftResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off slightly",
            hasProjectionConfidence = true
        )

        // First zone entry at T=0; cooldown starts from 0L (no reset since cooldown not expired).
        routeTick(router, ZoneStatus.IN_ZONE, 0L, config, events, elapsedSeconds = 0L)

        // Rapid oscillations every 20s (< 60s cooldown). Each re-entry skips the reset because
        // the cooldown hasn't expired. The timer stays at 0L throughout.
        routeTick(router, ZoneStatus.ABOVE_ZONE, 10_000L, config, events, elapsedSeconds = 10L)
        routeTick(router, ZoneStatus.IN_ZONE,    30_000L, config, events, elapsedSeconds = 30L)
        routeTick(router, ZoneStatus.ABOVE_ZONE, 40_000L, config, events, elapsedSeconds = 40L)
        routeTick(router, ZoneStatus.IN_ZONE,    50_000L, config, events, elapsedSeconds = 50L)

        // After warmup (90s) with drift projection — warning fires because 60s has elapsed
        // since lastPredictiveWarningTime = 0L (no re-entry reset occurred during rapid cycles).
        routeTick(router, ZoneStatus.IN_ZONE, 95_000L, config, events,
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 95L)

        assertTrue(
            "PREDICTIVE_WARNING must still fire for rapid oscillators once 60s elapses",
            events.any { it.first == CoachingEvent.PREDICTIVE_WARNING }
        )
    }

    // ── RETURN_TO_ZONE tests ──────────────────────────────────

    private fun steadyConfig() = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)

    private fun routeTick(
        router: CoachingEventRouter,
        zone: ZoneStatus,
        nowMs: Long,
        config: WorkoutConfig = steadyConfig(),
        events: MutableList<Pair<CoachingEvent, String?>> = mutableListOf(),
        adaptiveResult: AdaptivePaceController.TickResult? = null,
        guidance: String = "some guidance",
        elapsedSeconds: Long = nowMs / 1000L
    ): MutableList<Pair<CoachingEvent, String?>> {
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 0f,
            elapsedSeconds = elapsedSeconds,
            zoneStatus = zone,
            adaptiveResult = adaptiveResult,
            guidance = guidance,
            nowMs = nowMs,
            emitEvent = { event, text -> events += event to text }
        )
        return events
    }

    @Test
    fun `RETURN_TO_ZONE does not fire on initial zone entry`() {
        val router = CoachingEventRouter()
        // Warm up below zone, then enter zone for the first time
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.BELOW_ZONE, 1_000L, events = events)
        routeTick(router, ZoneStatus.BELOW_ZONE, 2_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, 3_000L, events = events)

        assertTrue(events.none { it.first == CoachingEvent.RETURN_TO_ZONE })
    }

    @Test
    fun `RETURN_TO_ZONE fires after leaving and re-entering zone`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        // Enter zone (initial — should not fire)
        routeTick(router, ZoneStatus.IN_ZONE, 1_000L, events = events)
        // Leave zone
        routeTick(router, ZoneStatus.ABOVE_ZONE, 2_000L, events = events)
        // Re-enter zone — should fire
        routeTick(router, ZoneStatus.IN_ZONE, 33_000L, events = events)

        val returnEvents = events.filter { it.first == CoachingEvent.RETURN_TO_ZONE }
        assertEquals(1, returnEvents.size)
    }

    @Test
    fun `RETURN_TO_ZONE falls back to null when no adaptive result is present`() {
        // When adaptiveResult is null (FREE_RUN, startup, or disconnect), the confidence-gated
        // branch reduces to "null" — VoicePlayer uses its "Back in zone" fallback. Confident
        // and LOW-confidence cases are exercised in the two dedicated tests below.
        val router = CoachingEventRouter()
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1_000L, events = events)
        routeTick(router, ZoneStatus.ABOVE_ZONE, 2_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, 33_000L, events = events)

        val returnEvent = events.first { it.first == CoachingEvent.RETURN_TO_ZONE }
        assertEquals(null, returnEvent.second)
    }

    @Test
    fun `RETURN_TO_ZONE cooldown prevents rapid re-firing`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        // Initial zone entry
        routeTick(router, ZoneStatus.IN_ZONE, 1_000L, events = events)
        // Leave and re-enter (first RETURN_TO_ZONE)
        routeTick(router, ZoneStatus.ABOVE_ZONE, 2_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, 33_000L, events = events)
        assertEquals(1, events.count { it.first == CoachingEvent.RETURN_TO_ZONE })

        // Quick jitter: leave and re-enter within 30s cooldown — should NOT fire again
        routeTick(router, ZoneStatus.ABOVE_ZONE, 34_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, 35_000L, events = events)
        assertEquals(1, events.count { it.first == CoachingEvent.RETURN_TO_ZONE })

        // After cooldown expires — should fire again
        routeTick(router, ZoneStatus.ABOVE_ZONE, 64_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, 65_000L, events = events)
        assertEquals(2, events.count { it.first == CoachingEvent.RETURN_TO_ZONE })
    }

    @Test
    fun `RETURN_TO_ZONE does not fire from NO_DATA to IN_ZONE`() {
        val router = CoachingEventRouter()
        // First tick ever: NO_DATA -> IN_ZONE
        val events = routeTick(router, ZoneStatus.IN_ZONE, 1_000L)
        assertTrue(events.none { it.first == CoachingEvent.RETURN_TO_ZONE })
    }

    @Test
    fun `IN_ZONE_CONFIRM does not fire on zone re-entry during rapid oscillations`() {
        // Regression: when RETURN_TO_ZONE was suppressed by its 30s cooldown, lastVoiceCueTimeMs
        // stayed stale. After 3+ minutes of quick out-in oscillation (< 30s each), the baseline
        // would be >3 min old and IN_ZONE_CONFIRM would fire the moment the runner stepped back
        // into zone — heard as a guidance description ("Pace looks good") on every zone return.
        // Fix: always reset lastVoiceCueTimeMs on any zone re-entry, regardless of cooldown.
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 0L)
        val events = mutableListOf<Pair<CoachingEvent, String?>>()

        // Establish hasBeenInZone and fire RETURN_TO_ZONE at T=33s.
        // lastReturnToZoneMs=33_000, lastVoiceCueTimeMs=33_000.
        routeTick(router, ZoneStatus.IN_ZONE, nowMs = 1_000L, events = events)
        routeTick(router, ZoneStatus.ABOVE_ZONE, nowMs = 2_000L, events = events)
        routeTick(router, ZoneStatus.IN_ZONE, nowMs = 33_000L, events = events)
        assertEquals(1, events.count { it.first == CoachingEvent.RETURN_TO_ZONE })

        // Oscillate with 14s out-in cycles (< 30s RETURN_TO_ZONE cooldown) for 3+ minutes.
        // Without fix: lastVoiceCueTimeMs stays at 33_000 throughout; IN_ZONE_CONFIRM fires
        // after 180s of stale baseline on the next re-entry even though runner just arrived.
        // With fix: every re-entry resets lastVoiceCueTimeMs so the 3-min window never expires.
        var t = 34_000L
        while (t < 33_000L + 200_000L) {
            routeTick(router, ZoneStatus.ABOVE_ZONE, nowMs = t, events = events)
            routeTick(router, ZoneStatus.IN_ZONE, nowMs = t + 14_000L, events = events)
            t += 15_000L
        }

        assertEquals(
            "IN_ZONE_CONFIRM must not fire during rapid zone oscillations",
            0, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM }
        )
    }

    // ── IN_ZONE_CONFIRM gates + cadence + RETURN_TO_ZONE guidance (added 2026-04-17) ─

    private fun inZoneTick(
        slope: Float = 0f,
        hasConfidence: Boolean = true,
        projected: ZoneStatus = ZoneStatus.IN_ZONE
    ) = AdaptivePaceController.TickResult(
        zoneStatus = ZoneStatus.IN_ZONE,
        projectedZoneStatus = projected,
        predictedHr = 140,
        currentPaceMinPerKm = 6f,
        guidance = "unused",
        hasProjectionConfidence = hasConfidence,
        hrSlopeBpmPerMin = slope
    )

    @Test
    fun `IN_ZONE_CONFIRM is suppressed when HR slope is above the gate threshold`() {
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 1L)  // non-zero baseline so the router's "unset" branch doesn't fire
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1L, events = events, adaptiveResult = inZoneTick(slope = 0f))
        routeTick(router, ZoneStatus.IN_ZONE, 180_001L, events = events, adaptiveResult = inZoneTick(slope = 1.0f))
        assertEquals(0, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `IN_ZONE_CONFIRM fires when slope is calm under the gate threshold`() {
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 1L)
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1L, events = events, adaptiveResult = inZoneTick(slope = 0f))
        routeTick(router, ZoneStatus.IN_ZONE, 180_001L, events = events, adaptiveResult = inZoneTick(slope = 0.4f))
        assertEquals(1, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `IN_ZONE_CONFIRM uses STANDARD cadence - 3 min first, 5 min repeat`() {
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 1L)
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1L, events = events, adaptiveResult = inZoneTick())
        // First confirm at 3 min.
        routeTick(router, ZoneStatus.IN_ZONE, 180_001L, events = events, adaptiveResult = inZoneTick())
        assertEquals(1, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
        // At 5 min (120s later — only 2 min gap since first confirm), repeat interval not yet met.
        routeTick(router, ZoneStatus.IN_ZONE, 300_001L, events = events, adaptiveResult = inZoneTick())
        assertEquals(1, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
        // At 8 min (300s = 5 min after first confirm), second confirm fires.
        routeTick(router, ZoneStatus.IN_ZONE, 480_001L, events = events, adaptiveResult = inZoneTick())
        assertEquals(2, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `FREQUENT cadence fires every 3 min after first`() {
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 1L)
        router.confirmCadence = com.hrcoach.domain.model.ConfirmCadence.FREQUENT
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1L, events = events, adaptiveResult = inZoneTick())
        routeTick(router, ZoneStatus.IN_ZONE, 180_001L, events = events, adaptiveResult = inZoneTick())
        routeTick(router, ZoneStatus.IN_ZONE, 360_001L, events = events, adaptiveResult = inZoneTick())
        assertEquals(2, events.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `RETURN_TO_ZONE passes null guidance on LOW-confidence re-entry`() {
        // When the runner is still in calibration (hasProjectionConfidence=false), the in-zone
        // guidance is "Learning your patterns - hold steady" — not useful as re-entry content.
        // Router should pass null so VoicePlayer falls back to "Back in zone".
        val router = CoachingEventRouter()
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = false))
        routeTick(router, ZoneStatus.ABOVE_ZONE, 2_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = false))
        routeTick(router, ZoneStatus.IN_ZONE, 33_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = false), guidance = "Learning your patterns - hold steady")

        val returnEvent = events.first { it.first == CoachingEvent.RETURN_TO_ZONE }
        assertEquals(null, returnEvent.second)
    }

    @Test
    fun `RETURN_TO_ZONE passes live guidance on confident re-entry`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<Pair<CoachingEvent, String?>>()
        routeTick(router, ZoneStatus.IN_ZONE, 1_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = true))
        routeTick(router, ZoneStatus.ABOVE_ZONE, 2_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = true))
        routeTick(router, ZoneStatus.IN_ZONE, 33_000L, events = events,
            adaptiveResult = inZoneTick(hasConfidence = true), guidance = "In zone - HR falling")

        val returnEvent = events.first { it.first == CoachingEvent.RETURN_TO_ZONE }
        assertEquals("In zone - HR falling", returnEvent.second)
    }

    @Test
    fun `time-based workout emits SEGMENT_CHANGE on interval transition`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 300, targetHr = 120, label = "Warm-up"),
                HrSegment(durationSeconds = 240, targetHr = 165, label = "Interval 1")
            )
        )
        // elapsed=0: first segment, no change yet
        router.route(workoutConfig = config, connected = true, distanceMeters = 0f,
            elapsedSeconds = 0L, zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = null, guidance = "hold", nowMs = 0L,
            emitEvent = { e, _ -> events += e })
        assertTrue(CoachingEvent.SEGMENT_CHANGE !in events)

        // elapsed=300: crosses into segment 1
        router.route(workoutConfig = config, connected = true, distanceMeters = 0f,
            elapsedSeconds = 300L, zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = null, guidance = "hold", nowMs = 300_000L,
            emitEvent = { e, _ -> events += e })
        assertTrue(CoachingEvent.SEGMENT_CHANGE in events)
    }
}
