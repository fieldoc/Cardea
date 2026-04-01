# Onboarding & First-Run Tutorial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an 8-screen first-run onboarding flow between splash and home, with contextual permission requests, user profile collection, HR zone education, BLE guidance, spotlight tab tour, and bootcamp launch funnel.

**Architecture:** Linear `HorizontalPager` with 8 pages, gated by a `onboarding_completed` SharedPreferences flag. The `OnboardingViewModel` manages profile input, permission state, and page navigation. Permissions move from a batch request in `MainActivity` to contextual per-screen requests. Screen 7 uses a `BlendMode.Clear` spotlight overlay on the real bottom nav bar.

**Tech Stack:** Kotlin, Jetpack Compose (`HorizontalPager`), Hilt, SharedPreferences, Room (count query for upgrade path), JUnit4 + MockK + Turbine for tests.

**Spec:** `docs/superpowers/specs/2026-03-31-onboarding-first-run-design.md`

---

## File Map

### New Files

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/hrcoach/data/repository/OnboardingRepository.kt` | SharedPreferences wrapper for `onboarding_completed` flag + upgrade-path detection |
| `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt` | Profile input state, HRmax calculation, permission tracking, page navigation, save-to-repos |
| `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingScreen.kt` | HorizontalPager shell, progress dots, skip button, per-page CTA buttons |
| `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt` | 8 page composables: WelcomePage, ProfilePage, ZonesPage, BlePage, GpsPage, AlertsPage, TabTourPage, LaunchPadPage |
| `app/src/main/java/com/hrcoach/ui/onboarding/SpotlightOverlay.kt` | Reusable spotlight scrim + pulsing ring + tooltip composable for Screen 7 |
| `app/src/test/java/com/hrcoach/data/repository/OnboardingRepositoryTest.kt` | Unit tests for flag persistence and upgrade-path logic |
| `app/src/test/java/com/hrcoach/ui/onboarding/OnboardingViewModelTest.kt` | Unit tests for profile validation, HRmax calc, save logic |

### Modified Files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add explicit `androidx.compose.foundation:foundation` dependency |
| `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt` | Add age + weight fields (getAge/setAge, getWeight/setWeight, getWeightUnit/setWeightUnit) |
| `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt` | Add `getWorkoutCount(): Int` query |
| `app/src/main/java/com/hrcoach/util/PermissionGate.kt` | Add `blePermissions()`, `locationPermissions()`, `notificationPermissions()` group methods |
| `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` | Add `Routes.ONBOARDING`, modify splash destination to branch on onboarding flag, add onboarding composable route |
| `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt` | Change `onFinished: () -> Unit` to `onFinished: (onboardingCompleted: Boolean) -> Unit` so NavGraph can route |
| `app/src/main/java/com/hrcoach/MainActivity.kt` | Remove batch permission request from `onCreate` (safety net in SetupScreen remains) |

---

## Task 1: Add Foundation Dependency + UserProfileRepository Age/Weight Fields

**Files:**
- Modify: `app/build.gradle.kts:69` (add foundation dep)
- Modify: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/UserProfileRepositoryTest.kt`

- [ ] **Step 1: Add foundation dependency to build.gradle.kts**

In `app/build.gradle.kts`, after line `implementation("androidx.compose.ui:ui")` (line 69), add:

```kotlin
implementation("androidx.compose.foundation:foundation")
```

- [ ] **Step 2: Add age, weight, and weightUnit fields to UserProfileRepository**

In `UserProfileRepository.kt`, add these constants to the companion object:

```kotlin
private const val PREF_AGE = "age"
private const val PREF_WEIGHT = "weight"
private const val PREF_WEIGHT_UNIT = "weight_unit" // "lbs" or "kg"
```

Add these methods after the existing ones:

```kotlin
@Synchronized
fun getAge(): Int? {
    val stored = prefs.getInt(PREF_AGE, UNSET)
    return if (stored == UNSET) null else stored
}

@Synchronized
fun setAge(age: Int) {
    require(age in 13..99) { "age must be in 13..99" }
    prefs.edit().putInt(PREF_AGE, age).apply()
}

@Synchronized
fun getWeight(): Int? {
    val stored = prefs.getInt(PREF_WEIGHT, UNSET)
    return if (stored == UNSET) null else stored
}

@Synchronized
fun setWeight(weight: Int) {
    require(weight in 27..400) { "weight must be in 27..400" }
    prefs.edit().putInt(PREF_WEIGHT, weight).apply()
}

@Synchronized
fun getWeightUnit(): String {
    return prefs.getString(PREF_WEIGHT_UNIT, "lbs") ?: "lbs"
}

@Synchronized
fun setWeightUnit(unit: String) {
    require(unit == "lbs" || unit == "kg") { "unit must be 'lbs' or 'kg'" }
    prefs.edit().putString(PREF_WEIGHT_UNIT, unit).apply()
}
```

