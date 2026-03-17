package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.achievement.StreakCalculator.computeSessionStreak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeSessionStreakTest {

    // enrollment starts 2026-01-05 (Monday of week 1)
    private val startMs = java.time.LocalDate.of(2026, 1, 5)
        .atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()

    private val today = LocalDate.of(2026, 1, 26) // Monday of week 4

    private fun session(week: Int, day: Int, status: String) = BootcampSessionEntity(
        enrollmentId = 1L,
        weekNumber = week,
        dayOfWeek = day,
        sessionType = "EASY_RUN",
        targetMinutes = 30,
        status = status
    )

    @Test fun `empty list returns 0`() {
        assertEquals(0, computeSessionStreak(emptyList(), startMs, today))
    }

    @Test fun `all completed returns count`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
        )
        assertEquals(3, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `skipped session stops streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_SKIPPED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // walk backward: W2D3=COMPLETED(1), W2D1=COMPLETED(2), W1D3=SKIPPED → stop → 2
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `past scheduled session counts as missed`() {
        // W1D1 = 2026-01-05 (past), never actioned (SCHEDULED)
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_SCHEDULED), // past, effectively missed
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // walk backward: W2D3=COMPLETED(1), W2D1=COMPLETED(2), W1D1=SCHEDULED+past → stop → 2
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `future scheduled session is skipped`() {
        // today = 2026-01-26 (week 4 Monday). W4D3 = 2026-01-28 = future
        val sessions = listOf(
            session(4, 3, BootcampSessionEntity.STATUS_SCHEDULED), // future, skip
            session(3, 5, BootcampSessionEntity.STATUS_COMPLETED),
            session(3, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `deferred session does not break streak`() {
        val sessions = listOf(
            session(3, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 5, BootcampSessionEntity.STATUS_DEFERRED), // skipped in streak calc
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // W3D1=COMPLETED(1), W2D5=DEFERRED(skip), W2D3=COMPLETED(2), W2D1=COMPLETED(3)
        assertEquals(3, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `no runs returns 0 streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_SCHEDULED), // past — effectively missed
        )
        assertEquals(0, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `skipped most recent session returns 0`() {
        val sessions = listOf(
            session(3, 1, BootcampSessionEntity.STATUS_SKIPPED), // most recent, immediately breaks
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
        )
        assertEquals(0, computeSessionStreak(sessions, startMs, today))
    }
}
