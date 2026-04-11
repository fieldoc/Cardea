package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.*
import org.junit.Test

class PresetLibraryTest {

    @Test
    fun `all presets resolve to valid WorkoutConfig given maxHr 180 restHr 60`() {
        PresetLibrary.ALL.forEach { preset ->
            val config = preset.buildConfig(180, 60)
            assertNotNull("Preset ${preset.id} returned null config", config)
            when {
                config.mode == WorkoutMode.STEADY_STATE -> {
                    val hr = config.steadyStateTargetHr!!
                    assertTrue("${preset.id} steadyState HR $hr out of range", hr in 80..200)
                }
                config.isTimeBased() -> {
                    config.segments.forEach { seg ->
                        assertTrue("${preset.id} seg HR ${seg.targetHr} out of range",
                            seg.targetHr in 80..200)
                        assertTrue("${preset.id} seg must have durationSeconds",
                            seg.durationSeconds != null && seg.durationSeconds > 0)
                    }
                }
                else -> {
                    config.segments.forEach { seg ->
                        assertTrue("${preset.id} seg HR ${seg.targetHr} out of range",
                            seg.targetHr in 80..200)
                        assertNotNull("${preset.id} distance seg must have distanceMeters",
                            seg.distanceMeters)
                    }
                }
            }
        }
    }

    @Test
    fun `zone2 base - target HR uses Karvonen formula`() {
        val config = PresetLibrary.ALL.first { it.id == "zone2_base" }.buildConfig(180, 60)
        assertEquals(WorkoutMode.STEADY_STATE, config.mode)
        // Karvonen: 60 + (180-60) * 0.68 = 60 + 81.6 = 142
        assertEquals(142, config.steadyStateTargetHr)
    }

    @Test
    fun `norwegian 4x4 - has 4 work intervals of 240s each`() {
        val config = PresetLibrary.ALL.first { it.id == "norwegian_4x4" }.buildConfig(180, 60)
        assertTrue(config.isTimeBased())
        val intervals = config.segments.filter { it.durationSeconds == 240 }
        assertEquals(4, intervals.size)
        // Karvonen: 60 + 120*0.92 = 60 + 110.4 = 170
        intervals.forEach { assertTrue("Interval HR ${it.targetHr} out of range", it.targetHr in 160..180) }
    }

    @Test
    fun `half marathon prep - last segment ends at 21100 meters`() {
        val config = PresetLibrary.ALL.first { it.id == "half_marathon_prep" }.buildConfig(180, 60)
        assertFalse(config.isTimeBased())
        assertEquals(21100f, config.segments.last().distanceMeters)
    }

    @Test
    fun `zone2 with strides has strides guidance tag`() {
        val config = PresetLibrary.ALL.first { it.id == "zone2_with_strides" }.buildConfig(180, 60)
        assertEquals("strides", config.guidanceTag)
        assertEquals("Easy + Strides", config.sessionLabel)
        // Same Karvonen target as zone2_base
        assertEquals(142, config.steadyStateTargetHr)
    }

    @Test
    fun `preset IDs are unique`() {
        val ids = PresetLibrary.ALL.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun `strides_20s preset exists and has strides guidance tag`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "strides_20s" }
        assertNotNull("strides_20s preset must exist", preset)
        val config = preset!!.buildConfig(190, 60)
        assertEquals(WorkoutMode.STEADY_STATE, config.mode)
        assertEquals("strides", config.guidanceTag)
        assertNotNull(config.steadyStateTargetHr)
    }

    @Test
    fun `race_sim_5k preset exists and has distance segments`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "race_sim_5k" }
        assertNotNull("race_sim_5k preset must exist", preset)
        val config = preset!!.buildConfig(190, 60)
        assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
        assertTrue(config.segments.isNotEmpty())
        assertEquals(5000f, config.segments.last().distanceMeters!!, 100f)
    }

    @Test
    fun `race_sim_10k preset exists and has distance segments`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "race_sim_10k" }
        assertNotNull("race_sim_10k preset must exist", preset)
        val config = preset!!.buildConfig(190, 60)
        assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
        assertTrue(config.segments.isNotEmpty())
        assertEquals(10000f, config.segments.last().distanceMeters!!, 100f)
    }

    @Test
    fun `all preset IDs are unique`() {
        val ids = PresetLibrary.ALL.map { it.id }
        assertEquals("Duplicate preset IDs found", ids.size, ids.distinct().size)
    }
}