- [ ] **Step 3: Write tests for the new fields**

Create or extend `app/src/test/java/com/hrcoach/data/repository/UserProfileRepositoryTest.kt`:

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repo: UserProfileRepository

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true) {
            every { edit() } returns editor
            every { editor.putInt(any(), any()) } returns editor
            every { editor.putString(any(), any()) } returns editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences("hr_coach_user_profile", Context.MODE_PRIVATE) } returns prefs
        }
        repo = UserProfileRepository(context)
    }

    @Test
    fun `getAge returns null when unset`() {
        every { prefs.getInt("age", -1) } returns -1
        assertNull(repo.getAge())
    }

    @Test
    fun `setAge stores value and getAge returns it`() {
        every { prefs.getInt("age", -1) } returns 32
        repo.setAge(32)
        verify { editor.putInt("age", 32) }
        assertEquals(32, repo.getAge())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAge rejects value below 13`() {
        repo.setAge(12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAge rejects value above 99`() {
        repo.setAge(100)
    }

    @Test
    fun `getWeight returns null when unset`() {
        every { prefs.getInt("weight", -1) } returns -1
        assertNull(repo.getWeight())
    }

    @Test
    fun `setWeight stores value`() {
        repo.setWeight(165)
        verify { editor.putInt("weight", 165) }
    }

    @Test
    fun `getWeightUnit defaults to lbs`() {
        every { prefs.getString("weight_unit", "lbs") } returns "lbs"
        assertEquals("lbs", repo.getWeightUnit())
    }

    @Test
    fun `setWeightUnit stores kg`() {
        repo.setWeightUnit("kg")
        verify { editor.putString("weight_unit", "kg") }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setWeightUnit rejects invalid unit`() {
        repo.setWeightUnit("stones")
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.UserProfileRepositoryTest" --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt app/src/test/java/com/hrcoach/data/repository/UserProfileRepositoryTest.kt
git commit -m "feat(onboarding): add age/weight fields to UserProfileRepository + foundation dep"
```

---

## Task 2: OnboardingRepository + WorkoutDao Count Query

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/OnboardingRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/OnboardingRepositoryTest.kt`

- [ ] **Step 1: Add getWorkoutCount to WorkoutDao**

In `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt`, add:

```kotlin
@Query("SELECT COUNT(*) FROM workouts")
suspend fun getWorkoutCount(): Int
```

- [ ] **Step 2: Create OnboardingRepository**

Create `app/src/main/java/com/hrcoach/data/repository/OnboardingRepository.kt`:

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import com.hrcoach.data.db.WorkoutDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val workoutDao: WorkoutDao,
) {
    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_COMPLETED = "onboarding_completed"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    @Synchronized
    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    /**
     * For existing users upgrading to a version with onboarding:
     * if they already have workout data, auto-complete onboarding.
     */
    suspend fun autoCompleteForExistingUser(): Boolean {
        if (isOnboardingCompleted()) return true
        val count = workoutDao.getWorkoutCount()
        if (count > 0) {
            setOnboardingCompleted()
            return true
        }
        return false
    }
}
```

- [ ] **Step 3: Write OnboardingRepository tests**

Create `app/src/test/java/com/hrcoach/data/repository/OnboardingRepositoryTest.kt`:

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hrcoach.data.db.WorkoutDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var workoutDao: WorkoutDao
    private lateinit var repo: OnboardingRepository

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true) {
            every { edit() } returns editor
            every { editor.putBoolean(any(), any()) } returns editor
        }
        workoutDao = mockk(relaxed = true)
        val context = mockk<Context> {
            every { getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE) } returns prefs
        }
        repo = OnboardingRepository(context, workoutDao)
    }

    @Test
    fun `isOnboardingCompleted returns false by default`() {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        assertFalse(repo.isOnboardingCompleted())
    }

    @Test
    fun `setOnboardingCompleted sets flag to true`() {
        repo.setOnboardingCompleted()
        verify { editor.putBoolean("onboarding_completed", true) }
    }

    @Test
    fun `autoCompleteForExistingUser returns true if already completed`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns true
        assertTrue(repo.autoCompleteForExistingUser())
    }

    @Test
    fun `autoCompleteForExistingUser auto-completes when workouts exist`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        coEvery { workoutDao.getWorkoutCount() } returns 3
        assertTrue(repo.autoCompleteForExistingUser())
        verify { editor.putBoolean("onboarding_completed", true) }
    }

    @Test
    fun `autoCompleteForExistingUser returns false for new user with no workouts`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        coEvery { workoutDao.getWorkoutCount() } returns 0
        assertFalse(repo.autoCompleteForExistingUser())
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.OnboardingRepositoryTest" --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/OnboardingRepository.kt app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt app/src/test/java/com/hrcoach/data/repository/OnboardingRepositoryTest.kt
git commit -m "feat(onboarding): add OnboardingRepository + WorkoutDao count query"
```

---

## Task 3: PermissionGate Group Methods

**Files:**
- Modify: `app/src/main/java/com/hrcoach/util/PermissionGate.kt`

- [ ] **Step 1: Add permission group helper methods**

In `PermissionGate.kt`, add these methods inside the `PermissionGate` object:

```kotlin
/** Bluetooth permissions — only needed on Android 12+ (API 31). */
fun blePermissions(): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
    return listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
}

