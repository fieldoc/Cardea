package com.hrcoach.data.repository

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.ui.bootcamp.BootcampSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredDayReslotTest {

    private fun preferredDay(day: Int) = DayPreference(day, DaySelectionLevel.AVAILABLE)

    private fun scheduledSession(id: Long, day: Int) = BootcampSessionEntity(
        id = id,
        enrollmentId = 1L,
        weekNumber = 5,
        dayOfWeek = day,
        sessionType = "EASY",
        targetMinutes = 30,
        status = BootcampSessionEntity.STATUS_SCHEDULED
    )

    private fun completedSession(id: Long, day: Int) = BootcampSessionEntity(
        id = id,
        enrollmentId = 1L,
        weekNumber = 5,
        dayOfWeek = day,
        sessionType = "EASY",
        targetMinutes = 30,
        status = BootcampSessionEntity.STATUS_COMPLETED,
        completedWorkoutId = 99L
    )

    @Test
    fun `reslot assigns new days to scheduled sessions preserving order`() {
        val sessions = listOf(scheduledSession(1, 1), scheduledSession(2, 3), scheduledSession(3, 6))
        val newDays = listOf(preferredDay(2), preferredDay(4), preferredDay(7))

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(listOf(2, 4, 7), result.map { it.second })
    }

    @Test
    fun `reslot skips days already occupied by completed sessions`() {
        val sessions = listOf(
            completedSession(1, 1),
            scheduledSession(2, 3),
            scheduledSession(3, 6)
        )
        val newDays = listOf(preferredDay(1), preferredDay(4), preferredDay(6))

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(listOf(4, 6), result.map { it.second })
    }

    @Test
    fun `reslot returns empty list when no scheduled sessions exist`() {
        val sessions = listOf(completedSession(1, 1), completedSession(2, 3))
        val newDays = listOf(preferredDay(2), preferredDay(4))

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(emptyList<Pair<BootcampSessionEntity, Int>>(), result)
    }

    @Test
    fun `reslot falls back to original day when new days list is shorter than scheduled count`() {
        val sessions = listOf(scheduledSession(1, 1), scheduledSession(2, 3), scheduledSession(3, 6))
        val newDays = listOf(preferredDay(2), preferredDay(5))

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(3, result.size)
        assertEquals(6, result[2].second)
    }
}

class BootcampSettingsUiStateValidationTest {

    private fun dayPreference(day: Int) = DayPreference(day, DaySelectionLevel.AVAILABLE)

    @Test
    fun `validation error appears when selected day count does not match runs per week`() {
        val state = BootcampSettingsUiState(
            runsPerWeek = 3,
            preferredDays = listOf(dayPreference(1), dayPreference(3), dayPreference(5)),
            editPreferredDays = listOf(dayPreference(1), dayPreference(3))
        )

        assertEquals(
            "Select exactly 3 days - your program is set to 3 runs/week. To change your frequency, edit it above.",
            state.preferredDaysValidationError
        )
        assertFalse(state.canSave)
    }

    @Test
    fun `validation error is absent when selected day count matches runs per week`() {
        val state = BootcampSettingsUiState(
            runsPerWeek = 3,
            preferredDays = listOf(dayPreference(1), dayPreference(3), dayPreference(5)),
            editPreferredDays = listOf(dayPreference(1), dayPreference(4), dayPreference(6))
        )

        assertNull(state.preferredDaysValidationError)
        assertTrue(state.canSave)
    }

    @Test
    fun `save disabled when hrMax validation fails`() {
        val state = BootcampSettingsUiState(
            runsPerWeek = 3,
            preferredDays = listOf(dayPreference(1), dayPreference(3), dayPreference(5)),
            editPreferredDays = listOf(dayPreference(1), dayPreference(4), dayPreference(6)),
            hrMaxValidationError = "Max HR must be between 100 and 220."
        )

        assertFalse(state.canSave)
    }
}
