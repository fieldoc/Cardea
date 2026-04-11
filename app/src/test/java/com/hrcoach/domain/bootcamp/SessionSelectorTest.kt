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
    fun `peak phase for marathon at tier 1 includes tempo session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 45
        )
        assertTrue(sessions.any { it.type == SessionType.TEMPO })
    }

    @Test
    fun `taper phase reduces total volume compared to build`() {
        val buildSessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.HALF_MARATHON,
            runsPerWeek = 3,
            targetMinutes = 40
        )
        val taperSessions = SessionSelector.weekSessions(
            phase = TrainingPhase.TAPER,
            goal = BootcampGoal.HALF_MARATHON,
            runsPerWeek = 3,
            targetMinutes = 40
        )
        val buildTotal = buildSessions.sumOf { it.minutes }
        val taperTotal = taperSessions.sumOf { it.minutes }
        assertTrue("Taper ($taperTotal) should be less than build ($buildTotal)", taperTotal < buildTotal)
    }

    @Test
    fun `taper tempo duration is at least 15 minutes`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.TAPER,
            goal = BootcampGoal.HALF_MARATHON,
            runsPerWeek = 3,
            targetMinutes = 40
        )
        val tempo = sessions.firstOrNull { it.type == SessionType.TEMPO }
        assertNotNull("TAPER should have a tempo session", tempo)
        assertTrue("Taper tempo should be at least 15 min, was ${tempo!!.minutes}", tempo.minutes >= 15)
    }

    @Test
    fun `cardio health BASE phase has no interval sessions`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 25
        )
        assertFalse(
            "BASE should not have intervals for cardio health",
            sessions.any { it.type == SessionType.INTERVAL }
        )
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
            targetMinutes = 35,
            tierIndex = 2
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
        // tierIndex=0 → baseAerobicWeek path (all easy + optional long)
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
        // tierIndex=2 → 2 quality sessions in PEAK
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
        // tierIndex=0 → baseAerobicWeek, no strides possible
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
        // tierIndex=1 is the default — explicit and implicit should match.
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

    // ── New safety tests ──────────────────────────

    @Test
    fun `marathon tier 0 gets only easy and long sessions in all phases`() {
        for (phase in TrainingPhase.entries) {
            val sessions = SessionSelector.weekSessions(
                phase = phase, goal = BootcampGoal.MARATHON,
                runsPerWeek = 4, targetMinutes = 40, tierIndex = 0
            )
            val types = sessions.map { it.type }.toSet()
            assertTrue("Marathon T0 $phase should have no TEMPO", SessionType.TEMPO !in types)
            assertTrue("Marathon T0 $phase should have no INTERVAL", SessionType.INTERVAL !in types)
            assertTrue("Marathon T0 $phase should have no RACE_SIM", SessionType.RACE_SIM !in types)
        }
    }

    @Test
    fun `tier 1 BUILD gets tempo but not intervals for any goal`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD, goal = BootcampGoal.RACE_10K,
            runsPerWeek = 4, targetMinutes = 35, tierIndex = 1
        )
        val types = sessions.map { it.type }.toSet()
        assertTrue("T1 BUILD should have TEMPO", SessionType.TEMPO in types)
        assertTrue("T1 BUILD should NOT have INTERVAL", SessionType.INTERVAL !in types)
    }

    @Test
    fun `tier 2 PEAK 5K gets intervals as primary quality session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4, targetMinutes = 35, tierIndex = 2
        )
        val types = sessions.map { it.type }.toSet()
        assertTrue("5K T2 PEAK should have INTERVAL", SessionType.INTERVAL in types)
    }

    @Test
    fun `tier 2 PEAK marathon gets LT tempo as primary quality session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK, goal = BootcampGoal.MARATHON,
            runsPerWeek = 4, targetMinutes = 50, tierIndex = 2
        )
        val types = sessions.map { it.type }.toSet()
        assertTrue("Marathon T2 PEAK should have TEMPO", SessionType.TEMPO in types)
    }

    // ── BASE phase strides (Karvonen update) ─────────────

    @Test
    fun `BASE phase tier 1 assigns one strides session per week`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.RACE_10K,
            runsPerWeek = 3,
            targetMinutes = 45,
            tierIndex = 1
        )
        val stridesSessions = sessions.filter { it.presetId == "zone2_with_strides" }
        assertEquals("Expected exactly 1 strides session in BASE tier 1", 1, stridesSessions.size)
    }

    @Test
    fun `BASE phase tier 0 has no strides sessions`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.RACE_10K,
            runsPerWeek = 3,
            targetMinutes = 45,
            tierIndex = 0
        )
        val stridesSessions = sessions.filter { it.presetId == "zone2_with_strides" }
        assertEquals("Tier 0 should have no strides in BASE", 0, stridesSessions.size)
    }

    @Test
    fun `BASE phase cardio health has no strides even at tier 1`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 1
        )
        val stridesSessions = sessions.filter { it.presetId == "zone2_with_strides" }
        assertEquals("CARDIO_HEALTH should have no strides", 0, stridesSessions.size)
    }

    // ── RACE_SIM preset resolution tests ──────────────────────────

    @Test
    fun `RACE_SIM session has goal-appropriate preset for 5K`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4,
            targetMinutes = 35,
            tierIndex = 2
        )
        val raceSim = sessions.firstOrNull { it.type == SessionType.RACE_SIM }
        assertNotNull("5K tier 2 PEAK should have RACE_SIM", raceSim)
        assertEquals("race_sim_5k", raceSim!!.presetId)
    }

    @Test
    fun `RACE_SIM session has goal-appropriate preset for marathon`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 50,
            tierIndex = 2
        )
        val raceSim = sessions.firstOrNull { it.type == SessionType.RACE_SIM }
        assertNotNull("Marathon tier 2 PEAK should have RACE_SIM", raceSim)
        assertEquals("marathon_prep", raceSim!!.presetId)
    }

    // ── EVERGREEN phase tests ──────────────────────────

    @Test
    fun `EVERGREEN week 0 includes tempo for tier 1`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 1,
            weekInPhase = 0
        )
        assertTrue("Week A should have TEMPO", sessions.any { it.type == SessionType.TEMPO })
    }

    @Test
    fun `EVERGREEN week 1 includes strides for tier 1`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 1,
            weekInPhase = 1
        )
        assertTrue("Week B should have STRIDES", sessions.any { it.type == SessionType.STRIDES })
    }

    @Test
    fun `EVERGREEN week 2 includes interval for tier 2`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 2,
            weekInPhase = 2
        )
        assertTrue("Week C tier 2 should have INTERVAL", sessions.any { it.type == SessionType.INTERVAL })
    }

    @Test
    fun `EVERGREEN week 2 uses tempo for tier 1 instead of interval`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 1,
            weekInPhase = 2
        )
        assertFalse("Week C tier 1 should NOT have INTERVAL", sessions.any { it.type == SessionType.INTERVAL })
        assertTrue("Week C tier 1 should have TEMPO", sessions.any { it.type == SessionType.TEMPO })
    }

    @Test
    fun `EVERGREEN week 3 is all easy - recovery week`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30,
            tierIndex = 2,
            weekInPhase = 3
        )
        assertTrue("Week D (recovery) should be all easy/long",
            sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `EVERGREEN tier 0 gets only easy and long regardless of week`() {
        for (week in 0..3) {
            val sessions = SessionSelector.weekSessions(
                phase = TrainingPhase.EVERGREEN,
                goal = BootcampGoal.CARDIO_HEALTH,
                runsPerWeek = 3,
                targetMinutes = 25,
                tierIndex = 0,
                weekInPhase = week
            )
            assertTrue("Tier 0 week $week should be all easy/long",
                sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
        }
    }

    @Test
    fun `EVERGREEN week 2 alternates between hill repeats and HIIT for tier 2`() {
        val hillWeek = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3, targetMinutes = 30, tierIndex = 2,
            weekInPhase = 2, absoluteWeek = 6 // even -> hill
        )
        val hiitWeek = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3, targetMinutes = 30, tierIndex = 2,
            weekInPhase = 2, absoluteWeek = 7 // odd -> hiit
        )
        val hillPreset = hillWeek.first { it.type == SessionType.INTERVAL }.presetId
        val hiitPreset = hiitWeek.first { it.type == SessionType.INTERVAL }.presetId
        assertEquals("hill_repeats", hillPreset)
        assertEquals("hiit_30_30", hiitPreset)
    }

    // ── Recovery week composition tests ──────────────────────────

    @Test
    fun `tier 1 recovery week replaces quality with easy`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD, goal = BootcampGoal.RACE_5K,
            runsPerWeek = 3, targetMinutes = 19,
            tierIndex = 1, isRecoveryWeek = true
        )
        assertTrue("Tier 1 recovery should be all easy/long",
            sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `tier 2 recovery week downgrades interval to easier quality`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4, targetMinutes = 22,
            tierIndex = 2, isRecoveryWeek = true
        )
        assertFalse("Tier 2 recovery should not have INTERVAL",
            sessions.any { it.type == SessionType.INTERVAL })
        assertTrue("Tier 2 recovery should have downgraded quality",
            sessions.any { it.type == SessionType.TEMPO || it.type == SessionType.STRIDES })
    }

    // ── Interval variety tests ──────────────────────────

    @Test
    fun `PEAK interval preset alternates between norwegian and hills for race goals`() {
        val n4x4Week = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4, targetMinutes = 35, tierIndex = 2,
            absoluteWeek = 10 // even -> norwegian_4x4
        )
        val hillWeek = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
            runsPerWeek = 4, targetMinutes = 35, tierIndex = 2,
            absoluteWeek = 11 // odd -> hill_repeats
        )
        val n4x4Preset = n4x4Week.first { it.type == SessionType.INTERVAL }.presetId
        val hillPreset = hillWeek.first { it.type == SessionType.INTERVAL }.presetId
        assertEquals("norwegian_4x4", n4x4Preset)
        assertEquals("hill_repeats", hillPreset)
    }
}
