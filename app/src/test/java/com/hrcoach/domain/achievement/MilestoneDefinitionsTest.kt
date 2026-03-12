package com.hrcoach.domain.achievement

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneDefinitionsTest {

    @Test
    fun `distance milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.DISTANCE_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `streak milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.STREAK_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `weekly goal milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.WEEKLY_GOAL_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `distance prestige 50km is 1, 250km is 2, 1000km is 3`() {
        val map = MilestoneDefinitions.DISTANCE_THRESHOLDS.toMap()
        assertEquals(1, map[50.0])
        assertEquals(2, map[250.0])
        assertEquals(3, map[1000.0])
    }

    @Test
    fun `tier graduation prestige is 2 for tier 1 and 3 for tier 2`() {
        assertEquals(2, MilestoneDefinitions.tierGraduationPrestige(1))
        assertEquals(3, MilestoneDefinitions.tierGraduationPrestige(2))
    }

    @Test
    fun `bootcamp graduation is always prestige 3`() {
        assertEquals(3, MilestoneDefinitions.BOOTCAMP_GRADUATION_PRESTIGE)
    }
}
