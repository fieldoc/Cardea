# Bootcamp Exit / Delete Flow Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the broken pause flow (pausing currently destroys the visible state because the DAO only queries `status = 'ACTIVE'`), add a proper resume path, and add a destructive "end program" flow with confirmation.

**Architecture:** The fix is entirely additive except for two query string changes in `BootcampDao`. No schema migration is needed. A `MoreVert` (⋮) overflow icon is added to `PhaseHeader`; it opens a `DropdownMenu` with context-sensitive "Pause / Resume program" and "End program…" items. A separate `PausedCard` composable replaces the dead `StatusCard` for the paused state and includes a prominent Resume button.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Coroutines. All tests are JVM unit tests run with `./gradlew test`. Build validation with `./gradlew assembleDebug`.

---

## Bug context (read before starting)

`BootcampDao.getActiveEnrollment()` currently queries `WHERE status = 'ACTIVE'`. When `pauseEnrollment()` flips status to `PAUSED`, the Flow re-emits `null`, and `BootcampViewModel.loadBootcampState()` resets the screen to the "Start my program" state — making the paused enrollment completely invisible with no resume path. This is the root cause.

---

### Task 1: Fix BootcampDao — let PAUSED enrollments through + add delete

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt`

**Step 1: Apply the two query fixes and add deleteEnrollment**

Replace the entire file contents with:

```kotlin
package com.hrcoach.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BootcampDao {
    @Insert
    suspend fun insertEnrollment(enrollment: BootcampEnrollmentEntity): Long

    @Update
    suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity)

    @Delete
    suspend fun deleteEnrollment(enrollment: BootcampEnrollmentEntity)

    // Changed: was status = 'ACTIVE', now includes PAUSED so paused dashboards stay visible
    @Query("SELECT * FROM bootcamp_enrollments WHERE status IN ('ACTIVE', 'PAUSED') LIMIT 1")
    fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?>

    @Query("SELECT * FROM bootcamp_enrollments WHERE status IN ('ACTIVE', 'PAUSED') LIMIT 1")
    suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity?

    @Query("SELECT * FROM bootcamp_enrollments WHERE id = :id")
    suspend fun getEnrollment(id: Long): BootcampEnrollmentEntity?

    @Insert
    suspend fun insertSession(session: BootcampSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<BootcampSessionEntity>)

    @Update
    suspend fun updateSession(session: BootcampSessionEntity)

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId ORDER BY weekNumber, dayOfWeek")
    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber = :week ORDER BY dayOfWeek")
    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'SCHEDULED' ORDER BY weekNumber, dayOfWeek LIMIT 1")
    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'COMPLETED' ORDER BY weekNumber DESC, dayOfWeek DESC LIMIT 1")
    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("DELETE FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber > :weekNumber")
    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int)
}
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampDao.kt
git commit -m "fix(bootcamp): include PAUSED enrollments in DAO queries; add deleteEnrollment"
```

---

### Task 2: Update BootcampRepository — add resumeEnrollment and deleteEnrollment

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`

**Step 1: Add the two new methods after `pauseEnrollment`**

Find the existing `pauseEnrollment` method (line ~35):

```kotlin
    suspend fun pauseEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(enrollment.copy(status = BootcampEnrollmentEntity.STATUS_PAUSED))
    }
```

Add the following two methods immediately after it:

```kotlin
    suspend fun resumeEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(enrollment.copy(status = BootcampEnrollmentEntity.STATUS_ACTIVE))
    }

    suspend fun deleteEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        // CASCADE foreign key on bootcamp_sessions deletes all sessions automatically
        bootcampDao.deleteEnrollment(enrollment)
    }
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git commit -m "feat(bootcamp): add resumeEnrollment and deleteEnrollment to repository"
```

---

### Task 3: Update BootcampUiState — add showDeleteConfirmDialog field

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`

**Step 1: Add the new field**

In `BootcampUiState`, add `val showDeleteConfirmDialog: Boolean = false` after the `missedSession` field. The end of the data class should look like:

```kotlin
    val scheduledRestDay: Boolean = false,
    val missedSession: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false
)
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt
git commit -m "feat(bootcamp): add showDeleteConfirmDialog field to BootcampUiState"
```

---

### Task 4: Update BootcampViewModel — add resume, delete, and dialog methods

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

**Step 1: Add the four new methods**

Find the existing `pauseBootcamp()` method (around line 280):

```kotlin
    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
            _uiState.update { it.copy(isPaused = true) }
        }
    }