/** Location permissions for GPS tracking. */
fun locationPermissions(): List<String> = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/** Notification permission — only needed on Android 13+ (API 33). */
fun notificationPermissions(): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
    return listOf(Manifest.permission.POST_NOTIFICATIONS)
}

/** Check if a specific permission group is fully granted. */
fun hasPermissions(context: Context, permissions: List<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/util/PermissionGate.kt
git commit -m "feat(onboarding): add per-group permission helpers to PermissionGate"
```

---

## Task 4: OnboardingViewModel

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt`
- Test: `app/src/test/java/com/hrcoach/ui/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Write failing tests for OnboardingViewModel**

Create `app/src/test/java/com/hrcoach/ui/onboarding/OnboardingViewModelTest.kt`:

```kotlin
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.onboarding.OnboardingViewModelTest" --info`
Expected: FAIL — `OnboardingViewModel` does not exist.

- [ ] **Step 3: Create OnboardingViewModel**

Create `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt`:

```kotlin
package com.hrcoach.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WeightUnit { LBS, KG }

data class OnboardingUiState(
    val currentPage: Int = 0,
    val age: String = "",
    val weight: String = "",
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val estimatedHrMax: Int? = null,
    val hrMaxOverride: String = "",
    val isHrMaxOverrideExpanded: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onAgeChanged(value: String) {
        val age = value.toIntOrNull()
        val estimated = if (age != null && age in 13..99) 220 - age else null
        _uiState.update { it.copy(age = value, estimatedHrMax = estimated) }
    }

    fun onWeightChanged(value: String) {
        _uiState.update { it.copy(weight = value) }
    }

    fun toggleWeightUnit() {
        _uiState.update {
            it.copy(weightUnit = if (it.weightUnit == WeightUnit.LBS) WeightUnit.KG else WeightUnit.LBS)
        }
    }

    fun onHrMaxOverrideChanged(value: String) {
        _uiState.update { it.copy(hrMaxOverride = value) }
    }

    fun toggleHrMaxOverride() {
        _uiState.update { it.copy(isHrMaxOverrideExpanded = !it.isHrMaxOverrideExpanded) }
    }

    fun effectiveHrMax(): Int? {
        val override = _uiState.value.hrMaxOverride.toIntOrNull()
        if (override != null && override in 120..220) return override
        return _uiState.value.estimatedHrMax
    }

    fun isAgeValid(): Boolean {
        val age = _uiState.value.age.toIntOrNull() ?: return false
        return age in 13..99
    }

    fun saveProfile() {
        viewModelScope.launch {
            val state = _uiState.value
            val age = state.age.toIntOrNull() ?: return@launch
            if (age !in 13..99) return@launch

            userProfileRepository.setAge(age)

            val weight = state.weight.toIntOrNull()
            if (weight != null && weight in 27..400) {
                userProfileRepository.setWeight(weight)
            }
            userProfileRepository.setWeightUnit(
                if (state.weightUnit == WeightUnit.LBS) "lbs" else "kg"
            )

            val hrMax = effectiveHrMax()
            if (hrMax != null && hrMax in 100..220) {
                userProfileRepository.setMaxHr(hrMax)
            }
        }
    }

    fun onPermissionResult(type: PermissionType, granted: Boolean) {
        _uiState.update {
            when (type) {
                PermissionType.BLUETOOTH -> it.copy(bluetoothPermissionGranted = granted)
                PermissionType.LOCATION -> it.copy(locationPermissionGranted = granted)
                PermissionType.NOTIFICATION -> it.copy(notificationPermissionGranted = granted)
            }
        }
    }

    fun completeOnboarding() {
        onboardingRepository.setOnboardingCompleted()
    }
}

enum class PermissionType { BLUETOOTH, LOCATION, NOTIFICATION }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.onboarding.OnboardingViewModelTest" --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt app/src/test/java/com/hrcoach/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): add OnboardingViewModel with profile input and permission tracking"
```

---

## Task 5: OnboardingScreen Shell (HorizontalPager + Navigation)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt`

- [ ] **Step 1: Create OnboardingScreen composable**

Create `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingScreen.kt`:

```kotlin
package com.hrcoach.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.ui.theme.*
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 8

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToBootcamp: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun advancePage() {
        scope.launch {
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    fun skip() {
        viewModel.completeOnboarding()
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaBgPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip button (hidden on last page)
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, end = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Skip",
                        color = CardeaTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { skip() }
                            .padding(8.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProfilePage(
                        uiState = uiState,
                        onAgeChanged = viewModel::onAgeChanged,
                        onWeightChanged = viewModel::onWeightChanged,
                        onToggleWeightUnit = viewModel::toggleWeightUnit,
                        onHrMaxOverrideChanged = viewModel::onHrMaxOverrideChanged,
                        onToggleHrMaxOverride = viewModel::toggleHrMaxOverride,
                    )
                    2 -> ZonesPage(effectiveHrMax = viewModel.effectiveHrMax())
                    3 -> BlePage(
                        permissionGranted = uiState.bluetoothPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.BLUETOOTH, granted)
                        },
                    )
                    4 -> GpsPage(
                        permissionGranted = uiState.locationPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.LOCATION, granted)
                        },
                    )
                    5 -> AlertsPage(
                        permissionGranted = uiState.notificationPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.NOTIFICATION, granted)
                        },
                    )
                    6 -> TabTourPage()
                    7 -> LaunchPadPage(
                        onStartBootcamp = {
                            viewModel.completeOnboarding()
                            onNavigateToBootcamp()
                        },
                        onExploreFirst = {
                            viewModel.completeOnboarding()
                            onNavigateToHome()
                        },
                    )
                }
            }

            // Bottom: CTA button + progress dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // CTA button (not on last page — it has its own CTAs)
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    val buttonText = when (pagerState.currentPage) {
                        0 -> "Get Started"
                        1 -> if (viewModel.isAgeValid()) "Next" else "Enter your age to continue"
                        else -> "Next"
                    }
                    val enabled = pagerState.currentPage != 1 || viewModel.isAgeValid()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enabled) CardeaCtaGradient
                                else Brush.linearGradient(listOf(CardeaTextTertiary, CardeaTextTertiary))
                            )
                            .then(
                                if (enabled) Modifier.clickable {
                                    if (pagerState.currentPage == 1) viewModel.saveProfile()
                                    advancePage()
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = buttonText,
                            color = if (enabled) CardeaTextPrimary else CardeaTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    repeat(PAGE_COUNT) { index ->
                        val isActive = index == pagerState.currentPage
                        val width by animateDpAsState(
                            targetValue = if (isActive) 20.dp else 6.dp,
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) CardeaCtaGradient
                                    else Brush.linearGradient(
                                        listOf(CardeaTextTertiary, CardeaTextTertiary)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add OnboardingSplashViewModel to OnboardingViewModel.kt**

SplashScreen's signature stays unchanged (`onFinished: () -> Unit`). The branching logic lives in NavGraph, which uses a tiny ViewModel to access the onboarding flag.

At the bottom of `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt`, add:

```kotlin
@HiltViewModel
class OnboardingSplashViewModel @Inject constructor(
    val onboardingRepository: OnboardingRepository,
) : ViewModel()
```

- [ ] **Step 3: Modify NavGraph for onboarding route and splash branching**

In `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`:

Add imports:
```kotlin
import com.hrcoach.ui.onboarding.OnboardingSplashViewModel
import com.hrcoach.ui.onboarding.OnboardingScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
```

Add `ONBOARDING` to the Routes object:
```kotlin
const val ONBOARDING = "onboarding"
```

**Replace** the splash composable body (the `SplashScreen(onFinished = { ... })` block, around lines 260-267) with:

```kotlin
val splashVm: OnboardingSplashViewModel = hiltViewModel()
var destination by remember { mutableStateOf<String?>(null) }

LaunchedEffect(Unit) {
    val completed = splashVm.onboardingRepository.autoCompleteForExistingUser()
    destination = if (completed) Routes.HOME else Routes.ONBOARDING
}

SplashScreen(
    onFinished = {
        val dest = destination ?: Routes.ONBOARDING
        navController.navigate(dest) {
            popUpTo(Routes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }
)
```

**Add the onboarding composable route** after the splash route:

```kotlin
composable(
    route = Routes.ONBOARDING,
    enterTransition = { fadeIn(animationSpec = tween(NavDurationMs)) },
    exitTransition = { fadeOut(animationSpec = tween(NavDurationMs)) },
) {
    OnboardingScreen(
        onNavigateToHome = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
                launchSingleTop = true
            }
        },
        onNavigateToBootcamp = {
            navController.navigate(Routes.BOOTCAMP) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
                launchSingleTop = true
            }
        },
    )
}
```

Also: hide bottom nav bar during onboarding. Find the `showBottomBar` condition (around line 155) and add `Routes.ONBOARDING`:

```kotlin
val showBottomBar = currentRoute != Routes.SPLASH
    && currentRoute != Routes.WORKOUT
    && currentRoute != Routes.ONBOARDING
    && !isWorkoutRunning
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (pages composables don't exist yet, but OnboardingScreen references them — need placeholder stubs).

**Note:** This step will fail until Task 6 provides the page composables. The implementer should create stub composables first:

Create `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt` with stub composables:

```kotlin
package com.hrcoach.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable fun WelcomePage() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Welcome") } }
@Composable fun ProfilePage(uiState: OnboardingUiState, onAgeChanged: (String) -> Unit, onWeightChanged: (String) -> Unit, onToggleWeightUnit: () -> Unit, onHrMaxOverrideChanged: (String) -> Unit, onToggleHrMaxOverride: () -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Profile") } }
@Composable fun ZonesPage(effectiveHrMax: Int?) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Zones") } }
@Composable fun BlePage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("BLE") } }
@Composable fun GpsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("GPS") } }
@Composable fun AlertsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Alerts") } }
@Composable fun TabTourPage() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Tab Tour") } }
@Composable fun LaunchPadPage(onStartBootcamp: () -> Unit, onExploreFirst: () -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Launch Pad") } }
```

Then build should succeed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/OnboardingScreen.kt app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt app/src/main/java/com/hrcoach/ui/onboarding/OnboardingViewModel.kt app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(onboarding): add OnboardingScreen shell, NavGraph routing, stub pages"
```

---

## Task 6: Onboarding Pages — Welcome, Profile, Zones (Screens 1-3)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt` (replace stubs)

- [ ] **Step 1: Replace WelcomePage stub**

Replace the `WelcomePage` stub in `OnboardingPages.kt` with the full implementation. Use:
- `CardeaLogo(size = 120.dp, animate = true)` with a `Box` for a gradient glow ring behind it
- "Meet Cardea" headline in white, 28sp, FontWeight.ExtraBold
- Subtext in `CardeaTextSecondary`, 14sp, max-width 260dp
- "Train Smarter. Run Stronger." tagline in `CardeaTextTertiary`, 11sp, uppercase, letter-spacing 2sp
- Full-screen `Column` centered vertically on `CardeaBgPrimary` background

Refer to the elevated visual mockup at `.superpowers/brainstorm/1982-1775012231/onboarding-elevated.html` (Screen 1) for the exact visual treatment.

- [ ] **Step 2: Replace ProfilePage stub**

Replace with full implementation:
- Headline + subtitle at top
- Age input: `OutlinedTextField` with `KeyboardType.Number`, glass-style border
- Weight input: `OutlinedTextField` with unit toggle chip ("lbs" / "kg") using `toggleWeightUnit` callback
- HRmax card: `GlassCard` with a gradient top-border accent (use `Modifier.drawBehind` to draw a 2dp gradient line at top). Show `estimatedHrMax` in large gradient text (36sp). Formula explanation in `CardeaTextTertiary`. "I know my actual HRmax" link in `CardeaTextSecondary` that calls `onToggleHrMaxOverride`, revealing an `OutlinedTextField` for override.

- [ ] **Step 3: Replace ZonesPage stub**

Replace with full implementation:
- Headline + subtitle
- Continuous zone color bar (5 equal `Box`es in a `Row`, 6dp height, each with zone color)
- 5 zone rows in a `Column`: zone badge (`Box` with zone color, 36x28dp, rounded 8dp, "Z1"-"Z5" text), zone name + description, and BPM range in `JetBrains Mono` (or monospace). Calculate BPM ranges from `effectiveHrMax` parameter: Z1 = 50-60%, Z2 = 60-70%, Z3 = 70-80%, Z4 = 80-90%, Z5 = 90-100%. If `effectiveHrMax` is null, show percentage only.
- Footer text: "BPM ranges based on your max of X bpm" in `CardeaTextTertiary`

- [ ] **Step 4: Verify build and visual check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Install on device/emulator and verify the first 3 pages render correctly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt
git commit -m "feat(onboarding): implement Welcome, Profile, and Zones pages"
```

---

## Task 7: Onboarding Pages — BLE, GPS, Alerts (Screens 4-6)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt`

These three pages follow a similar pattern: educational content + contextual permission request.

- [ ] **Step 1: Replace BlePage stub**

Implement Screen 4:
- Headline "Your HR Monitor" + subtitle
- Animated BLE illustration: `Canvas` drawing concentric circles with expanding/fading animation (`infiniteTransition`, scale 0.8→1.2, alpha 0.6→0). Bluetooth icon `Box` in center.
- Green compatibility card (`GlassCard` with `ZoneGreen`-tinted background): checkmark icon + "Works with Bluetooth (BLE)" + description
- Red compatibility card (`GlassCard` with `ZoneRed`-tinted background): X icon + "ANT+ only — not supported" + description
- Info callout: points to Workout tab
- Permission request: use `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`. On page entry (`LaunchedEffect`), check if BLE permissions are already granted via `PermissionGate.hasPermissions(context, PermissionGate.blePermissions())`. If not, show a "Continue" button that launches the permission request. On result, call `onPermissionResult(allGranted)`.

**Important:** Only request if `PermissionGate.blePermissions()` is non-empty (empty on Android < 12).

- [ ] **Step 2: Replace GpsPage stub**

Implement Screen 5:
- Headline "Track Your Runs" + subtitle
- Route illustration: `Canvas`-drawn route path with gradient stroke (`CardeaGradient`), dot-grid background, pulsing end marker
- Metric strip: 3 items (5.2 km, 5:42 /km, 29:38 time) in a `Row`, monospace values
- Permission request: `PermissionGate.locationPermissions()` via `RequestMultiplePermissions()` launcher

- [ ] **Step 3: Replace AlertsPage stub**

Implement Screen 6:
- Headline "Stay in the Zone" + subtitle
- 3 alert cards: In Zone (green tint), Ease Up (amber tint), Pick It Up (red tint). Each has an icon, title, description, and a small waveform `Canvas` drawing.
- Music note callout: "Alerts layer over your music without pausing it"
- Permission request: `PermissionGate.notificationPermissions()` (empty on Android < 13)

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt
git commit -m "feat(onboarding): implement BLE, GPS, and Alerts pages with contextual permissions"
```

---

## Task 8: Spotlight Overlay + Tab Tour Page (Screen 7)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/onboarding/SpotlightOverlay.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt` (replace TabTourPage stub)

- [ ] **Step 1: Create SpotlightOverlay composable**

Create `app/src/main/java/com/hrcoach/ui/onboarding/SpotlightOverlay.kt`:

```kotlin
package com.hrcoach.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.*

/**
 * Spotlight overlay that dims everything except a circle at [targetOffset].
 * Shows a pulsing ring and a tooltip bubble above the spotlight.
 */
@Composable
fun SpotlightOverlay(
    targetOffset: Offset,
    spotlightRadius: Dp = 32.dp,
    ringColor: Color = GradientCyan,
    tooltipName: String,
    tooltipDescription: String,
    useGradientName: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { spotlightRadius.toPx() }

    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim with circle cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            // Draw full-screen dark scrim
            drawRect(Color.Black.copy(alpha = 0.75f))
            // Cut out circle at target
            drawCircle(
                color = Color.Transparent,
                radius = radiusPx,
                center = targetOffset,
                blendMode = BlendMode.Clear
            )
        }

        // Pulsing ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = radiusPx * ringScale,
                center = targetOffset,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Tooltip bubble above spotlight
        if (targetOffset != Offset.Zero) {
            val tooltipY = targetOffset.y - with(density) { (spotlightRadius + 56.dp).toPx() }
            val tooltipX = targetOffset.x

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (tooltipX - with(density) { 100.dp.toPx() }).toInt(),
                            y = tooltipY.toInt()
                        )
                    }
                    .width(200.dp)
                    .background(
                        CardeaBgSecondary,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tooltipName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (useGradientName) GradientRed else CardeaTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tooltipDescription,
                        fontSize = 11.sp,
                        color = CardeaTextSecondary,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
```

- [ ] **Step 2: Replace TabTourPage stub**

In `OnboardingPages.kt`, replace `TabTourPage` stub with:

```kotlin
@Composable
fun TabTourPage() {
    val tabs = remember {
        listOf(
            TabInfo("Home", "Your dashboard — training status, streaks, next session", Icons.Filled.Home, false),
            TabInfo("Workout", "Start runs, connect HR monitor, bootcamp sessions", Icons.Filled.FavoriteBorder, true),
            TabInfo("History", "Past runs with route maps and HR charts", Icons.AutoMirrored.Filled.List, false),
            TabInfo("Progress", "Trends, volume, and fitness analytics", Icons.AutoMirrored.Filled.ShowChart, false),
            TabInfo("Account", "Settings, audio, theme, and profile", Icons.Filled.Person, false),
        )
    }

    var currentTabIndex by remember { mutableIntStateOf(0) }
    val tabPositions = remember { mutableStateMapOf<Int, Offset>() }

    // Auto-advance
    LaunchedEffect(currentTabIndex) {
        val delayMs = if (tabs[currentTabIndex].isHighlighted) 3500L else 2500L
        delay(delayMs)
        currentTabIndex = (currentTabIndex + 1) % tabs.size
    }

    val currentTab = tabs[currentTabIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content — tab info
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Counter
            Text(
                text = "${currentTabIndex + 1} of ${tabs.size}",
                color = CardeaTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 24.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Tab icon (large)
            Icon(
                imageVector = currentTab.icon,
                contentDescription = currentTab.name,
                tint = if (currentTab.isHighlighted) GradientRed else CardeaTextPrimary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tab name
            Text(
                text = currentTab.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentTab.isHighlighted) GradientRed else CardeaTextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Tab description
            Text(
                text = currentTab.description,
                fontSize = 14.sp,
                color = CardeaTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Mock bottom nav bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(CardeaBgSecondary),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { currentTabIndex = index }
                            .onGloballyPositioned { coords ->
                                val pos = coords.localToRoot(Offset.Zero)
                                val center = Offset(
                                    pos.x + coords.size.width / 2f,
                                    pos.y + coords.size.height / 2f
                                )
                                tabPositions[index] = center
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.name,
                            tint = if (index == currentTabIndex) CardeaTextPrimary else CardeaTextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = tab.name,
                            fontSize = 10.sp,
                            color = if (index == currentTabIndex) CardeaTextPrimary else CardeaTextTertiary,
                        )
                    }
                }
            }
        }

        // Spotlight overlay
        val targetOffset = tabPositions[currentTabIndex] ?: Offset.Zero
        if (targetOffset != Offset.Zero) {
            SpotlightOverlay(
                targetOffset = targetOffset,
                ringColor = if (currentTab.isHighlighted) GradientRed else GradientCyan,
                tooltipName = currentTab.name,
                tooltipDescription = currentTab.description,
                useGradientName = currentTab.isHighlighted,
            )
        }
    }
}

private data class TabInfo(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isHighlighted: Boolean,
)
```

Add necessary imports to `OnboardingPages.kt`:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/SpotlightOverlay.kt app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt
git commit -m "feat(onboarding): implement SpotlightOverlay + TabTourPage with auto-advancing coach marks"
```

---

## Task 9: Launch Pad Page (Screen 8)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt`

- [ ] **Step 1: Replace LaunchPadPage stub**

Replace with full implementation:
- Centered layout with:
  - Gradient checkmark circle (72dp, `CardeaCtaGradient` background, white checkmark SVG path drawn on `Canvas` or using `Icon`)
  - Pulsing outer ring animation (same pattern as SpotlightOverlay ring)
  - "You're All Set!" headline, 26sp, FontWeight.ExtraBold
  - Subtext about Bootcamp in `CardeaTextSecondary`
  - "Start Bootcamp Setup" button: full-width, `CardeaCtaGradient`, rounded 14dp, calls `onStartBootcamp`
  - "Explore the app first" text link: `CardeaTextTertiary`, calls `onExploreFirst`

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt
git commit -m "feat(onboarding): implement LaunchPad page with bootcamp CTA"
```

---

## Task 10: Remove Batch Permission Request from MainActivity

**Files:**
- Modify: `app/src/main/java/com/hrcoach/MainActivity.kt`

- [ ] **Step 1: Remove the batch permission request from onCreate**

In `MainActivity.kt`, remove lines 78-81:

```kotlin
val missingPermissions = PermissionGate.missingRuntimePermissions(this)
if (missingPermissions.isNotEmpty()) {
    permissionLauncher.launch(missingPermissions.toTypedArray())
}
```

Keep the `permissionLauncher` definition — it may still be useful for the Settings intent fallback. Or if it's now unused, remove it too.

**Important:** The existing permission checks in `SetupScreen` (before starting a workout) remain as the safety net. Do NOT remove those.

- [ ] **Step 2: Verify build and confirm SetupScreen safety net is intact**

Run: `./gradlew assembleDebug`

Read `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` and confirm it still calls `PermissionGate.hasWorkoutPermissions()` or equivalent before launching a workout.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/MainActivity.kt
git commit -m "refactor(onboarding): remove batch permission request from MainActivity

Permissions are now requested contextually during onboarding flow.
SetupScreen safety-net permission check remains unchanged."
```

---

## Task 11: Integration Build + Visual QA

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL with no warnings related to onboarding.

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew testDebugUnitTest --info`
Expected: All tests PASS, including new onboarding tests.

- [ ] **Step 3: Install and test on device/emulator**

Test the following flows:
1. **Fresh install → onboarding shown** — clear app data, launch, verify splash → onboarding → 8 pages work
2. **Profile page** — enter age, verify HRmax auto-calculates, verify weight unit toggle, verify "Next" is disabled without age
3. **Permission pages** — verify pre-explainer text, verify OS dialogs appear, verify denial doesn't block progression
4. **Tab tour** — verify spotlight overlay renders, auto-advances, Workout tab gets gradient highlight
5. **Launch pad** — verify "Start Bootcamp" navigates to bootcamp wizard, verify "Explore first" goes to HomeScreen
6. **Skip** — verify skip on any page goes to HomeScreen, verify onboarding doesn't show again on re-launch
7. **Existing user upgrade** — with existing workouts, verify onboarding is auto-skipped

- [ ] **Step 4: Commit any fixes from QA**

```bash
git add -A
git commit -m "fix(onboarding): QA fixes from integration testing"
```

---

## Task 12: Final Commit + Summary

- [ ] **Step 1: Run full test suite one more time**

Run: `./gradlew testDebugUnitTest`
Expected: All PASS.

- [ ] **Step 2: Verify git log**

Run: `git log --oneline -10`

Expected commits (newest first):
- `fix(onboarding): QA fixes from integration testing` (if any)
- `refactor(onboarding): remove batch permission request from MainActivity`
- `feat(onboarding): implement LaunchPad page with bootcamp CTA`
- `feat(onboarding): implement SpotlightOverlay + TabTourPage with auto-advancing coach marks`
- `feat(onboarding): implement BLE, GPS, and Alerts pages with contextual permissions`
- `feat(onboarding): implement Welcome, Profile, and Zones pages`
- `feat(onboarding): add OnboardingScreen shell, NavGraph routing, stub pages`
- `feat(onboarding): add OnboardingViewModel with profile input and permission tracking`
- `feat(onboarding): add OnboardingRepository + WorkoutDao count query`
- `feat(onboarding): add age/weight fields to UserProfileRepository + foundation dep`
