package com.hrcoach.ui.home

import com.hrcoach.domain.model.PartnerActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeBannerVisibilityTest {

    private val today = java.time.LocalDate.now().toString()
    private val yesterday = java.time.LocalDate.now().minusDays(1).toString()
    private val threeDaysAgo = java.time.LocalDate.now().minusDays(3).toString()

    private fun partner(
        name: String = "Sarah",
        lastRunDate: String? = today,
        streak: Int = 5,
        weeklyRuns: Int = 2,
    ) = PartnerActivity(
        userId = "id-$name",
        displayName = name,
        emblemId = "bolt",
        currentStreak = streak,
        weeklyRunCount = weeklyRuns,
        lastRunDate = lastRunDate,
        lastRunDurationMin = 30,
        lastRunPhase = "Base building",
    )

    @Test
    fun `show nudge when partner ran today and user has not`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = today)),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Sarah"))
    }

    @Test
    fun `hide nudge when user already ran today`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = today)),
            userRanToday = true,
        )
        assertNull(result)
    }

    @Test
    fun `hide nudge when no partners`() {
        val result = computeNudgeBanner(
            partners = emptyList(),
            userRanToday = false,
        )
        assertNull(result)
    }

    @Test
    fun `hide nudge when partner activity older than 48 hours`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = threeDaysAgo)),
            userRanToday = false,
        )
        assertNull(result)
    }

    @Test
    fun `show nudge for yesterday activity`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(name = "Mike", lastRunDate = yesterday)),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Mike"))
    }

    @Test
    fun `combined text when two partners ran today`() {
        val result = computeNudgeBanner(
            partners = listOf(
                partner(name = "Sarah", lastRunDate = today),
                partner(name = "Mike", lastRunDate = today),
            ),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Sarah"))
        assertTrue(result!!.text.contains("Mike"))
    }

    @Test
    fun `most recent partner shown first`() {
        val result = computeNudgeBanner(
            partners = listOf(
                partner(name = "Mike", lastRunDate = yesterday),
                partner(name = "Sarah", lastRunDate = today),
            ),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.startsWith("Sarah"))
    }
}