```

The `_uiState.update { it.copy(isPaused = true) }` line was a manual patch that is now unnecessary — the DAO fix means the Flow re-emits the updated enrollment and `refreshFromEnrollment` sets `isPaused` correctly. Remove the manual update line so `pauseBootcamp` reads:

```kotlin
    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
        }
    }
```

Then add the following four methods immediately after `pauseBootcamp`:

```kotlin
    fun resumeBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.resumeEnrollment(enrollment.id)
        }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            WorkoutState.setPendingBootcampSessionId(null)
            bootcampRepository.deleteEnrollment(enrollment.id)
            _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        }
    }
```

**Step 2: Run existing tests to verify no regressions**

```bash
./gradlew test
```

Expected: All existing tests pass (BUILD SUCCESSFUL). The new VM methods are not directly unit-tested here because a ViewModel test would require a full coroutine test harness and mocked repository — the functional correctness is verified by the build and the manual UI test in Task 5.

**Step 3: Verify build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
git commit -m "feat(bootcamp): add resumeBootcamp, deleteBootcamp, and dialog toggle methods"
```

---

### Task 5: Update BootcampScreen — overflow menu, paused card, delete dialog

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

This task has several sub-steps. Apply them in order.

---

**Step 1: Add missing imports at the top of BootcampScreen.kt**

After the existing import block, add these (merge with existing imports alphabetically — do not duplicate):

```kotlin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

---

**Step 2: Update ActiveBootcampDashboard signature and wiring**

Find `ActiveBootcampDashboard` (around line 558). Replace its signature and body with the following. Key changes: `onPause` is replaced by `onPause` + `onResume` + `onEndProgram`; the `StatusCard` for paused is replaced by `PausedCard`; `NextSessionCard` is hidden when paused; the pause `TextButton` at the bottom is removed (the ⋮ menu now owns that action).

```kotlin
@Composable
private fun ActiveBootcampDashboard(
    uiState: BootcampUiState,
    onStartWorkout: (configJson: String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onBootcampWorkoutStarting: () -> Unit,
    onDismissTierPrompt: () -> Unit,
    onAcceptTierChange: (TierPromptDirection) -> Unit,
    onConfirmIllness: () -> Unit,
    onDismissIllness: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phase header with overflow menu
        PhaseHeader(
            uiState = uiState,
            onPause = onPause,
            onResume = onResume,
            onEndProgram = onEndProgram
        )

        // Paused state — shows resume button instead of a dead status message
        if (uiState.isPaused) {
            PausedCard(onResume = onResume)
        }

        if (uiState.missedSession) {
            StatusCard(
                title = "Missed session detected",
                detail = "You have at least one earlier session this week that is still incomplete."
            )
        }
        if (uiState.scheduledRestDay) {
            StatusCard(
                title = "Scheduled rest day",
                detail = "No run is scheduled for today. Recovery supports adaptation."
            )
        }

        if (uiState.tierPromptDirection != TierPromptDirection.NONE) {
            TierPromptCard(
                direction = uiState.tierPromptDirection,
                evidence = uiState.tierPromptEvidence,
                onAccept = onAcceptTierChange,
                onDismiss = onDismissTierPrompt
            )
        } else if (uiState.illnessFlag) {
            IllnessPromptCard(
                onConfirm = onConfirmIllness,
                onDismiss = onDismissIllness
            )
        }

        // Week sessions list
        WeekSessionList(sessions = uiState.currentWeekSessions)

        // Next session CTA — hidden when paused (no point starting a run)
        val nextSession = uiState.nextSession
        if (nextSession != null && !uiState.isPaused) {
            NextSessionCard(
                session = nextSession,
                dayLabel = uiState.nextSessionDayLabel,
                onStartWorkout = { configJson ->
                    onBootcampWorkoutStarting()
                    onStartWorkout(configJson)
                }
            )
        }
    }
}
```

---

**Step 3: Replace PhaseHeader to add the overflow menu**

Find `PhaseHeader` (around line 659). Replace the entire function with:

```kotlin
@Composable
private fun PhaseHeader(
    uiState: BootcampUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val goal = uiState.goal
                if (goal != null) {
                    Text(
                        text = goalDisplayName(goal),
                        style = MaterialTheme.typography.labelMedium,
                        color = GradientPink
                    )
                }
                val phase = uiState.currentPhase
                Text(
                    text = if (phase != null) {
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks} — ${phase.displayName}"
                    } else {
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CardeaTextPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isRecoveryWeek) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassHighlight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Recovery",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ZoneGreen
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Program options",
                            tint = CardeaTextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (uiState.isPaused) {
                            DropdownMenuItem(
                                text = { Text("Resume program") },
                                onClick = {
                                    menuExpanded = false
                                    onResume()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause program") },
                                onClick = {
                                    menuExpanded = false
                                    onPause()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "End program…",
                                    color = GradientRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEndProgram()
                            }
                        )
                    }
                }
            }
        }

        // Progress bar
        val progress = if (uiState.totalWeeks > 0) uiState.absoluteWeek.toFloat() / uiState.totalWeeks else 0f
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GlassHighlight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaGradient)
            )
        }
    }
}
```

---

**Step 4: Add PausedCard composable**

Add this new composable immediately after the existing `StatusCard` composable (around line 657):

```kotlin
@Composable
private fun PausedCard(onResume: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Program paused",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your schedule is on hold. Resume whenever you're ready.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        CardeaButton(
            text = "Resume Program",
            onClick = onResume,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}
