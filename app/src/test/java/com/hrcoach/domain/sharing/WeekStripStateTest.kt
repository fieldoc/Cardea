package com.hrcoach.domain.sharing

import com.hrcoach.data.firebase.RunCompletionPayload
import org.junit.Assert.*
import org.junit.Test

class WeekStripStateTest {

    @Test
    fun `completed scheduled run shows as COMPLETED`() {
        val completions = listOf(payload(weekDay = 1, wasScheduled = true, originalScheduledWeekDay = null))
        val states = WeekStripState.compute(completions, todayWeekDay = 5)
        assertEquals(DayState.COMPLETED, states[0])
    }

    @Test
    fun `deferred run shows DEFERRED on original day and COMPLETED on actual day`() {
        val completions = listOf(payload(weekDay = 3, wasScheduled = true, originalScheduledWeekDay = 2))
        val states = WeekStripState.compute(completions, todayWeekDay = 5)
        assertEquals(DayState.DEFERRED, states[1])
        assertEquals(DayState.COMPLETED, states[2])
    }

    @Test
    fun `bonus run shows as BONUS`() {
        val completions = listOf(payload(weekDay = 6, wasScheduled = false, originalScheduledWeekDay = null))
        val states = WeekStripState.compute(completions, todayWeekDay = 7)
        assertEquals(DayState.BONUS, states[5])
    }

    @Test
    fun `today with no run shows as TODAY`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 3)
        assertEquals(DayState.TODAY, states[2])
    }

    @Test
    fun `past day with no run shows as REST`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 5)
        assertEquals(DayState.REST, states[0])
    }

    @Test
    fun `future day shows as FUTURE`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 3)
        assertEquals(DayState.FUTURE, states[4])
    }

    private fun payload(weekDay: Int, wasScheduled: Boolean, originalScheduledWeekDay: Int?) =
        RunCompletionPayload(
            userId = "u1", timestamp = System.currentTimeMillis(), distanceMeters = 3000.0,
            routePolyline = "", streakCount = 1, programPhase = null, sessionLabel = null,
            wasScheduled = wasScheduled, originalScheduledWeekDay = originalScheduledWeekDay, weekDay = weekDay
        )
}
