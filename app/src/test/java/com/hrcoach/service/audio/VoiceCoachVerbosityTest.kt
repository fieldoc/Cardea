package com.hrcoach.service.audio

import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCoachVerbosityTest {

    @Test
    fun `MINIMAL includes RETURN_TO_ZONE`() {
        assertEquals(R.raw.voice_return_to_zone,
            VoiceCoach.minimalResForEvent(CoachingEvent.RETURN_TO_ZONE))
    }

    @Test
    fun `MINIMAL includes PREDICTIVE_WARNING`() {
        assertEquals(R.raw.voice_predictive_warning,
            VoiceCoach.minimalResForEvent(CoachingEvent.PREDICTIVE_WARNING))
    }

    @Test
    fun `MINIMAL includes SPEED_UP`() {
        assertEquals(R.raw.voice_speed_up,
            VoiceCoach.minimalResForEvent(CoachingEvent.SPEED_UP))
    }

    @Test
    fun `MINIMAL includes SLOW_DOWN`() {
        assertEquals(R.raw.voice_slow_down,
            VoiceCoach.minimalResForEvent(CoachingEvent.SLOW_DOWN))
    }

    @Test
    fun `MINIMAL includes SEGMENT_CHANGE`() {
        assertEquals(R.raw.voice_segment_change,
            VoiceCoach.minimalResForEvent(CoachingEvent.SEGMENT_CHANGE))
    }

    @Test
    fun `MINIMAL includes SIGNAL_LOST`() {
        assertEquals(R.raw.voice_signal_lost,
            VoiceCoach.minimalResForEvent(CoachingEvent.SIGNAL_LOST))
    }

    @Test
    fun `MINIMAL does not include SIGNAL_REGAINED`() {
        assertNull(VoiceCoach.minimalResForEvent(CoachingEvent.SIGNAL_REGAINED))
    }

    @Test
    fun `MINIMAL does not include KM_SPLIT`() {
        assertNull(VoiceCoach.minimalResForEvent(CoachingEvent.KM_SPLIT))
    }

    @Test
    fun `MINIMAL does not include HALFWAY`() {
        assertNull(VoiceCoach.minimalResForEvent(CoachingEvent.HALFWAY))
    }

    @Test
    fun `MINIMAL does not include WORKOUT_COMPLETE`() {
        assertNull(VoiceCoach.minimalResForEvent(CoachingEvent.WORKOUT_COMPLETE))
    }

    @Test
    fun `MINIMAL does not include IN_ZONE_CONFIRM`() {
        assertNull(VoiceCoach.minimalResForEvent(CoachingEvent.IN_ZONE_CONFIRM))
    }
}
