package com.hrcoach.ui.home

import app.cash.turbine.test
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.bootcamp.CalendarDriftRecoverer
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import android.content.Context
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.repository.UserProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var bootcampRepository: BootcampRepository
    private lateinit var partnerRepository: FirebasePartnerRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var calendarDriftRecoverer: CalendarDriftRecoverer
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepository = mockk(relaxed = true)
        bootcampRepository = mockk(relaxed = true)
        partnerRepository = mockk(relaxed = true)
        userProfileRepository = mockk(relaxed = true)
        calendarDriftRecoverer = mockk(relaxed = true)
        every { partnerRepository.observePartners() } returns flowOf(emptyList())
        every { userProfileRepository.getDistanceUnit() } returns "km"
        coEvery {
            calendarDriftRecoverer.recover(any(), any(), any(), any(), any(), any())
        } returns CalendarDriftRecoverer.Outcome.NoChange
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            workoutRepository,
            bootcampRepository,
            partnerRepository,
            userProfileRepository,
            calendarDriftRecoverer,
            context
        )
    }

    @Test
    fun `default state has no active bootcamp`() = runTest {
        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(null)

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.hasActiveBootcamp)
            assertEquals(null, state.nextSession)
            assertEquals(0, state.sessionStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `active enrollment is reflected in state`() = runTest {
        val enrollment = BootcampEnrollmentEntity(
            id = 1L,
            goalType = "RACE_5K",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(
                DayPreference(2, DaySelectionLevel.AVAILABLE),
                DayPreference(4, DaySelectionLevel.AVAILABLE),
                DayPreference(6, DaySelectionLevel.AVAILABLE),
            ),
            startDate = System.currentTimeMillis(),
            currentPhaseIndex = 0,
            currentWeekInPhase = 0,
            status = "ACTIVE",
            tierIndex = 1,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L,
        )

        val nextSession = BootcampSessionEntity(
            id = 10L,
            enrollmentId = 1L,
            weekNumber = 2,
            dayOfWeek = 2,
            sessionType = "EASY_RUN",
            targetMinutes = 30,
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getNextSession(1L) } returns nextSession
        coEvery { bootcampRepository.getScheduledAndDeferredSessions(1L) } returns listOf(nextSession)
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(1L) } returns listOf(nextSession)

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.hasActiveBootcamp)
            assertEquals(nextSession, state.nextSession)
            assertEquals(3, state.weeklyTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isSessionRunning reflects WorkoutState`() = runTest {
        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(null)

        WorkoutState.set(WorkoutSnapshot(isRunning = true))

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isSessionRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paused enrollment surfaces Paused lifecycle state with progress`() = runTest {
        val enrollment = BootcampEnrollmentEntity(
            id = 7L,
            goalType = "MARATHON",
            targetMinutesPerRun = 60,
            runsPerWeek = 4,
            preferredDays = listOf(DayPreference(2, DaySelectionLevel.AVAILABLE)),
            startDate = System.currentTimeMillis(),
            currentPhaseIndex = 1,
            currentWeekInPhase = 2,
            status = "PAUSED",
            tierIndex = 2,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L,
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getCompletedSessionCount(7L) } returns 15
        coEvery { bootcampRepository.getTotalSessionCount(7L) } returns 26
        coEvery { bootcampRepository.getScheduledAndDeferredSessions(7L) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(7L) } returns emptyList()

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.hasActiveBootcamp)
            val paused = state.bootcampState
            assertTrue(paused is HomeBootcampState.Paused)
            paused as HomeBootcampState.Paused
            assertEquals(15, paused.sessionsDone)
            assertEquals(26, paused.sessionsTotal)
            assertEquals(7L, paused.enrollment.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `graduated enrollment surfaces Graduated lifecycle with stats and km conversion`() = runTest {
        val enrollment = BootcampEnrollmentEntity(
            id = 9L,
            goalType = "MARATHON",
            targetMinutesPerRun = 60,
            runsPerWeek = 4,
            preferredDays = listOf(DayPreference(2, DaySelectionLevel.AVAILABLE)),
            startDate = System.currentTimeMillis(),
            currentPhaseIndex = 3,
            currentWeekInPhase = 3,
            status = "GRADUATED",
            tierIndex = 3,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L,
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getCompletedSessionCount(9L) } returns 72
        coEvery { bootcampRepository.sumCompletedWorkoutDistanceMeters(9L) } returns 487_500.0
        coEvery { bootcampRepository.countConsecutiveCompletedWeeks(9L) } returns 18
        coEvery { bootcampRepository.getScheduledAndDeferredSessions(9L) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(9L) } returns emptyList()

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.hasActiveBootcamp)
            val graduated = state.bootcampState
            assertTrue(graduated is HomeBootcampState.Graduated)
            graduated as HomeBootcampState.Graduated
            assertEquals(18, graduated.weeksCompleted)
            assertEquals(72, graduated.sessionsCompleted)
            // 487500m → 487.5km exact
            assertEquals(487.5, graduated.totalKm, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `evergreen graduated row is defensively promoted to Active not Graduated`() = runTest {
        // EVERGREEN/CARDIO_HEALTH should never reach GRADUATED. If a data anomaly exists,
        // the VM treats it as Active rather than triggering the triumph hero.
        val enrollment = BootcampEnrollmentEntity(
            id = 11L,
            goalType = "CARDIO_HEALTH",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(DayPreference(2, DaySelectionLevel.AVAILABLE)),
            startDate = System.currentTimeMillis(),
            currentPhaseIndex = 0,
            currentWeekInPhase = 0,
            status = "GRADUATED", // anomaly
            tierIndex = 1,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L,
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getScheduledAndDeferredSessions(11L) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(11L) } returns emptyList()

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            // EVERGREEN/CARDIO_HEALTH should be promoted back to Active, not surfaced as Graduated
            assertFalse(state.bootcampState is HomeBootcampState.Graduated)
            assertTrue(state.bootcampState is HomeBootcampState.Active)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `workoutsThisWeek counts workouts from current week`() = runTest {
        val now = System.currentTimeMillis()
        // Create workouts: one recent (within this week), one old (30 days ago)
        val recentWorkout = WorkoutEntity(
            id = 1L,
            startTime = now - 60_000L, // 1 minute ago
            endTime = now,
            totalDistanceMeters = 5000f,
            mode = "FREE_RUN",
            targetConfig = "",
        )
        val oldWorkout = WorkoutEntity(
            id = 2L,
            startTime = now - 30L * 24 * 60 * 60 * 1000, // 30 days ago
            endTime = now - 30L * 24 * 60 * 60 * 1000 + 60_000L,
            totalDistanceMeters = 3000f,
            mode = "FREE_RUN",
            targetConfig = "",
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(listOf(recentWorkout, oldWorkout))
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        every { bootcampRepository.getLatestEnrollmentAnyStatus() } returns flowOf(null)

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.workoutsThisWeek)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
