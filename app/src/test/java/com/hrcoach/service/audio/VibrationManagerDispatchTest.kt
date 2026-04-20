package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationManagerDispatchTest {

    @Test
    fun speed_up_routes_to_speedUp_pattern() {
        assertEquals(VibrationPattern.SPEED_UP, VibrationManager.patternFor(CoachingEvent.SPEED_UP))
    }

    @Test
    fun slow_down_routes_to_slowDown_pattern() {
        assertEquals(VibrationPattern.SLOW_DOWN, VibrationManager.patternFor(CoachingEvent.SLOW_DOWN))
    }

    @Test
    fun signal_lost_routes_to_generic_alert() {
        assertEquals(VibrationPattern.GENERIC_ALERT, VibrationManager.patternFor(CoachingEvent.SIGNAL_LOST))
    }

    @Test
    fun informational_events_route_to_none() {
        listOf(
            CoachingEvent.IN_ZONE_CONFIRM,
            CoachingEvent.KM_SPLIT,
            CoachingEvent.HALFWAY,
            CoachingEvent.WORKOUT_COMPLETE,
            CoachingEvent.RETURN_TO_ZONE,
            CoachingEvent.PREDICTIVE_WARNING,
            CoachingEvent.SEGMENT_CHANGE,
            CoachingEvent.SIGNAL_REGAINED,
        ).forEach {
            assertEquals("for $it", VibrationPattern.NONE, VibrationManager.patternFor(it))
        }
    }
}
