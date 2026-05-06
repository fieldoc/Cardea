package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SessionReschedulerTest {

    private fun dayPrefs(vararg specs: Pair<Int, DaySelectionLevel>): List<DayPreference> =
        specs.map { DayPreference(it.first, it.second) }

    private fun enrollment(
        preferredDays: List<DayPreference> = dayPrefs(
            1 to DaySelectionLevel.AVAILABLE,
            3 to DaySelectionLevel.AVAILABLE,
            6 to DaySelectionLevel.AVAILABLE
        )
    ) = BootcampEnrollmentEntity(
        id = 1L, goalType = "CARDIO_HEALTH", targetMinutesPerRun = 30,
        runsPerWeek = 3, preferredDays = preferredDays, startDate = 0L
    )

    private fun session(day: Int, type: String = "EASY", status: String = "SCHEDULED") =
        BootcampSessionEntity(
            id = day.toLong(), enrollmentId = 1L, weekNumber = 1,
            dayOfWeek = day, sessionType = type, targetMinutes = 30, status = status
        )

    private fun reasonOn(suggestions: List<DaySuggestion>, day: Int): SuggestionReason? =
        suggestions.firstOrNull { it.dayOfWeek == day }?.reason

    // ─── reschedule() result shape ────────────────────────────────────────────

    @Test fun moves_to_next_available_day() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals(SuggestionReason.FREE, reasonOn(suggestions, 2))
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Moved)
        // Day 2 is the first FREE day (3 and 6 are OCCUPIED).
        assertEquals(2, (result as RescheduleResult.Moved).firstFreeDayOrNull)
    }

    @Test fun marks_blackout_days_as_blackout_not_excluded() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.BLACKOUT,
                3 to DaySelectionLevel.BLACKOUT,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        // Blackout days are present, not absent.
        assertEquals(SuggestionReason.BLACKOUT, reasonOn(suggestions, 2))
        assertEquals(SuggestionReason.BLACKOUT, reasonOn(suggestions, 3))
        assertEquals(SuggestionReason.BLACKOUT, reasonOn(suggestions, 4))
        assertEquals(SuggestionReason.BLACKOUT, reasonOn(suggestions, 5))
        // Day 6 is the only FREE day.
        assertEquals(SuggestionReason.FREE, reasonOn(suggestions, 6))
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Moved
        assertEquals(6, result.firstFreeDayOrNull)
    }

    @Test fun no_free_days_returns_moved_with_null_target() {
        // Mix of OCCUPIED + BLACKOUT — every future day has a conflict.
        val req = RescheduleRequest(
            session = session(day = 3, type = "TEMPO"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 5,
            occupiedDaysThisWeek = setOf(1, 3, 6),
            allSessionsThisWeek = listOf(session(1, "EASY"), session(3, "TEMPO"), session(6, "LONG"))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        // Every future non-self day is annotated, none FREE.
        assertTrue("No suggestion should be FREE", suggestions.none { it.reason == SuggestionReason.FREE })
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Moved
        assertNull("No FREE day exists, target should be null", result.firstFreeDayOrNull)
    }

    @Test fun defer_returns_deferred() {
        val result = SessionRescheduler.defer()
        assertTrue(result is RescheduleResult.Deferred)
    }

    // ─── annotation correctness ───────────────────────────────────────────────

    @Test fun recovery_spacing_is_annotated_not_excluded() {
        // Day 2 has TEMPO. Rescheduling TEMPO from day 1; day 3 sits adjacent to day 2's
        // hard session — was previously rejected, now annotated RECOVERY_SPACING.
        val req = RescheduleRequest(
            session = session(day = 1, type = "TEMPO"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.BLACKOUT,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 2),
            allSessionsThisWeek = listOf(
                session(1, "TEMPO"),
                session(2, "TEMPO"),
                session(3, "EASY")
            )
        )
        val suggestions = SessionRescheduler.suggestions(req)
        // Day 3 is adjacent to day 2's TEMPO — recovery spacing.
        assertEquals(SuggestionReason.RECOVERY_SPACING, reasonOn(suggestions, 3))
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Moved
        assertNull(result.firstFreeDayOrNull)
    }

    @Test fun long_run_recovery_spacing_is_annotated() {
        val req = RescheduleRequest(
            session = session(day = 1, type = "EASY"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.BLACKOUT,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 3),
            allSessionsThisWeek = listOf(
                session(1, "EASY"),
                session(3, "LONG")
            )
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals(SuggestionReason.RECOVERY_SPACING, reasonOn(suggestions, 2))
    }

    @Test fun today_appears_when_not_occupied() {
        val req = RescheduleRequest(
            session = session(day = 4),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.AVAILABLE,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 3,
            occupiedDaysThisWeek = setOf(1, 4, 6),
            allSessionsThisWeek = listOf(session(1), session(4), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals("Today (3) should appear as FREE", SuggestionReason.FREE, reasonOn(suggestions, 3))
    }

    @Test fun today_appears_as_occupied_when_session_present() {
        val req = RescheduleRequest(
            session = session(day = 4),
            enrollment = enrollment(),
            todayDayOfWeek = 3,
            occupiedDaysThisWeek = setOf(1, 3, 4, 6),
            allSessionsThisWeek = listOf(session(1), session(3), session(4), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals("Today (3) is OCCUPIED, not absent", SuggestionReason.OCCUPIED, reasonOn(suggestions, 3))
    }

    @Test fun allows_non_preferred_days_for_reschedule() {
        // Preferred days: Mon/Wed/Sat. Tue/Thu/Fri are NONE-level — should appear as FREE.
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals(SuggestionReason.FREE, reasonOn(suggestions, 2))
        assertEquals(SuggestionReason.FREE, reasonOn(suggestions, 4))
        assertEquals(SuggestionReason.FREE, reasonOn(suggestions, 5))
    }

    @Test fun none_level_blackout_marked_blackout() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals(SuggestionReason.BLACKOUT, reasonOn(suggestions, 4))
    }

    // ─── new edge-case tests ──────────────────────────────────────────────────

    @Test fun all_days_occupied_returns_no_free_recommendation() {
        // Every future day has another session.
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 2, 3, 4, 5, 6, 7),
            allSessionsThisWeek = (1..7).map { session(it) }
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertTrue("All non-self days should be OCCUPIED",
            suggestions.all { it.reason == SuggestionReason.OCCUPIED })
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Moved
        assertNull(result.firstFreeDayOrNull)
    }

    @Test fun today_is_sunday_session_on_sunday_returns_empty_suggestions() {
        // (7..7) minus self-day (7) = empty.
        val req = RescheduleRequest(
            session = session(day = 7),
            enrollment = enrollment(dayPrefs(7 to DaySelectionLevel.AVAILABLE)),
            todayDayOfWeek = 7,
            occupiedDaysThisWeek = setOf(7),
            allSessionsThisWeek = listOf(session(7))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertTrue("No future days exist on Sunday for a Sunday session", suggestions.isEmpty())
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Moved
        assertNull(result.firstFreeDayOrNull)
    }

    @Test fun empty_preferred_days_treats_all_non_blackout_as_free() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(preferredDays = emptyList()),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        // Days 2-7 should all be FREE.
        for (day in 2..7) {
            assertEquals("Day $day should be FREE with empty prefs",
                SuggestionReason.FREE, reasonOn(suggestions, day))
        }
    }

    @Test fun self_day_is_never_in_suggestions() {
        // Session is on Thu (4); today is Wed (3). Thu would be FREE but is excluded.
        val req = RescheduleRequest(
            session = session(day = 4),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.AVAILABLE,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 3,
            occupiedDaysThisWeek = setOf(4),
            allSessionsThisWeek = listOf(session(4))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertNull("Self-day (4) must not appear", reasonOn(suggestions, 4))
    }

    @Test fun multi_reason_precedence_picks_occupied_over_blackout() {
        // Day 5 is BLACKOUT and OCCUPIED — OCCUPIED wins (most-restrictive precedence).
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                5 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 5),
            allSessionsThisWeek = listOf(session(1), session(5))
        )
        val suggestions = SessionRescheduler.suggestions(req)
        assertEquals(SuggestionReason.OCCUPIED, reasonOn(suggestions, 5))
    }
}
