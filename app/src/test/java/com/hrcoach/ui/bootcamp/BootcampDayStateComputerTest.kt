package com.hrcoach.ui.bootcamp

import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootcampDayStateComputerTest {

    private fun session(dayOfWeek: Int, status: String = BootcampSessionEntity.STATUS_SCHEDULED) =
        BootcampSessionEntity(
            id = dayOfWeek.toLong(),
            enrollmentId = 1L,
            weekNumber = 1,
            dayOfWeek = dayOfWeek,
            sessionType = "EASY",
            targetMinutes = 30,
            status = status
        )

    // ── computeDayKind ──────────────────────────────────────────────────────

    @Test
    fun `computeDayKind returns RunUpcoming when today has scheduled session`() {
        val sessions = listOf(session(1), session(3), session(5))
        assertEquals(DayKind.RUN_UPCOMING, computeDayKind(sessions, todayDow = 1))
    }

    @Test
    fun `computeDayKind returns RunDone when today session is completed`() {
        val sessions = listOf(
            session(1, BootcampSessionEntity.STATUS_COMPLETED),
            session(3),
            session(5)
        )
        assertEquals(DayKind.RUN_DONE, computeDayKind(sessions, todayDow = 1))
    }

    @Test
    fun `computeDayKind returns Rest when no session on today`() {
        val sessions = listOf(session(1), session(3), session(5))
        assertEquals(DayKind.REST, computeDayKind(sessions, todayDow = 2))
    }

    @Test
    fun `computeDayKind returns Rest when session list is empty`() {
        assertEquals(DayKind.REST, computeDayKind(emptyList(), todayDow = 3))
    }

    @Test
    fun `computeDayKind returns RunDone for skipped session on today`() {
        val sessions = listOf(session(1, BootcampSessionEntity.STATUS_SKIPPED))
        assertEquals(DayKind.RUN_DONE, computeDayKind(sessions, todayDow = 1))
    }

    // ── computeRelativeLabel ────────────────────────────────────────────────

    @Test
    fun `computeRelativeLabel returns today when same day`() {
        assertEquals("today", computeRelativeLabel(targetDow = 3, todayDow = 3))
    }

    @Test
    fun `computeRelativeLabel returns tomorrow when next day`() {
        assertEquals("tomorrow", computeRelativeLabel(targetDow = 4, todayDow = 3))
    }

    @Test
    fun `computeRelativeLabel handles week wrap correctly`() {
        // Today is Friday (5), next session is Monday (1) — 3 days away
        assertEquals("in 3 days", computeRelativeLabel(targetDow = 1, todayDow = 5))
    }

    @Test
    fun `computeRelativeLabel returns in N days for further sessions`() {
        assertEquals("in 2 days", computeRelativeLabel(targetDow = 5, todayDow = 3))
    }

    // ── computeWeekDateRange ────────────────────────────────────────────────

    @Test
    fun `computeWeekDateRange formats same-month week`() {
        val monday = java.time.LocalDate.of(2026, 3, 9) // Mon Mar 9
        assertEquals("Mar 9–15", computeWeekDateRange(monday))
    }

    @Test
    fun `computeWeekDateRange formats cross-month week`() {
        val monday = java.time.LocalDate.of(2026, 3, 30) // Mon Mar 30
        assertEquals("Mar 30–Apr 5", computeWeekDateRange(monday))
    }
}
