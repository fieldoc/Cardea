package com.hrcoach.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerActivityTest {

    @Test
    fun `create partner activity with all fields`() {
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 12,
            weeklyRunCount = 3,
            lastRunDate = "2026-04-01",
            lastRunDurationMin = 30,
            lastRunPhase = "Base building",
        )
        assertEquals("abc-123", partner.userId)
        assertEquals("Sarah", partner.displayName)
        assertEquals("bolt", partner.emblemId)
        assertEquals(12, partner.currentStreak)
        assertEquals(3, partner.weeklyRunCount)
        assertEquals("2026-04-01", partner.lastRunDate)
        assertEquals(30, partner.lastRunDurationMin)
        assertEquals("Base building", partner.lastRunPhase)
    }

    @Test
    fun `nullable fields default to null`() {
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Mike",
            emblemId = "pulse",
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = null,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertNull(partner.lastRunDate)
        assertNull(partner.lastRunDurationMin)
        assertNull(partner.lastRunPhase)
    }

    @Test
    fun `ranToday returns true when lastRunDate is today`() {
        val today = java.time.LocalDate.now().toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = today,
            lastRunDurationMin = 30,
            lastRunPhase = "Base building",
        )
        assertTrue(partner.ranToday())
    }

    @Test
    fun `ranToday returns false when lastRunDate is yesterday`() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = yesterday,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(!partner.ranToday())
    }

    @Test
    fun `isRecentlyActive returns true within 48 hours`() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = yesterday,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(partner.isRecentlyActive())
    }

    @Test
    fun `isRecentlyActive returns false after 3 days`() {
        val threeDaysAgo = java.time.LocalDate.now().minusDays(3).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = threeDaysAgo,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(!partner.isRecentlyActive())
    }
}
