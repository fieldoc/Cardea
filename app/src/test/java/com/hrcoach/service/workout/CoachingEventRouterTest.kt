package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
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
            zoneStatus = ZoneStatus.IN_ZONE,
            adaptiveResult = adaptiveResult,
            guidance = "ease off",
            nowMs = 100_000L,
            emitEvent = { event, _ -> events += event }
        )

        assertTrue(events.contains(CoachingEvent.PREDICTIVE_WARNING))
    }
}
