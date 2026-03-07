package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection
import org.junit.Test
import org.junit.Assert.*

class CoachingCopyGeneratorTest {

    @Test
    fun `up copy contains no raw metrics`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.UP,
            aboveOrBelowWeeks = 3,
            ctlTrend = 1.2f,
            tsb = 5.4f
        )
        assertFalse("Copy must not mention CTL", copy.contains("CTL"))
        assertFalse("Copy must not mention TSB", copy.contains("TSB"))
        assertTrue("Copy must not be blank", copy.isNotBlank())
    }

    @Test
    fun `down copy contains no raw metrics`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.DOWN,
            aboveOrBelowWeeks = 2,
            ctlTrend = -0.8f,
            tsb = -28f
        )
        assertFalse(copy.contains("CTL"))
        assertFalse(copy.contains("TSB"))
        assertTrue(copy.isNotBlank())
    }

    @Test
    fun `up copy references weeks context`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.UP,
            aboveOrBelowWeeks = 3,
            ctlTrend = 1.0f,
            tsb = 6.0f
        )
        assertTrue("Copy should reference the week count", copy.contains("3") || copy.contains("three") || copy.contains("weeks"))
    }

    @Test
    fun `NONE direction returns blank string`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.NONE,
            aboveOrBelowWeeks = 0,
            ctlTrend = 0f,
            tsb = 0f
        )
        assertTrue(copy.isBlank())
    }
}
