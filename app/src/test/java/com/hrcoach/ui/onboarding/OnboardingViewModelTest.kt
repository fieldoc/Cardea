package com.hrcoach.ui.onboarding

import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var onboardingRepo: OnboardingRepository
    private lateinit var userProfileRepo: UserProfileRepository
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        onboardingRepo = mockk(relaxed = true)
        userProfileRepo = mockk(relaxed = true) {
            every { getAge() } returns null
            every { getWeight() } returns null
            every { getWeightUnit() } returns "lbs"
            every { getMaxHr() } returns null
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = OnboardingViewModel(onboardingRepo, userProfileRepo)

    @Test
    fun `initial state has empty fields and page 0`() {
        vm = createVm()
        val state = vm.uiState.value
        assertEquals(0, state.currentPage)
        assertEquals("", state.age)
        assertEquals("", state.weight)
        assertEquals(WeightUnit.LBS, state.weightUnit)
        assertNull(state.estimatedHrMax)
        assertFalse(state.isHrMaxOverrideExpanded)
    }

    @Test
    fun `onAgeChanged updates age and recalculates HRmax`() {
        vm = createVm()
        vm.onAgeChanged("32")
        val state = vm.uiState.value
        assertEquals("32", state.age)
        assertEquals(188, state.estimatedHrMax)
    }

    @Test
    fun `onAgeChanged with invalid input sets null HRmax`() {
        vm = createVm()
        vm.onAgeChanged("abc")
        assertNull(vm.uiState.value.estimatedHrMax)
    }

    @Test
    fun `onAgeChanged with out-of-range age sets null HRmax`() {
        vm = createVm()
        vm.onAgeChanged("5")
        assertNull(vm.uiState.value.estimatedHrMax)
    }

    @Test
    fun `onWeightChanged updates weight`() {
        vm = createVm()
        vm.onWeightChanged("165")
        assertEquals("165", vm.uiState.value.weight)
    }

    @Test
    fun `toggleWeightUnit switches between lbs and kg`() {
        vm = createVm()
        assertEquals(WeightUnit.LBS, vm.uiState.value.weightUnit)
        vm.toggleWeightUnit()
        assertEquals(WeightUnit.KG, vm.uiState.value.weightUnit)
        vm.toggleWeightUnit()
        assertEquals(WeightUnit.LBS, vm.uiState.value.weightUnit)
    }

    @Test
    fun `onHrMaxOverrideChanged updates override`() {
        vm = createVm()
        vm.onHrMaxOverrideChanged("195")
        assertEquals("195", vm.uiState.value.hrMaxOverride)
    }

    @Test
    fun `effectiveHrMax returns override when valid, otherwise estimated`() {
        vm = createVm()
        vm.onAgeChanged("32") // estimated = 188
        assertEquals(188, vm.effectiveHrMax())

        vm.onHrMaxOverrideChanged("195") // valid override
        assertEquals(195, vm.effectiveHrMax())

        vm.onHrMaxOverrideChanged("50") // out of range — fall back to estimated
        assertEquals(188, vm.effectiveHrMax())
    }

    @Test
    fun `saveProfile persists age, weight, and HRmax to UserProfileRepository`() = runTest {
        vm = createVm()
        vm.onAgeChanged("32")
        vm.onWeightChanged("165")

        vm.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { userProfileRepo.setAge(32) }
        verify { userProfileRepo.setWeight(165) }
        verify { userProfileRepo.setMaxHr(188) }
        verify { userProfileRepo.setWeightUnit("lbs") }
    }

    @Test
    fun `saveProfile skips weight when blank`() = runTest {
        vm = createVm()
        vm.onAgeChanged("25")
        // weight left blank

        vm.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { userProfileRepo.setAge(25) }
        verify(exactly = 0) { userProfileRepo.setWeight(any()) }
    }

    @Test
    fun `completeOnboarding sets flag`() {
        vm = createVm()
        vm.completeOnboarding()
        verify { onboardingRepo.setOnboardingCompleted() }
    }

    @Test
    fun `isAgeValid returns true for valid age`() {
        vm = createVm()
        vm.onAgeChanged("32")
        assertTrue(vm.isAgeValid())
    }

    @Test
    fun `isAgeValid returns false for empty or invalid`() {
        vm = createVm()
        assertFalse(vm.isAgeValid())
        vm.onAgeChanged("abc")
        assertFalse(vm.isAgeValid())
    }
}
