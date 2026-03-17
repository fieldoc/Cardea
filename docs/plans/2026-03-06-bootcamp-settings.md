# Bootcamp Settings — Preferred Days + Extensible Dedicated Settings Screen

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a dedicated Bootcamp Settings screen (mirroring the AccountScreen style: scrollable GlassCards, TopAppBar with back button) accessible only from the `⋮` overflow menu in the Bootcamp dashboard. The first settings category allows users to change their preferred training days mid-program. The screen is designed with clearly labelled sections so future bootcamp-specific settings can be appended without touching existing logic.

**Architecture:**
- New nav route `bootcamp_settings` added to `NavGraph.kt`; navigated to from `BootcampScreen` via the overflow menu.
- New `BootcampSettingsScreen.kt` + `BootcampSettingsViewModel.kt` in `ui/bootcamp/` — keeps bootcamp UI contained in one package.
- `BootcampSettingsViewModel` is a separate `@HiltViewModel` (not shared with `BootcampViewModel`) — it reads the active enrollment once on init, owns draft state for all editable fields, and saves changes via `BootcampRepository`. The parent `BootcampViewModel` auto-refreshes via its existing DAO Flow when enrollment rows change.
- On save, `BootcampRepository.updatePreferredDays()` writes the new preferred days to the enrollment and re-slots any still-SCHEDULED sessions in the current week to the new day slots. Future weeks pick up new days automatically via the existing `ensureCurrentWeekSessions` path.
- The AccountScreen continues to own app-wide settings (audio, maps, profile). This screen owns bootcamp-program settings only.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Compose Navigation

---

### Task 1: Repository — `updatePreferredDays` with current-week re-slot

Adds the only new persistence logic needed. No new DAO methods — uses existing
`updateEnrollment`, `getSessionsForWeek`, and `updateSession`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/PreferredDayReslotTest.kt` (new)

**Background:**
`ensureCurrentWeekSessions` in `BootcampViewModel` returns early if *any* sessions exist for a
week. So re-slotting can't rely on re-seeding; we must explicitly reassign `dayOfWeek` on
still-SCHEDULED sessions. COMPLETED sessions are never moved (they already happened).
Future weeks haven't been seeded yet, so they pick up new preferred days automatically.

**Step 1: Write the failing test**

Create `app/src/test/java/com/hrcoach/data/repository/PreferredDayReslotTest.kt`:

```kotlin
package com.hrcoach.data.repository

import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredDayReslotTest {

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
        val newDays = listOf(2, 4, 7)

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(listOf(2, 4, 7), result.map { it.second })
    }

    @Test
    fun `reslot skips days already occupied by completed sessions`() {
        // Completed on Mon (1), scheduled on Wed (3) and Sat (6)
        val sessions = listOf(
            completedSession(1, 1),
            scheduledSession(2, 3),
            scheduledSession(3, 6)
        )
        // New desired days: Mon, Thu, Sat — Mon is already completed, so scheduled gets Thu and Sat
        val newDays = listOf(1, 4, 6)

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(listOf(4, 6), result.map { it.second })
    }

    @Test
    fun `reslot returns empty list when no scheduled sessions exist`() {
        val sessions = listOf(completedSession(1, 1), completedSession(2, 3))
        val newDays = listOf(2, 4)

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(emptyList<Pair<BootcampSessionEntity, Int>>(), result)
    }

    @Test
    fun `reslot falls back to original day when new days list is shorter than scheduled count`() {
        // Shouldn't happen in practice, but must not crash
        val sessions = listOf(scheduledSession(1, 1), scheduledSession(2, 3), scheduledSession(3, 6))
        val newDays = listOf(2, 5)

        val result = BootcampRepository.computeReslottedDays(sessions, newDays)

        assertEquals(3, result.size)
        assertEquals(6, result[2].second) // falls back to original day
    }
}
```

**Step 2: Run the test to verify it fails**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.PreferredDayReslotTest" -x lint
```

Expected: compile error — `computeReslottedDays` doesn't exist yet.

**Step 3: Add `computeReslottedDays` (pure, companion) + `updatePreferredDays` (suspend) to `BootcampRepository`**

In `BootcampRepository.kt`, add inside the `companion object`:

```kotlin
/**
 * Returns (session, newDayOfWeek) pairs for all SCHEDULED sessions, reassigning them to
 * [newDays] slots not already occupied by completed sessions.
 */
fun computeReslottedDays(
    sessions: List<BootcampSessionEntity>,
    newDays: List<Int>
): List<Pair<BootcampSessionEntity, Int>> {
    val completedDays = sessions
        .filter { it.status == BootcampSessionEntity.STATUS_COMPLETED }
        .map { it.dayOfWeek }
        .toSet()
    val availableNewDays = newDays.filter { it !in completedDays }
    val scheduled = sessions.filter { it.status == BootcampSessionEntity.STATUS_SCHEDULED }
    return scheduled.mapIndexed { i, session ->
        session to availableNewDays.getOrElse(i) { session.dayOfWeek }
    }
}
```