```

---

**Step 5: Add DeleteConfirmDialog composable**

Add this composable immediately before the `// ─── Helpers ─` section at the bottom of the file:

```kotlin
// ─── Delete Confirmation Dialog ─────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "End this program?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTextPrimary
            )
        },
        text = {
            Text(
                text = "Your schedule and progress will be permanently deleted. Your completed run history stays in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("End Program", color = GradientRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep it", color = CardeaTextSecondary)
            }
        },
        containerColor = CardeaBgSecondary,
        shape = RoundedCornerShape(18.dp)
    )
}
```

---

**Step 6: Wire up callbacks in BootcampScreen**

Find the `else ->` branch in `BootcampScreen` (around line 120) where `ActiveBootcampDashboard` is called. Replace it with:

```kotlin
                else -> {
                    ActiveBootcampDashboard(
                        uiState = uiState,
                        onStartWorkout = onStartWorkout,
                        onPause = { viewModel.pauseBootcamp() },
                        onResume = { viewModel.resumeBootcamp() },
                        onEndProgram = { viewModel.showDeleteConfirmDialog() },
                        onBootcampWorkoutStarting = viewModel::onBootcampWorkoutStarting,
                        onDismissTierPrompt = viewModel::dismissTierPrompt,
                        onAcceptTierChange = viewModel::acceptTierChange,
                        onConfirmIllness = viewModel::confirmIllness,
                        onDismissIllness = viewModel::dismissIllness
                    )
                }
```

Then, add the `DeleteConfirmDialog` call alongside the existing `WelcomeBackDialog` call (still inside the top-level `Box`, after the `when` block):

```kotlin
            // Welcome-back dialog shown on top of any state
            if (uiState.welcomeBackMessage != null) {
                WelcomeBackDialog(
                    message = uiState.welcomeBackMessage!!,
                    onDismiss = { viewModel.dismissWelcomeBack() }
                )
            }

            // Delete confirmation dialog
            if (uiState.showDeleteConfirmDialog) {
                DeleteConfirmDialog(
                    onConfirm = { viewModel.deleteBootcamp() },
                    onDismiss = { viewModel.dismissDeleteConfirmDialog() }
                )
            }
```

---

**Step 7: Verify build and all existing tests**

```bash
./gradlew test
```

Expected: All existing tests pass.

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors or warnings about unused imports.

**Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp): add overflow menu with pause/resume/delete, fix paused dashboard"
```

---

## Manual verification checklist

After installing the debug APK on a device or emulator:

1. **Active state** — Open Bootcamp tab. Tap ⋮ in the header. Menu shows "Pause program" and "End program…".
2. **Pause** — Tap "Pause program". Screen stays on Bootcamp dashboard (does NOT reset to "Start my program"). A "Program paused" card appears with a "Resume Program" button. "Next Session" card is gone. Tap ⋮ again — menu now shows "Resume program" instead of "Pause program".
3. **Resume via card button** — Tap "Resume Program" in the paused card. Dashboard returns to normal active state. Next Session card reappears.
4. **Resume via menu** — Pause again. Tap ⋮ → "Resume program". Same result.
5. **Delete — cancel** — Tap ⋮ → "End program…". Confirmation dialog appears. Tap "Keep it". Dialog dismisses. Program still active.
6. **Delete — confirm** — Tap ⋮ → "End program…". Tap "End Program". Program is deleted. Screen transitions to "Start my program". Completed workout history is still present in the History tab.
7. **Re-enroll** — Tap "Start my program". Complete onboarding. New program starts cleanly.
