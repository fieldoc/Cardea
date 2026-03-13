package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.service.WorkoutState
import com.hrcoach.service.WorkoutSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostRunSummaryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val workoutRepository: WorkoutRepository = mockk(relaxed = true)
    private val workoutMetricsRepository: WorkoutMetricsRepository = mockk(relaxed = true)
    private val bootcampSessionCompleter: BootcampSessionCompleter = mockk(relaxed = true)
    private val achievementEvaluator: AchievementEvaluator = mockk(relaxed = true)
    private val achievementDao: AchievementDao = mockk(relaxed = true)
    private val bootcampRepository: BootcampRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun savedState(workoutId: Long = 1L, fresh: Boolean = true) =
        SavedStateHandle(mapOf("workoutId" to workoutId, "fresh" to fresh))

    private fun makeWorkout(id: Long = 1L) = WorkoutEntity(
        id = id,
        startTime = System.currentTimeMillis() - 1_800_000,
        endTime = System.currentTimeMillis(),
        totalDistanceMeters = 5000f,
        mode = "STEADY_STATE",
        targetConfig = ""
    )

    private fun stubDefaults(workoutId: Long = 1L) {
        coEvery { workoutRepository.getWorkoutById(workoutId) } returns makeWorkout(workoutId)
        coEvery { workoutRepository.getTrackPoints(workoutId) } returns emptyList()
        coEvery { workoutRepository.getAllWorkoutsOnce() } returns emptyList()
        coEvery { workoutRepository.sumAllDistanceKm() } returns 10.0
        coEvery { workoutMetricsRepository.getWorkoutMetrics(workoutId) } returns null
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        coEvery { achievementDao.getUnshownAchievements() } returns emptyList()
        coEvery { bootcampSessionCompleter.complete(any(), any(), any()) } returns
            BootcampSessionCompleter.CompletionResult(completed = false)
    }

    private fun buildVm(
        workoutId: Long = 1L,
        fresh: Boolean = true
    ): PostRunSummaryViewModel {
        return PostRunSummaryViewModel(
            savedStateHandle = savedState(workoutId, fresh),
            workoutRepository = workoutRepository,
            workoutMetricsRepository = workoutMetricsRepository,
            bootcampSessionCompleter = bootcampSessionCompleter,
            achievementEvaluator = achievementEvaluator,
            achievementDao = achievementDao,
            bootcampRepository = bootcampRepository
        )
    }

    @Test
    fun `loads workout data successfully`() = runTest {
        stubDefaults()

        val vm = buildVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue("distanceText should contain '5.00'", state.distanceText.contains("5.00"))
    }

    @Test
    fun `missing workout shows error`() = runTest {
        stubDefaults()
        coEvery { workoutRepository.getWorkoutById(1L) } returns null

        val vm = buildVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `fresh workout calls bootcamp completer`() = runTest {
        stubDefaults()
        WorkoutState.set(WorkoutSnapshot(pendingBootcampSessionId = 42L))
        coEvery { bootcampSessionCompleter.complete(1L, 42L, any()) } returns
            BootcampSessionCompleter.CompletionResult(
                completed = true,
                weekComplete = false,
                progressLabel = "1 of 3 sessions this week"
            )

        val vm = buildVm(fresh = true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isBootcampRun)
        assertEquals("1 of 3 sessions this week", state.bootcampProgressLabel)
        assertFalse(state.bootcampWeekComplete)
    }

    @Test
    fun `non-fresh workout skips bootcamp completion`() = runTest {
        stubDefaults()
        WorkoutState.set(WorkoutSnapshot(pendingBootcampSessionId = 42L))

        val vm = buildVm(fresh = false)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isBootcampRun)
        coVerify(exactly = 0) { bootcampSessionCompleter.complete(any(), any(), any()) }
    }

    @Test
    fun `non-fresh workout does not activate HRR`() = runTest {
        stubDefaults()

        val vm = buildVm(fresh = false)
        advanceUntilIdle()

        // Non-fresh should never set isHrrActive; the countdown never fires
        assertFalse(vm.uiState.value.isHrrActive)
    }
}