Then add outside the companion object (a suspend instance method):

```kotlin
/**
 * Persists [newDays] as the enrollment's preferred days, then re-slots any
 * SCHEDULED sessions in [currentWeekNumber] to the new day order.
 */
suspend fun updatePreferredDays(
    enrollmentId: Long,
    newDays: List<Int>,
    currentWeekNumber: Int
) {
    val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
    bootcampDao.updateEnrollment(
        enrollment.copy(preferredDays = newDays.joinToString(",", "[", "]"))
    )
    val currentWeekSessions = bootcampDao.getSessionsForWeek(enrollmentId, currentWeekNumber)
    computeReslottedDays(currentWeekSessions, newDays).forEach { (session, newDay) ->
        bootcampDao.updateSession(session.copy(dayOfWeek = newDay))
    }
}
```

**Step 4: Run the tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.PreferredDayReslotTest" -x lint
```

Expected: all 4 tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git add app/src/test/java/com/hrcoach/data/repository/PreferredDayReslotTest.kt
git commit -m "feat(bootcamp-settings): add updatePreferredDays with current-week re-slot logic"
```

---

### Task 2: `BootcampSettingsViewModel`

A thin `@HiltViewModel` that loads the active enrollment once, holds draft state for all
editable bootcamp settings, and saves via `BootcampRepository`.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt`

**Step 1: Create `BootcampSettingsUiState.kt`**

```kotlin
package com.hrcoach.ui.bootcamp

data class BootcampSettingsUiState(
    val isLoading: Boolean = true,
    val runsPerWeek: Int = 3,
    val preferredDays: List<Int> = emptyList(),   // currently saved days (1=Mon … 7=Sun)
    val editPreferredDays: List<Int> = emptyList(), // draft — user is editing this
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    /** True when the draft differs from saved and passes validation. */
    val canSave: Boolean
        get() = editPreferredDays.size == runsPerWeek && editPreferredDays != preferredDays
}
```

**Step 2: Create `BootcampSettingsViewModel.kt`**

```kotlin
package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.model.BootcampGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BootcampSettingsViewModel @Inject constructor(
    private val bootcampRepository: BootcampRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootcampSettingsUiState())
    val uiState: StateFlow<BootcampSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce()
            if (enrollment == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val days = BootcampEnrollmentEntity.parsePreferredDays(enrollment.preferredDays)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    runsPerWeek = enrollment.runsPerWeek,
                    preferredDays = days,
                    editPreferredDays = days
                )
            }
        }
    }

    fun togglePreferredDay(day: Int) {
        _uiState.update { state ->
            val current = state.editPreferredDays
            val updated = if (day in current) current - day else current + day
            state.copy(editPreferredDays = updated.sorted())
        }
    }

    fun savePreferredDays(onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
                val engine = PhaseEngine(
                    goal = BootcampGoal.valueOf(enrollment.goalType),
                    phaseIndex = enrollment.currentPhaseIndex,
                    weekInPhase = enrollment.currentWeekInPhase,
                    runsPerWeek = enrollment.runsPerWeek,
                    targetMinutes = enrollment.targetMinutesPerRun
                )
                bootcampRepository.updatePreferredDays(
                    enrollmentId = enrollment.id,
                    newDays = state.editPreferredDays,
                    currentWeekNumber = engine.absoluteWeek
                )
                _uiState.update { it.copy(isSaving = false) }
                onDone()
            } catch (t: Throwable) {
                _uiState.update { it.copy(isSaving = false, saveError = t.message ?: "Save failed") }
            }
        }
    }
}
```

**Step 3: Build to verify compilation**

```
./gradlew assembleDebug -x lint
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt
git commit -m "feat(bootcamp-settings): add BootcampSettingsViewModel and UiState"
```

---

### Task 3: `BootcampSettingsScreen.kt`

Full-screen settings composable matching AccountScreen's visual style: dark radial-gradient
background, `TopAppBar` with back chevron, scrollable column of `GlassCard` sections.
Each section is a clearly labelled card — mirrors the "Maps", "Profile", "Audio & Alerts" pattern
in AccountScreen so users see a consistent settings language across the app.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt`

**Step 1: Create the file**

