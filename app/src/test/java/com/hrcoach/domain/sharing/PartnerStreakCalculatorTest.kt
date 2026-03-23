package com.hrcoach.domain.sharing

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PartnerStreakCalculatorTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `bootcamp streak counts consecutive completed sessions`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 5, BootcampSessionEntity.STATUS_COMPLETED)
        )
        val streak = PartnerStreakCalculator.computeBootcampStreak(sessions)
        assertEquals(3, streak)
    }

    @Test
    fun `deferred session does not break streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_DEFERRED),
            session(1, 4, BootcampSessionEntity.STATUS_COMPLETED)
        )
        val streak = PartnerStreakCalculator.computeBootcampStreak(sessions)
        assertEquals(2, streak)
    }

    @Test
    fun `missed session breaks streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_SCHEDULED),
            session(1, 5, BootcampSessionEntity.STATUS_COMPLETED)
        )
        val today = LocalDate.of(2026, 3, 22)
        val enrollStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val streak = PartnerStreakCalculator.computeBootcampStreak(sessions, enrollStart, today, zone)
        assertEquals(1, streak)
    }

    @Test
    fun `free runner streak counts consecutive days with runs`() {
        val workouts = listOf(workout(0), workout(-1), workout(-2))
        val today = LocalDate.of(2026, 3, 22)
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(workouts, today, zone)
        assertEquals(3, streak)
    }

    @Test
    fun `free runner streak breaks on gap day`() {
        val workouts = listOf(workout(0), workout(-2))
        val today = LocalDate.of(2026, 3, 22)
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(workouts, today, zone)
        assertEquals(1, streak)
    }

    @Test
    fun `free runner with no workouts has zero streak`() {
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(emptyList(), LocalDate.of(2026, 3, 22), zone)
        assertEquals(0, streak)
    }

    private fun session(week: Int, day: Int, status: String) =
        BootcampSessionEntity(
            enrollmentId = 1L, weekNumber = week, dayOfWeek = day,
            sessionType = "EASY_RUN", targetMinutes = 30, status = status
        )

    private val baseDate = LocalDate.of(2026, 3, 22)
    private fun workout(dayOffset: Int): WorkoutEntity {
        val date = baseDate.plusDays(dayOffset.toLong())
        val ms = date.atStartOfDay(zone).toInstant().toEpochMilli() + 36_000_000
        return WorkoutEntity(startTime = ms, endTime = ms + 1_800_000, totalDistanceMeters = 3000f, mode = "FREE_RUN", targetConfig = "")
    }
}
