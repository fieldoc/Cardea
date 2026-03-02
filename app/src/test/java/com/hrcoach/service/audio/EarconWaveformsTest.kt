package com.hrcoach.service.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EarconWaveformsTest {

    @Test
    fun `waveforms have expected duration energy and headroom`() {
        val toleranceMs = 50f
        val cases = listOf(
            EarconWaveforms.risingArpeggio() to 400f,
            EarconWaveforms.fallingArpeggio() to 500f,
            EarconWaveforms.warmChime() to 300f,
            EarconWaveforms.doubleTap() to 250f,
            EarconWaveforms.ascendingFifth() to 200f,
            EarconWaveforms.sosClicks() to 840f,
            EarconWaveforms.brightPing() to 150f
        )

        cases.forEachIndexed { index, (samples, expectedMs) ->
            val durationMs = samples.size * 1000f / EarconWaveforms.SAMPLE_RATE_HZ
            val peak = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
            val hasSignal = samples.any { it.toInt() != 0 }

            assertTrue("waveform[$index] duration", abs(durationMs - expectedMs) <= toleranceMs)
            assertTrue("waveform[$index] non-zero", hasSignal)
            assertTrue("waveform[$index] headroom", peak < Short.MAX_VALUE.toInt())
        }
    }
}
