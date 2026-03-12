package com.hrcoach.domain.achievement

import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 3, 12)
    private val enrollmentStartMs = LocalDate.of(2026, 1, 1)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    private fun session(
        week: Int, day: Int,
        status: String = BootcampSessionEntity.STATUS_COMPLETED,
        completedAtMs: Long? = null
    ) = BootcampSessionEntity(
        id = 0,
        enrollmentId = 1,
        weekNumber = week,
        dayOfWeek = day,
        sessionType = "EASY",
        targetMinutes = 30,
        status = status,
        completedWorkoutId = if (status == BootcampSessionEntity.STATUS_COMPLETED) 1L else null,
        completedAtMs = completedAtMs
    )

    @Test
    fun `all completed sessions count as streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3), session(1, 5),
            session(2, 1), session(2, 3)
        )
        assertEquals(5, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `skipped session breaks streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3, BootcampSessionEntity.STATUS_SKIPPED),
            session(1, 5), session(2, 1)
        )
        assertEquals(2, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `deferred sessions are skipped, do not break streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3, BootcampSessionEntity.STATUS_DEFERRED),
            session(1, 5), session(2, 1)
        )
        assertEquals(3, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `empty sessions returns 0`() {
        assertEquals(0, StreakCalculator.computeSessionStreak(emptyList(), enrollmentStartMs, today, zone))
    }

    // --- Weekly Goal Streak tests ---

    @Test
    fun `weekly goal streak counts consecutive weeks at target`() {
        val sessions = (1..4).flatMap { w ->
            listOf(session(w, 1), session(w, 3), session(w, 5))
        }
        assertEquals(4, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 2, 2), zone = zone
        ))
    }

    @Test
    fun `weekly goal streak breaks at first short week`() {
        val sessions = listOf(
            session(2, 1), session(2, 3),
            session(3, 1), session(3, 3), session(3, 5),
            session(4, 1), session(4, 3), session(4, 5)
        )
        assertEquals(2, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 2, 2), zone = zone
        ))
    }

    @Test
    fun `weekly goal streak excludes current incomplete week`() {
        val sessions = listOf(session(1, 1), session(1, 3), session(1, 5, BootcampSessionEntity.STATUS_SCHEDULED))
        assertEquals(0, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 1, 5), zone = zone
        ))
    }

    @Test
    fun `deferred sessions do not count toward weekly goal`() {
        val sessions = listOf(
            session(1, 1), session(1, 3), session(1, 5, BootcampSessionEntity.STATUS_DEFERRED)
        )
        assertEquals(0, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 1, 12), zone = zone
        ))
    }
}
