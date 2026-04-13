package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
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
    fun `emits predictive warning when projected drift is confident`() {
        val router = CoachingEventRouter()
        val events = mutableListOf<CoachingEvent>()
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        val adaptiveResult = AdaptivePaceController(
            config = config,
            initialProfile = AdaptiveProfile()
        ).run {
            AdaptivePaceController.TickResult(
                zoneStatus = ZoneStatus.IN_ZONE,
                projectedZoneStatus = ZoneStatus.ABOVE_ZONE,
                predictedHr = 150,
                currentPaceMinPerKm = 6f,
                guidance = "ease off",
                hasProjectionConfidence = true
            )
        }

        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = 100f,
            elapsedSeconds = 0L,
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 100_000L,
            emitEvent = { event, _ -> events += event }
        )

        assertTrue(events.contains(CoachingEvent.PREDICTIVE_WARNING))
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
