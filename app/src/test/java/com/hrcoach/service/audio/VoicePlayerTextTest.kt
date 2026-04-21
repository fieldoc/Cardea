package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlayerVerbosityFilterTest {

    @Test
    fun `OFF blocks all CoachingEvent entries`() {
        CoachingEvent.entries.forEach { event ->
            assertFalse(
                "OFF should block $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.OFF)
            )
        }
    }

    @Test
    fun `MINIMAL allows SPEED_UP`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.SPEED_UP, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows SLOW_DOWN`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.SLOW_DOWN, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows RETURN_TO_ZONE`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.RETURN_TO_ZONE, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows PREDICTIVE_WARNING`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.PREDICTIVE_WARNING, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows SEGMENT_CHANGE`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.SEGMENT_CHANGE, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows SIGNAL_LOST`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.SIGNAL_LOST, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL allows SIGNAL_REGAINED`() {
        assertTrue(VoicePlayer.shouldSpeak(CoachingEvent.SIGNAL_REGAINED, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL blocks KM_SPLIT`() {
        assertFalse(VoicePlayer.shouldSpeak(CoachingEvent.KM_SPLIT, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL blocks HALFWAY`() {
        assertFalse(VoicePlayer.shouldSpeak(CoachingEvent.HALFWAY, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL blocks WORKOUT_COMPLETE`() {
        assertFalse(VoicePlayer.shouldSpeak(CoachingEvent.WORKOUT_COMPLETE, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `MINIMAL blocks IN_ZONE_CONFIRM`() {
        assertFalse(VoicePlayer.shouldSpeak(CoachingEvent.IN_ZONE_CONFIRM, VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `FULL allows all events`() {
        CoachingEvent.entries.forEach { event ->
            assertTrue(
                "FULL should allow $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.FULL)
            )
        }
    }
}

class VoicePlayerKmSplitTextTest {

    @Test
    fun `STEADY_STATE returns kilometer number only`() {
        assertEquals(
            "Kilometer 5",
            VoicePlayer.kmSplitText(5, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `DISTANCE_PROFILE returns kilometer number only`() {
        assertEquals(
            "Kilometer 10",
            VoicePlayer.kmSplitText(10, WorkoutMode.DISTANCE_PROFILE)
        )
    }

    @Test
    fun `FREE_RUN with pace includes pace with seconds unit`() {
        assertEquals(
            "Kilometer 5. Pace: 5 minutes 40 seconds.",
            VoicePlayer.kmSplitText(5, WorkoutMode.FREE_RUN, 5.67f)
        )
    }

    @Test
    fun `FREE_RUN without pace returns kilometer only`() {
        assertEquals(
            "Kilometer 3",
            VoicePlayer.kmSplitText(3, WorkoutMode.FREE_RUN)
        )
    }

    @Test
    fun `exact minutes pace omits zero seconds`() {
        assertEquals(
            "Kilometer 1. Pace: 6 minutes.",
            VoicePlayer.kmSplitText(1, WorkoutMode.FREE_RUN, 6.0f)
        )
    }
}

class VoicePlayerEventTextTest {

    @Test
    fun `SPEED_UP with guidance uses guidance text`() {
        assertEquals(
            "Pick it up a bit",
            VoicePlayer.eventText(CoachingEvent.SPEED_UP, "Pick it up a bit", WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `SLOW_DOWN with null guidance uses fallback`() {
        assertEquals(
            "Slow down",
            VoicePlayer.eventText(CoachingEvent.SLOW_DOWN, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `RETURN_TO_ZONE with guidance uses guidance`() {
        assertEquals(
            "Back in the zone",
            VoicePlayer.eventText(CoachingEvent.RETURN_TO_ZONE, "Back in the zone", WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `HALFWAY returns fixed text`() {
        assertEquals(
            "Halfway",
            VoicePlayer.eventText(CoachingEvent.HALFWAY, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `WORKOUT_COMPLETE returns fixed text`() {
        assertEquals(
            "Workout complete",
            VoicePlayer.eventText(CoachingEvent.WORKOUT_COMPLETE, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `IN_ZONE_CONFIRM returns fixed text`() {
        assertEquals(
            "In zone",
            VoicePlayer.eventText(CoachingEvent.IN_ZONE_CONFIRM, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `SIGNAL_LOST returns fixed text`() {
        assertEquals(
            "Heart-rate signal lost",
            VoicePlayer.eventText(CoachingEvent.SIGNAL_LOST, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `SIGNAL_REGAINED returns fixed text`() {
        assertEquals(
            "Heart-rate signal back",
            VoicePlayer.eventText(CoachingEvent.SIGNAL_REGAINED, null, WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `SEGMENT_CHANGE returns fixed text`() {
        assertEquals(
            "Next interval",
            VoicePlayer.eventText(CoachingEvent.SEGMENT_CHANGE, null, WorkoutMode.DISTANCE_PROFILE)
        )
    }

    @Test
    fun `PREDICTIVE_WARNING with guidance uses guidance text`() {
        assertEquals(
            "Heart rate rising fast",
            VoicePlayer.eventText(CoachingEvent.PREDICTIVE_WARNING, "Heart rate rising fast", WorkoutMode.STEADY_STATE)
        )
    }
}

/**
 * HR-context suffix for zone alerts ("Speed up. 9 under." / "Slow down. 12 over.").
 * Only applies to SPEED_UP / SLOW_DOWN, only on FULL verbosity, only when both current
 * and target HR are known, and only when |delta| >= 3 (smaller deltas would be noise
 * and the AlertPolicy threshold filters most of them anyway).
 */
class VoicePlayerHrContextTest {

    @Test
    fun `SPEED_UP with FULL appends BPM below target`() {
        assertEquals(
            "Speed up. 9 under.",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 141,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `SLOW_DOWN with FULL appends BPM above target`() {
        assertEquals(
            "Slow down. 12 over.",
            VoicePlayer.eventText(
                event = CoachingEvent.SLOW_DOWN,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 162,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `SPEED_UP with FULL preserves adaptive guidance before suffix`() {
        assertEquals(
            "Pick it up. 9 under.",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = "Pick it up",
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 141,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `guidance ending in period does not produce double period`() {
        assertEquals(
            "Push harder. 9 under.",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = "Push harder.",
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 141,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `MINIMAL verbosity omits HR context suffix`() {
        assertEquals(
            "Speed up",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 141,
                targetHr = 150,
                verbosity = VoiceVerbosity.MINIMAL
            )
        )
    }

    @Test
    fun `null currentHr omits suffix even on FULL`() {
        assertEquals(
            "Speed up",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = null,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `null targetHr omits suffix even on FULL`() {
        assertEquals(
            "Speed up",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 141,
                targetHr = null,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `delta of 2 BPM omits suffix as noise`() {
        assertEquals(
            "Speed up",
            VoicePlayer.eventText(
                event = CoachingEvent.SPEED_UP,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 148,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `RETURN_TO_ZONE never appends HR context`() {
        assertEquals(
            "Back in zone",
            VoicePlayer.eventText(
                event = CoachingEvent.RETURN_TO_ZONE,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 145,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `PREDICTIVE_WARNING never appends HR context`() {
        assertEquals(
            "Watch your pace",
            VoicePlayer.eventText(
                event = CoachingEvent.PREDICTIVE_WARNING,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 148,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `IN_ZONE_CONFIRM never appends HR context`() {
        assertEquals(
            "Pace looks good",
            VoicePlayer.eventText(
                event = CoachingEvent.IN_ZONE_CONFIRM,
                guidanceText = null,
                mode = WorkoutMode.STEADY_STATE,
                currentHr = 148,
                targetHr = 150,
                verbosity = VoiceVerbosity.FULL
            )
        )
    }

    @Test
    fun `existing callers without HR params still get baseline text`() {
        // Backward compatibility: existing VoicePlayer tests must keep passing.
        assertEquals(
            "Speed up",
            VoicePlayer.eventText(CoachingEvent.SPEED_UP, null, WorkoutMode.STEADY_STATE)
        )
    }
}

class VoicePlayerBriefingTextTest {

    @Test
    fun `STEADY_STATE briefing includes planned duration when set`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 142,
            plannedDurationMinutes = 51
        )
        assertEquals(
            "51 minutes run. Aim for heart rate around 142.",
            VoicePlayer.buildBriefingText(config)
        )
    }

    @Test
    fun `STEADY_STATE briefing without duration says steady state`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 142
        )
        assertEquals(
            "Steady state run. Aim for heart rate around 142.",
            VoicePlayer.buildBriefingText(config)
        )
    }

    @Test
    fun `FREE_RUN briefing includes duration when plannedDurationMinutes set`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.FREE_RUN,
            plannedDurationMinutes = 30
        )
        assertEquals(
            "30 minute run. No heart rate target. Enjoy your run.",
            VoicePlayer.buildBriefingText(config)
        )
    }

    @Test
    fun `FREE_RUN briefing without duration uses generic text`() {
        val config = WorkoutConfig(mode = WorkoutMode.FREE_RUN)
        assertEquals(
            "Free run. No heart rate target. Enjoy your run.",
            VoicePlayer.buildBriefingText(config)
        )
    }
}
