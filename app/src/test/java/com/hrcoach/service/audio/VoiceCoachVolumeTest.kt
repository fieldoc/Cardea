package com.hrcoach.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCoachVolumeTest {

    @Test
    fun `volume scalar 100 percent maps to 1f`() {
        assertEquals(1.0f, VoiceCoach.volumeScalarFor(100), 0.001f)
    }

    @Test
    fun `volume scalar 80 percent maps to 0_8f`() {
        assertEquals(0.8f, VoiceCoach.volumeScalarFor(80), 0.001f)
    }

    @Test
    fun `volume scalar 0 percent maps to 0f`() {
        assertEquals(0.0f, VoiceCoach.volumeScalarFor(0), 0.001f)
    }

    @Test
    fun `volume scalar clamps above 100`() {
        assertEquals(1.0f, VoiceCoach.volumeScalarFor(150), 0.001f)
    }

    @Test
    fun `volume scalar clamps below 0`() {
        assertEquals(0.0f, VoiceCoach.volumeScalarFor(-10), 0.001f)
    }
}
