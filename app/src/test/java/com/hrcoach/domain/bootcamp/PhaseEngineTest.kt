package com.hrcoach.domain.bootcamp

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
        assertEquals(1, next.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `advancePhase at final phase wraps for cycling goals`() {
        // Cardio health has 2 phases (BASE, BUILD). At index 1, it should wrap to 0.
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 5)
        val next = engine.advancePhase()
        assertEquals(0, next.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `isRecoveryWeek triggers on 2-on-1-off pattern`() {
        // weekInPhase=2 (0-indexed) means the 3rd week — recovery week
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertTrue(engine.isRecoveryWeek)
    }

    @Test
    fun `isRecoveryWeek is false for buildup weeks`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertFalse(engine.isRecoveryWeek)
    }

    @Test
    fun `weeksUntilNextRecovery returns zero during recovery week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertEquals(0, engine.weeksUntilNextRecovery)
    }

    @Test
    fun `weeksUntilNextRecovery counts down to next recovery week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertEquals(2, engine.weeksUntilNextRecovery)
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
}
