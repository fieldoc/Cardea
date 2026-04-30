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
        // Slope must exceed PREDICTIVE_SLOPE_GATE_BPM_PER_MIN (0.5) AND match projected-drift
        // direction. ABOVE_ZONE projection needs positive slope.
        val adaptiveResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off",
            hasProjectionConfidence = true,
            hrSlopeBpmPerMin = 1.0f
        )

        // First tick establishes zone entry; the entry reset absorbs the cooldown.
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

        // After PREDICTIVE_WARNING_COOLDOWN_MS (180s) in zone with drifting slope — warning fires.
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 300L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 280_001L,
            emitEvent = { event, _ -> events += event }
        )

        assertTrue(events.contains(CoachingEvent.PREDICTIVE_WARNING))
    }

    @Test
    fun `predictive warning respects custom warmupGraceSec`() {
        // Preset with a 600s warm-up segment → router should suppress PW for 600s, not 90s.
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val adaptiveResult = AdaptivePaceController.TickResult(
            zoneStatus = ZoneStatus.IN_ZONE,
            projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
            predictedHr = 150,
            currentPaceMinPerKm = 6f,
            guidance = "ease off",
            hasProjectionConfidence = true,
            hrSlopeBpmPerMin = 1.0f
        )

        // At t=300s with warmupGraceSec=600 → still inside grace window, suppressed.
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 1000f,
            elapsedSeconds = 300L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 300_000L,
            warmupGraceSec = 600,
            emitEvent = { event, _ -> events += event }
        )
        assertFalse(events.contains(CoachingEvent.PREDICTIVE_WARNING))
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
        // After a PW fires and the runner drops out of zone, returning within the cooldown
        // window must not immediately re-fire PW. With the 2026-04-22 rearm gate, re-entry
        // rearms predictiveArmed but the cooldown itself still blocks — both paths verified here.
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
            hasProjectionConfidence = true,
            hrSlopeBpmPerMin = 1.0f  // positive slope matches ABOVE projection
        )

        // Initial zone entry at T=0 (warmup not done, cooldown not expired) — no warning.
        routeTick(router, ZoneStatus.IN_ZONE, 0L, config, events, elapsedSeconds = 0L)

        // Step 1: fire a PREDICTIVE_WARNING while stably in zone past warmup + cooldown.
        routeTick(router, ZoneStatus.IN_ZONE, 200_000L, config, events,
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 200L)
        assertTrue("setup: PREDICTIVE_WARNING must fire while stably in zone",
            events.any { it.first == CoachingEvent.PREDICTIVE_WARNING })

        // Step 2: leave zone. Then return 63s later (well within the 180s cooldown).
        // PW must NOT fire on re-entry — cooldown alone blocks it here.
        routeTick(router, ZoneStatus.ABOVE_ZONE, 201_000L, config, events, elapsedSeconds = 201L)
        val countBeforeReturn = events.count { it.first == CoachingEvent.PREDICTIVE_WARNING }

        routeTick(router, ZoneStatus.IN_ZONE, 263_000L, config, events,
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 263L)

        assertEquals(
            "PREDICTIVE_WARNING must not fire on the zone re-entry tick — cooldown applies",
            countBeforeReturn,
            events.count { it.first == CoachingEvent.PREDICTIVE_WARNING }
        )
    }

    @Test
    fun `PREDICTIVE_WARNING still fires for rapid oscillators after cooldown elapses in zone`() {
        // Rapid oscillators (cycles << cooldown) don't trigger the entry reset — the cooldown
        // simply keeps running. Once the full cooldown elapses, PW fires normally (given slope).
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
            hasProjectionConfidence = true,
            hrSlopeBpmPerMin = 1.0f
        )

        // First zone entry at T=0; cooldown starts from 0L (no reset since cooldown not expired).
        routeTick(router, ZoneStatus.IN_ZONE, 0L, config, events, elapsedSeconds = 0L)

        // Rapid oscillations (each cycle << cooldown). Each re-entry skips the reset because
        // the cooldown hasn't expired. Timer stays at 0L throughout.
        routeTick(router, ZoneStatus.ABOVE_ZONE, 10_000L, config, events, elapsedSeconds = 10L)
        routeTick(router, ZoneStatus.IN_ZONE,    30_000L, config, events, elapsedSeconds = 30L)
        routeTick(router, ZoneStatus.ABOVE_ZONE, 40_000L, config, events, elapsedSeconds = 40L)
        routeTick(router, ZoneStatus.IN_ZONE,    50_000L, config, events, elapsedSeconds = 50L)

        // After warmup (90s) + full cooldown (180s) since lastPredictiveWarningTime=0,
        // with slope matching drift — warning fires.
        routeTick(router, ZoneStatus.IN_ZONE, 200_000L, config, events,
            adaptiveResult = driftResult, guidance = "ease off slightly", elapsedSeconds = 200L)

        assertTrue(
            "PREDICTIVE_WARNING must fire once the full cooldown has elapsed in zone",
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
        // 2.0 > IN_ZONE_CONFIRM_SLOPE_GATE_BPM_PER_MIN (1.5) — gate engaged.
        routeTick(router, ZoneStatus.IN_ZONE, 180_001L, events = events, adaptiveResult = inZoneTick(slope = 2.0f))
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
