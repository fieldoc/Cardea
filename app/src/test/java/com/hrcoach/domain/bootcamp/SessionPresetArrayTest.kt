package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.preset.SessionPresetArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPresetArrayTest {

    @Test
    fun `PUSH_HARDER increments index within bounds`() {
        val array = SessionPresetArray.easyRunTier2()
        val baseIndex = 1
        val result = array.tune(baseIndex, TuningDirection.PUSH_HARDER)
        assertEquals(2, result)
    }

    @Test
    fun `EASE_BACK decrements index within bounds`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(1, TuningDirection.EASE_BACK)
        assertEquals(0, result)
    }

    @Test
    fun `PUSH_HARDER at ceiling returns ceiling`() {
        val array = SessionPresetArray.easyRunTier2()
        val ceiling = array.presets.size - 1
        val result = array.tune(ceiling, TuningDirection.PUSH_HARDER)
        assertEquals(ceiling, result)
    }

    @Test
    fun `EASE_BACK at floor returns 0`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(0, TuningDirection.EASE_BACK)
        assertEquals(0, result)
    }

    @Test
    fun `HOLD returns same index`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(2, TuningDirection.HOLD)
        assertEquals(2, result)
    }

    @Test
    fun `all tier 2 session arrays have at least 3 presets`() {
        listOf(
            SessionPresetArray.easyRunTier2(),
            SessionPresetArray.tempoTier2(),
            SessionPresetArray.longRunTier2()
        ).forEach { array ->
            assertTrue("${array.sessionTypeName} needs 3+ presets", array.presets.size >= 3)
        }
    }

    @Test
    fun `tempoTier2 never changes both duration and zone in a single step`() {
        val array = SessionPresetArray.tempoTier2()
        for (i in 1 until array.presets.size) {
            val prev = array.presets[i - 1]
            val curr = array.presets[i]
            val zoneChanged = prev.presetId != curr.presetId
            val durationIncreased = curr.durationMinutes > prev.durationMinutes
            assertFalse(
                "Step $i changes both zone (${prev.presetId}->${curr.presetId}) and duration (${prev.durationMinutes}->${curr.durationMinutes})",
                zoneChanged && durationIncreased
            )
        }
    }
}
