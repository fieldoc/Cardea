package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.model.BootcampGoal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarDriftRecovererTest {

    private val zone = ZoneId.of("UTC")

    private fun makeRecoverer(dao: FakeBootcampDao) =
        CalendarDriftRecoverer(BootcampRepository(dao))

    /**
     * Mirrors Graham's actual scenario at 2026-05-04 07:58 AM:
     * engine week N has three completions in calendar week W (Mon Apr 27 – Sun May 3),
     * one residual SCHEDULED on day=1, and today is Mon May 4 (calendar week W+1).
     * Recoverer should skip the residual, advance to week N+1, seed next week's
     * sessions, and stamp the SKIP with W's Sunday 23:59 UTC.
     */
    @Test
    fun stuck_week_with_completions_skips_residual_and_advances() = runTest {
        val today = LocalDate.of(2026, 5, 4) // Mon
        val sundayOfPrevWeek = LocalDate.of(2026, 5, 3) // Sun
        val sundayEndOfDayMs = sundayOfPrevWeek.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val tueOfPrevWeek = LocalDate.of(2026, 4, 28).atStartOfDay(zone).toInstant().toEpochMilli()
        val thuOfPrevWeek = LocalDate.of(2026, 4, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val sunOfPrevWeek = sundayOfPrevWeek.atStartOfDay(zone).toInstant().toEpochMilli()

        val enrollment = makeEnrollment(currentWeekInPhase = 3) // absoluteWeek = 4
        val dao = FakeBootcampDao(
            activeEnrollment = enrollment,
            sessionsByWeek = mutableMapOf(
                4 to mutableListOf(
                    makeSession(id = 17L, weekNumber = 4, dayOfWeek = 1, status = STATUS_SCHEDULED),
                    makeSession(id = 15L, weekNumber = 4, dayOfWeek = 2, status = STATUS_COMPLETED, completedAtMs = tueOfPrevWeek),
                    makeSession(id = 16L, weekNumber = 4, dayOfWeek = 4, status = STATUS_COMPLETED, completedAtMs = thuOfPrevWeek),
                    makeSession(id = 18L, weekNumber = 4, dayOfWeek = 7, sessionType = "LONG", status = STATUS_COMPLETED, completedAtMs = sunOfPrevWeek)
                )
            )
        )
        val recoverer = makeRecoverer(dao)
        val engine = engineFor(enrollment)

        val outcome = recoverer.recover(
            enrollment = enrollment,
            engine = engine,
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = null
        )

        assertTrue("Expected Recovered, got $outcome", outcome is CalendarDriftRecoverer.Outcome.Recovered)
        val recovered = outcome as CalendarDriftRecoverer.Outcome.Recovered
        assertEquals(1, recovered.weeksAdvanced)

        // Residual id=17 was the only SCHEDULED — should now be SKIPPED, past-dated.
        val skipped = dao.getSession(17L)!!
        assertEquals(STATUS_SKIPPED, skipped.status)
        assertEquals(sundayEndOfDayMs, skipped.completedAtMs)

        // Engine advanced.
        assertEquals(4, dao.activeEnrollment?.currentWeekInPhase)

        // Next week (5) seeded with at least one session.
        assertTrue(dao.getSessionsForWeek(1L, 5).isNotEmpty())
    }

    @Test
    fun fresh_week_no_completions_no_ops() = runTest {
        val today = LocalDate.of(2026, 5, 11) // Mon, well past previous week
        val enrollment = makeEnrollment(currentWeekInPhase = 4)
        val dao = FakeBootcampDao(
            activeEnrollment = enrollment,
            sessionsByWeek = mutableMapOf(
                5 to mutableListOf(
                    makeSession(id = 19L, weekNumber = 5, dayOfWeek = 1, status = STATUS_SCHEDULED),
                    makeSession(id = 20L, weekNumber = 5, dayOfWeek = 3, status = STATUS_SCHEDULED),
                    makeSession(id = 21L, weekNumber = 5, dayOfWeek = 5, status = STATUS_SCHEDULED),
                    makeSession(id = 22L, weekNumber = 5, dayOfWeek = 7, sessionType = "LONG", status = STATUS_SCHEDULED)
                )
            )
        )
        val recoverer = makeRecoverer(dao)

        val outcome = recoverer.recover(
            enrollment = enrollment,
            engine = engineFor(enrollment),
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = null
        )

        assertEquals(CalendarDriftRecoverer.Outcome.NoChange, outcome)
        // No SKIP writes — all sessions still SCHEDULED.
        assertTrue(dao.getSessionsForWeek(1L, 5).all { it.status == STATUS_SCHEDULED })
    }

    @Test
    fun multi_week_drift_loops_until_no_completions() = runTest {
        // Engine at week 3. Week 3 has a completion in calendar week W; week 4 (which
        // exists in DB but engine hasn't advanced into yet) has a completion in
        // calendar week W+1; today is in calendar week W+2. Two advances expected.
        val today = LocalDate.of(2026, 5, 11) // Mon, calendar week W+2
        val w3CompletionMs = LocalDate.of(2026, 4, 26).atStartOfDay(zone).toInstant().toEpochMilli() // Sun in W
        val w4CompletionMs = LocalDate.of(2026, 5, 3).atStartOfDay(zone).toInstant().toEpochMilli()  // Sun in W+1

        val enrollment = makeEnrollment(currentWeekInPhase = 2) // absoluteWeek = 3
        val dao = FakeBootcampDao(
            activeEnrollment = enrollment,
            sessionsByWeek = mutableMapOf(
                3 to mutableListOf(
                    makeSession(id = 11L, weekNumber = 3, dayOfWeek = 7, sessionType = "LONG", status = STATUS_COMPLETED, completedAtMs = w3CompletionMs)
                ),
                4 to mutableListOf(
                    makeSession(id = 18L, weekNumber = 4, dayOfWeek = 7, sessionType = "LONG", status = STATUS_COMPLETED, completedAtMs = w4CompletionMs)
                )
            )
        )
        val recoverer = makeRecoverer(dao)

        val outcome = recoverer.recover(
            enrollment = enrollment,
            engine = engineFor(enrollment),
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = null
        )

        assertTrue("Expected Recovered, got $outcome", outcome is CalendarDriftRecoverer.Outcome.Recovered)
        val recovered = outcome as CalendarDriftRecoverer.Outcome.Recovered
        assertEquals("Expected to advance week 3 -> 4 -> 5", 2, recovered.weeksAdvanced)
        assertEquals(4, dao.activeEnrollment?.currentWeekInPhase)
    }

    @Test
    fun graduation_bailout_writes_no_skip() = runTest {
        // Set up an enrollment where the engine is on the last week of the last phase.
        // RACE_5K phaseArc: BASE, BUILD, PEAK, TAPER. Use TAPER's last weekInPhase.
        val today = LocalDate.of(2026, 7, 1)
        val tuningCompletionMs = LocalDate.of(2026, 6, 21).atStartOfDay(zone).toInstant().toEpochMilli()

        // Build an enrollment positioned at the end of TAPER. Compute by advancing
        // a fresh PhaseEngine until shouldAdvancePhase() becomes true at TAPER.
        // Position the engine so `shouldAdvancePhase()` returns true AND
        // `advancePhase()` returns null (race goal graduation).
        val tunedEnrollment = makeEnrollment(
            currentPhaseIndex = BootcampGoal.RACE_5K.phaseArc.lastIndex,
            currentWeekInPhase = PhaseEngine.phaseMidpointWeeks(BootcampGoal.RACE_5K.phaseArc.last())
        )
        val finalWeek = engineFor(tunedEnrollment).absoluteWeek

        val dao = FakeBootcampDao(
            activeEnrollment = tunedEnrollment,
            sessionsByWeek = mutableMapOf(
                finalWeek to mutableListOf(
                    makeSession(id = 100L, weekNumber = finalWeek, dayOfWeek = 1, status = STATUS_SCHEDULED),
                    makeSession(id = 101L, weekNumber = finalWeek, dayOfWeek = 7, sessionType = "LONG", status = STATUS_COMPLETED, completedAtMs = tuningCompletionMs)
                )
            )
        )
        val recoverer = makeRecoverer(dao)

        val outcome = recoverer.recover(
            enrollment = tunedEnrollment,
            engine = engineFor(tunedEnrollment),
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = null
        )

        // Graduating-edge: advancePhase() returns null, recoverer should bail
        // without skipping. Residual id=100 stays SCHEDULED.
        assertEquals(CalendarDriftRecoverer.Outcome.NoChange, outcome)
        assertEquals(STATUS_SCHEDULED, dao.getSession(100L)!!.status)
    }

    @Test
    fun active_workout_gates_recoverer() = runTest {
        val today = LocalDate.of(2026, 5, 4)
        val tueCompletionMs = LocalDate.of(2026, 4, 28).atStartOfDay(zone).toInstant().toEpochMilli()

        val enrollment = makeEnrollment(currentWeekInPhase = 3)
        val dao = FakeBootcampDao(
            activeEnrollment = enrollment,
            sessionsByWeek = mutableMapOf(
                4 to mutableListOf(
                    makeSession(id = 17L, weekNumber = 4, dayOfWeek = 1, status = STATUS_SCHEDULED),
                    makeSession(id = 15L, weekNumber = 4, dayOfWeek = 2, status = STATUS_COMPLETED, completedAtMs = tueCompletionMs)
                )
            )
        )
        val recoverer = makeRecoverer(dao)

        // 1) isWorkoutActive=true gates everything.
        val outcomeActive = recoverer.recover(
            enrollment = enrollment,
            engine = engineFor(enrollment),
            today = today,
            zone = zone,
            isWorkoutActive = true,
            pendingSessionId = null
        )
        assertEquals(CalendarDriftRecoverer.Outcome.NoChange, outcomeActive)
        assertEquals(STATUS_SCHEDULED, dao.getSession(17L)!!.status)

        // 2) pendingSessionId non-null gates everything (even if isWorkoutActive=false).
        val outcomePending = recoverer.recover(
            enrollment = enrollment,
            engine = engineFor(enrollment),
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = 17L
        )
        assertEquals(CalendarDriftRecoverer.Outcome.NoChange, outcomePending)
        assertEquals(STATUS_SCHEDULED, dao.getSession(17L)!!.status)
    }

    @Test
    fun same_calendar_week_as_latest_completion_no_ops() = runTest {
        // User just ran an hour ago — engine is in sync, recoverer must not fire
        // even though the engine-week has a residual SCHEDULED.
        val today = LocalDate.of(2026, 5, 6) // Wed
        val mondayOfThisWeek = LocalDate.of(2026, 5, 4) // Mon, same calendar week as today
        val justNowMs = mondayOfThisWeek.atTime(8, 30).atZone(zone).toInstant().toEpochMilli()

        val enrollment = makeEnrollment(currentWeekInPhase = 4) // absoluteWeek = 5
        val dao = FakeBootcampDao(
            activeEnrollment = enrollment,
            sessionsByWeek = mutableMapOf(
                5 to mutableListOf(
                    makeSession(id = 19L, weekNumber = 5, dayOfWeek = 1, status = STATUS_COMPLETED, completedAtMs = justNowMs),
                    makeSession(id = 20L, weekNumber = 5, dayOfWeek = 3, status = STATUS_SCHEDULED), // upcoming Wed run
                    makeSession(id = 21L, weekNumber = 5, dayOfWeek = 5, status = STATUS_SCHEDULED),
                    makeSession(id = 22L, weekNumber = 5, dayOfWeek = 7, sessionType = "LONG", status = STATUS_SCHEDULED)
                )
            )
        )
        val recoverer = makeRecoverer(dao)

        val outcome = recoverer.recover(
            enrollment = enrollment,
            engine = engineFor(enrollment),
            today = today,
            zone = zone,
            isWorkoutActive = false,
            pendingSessionId = null
        )

        assertEquals(CalendarDriftRecoverer.Outcome.NoChange, outcome)
        // Wed (id=20) MUST still be SCHEDULED — must not be auto-skipped.
        assertEquals(STATUS_SCHEDULED, dao.getSession(20L)!!.status)
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private val STATUS_SCHEDULED = BootcampSessionEntity.STATUS_SCHEDULED
    private val STATUS_COMPLETED = BootcampSessionEntity.STATUS_COMPLETED
    private val STATUS_SKIPPED = BootcampSessionEntity.STATUS_SKIPPED

    private fun makeEnrollment(
        id: Long = 1L,
        runsPerWeek: Int = 4,
        currentPhaseIndex: Int = 0,
        currentWeekInPhase: Int = 0,
        startDate: Long = LocalDate.of(2026, 4, 13)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    ) = BootcampEnrollmentEntity(
        id = id,
        goalType = BootcampGoal.RACE_5K.name,
        targetMinutesPerRun = 30,
        runsPerWeek = runsPerWeek,
        preferredDays = listOf(
            DayPreference(1, DaySelectionLevel.AVAILABLE),
            DayPreference(3, DaySelectionLevel.AVAILABLE),
            DayPreference(5, DaySelectionLevel.AVAILABLE),
            DayPreference(7, DaySelectionLevel.LONG_RUN_BIAS)
        ),
        startDate = startDate,
        currentPhaseIndex = currentPhaseIndex,
        currentWeekInPhase = currentWeekInPhase,
        status = BootcampEnrollmentEntity.STATUS_ACTIVE,
        tierIndex = 1,
        tierPromptDismissCount = 0,
        tierPromptSnoozedUntilMs = 0L
    )

    private fun makeSession(
        id: Long,
        enrollmentId: Long = 1L,
        weekNumber: Int,
        dayOfWeek: Int,
        sessionType: String = "EASY",
        status: String,
        completedAtMs: Long? = null
    ) = BootcampSessionEntity(
        id = id,
        enrollmentId = enrollmentId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        sessionType = sessionType,
        targetMinutes = 30,
        status = status,
        completedWorkoutId = null,
        presetId = null,
        completedAtMs = completedAtMs
    )

    private fun engineFor(e: BootcampEnrollmentEntity): PhaseEngine = PhaseEngine(
        goal = BootcampGoal.valueOf(e.goalType),
        phaseIndex = e.currentPhaseIndex,
        weekInPhase = e.currentWeekInPhase,
        runsPerWeek = e.runsPerWeek,
        targetMinutes = e.targetMinutesPerRun
    )
}

// FakeBootcampDao is shared from FakeBootcampDao.kt in this package.
