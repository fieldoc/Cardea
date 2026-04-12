package com.hrcoach.ui.home

import app.cash.turbine.test
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import android.content.Context
import com.hrcoach.data.firebase.FirebasePartnerRepository
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
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepository = mockk(relaxed = true)
        bootcampRepository = mockk(relaxed = true)
        partnerRepository = mockk(relaxed = true)
        every { partnerRepository.observePartners() } returns flowOf(emptyList())
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(workoutRepository, bootcampRepository, partnerRepository, context)
    }

    @Test
    fun `default state has no active bootcamp`() = runTest {
        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)

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
            weekNumber = 1,
            dayOfWeek = 2,
            sessionType = "EASY_RUN",
            targetMinutes = 30,
        )

        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
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

        WorkoutState.set(WorkoutSnapshot(isRunning = true))

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isSessionRunning)
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

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.workoutsThisWeek)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
