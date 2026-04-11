package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import org.junit.Assert.*
import org.junit.Test

class PhaseEngineTest {

    @Test
    fun `currentPhase returns correct phase from arc`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 0)
        assertEquals(TrainingPhase.BASE, engine.currentPhase)
    }

    @Test
    fun `currentPhase at index 2 for marathon returns PEAK`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 2, weekInPhase = 0)
        assertEquals(TrainingPhase.PEAK, engine.currentPhase)
    }

    @Test
    fun `totalWeeks sums midpoints of all phase ranges`() {
        val engine = PhaseEngine(goal = BootcampGoal.RACE_5K, phaseIndex = 0, weekInPhase = 0)
        // BASE(3..6)=4 + BUILD(4..6)=5 + PEAK(2..3)=2 + TAPER(1..2)=1 = 12
        assertEquals(12, engine.totalWeeks)
    }

    @Test
    fun `absoluteWeek computes correctly`() {
        // Phase 0 (BASE, midpoint 4 weeks), weekInPhase=3 -> absolute week = 4
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 3)
        assertEquals(4, engine.absoluteWeek)
    }

    @Test
    fun `planCurrentWeek delegates to SessionSelector`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.CARDIO_HEALTH,
            phaseIndex = 0,
            weekInPhase = 0,
            runsPerWeek = 3,
            targetMinutes = 25
        )
        val sessions = engine.planCurrentWeek()
        assertEquals(3, sessions.size)
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `shouldAdvancePhase returns true when week exceeds phase midpoint`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.MARATHON,
            phaseIndex = 0,  // BASE, midpoint = 4
            weekInPhase = 4
        )
        assertTrue(engine.shouldAdvancePhase())
    }

    @Test
    fun `shouldAdvancePhase returns false when in middle of phase`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.MARATHON,
            phaseIndex = 0,  // BASE, midpoint = 4
            weekInPhase = 2
        )
        assertFalse(engine.shouldAdvancePhase())
    }

    @Test
    fun `advancePhase increments phase index and resets week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 4)
        val next = engine.advancePhase()
        assertNotNull(next)
        assertEquals(1, next!!.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `advancePhase at EVERGREEN wraps to itself`() {
        // CARDIO_HEALTH: phaseArc = [BASE, EVERGREEN]. EVERGREEN midpoint=4. weekInPhase=4 triggers advance.
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 4)
        assertTrue(engine.shouldAdvancePhase())
        val next = engine.advancePhase()
        assertNotNull("EVERGREEN should wrap", next)
        assertEquals("Should stay at EVERGREEN phase index", 1, next!!.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `advancePhase returns null when final phase is complete for race goals`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.RACE_5K,
            phaseIndex = 3, // TAPER (last phase for 5K)
            weekInPhase = 1 // past midpoint
        )
        assertTrue(engine.shouldAdvancePhase())
        assertNull("Should return null at end of arc, not wrap", engine.advancePhase())
    }

    @Test
    fun `advancePhase wraps EVERGREEN for CARDIO_HEALTH`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.CARDIO_HEALTH,
            phaseIndex = 1, // EVERGREEN (last phase for cardio health)
            weekInPhase = 5
        )
        assertTrue(engine.shouldAdvancePhase())
        val next = engine.advancePhase()
        assertNotNull("EVERGREEN should wrap", next)
        assertEquals("Should stay in EVERGREEN", 1, next!!.phaseIndex)
    }

    @Test
    fun `isRecoveryWeek triggers on 2-on-1-off pattern`() {
        // weekInPhase=2 (0-indexed) means the 3rd week — recovery week
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertTrue(engine.isRecoveryWeek())
    }

    @Test
    fun `isRecoveryWeek is false for buildup weeks`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertFalse(engine.isRecoveryWeek())
    }

    @Test
    fun `weeksUntilNextRecovery returns zero during recovery week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertEquals(0, engine.weeksUntilNextRecovery())
    }

    @Test
    fun `weeksUntilNextRecovery counts down to next recovery week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertEquals(2, engine.weeksUntilNextRecovery())
    }

    @Test
    fun `lookaheadWeeks returns requested number of projected weeks`() {
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 0, weekInPhase = 0)
        val lookahead = engine.lookaheadWeeks(2)
        assertEquals(2, lookahead.size)
        assertTrue(lookahead.all { it.sessions.isNotEmpty() })
    }

    @Test
    fun `planCurrentWeek passes tierIndex to SessionSelector`() {
        // RACE_5K (tier=2) with tierIndex=0 → effectiveTier=1 → baseAerobicWeek (no tempo/interval)
        val engine = PhaseEngine(
            goal = BootcampGoal.RACE_5K,
            phaseIndex = 1, // BUILD phase
            weekInPhase = 0,
            runsPerWeek = 4,
            targetMinutes = 30
        )
        val sessions = engine.planCurrentWeek(tierIndex = 0)
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `lookaheadWeeks forwards tierIndex`() {
        // RACE_5K with tierIndex=0 → all weeks should be aerobic-only
        val engine = PhaseEngine(
            goal = BootcampGoal.RACE_5K,
            phaseIndex = 1, // BUILD phase
            weekInPhase = 0,
            runsPerWeek = 4,
            targetMinutes = 30
        )
        val lookahead = engine.lookaheadWeeks(2, tierIndex = 0)
        assertEquals(2, lookahead.size)
        for (week in lookahead) {
            assertTrue(week.sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
        }
    }

    // --- Fitness-aware recovery week tests ---

    @Test
    fun `EASE_BACK triggers recovery every 2nd week`() {
        // weekInPhase=1 → (1+1)%2==0 → true
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 1)
        assertTrue(engine.isRecoveryWeek(TuningDirection.EASE_BACK))
        // weekInPhase=2 → (2+1)%2==1 → false
        val engine2 = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertFalse(engine2.isRecoveryWeek(TuningDirection.EASE_BACK))
    }

    @Test
    fun `PUSH_HARDER defers recovery to every 4th week`() {
        // weekInPhase=2 → (2+1)%4==3 → false (would be recovery under default)
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertFalse(engine.isRecoveryWeek(TuningDirection.PUSH_HARDER))
        // weekInPhase=3 → (3+1)%4==0 → true
        val engine2 = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 3)
        assertTrue(engine2.isRecoveryWeek(TuningDirection.PUSH_HARDER))
    }

    @Test
    fun `week 0 is never recovery regardless of tuning direction`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertFalse(engine.isRecoveryWeek())
        assertFalse(engine.isRecoveryWeek(TuningDirection.EASE_BACK))
        assertFalse(engine.isRecoveryWeek(TuningDirection.PUSH_HARDER))
    }

    @Test
    fun `planCurrentWeek applies recovery multiplier when tuning-aware`() {
        // weekInPhase=1 is recovery for EASE_BACK (cadence=2) but not for HOLD (cadence=3)
        val engine = PhaseEngine(
            goal = BootcampGoal.RACE_5K,
            phaseIndex = 1, // BUILD
            weekInPhase = 1,
            runsPerWeek = 3,
            targetMinutes = 30
        )
        val holdSessions = engine.planCurrentWeek(tierIndex = 1, tuningDirection = TuningDirection.HOLD)
        val easeBackSessions = engine.planCurrentWeek(tierIndex = 1, tuningDirection = TuningDirection.EASE_BACK)
        val holdTotal = holdSessions.sumOf { it.minutes }
        val easeTotal = easeBackSessions.sumOf { it.minutes }
        // EASE_BACK at weekInPhase=1 triggers recovery (0.65x) + tuning factor (0.90x)
        // HOLD at weekInPhase=1 is NOT recovery, just tuning factor 1.0x
        assertTrue("EASE_BACK recovery should reduce total minutes", easeTotal < holdTotal)
    }

    // --- EVERGREEN and phase boundary tests ---

    @Test
    fun `weeksUntilNextRecovery accounts for phase boundary reset`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.RACE_5K,
            phaseIndex = 0, // BASE
            weekInPhase = 3 // last week before advance (midpoint=4, so shouldAdvancePhase at 4)
        )
        val weeks = engine.weeksUntilNextRecovery()
        assertTrue("Should be at least 2 weeks (not 0 or 1)", weeks >= 2)
    }

    @Test
    fun `EVERGREEN weeksUntilNextRecovery returns correct micro-cycle distance`() {
        // EVERGREEN week 0: recovery is at week 3 → 3 weeks away
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 0)
        assertEquals(3, engine.weeksUntilNextRecovery())
    }

    @Test
    fun `EVERGREEN weeksUntilNextRecovery wraps around micro-cycle`() {
        // weekInPhase=4 → 4%4=0 → recovery at 3 → 3 weeks away
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 4)
        assertEquals(3, engine.weeksUntilNextRecovery())
    }
}