```kotlin
package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassBorder

private val DAY_LETTER = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_FULL  = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootcampSettingsScreen(
    onBack: () -> Unit,
    viewModel: BootcampSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Program Settings",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                        center = Offset.Zero,
                        radius = 1800f
                    )
                )
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // ── Training Schedule ────────────────────────────────────
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Training Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Preferred Days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = buildString {
                                val count = state.editPreferredDays.size
                                append("Select exactly ${state.runsPerWeek} days · $count selected")
                                if (count != state.runsPerWeek) append(" ✕")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.editPreferredDays.size == state.runsPerWeek)
                                CardeaTextSecondary else Color(0xFFFF5A5F)
                        )
                        Spacer(Modifier.height(12.dp))
                        DayChipRow(
                            selectedDays = state.editPreferredDays,
                            onToggle = viewModel::togglePreferredDay
                        )
                        Spacer(Modifier.height(8.dp))
                        state.saveError?.let { err ->
                            Text(err, color = Color(0xFFFF5A5F), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.savePreferredDays(onDone = onBack) },
                                enabled = state.canSave && !state.isSaving
                            ) {
                                Text(
                                    "Save",
                                    color = if (state.canSave) Color.White else CardeaTextSecondary
                                )
                            }
                        }
                    }

                    // ── (Add future bootcamp setting cards below this line) ──

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DayChipRow(
    selectedDays: List<Int>,
    onToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..7).forEach { day ->
            val isSelected = day in selectedDays
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CardeaGradient else Color.Transparent)
                    .border(1.dp, if (isSelected) Color.Transparent else GlassBorder, CircleShape)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DAY_LETTER[day - 1],
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}
```

**Step 2: Build to verify compilation**

```
./gradlew assembleDebug -x lint
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt
git commit -m "feat(bootcamp-settings): add BootcampSettingsScreen with day-chip UI"
```

---

### Task 4: Register nav route + wire overflow menu

Adds `bootcamp_settings` as a nav destination and makes the `⋮` menu navigate to it.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

**Step 1: Add the route constant and composable destination in `NavGraph.kt`**

Find the block where other composable destinations are declared (look for `composable("history")`,
`composable("account")`, etc.). Add:

```kotlin
composable("bootcamp_settings") {
    BootcampSettingsScreen(onBack = { navController.popBackStack() })
}
```

Also add the import at the top:
```kotlin
import com.hrcoach.ui.bootcamp.BootcampSettingsScreen
```

**Step 2: Update `BootcampScreen` to accept an `onNavigateToSettings` callback**

`BootcampScreen` already has `onStartWorkout` and `onBack` parameters. Add one more:

```kotlin
@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,           // <-- new
    viewModel: BootcampViewModel = hiltViewModel()
) {
```

**Step 3: Add "Settings" as the first item in the `⋮` `DropdownMenu`**

Locate the `DropdownMenu { ... }` block in `BootcampScreen`. Add at the very top, before
the Pause/Resume item:

```kotlin
DropdownMenuItem(
    text = { Text("Settings") },
    onClick = {
        showMenu = false
        onNavigateToSettings()
    }
)
```

**Step 4: Update the `NavGraph.kt` call site for `BootcampScreen`**

Find where `BootcampScreen(...)` is called inside `NavGraph.kt`. Pass the new callback:

```kotlin
BootcampScreen(
    onStartWorkout = { /* existing */ },
    onBack = { /* existing */ },
    onNavigateToSettings = { navController.navigate("bootcamp_settings") }
)
```

**Step 5: Build + run all unit tests**

```
./gradlew assembleDebug -x lint
./gradlew testDebugUnitTest -x lint
```

Expected: BUILD SUCCESSFUL, all tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp-settings): register bootcamp_settings route and wire overflow menu"
```

---

## Done Criteria

- [ ] `BootcampRepository.computeReslottedDays` is a pure function that passes all 4 unit tests
- [ ] `BootcampRepository.updatePreferredDays` persists new days and re-slots SCHEDULED sessions in current week
- [ ] `BootcampSettingsViewModel` loads enrollment on init, holds draft `editPreferredDays`, saves via repository
- [ ] `BootcampSettingsUiState.canSave` is `true` only when count matches `runsPerWeek` AND draft differs from saved
- [ ] `BootcampSettingsScreen` matches AccountScreen visual style (radial-gradient bg, GlassCards, TopAppBar + back button)
- [ ] Day chip row shows 7 toggleable chips (M–S), selected chips use `CardeaGradient` fill
- [ ] `⋮` overflow menu in BootcampScreen has "Settings" as its first item
- [ ] Navigating to Settings → saving → back navigates correctly; parent BootcampScreen refreshes via DAO Flow
- [ ] `./gradlew testDebugUnitTest` PASS, `./gradlew assembleDebug` BUILD SUCCESSFUL

## Extension Points — future bootcamp settings (append below "Training Schedule" card)

| Setting | Notes |
|---|---|
| Target minutes per run | Slider, updates `targetMinutesPerRun` in enrollment. Needs `BootcampRepository.updateTargetMinutes`. |
| Session reminders | Notification toggle per preferred day; needs `NotificationSettingsRepository`. |
| Runs per week change | Complex — deletes excess sessions or seeds extra; design separately before implementing. |
