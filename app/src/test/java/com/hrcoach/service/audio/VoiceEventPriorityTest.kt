package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEventPriorityTest {

    @Test
    fun `SPEED_UP and SLOW_DOWN are CRITICAL`() {
        assertEquals(VoiceEventPriority.CRITICAL, VoiceEventPriority.of(CoachingEvent.SPEED_UP))
        assertEquals(VoiceEventPriority.CRITICAL, VoiceEventPriority.of(CoachingEvent.SLOW_DOWN))
    }

    @Test
    fun `SIGNAL_LOST is CRITICAL`() {
        assertEquals(VoiceEventPriority.CRITICAL, VoiceEventPriority.of(CoachingEvent.SIGNAL_LOST))
    }

    @Test
    fun `RETURN_TO_ZONE is NORMAL`() {
        assertEquals(VoiceEventPriority.NORMAL, VoiceEventPriority.of(CoachingEvent.RETURN_TO_ZONE))
    }

    @Test
    fun `SEGMENT_CHANGE is NORMAL`() {
        assertEquals(VoiceEventPriority.NORMAL, VoiceEventPriority.of(CoachingEvent.SEGMENT_CHANGE))
    }

    @Test
    fun `KM_SPLIT is INFORMATIONAL`() {
        assertEquals(VoiceEventPriority.INFORMATIONAL, VoiceEventPriority.of(CoachingEvent.KM_SPLIT))
    }

    @Test
    fun `HALFWAY WORKOUT_COMPLETE IN_ZONE_CONFIRM are INFORMATIONAL`() {
        assertEquals(VoiceEventPriority.INFORMATIONAL, VoiceEventPriority.of(CoachingEvent.HALFWAY))
        assertEquals(VoiceEventPriority.INFORMATIONAL, VoiceEventPriority.of(CoachingEvent.WORKOUT_COMPLETE))
        assertEquals(VoiceEventPriority.INFORMATIONAL, VoiceEventPriority.of(CoachingEvent.IN_ZONE_CONFIRM))
    }

    @Test
    fun `CRITICAL has lower ordinal than INFORMATIONAL`() {
        // Lower ordinal = higher priority
        assertTrue(VoiceEventPriority.CRITICAL.ordinal < VoiceEventPriority.INFORMATIONAL.ordinal)
    }

    @Test
    fun `INFORMATIONAL does not interrupt CRITICAL`() {
        // If currently playing CRITICAL (ordinal 0), incoming INFORMATIONAL (ordinal 2) should be skipped
        val current = VoiceEventPriority.CRITICAL
        val incoming = VoiceEventPriority.INFORMATIONAL
        // Incoming ordinal > current ordinal means lower priority = should NOT interrupt
        assertTrue(incoming.ordinal > current.ordinal)
    }

    @Test
    fun `PREDICTIVE_WARNING and SIGNAL_REGAINED are NORMAL`() {
        assertEquals(VoiceEventPriority.NORMAL, VoiceEventPriority.of(CoachingEvent.PREDICTIVE_WARNING))
        assertEquals(VoiceEventPriority.NORMAL, VoiceEventPriority.of(CoachingEvent.SIGNAL_REGAINED))
    }

    @Test
    fun `ordinal order is CRITICAL lt NORMAL lt INFORMATIONAL`() {
        assertTrue(VoiceEventPriority.CRITICAL.ordinal < VoiceEventPriority.NORMAL.ordinal)
        assertTrue(VoiceEventPriority.NORMAL.ordinal < VoiceEventPriority.INFORMATIONAL.ordinal)
    }
}
