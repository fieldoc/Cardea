package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import org.junit.Assert.*
import org.junit.Test

class SessionSelectorTest {

    @Test
    fun `base phase for cardio health returns only easy and long sessions`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30
        )
        assertEquals(3, sessions.size)
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `build phase for marathon includes tempo session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 45
        )
        assertEquals(4, sessions.size)
        assertTrue(sessions.any { it.type == SessionType.TEMPO })
        assertTrue(sessions.any { it.type == SessionType.LONG })
    }

    @Test
    fun `peak phase for marathon includes interval session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 45
        )
        assertTrue(sessions.any { it.type == SessionType.INTERVAL })
    }

    @Test
    fun `taper phase reduces target minutes by 30 percent`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.TAPER,
            goal = BootcampGoal.HALF_MARATHON,
            runsPerWeek = 3,
            targetMinutes = 40
        )
        assertTrue(sessions.all { it.minutes <= 28 }) // 40 * 0.7 = 28
    }

    @Test
    fun `cardio health never gets interval sessions in any phase`() {
        for (phase in BootcampGoal.CARDIO_HEALTH.phaseArc) {
            val sessions = SessionSelector.weekSessions(
                phase = phase,
                goal = BootcampGoal.CARDIO_HEALTH,
                runsPerWeek = 3,
                targetMinutes = 25
            )
            assertFalse(
                "Phase $phase should not have intervals for cardio health",
                sessions.any { it.type == SessionType.INTERVAL }
            )
        }
    }

    @Test
    fun `2 runs per week still includes one quality session on build phase`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 2,
            targetMinutes = 30
        )
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.type == SessionType.LONG || it.type == SessionType.TEMPO })
    }

    @Test
    fun `build phase with four runs injects strides for tier two and above`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 35
        )
        assertTrue(sessions.any { it.type == SessionType.STRIDES })
    }

    @Test
    fun `lactate threshold preset uses threshold display label`() {
        assertEquals("Threshold (Z4)", SessionType.displayLabelForPreset("lactate_threshold"))
        assertEquals("Tempo (Z3)", SessionType.displayLabelForPreset("aerobic_tempo"))
    }

    @Test
    fun `tierIndex 0 demotes RACE_5K to baseAerobicWeek`() {
        // RACE_5K goal.tier=2, tierIndex=0 → effectiveTier=1 → baseAerobicWeek path
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 30,
            tierIndex = 0
        )
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
        assertFalse(sessions.any { it.type == SessionType.TEMPO })
        assertFalse(sessions.any { it.type == SessionType.INTERVAL })
    }

    @Test
    fun `tierIndex 2 promotes RACE_5K to 2 quality sessions in PEAK`() {
        // RACE_5K goal.tier=2, tierIndex=2 → effectiveTier=3 → 2 quality in PEAK
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 35,
            tierIndex = 2
        )
        val qualityTypes = setOf(SessionType.TEMPO, SessionType.INTERVAL)
        val qualityCount = sessions.count { it.type in qualityTypes }
        assertEquals(2, qualityCount)
    }

    @Test
    fun `tierIndex 0 removes strides from BUILD for tier-2 goal`() {
        // effectiveTier=1 → baseAerobicWeek, strides require effectiveTier>=2
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 30,
            tierIndex = 0
        )
        assertFalse(sessions.any { it.type == SessionType.STRIDES })
    }

    @Test
    fun `tierIndex 1 preserves current behavior for RACE_5K BUILD`() {
        // tierIndex=1 is neutral: effectiveTier = goal.tier = 2. Same as default.
        val withTier = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 30,
            tierIndex = 1
        )
        val withoutTier = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 30
        )
        assertEquals(withoutTier.map { it.type }, withTier.map { it.type })
    }
}
