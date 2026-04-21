package com.hrcoach.service.audio

import com.hrcoach.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlayerEndSummaryTest {

    @Test
    fun `km summary includes distance duration and avg hr`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 5200f,
            activeDurationSec = 1920L,
            avgHr = 152,
            unit = DistanceUnit.KM
        )
        assertEquals(
            "Workout complete. 5.2 kilometers in 32 minutes. Average heart rate 152.",
            text
        )
    }

    @Test
    fun `mile summary uses miles phrasing`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 1609f,
            activeDurationSec = 600L,
            avgHr = 140,
            unit = DistanceUnit.MI
        )
        assertTrue("should say miles: $text", text.contains("1.0 miles"))
    }

    @Test
    fun `sub-minute run rounds up to 1 minute`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 300f,
            activeDurationSec = 40L,
            avgHr = 128,
            unit = DistanceUnit.KM
        )
        assertTrue("should say 1 minute: $text", text.contains("1 minute"))
    }

    @Test
    fun `missing avg hr omits hr clause`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 3000f,
            activeDurationSec = 900L,
            avgHr = null,
            unit = DistanceUnit.KM
        )
        assertEquals("Workout complete. 3.0 kilometers in 15 minutes.", text)
    }

    @Test
    fun `zero-distance run still announces complete`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 0f,
            activeDurationSec = 0L,
            avgHr = null,
            unit = DistanceUnit.KM
        )
        assertEquals("Workout complete.", text)
    }
}
