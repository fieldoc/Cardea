package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class GapAdvisorTest {

    @Test
    fun `0-3 day gap returns ON_TRACK`() {
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 0))
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 1))
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 3))
    }

    @Test
    fun `4-7 day gap returns MINOR_SLIP`() {
        assertEquals(GapStrategy.MINOR_SLIP, GapAdvisor.assess(daysSinceLastRun = 4))
        assertEquals(GapStrategy.MINOR_SLIP, GapAdvisor.assess(daysSinceLastRun = 7))
    }

    @Test
    fun `8-14 day gap returns SHORT_BREAK`() {
        assertEquals(GapStrategy.SHORT_BREAK, GapAdvisor.assess(daysSinceLastRun = 8))
        assertEquals(GapStrategy.SHORT_BREAK, GapAdvisor.assess(daysSinceLastRun = 14))
    }

    @Test
    fun `15-28 day gap returns MEANINGFUL_BREAK`() {
        assertEquals(GapStrategy.MEANINGFUL_BREAK, GapAdvisor.assess(daysSinceLastRun = 15))
        assertEquals(GapStrategy.MEANINGFUL_BREAK, GapAdvisor.assess(daysSinceLastRun = 28))
    }

    @Test
    fun `29-60 day gap returns EXTENDED_BREAK`() {
        assertEquals(GapStrategy.EXTENDED_BREAK, GapAdvisor.assess(daysSinceLastRun = 29))
        assertEquals(GapStrategy.EXTENDED_BREAK, GapAdvisor.assess(daysSinceLastRun = 60))
    }

    @Test
    fun `61-120 day gap returns LONG_ABSENCE`() {
        assertEquals(GapStrategy.LONG_ABSENCE, GapAdvisor.assess(daysSinceLastRun = 61))
        assertEquals(GapStrategy.LONG_ABSENCE, GapAdvisor.assess(daysSinceLastRun = 120))
    }

    @Test
    fun `120+ day gap returns FULL_RESET`() {
        assertEquals(GapStrategy.FULL_RESET, GapAdvisor.assess(daysSinceLastRun = 121))
        assertEquals(GapStrategy.FULL_RESET, GapAdvisor.assess(daysSinceLastRun = 365))
    }

    @Test
    fun `ON_TRACK does not rewind phases`() {
        val action = GapAdvisor.action(GapStrategy.ON_TRACK, currentPhaseIndex = 1, currentWeekInPhase = 3)
        assertEquals(1, action.phaseIndex)
        assertEquals(3, action.weekInPhase)
        assertFalse(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `MEANINGFUL_BREAK rewinds 1 week and inserts return session`() {
        val action = GapAdvisor.action(GapStrategy.MEANINGFUL_BREAK, currentPhaseIndex = 1, currentWeekInPhase = 3)
        assertEquals(1, action.phaseIndex)
        assertEquals(2, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `MEANINGFUL_BREAK at week 0 stays at week 0`() {
        val action = GapAdvisor.action(GapStrategy.MEANINGFUL_BREAK, currentPhaseIndex = 1, currentWeekInPhase = 0)
        assertEquals(1, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
    }

    @Test
    fun `EXTENDED_BREAK rewinds to start of current phase`() {
        val action = GapAdvisor.action(GapStrategy.EXTENDED_BREAK, currentPhaseIndex = 2, currentWeekInPhase = 4)
        assertEquals(2, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `LONG_ABSENCE rewinds to base phase and requires calibration`() {
        val action = GapAdvisor.action(GapStrategy.LONG_ABSENCE, currentPhaseIndex = 2, currentWeekInPhase = 3)
        assertEquals(0, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertTrue(action.requiresCalibration)
    }

    @Test
    fun `FULL_RESET rewinds to base phase and requires calibration`() {
        val action = GapAdvisor.action(GapStrategy.FULL_RESET, currentPhaseIndex = 3, currentWeekInPhase = 1)
        assertEquals(0, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertTrue(action.requiresCalibration)
    }
}
