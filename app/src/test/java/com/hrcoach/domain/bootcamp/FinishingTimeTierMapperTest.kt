package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import org.junit.Assert.*
import org.junit.Test

class FinishingTimeTierMapperTest {

    // ── Bracket boundary tests ───────────────────────────────────────────────

    @Test
    fun `5K above 30 min is Easy tier`() {
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 31))
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 45))
    }

    @Test
    fun `5K between 22 and 30 is Moderate tier`() {
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 30))
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 25))
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 22))
    }

    @Test
    fun `5K below 22 min is Hard tier`() {
        assertEquals(2, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 21))
        assertEquals(2, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_5K, 15))
    }

    @Test
    fun `10K bracket boundaries`() {
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_10K, 66))
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_10K, 55))
        assertEquals(2, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.RACE_10K, 49))
    }

    @Test
    fun `Half Marathon bracket boundaries`() {
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.HALF_MARATHON, 141))
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.HALF_MARATHON, 125))
        assertEquals(2, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.HALF_MARATHON, 109))
    }

    @Test
    fun `Marathon bracket boundaries`() {
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.MARATHON, 281))
        assertEquals(1, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.MARATHON, 250))
        assertEquals(2, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.MARATHON, 219))
    }

    @Test
    fun `Cardio Health returns tier 0 and null brackets`() {
        assertNull(FinishingTimeTierMapper.bracketsFor(BootcampGoal.CARDIO_HEALTH))
        assertEquals(0, FinishingTimeTierMapper.tierFromFinishingTime(BootcampGoal.CARDIO_HEALTH, 30))
    }

    // ── isRaceGoal ───────────────────────────────────────────────────────────

    @Test
    fun `isRaceGoal returns false for Cardio Health`() {
        assertFalse(FinishingTimeTierMapper.isRaceGoal(BootcampGoal.CARDIO_HEALTH))
    }

    @Test
    fun `isRaceGoal returns true for race goals`() {
        assertTrue(FinishingTimeTierMapper.isRaceGoal(BootcampGoal.RACE_5K))
        assertTrue(FinishingTimeTierMapper.isRaceGoal(BootcampGoal.RACE_10K))
        assertTrue(FinishingTimeTierMapper.isRaceGoal(BootcampGoal.HALF_MARATHON))
        assertTrue(FinishingTimeTierMapper.isRaceGoal(BootcampGoal.MARATHON))
    }

    // ── validateTimeCommitment ───────────────────────────────────────────────

    @Test
    fun `compatible time commitment has no warning`() {
        val result = FinishingTimeTierMapper.validateTimeCommitment(
            goal = BootcampGoal.RACE_5K, tierIndex = 2, userMinutes = 35
        )
        assertTrue(result.canProceed)
        assertNull(result.warningMessage)
    }

    @Test
    fun `soft warning when user minutes below recommended but above minimum`() {
        val result = FinishingTimeTierMapper.validateTimeCommitment(
            goal = BootcampGoal.RACE_5K, tierIndex = 2, userMinutes = 25
        )
        assertTrue(result.canProceed)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage!!.contains("35 min"))
    }

    @Test
    fun `hard block when below absolute minimum`() {
        val result = FinishingTimeTierMapper.validateTimeCommitment(
            goal = BootcampGoal.RACE_5K, tierIndex = 1, userMinutes = 12
        )
        assertFalse(result.canProceed)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage!!.contains("15 min"))
    }

    // ── formatTime ───────────────────────────────────────────────────────────

    @Test
    fun `formatTime formats minutes only`() {
        assertEquals("28m", FinishingTimeTierMapper.formatTime(28))
    }

    @Test
    fun `formatTime formats hours and minutes`() {
        assertEquals("2h 30m", FinishingTimeTierMapper.formatTime(150))
    }

    @Test
    fun `formatTime formats exact hours`() {
        assertEquals("3h", FinishingTimeTierMapper.formatTime(180))
    }

    // ── recommendedRunMinutes ────────────────────────────────────────────────

    @Test
    fun `recommended run minutes matches bracket data`() {
        assertEquals(25, FinishingTimeTierMapper.recommendedRunMinutes(BootcampGoal.RACE_5K, 0))
        assertEquals(30, FinishingTimeTierMapper.recommendedRunMinutes(BootcampGoal.RACE_5K, 1))
        assertEquals(35, FinishingTimeTierMapper.recommendedRunMinutes(BootcampGoal.RACE_5K, 2))
    }

    @Test
    fun `recommended run minutes falls back to suggestedMinMinutes for Cardio Health`() {
        assertEquals(
            BootcampGoal.CARDIO_HEALTH.suggestedMinMinutes,
            FinishingTimeTierMapper.recommendedRunMinutes(BootcampGoal.CARDIO_HEALTH, 0)
        )
    }
}
