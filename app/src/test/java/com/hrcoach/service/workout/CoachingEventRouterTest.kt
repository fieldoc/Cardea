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

        // After 90s warmup gate — warning should fire
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 120L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 100_000L,
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
    fun `PREDICTIVE_WARNING does not fire on zone re-entry even when projection shows drift`() {
        // Regression: the adaptive engine projects drift immediately on re-entry (the runner keeps
        // overshooting). Without the fix, PREDICTIVE_WARNING fires simultaneously with RETURN_TO_ZONE
        // on the same tick — the user hears the guidance description every time they step back in zone.
        // Fix: lastPredictiveWarningTime is reset to nowMs on zone re-entry, imposing a 60s grace.
        val router = CoachingEventRouter()
        router.reset(workoutStartMs = 0L)
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val driftResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off slightly",
            hasProjectionConfidence = true
        )
        fun tick(zone: ZoneStatus, elapsedSec: Long, nowMs: Long,
                 adaptive: AdaptivePaceController.TickResult? = null) {
            router.route(config, connected = true, distanceMeters = 0f,
                elapsedSeconds = elapsedSec, zoneStatus = zone, adaptiveResult = adaptive,
                guidance = "ease off slightly", nowMs = nowMs,
                emitEvent = { e, _ -> events += e })
        }

        // Initial zone entry (warmup not done) — no warning expected.
        tick(ZoneStatus.IN_ZONE, 0L, 0L)

        // Step 1: fire a PREDICTIVE_WARNING while stably in zone (past warmup + 60s cooldown).
        // lastPredictiveWarningTime = 100_000.
        tick(ZoneStatus.IN_ZONE, 120L, 100_000L, driftResult)
        assertTrue("setup: PREDICTIVE_WARNING must fire while stably in zone",
            events.contains(CoachingEvent.PREDICTIVE_WARNING))

        // Step 2: leave zone. Then return 63s later (cooldown of 60s has elapsed).
        // On re-entry, the fix resets lastPredictiveWarningTime = nowMs, so PREDICTIVE_WARNING
        // must NOT fire this tick even though the 60s cooldown has expired.
        tick(ZoneStatus.ABOVE_ZONE, 180L, 101_000L)
        val countBeforeReturn = events.count { it == CoachingEvent.PREDICTIVE_WARNING }

        tick(ZoneStatus.IN_ZONE, 200L, 163_000L, driftResult)  // 63s after last warning

        assertEquals(
            "PREDICTIVE_WARNING must not fire on the zone re-entry tick — 60s grace applies",
            countBeforeReturn,
            events.count { it == CoachingEvent.PREDICTIVE_WARNING }
        )
    }

    // ── RETURN_TO_ZONE tests ──────────────────────────────────

    private fun steadyConfig() = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)

    private fun routeTick(
        router: CoachingEventRouter,
        zone: ZoneStatus,
        nowMs: Long,
        config: WorkoutConfig = steadyConfig(),
        events: MutableList<Pair<CoachingEvent, String?>> = mutableListOf()
    ): MutableList<Pair<CoachingEvent, String?>> {
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 0f,
            elapsedSeconds = nowMs / 1000,
            zoneStatus = zone,
            adaptiveResult = null,
            guidance = "some guidance",
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
    fun `RETURN_TO_ZONE passes null guidance so VoicePlayer uses default text`() {
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
