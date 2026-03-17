# Engine Wiring & UX Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the 5 orphaned domain engine objects into the workout pipeline, surface the ActiveSessionCard on Home, and implement long-press delete in History.

**Architecture:** All engine wiring happens inside `WorkoutForegroundService.stopWorkout()` — a section that already loads track points, builds metrics, and saves the profile. Tasks 2–4 extend that section sequentially; each reads/writes `currentProfile` and `metricsToSave`. Tasks 5–6 are independent UI changes. AdaptiveProfile gains two optional fields (`hrRest`, `lastTuningDirection`) with Gson-safe nullable defaults.

**Tech Stack:** Kotlin, Hilt, Room, Jetpack Compose, Gson (profile JSON), `kotlinx-coroutines`

---

## Chunk 1: Commit orphaned files

### Task 1: Git-add the 8 existing domain + UI files

**Files:**
- Stage only: `app/src/main/java/com/hrcoach/domain/engine/HrArtifactDetector.kt`
- Stage only: `app/src/main/java/com/hrcoach/domain/engine/HrCalibrator.kt`
- Stage only: `app/src/main/java/com/hrcoach/domain/engine/EnvironmentFlagDetector.kt`
- Stage only: `app/src/main/java/com/hrcoach/domain/engine/FitnessLoadCalculator.kt`
- Stage only: `app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt`
- Stage only: `app/src/main/java/com/hrcoach/domain/bootcamp/TierCtlRanges.kt`
- Stage only: `app/src/main/java/com/hrcoach/ui/components/ActiveSessionCard.kt`
- Stage only: `app/src/main/java/com/hrcoach/ui/components/CardeaLoadingScreen.kt`
- Stage only: `app/src/test/java/com/hrcoach/domain/engine/HrArtifactDetectorTest.kt`
- Stage only: `app/src/test/java/com/hrcoach/domain/engine/HrCalibratorTest.kt`
- Stage only: `app/src/test/java/com/hrcoach/domain/engine/EnvironmentFlagDetectorTest.kt`
- Stage only: `app/src/test/java/com/hrcoach/domain/engine/FitnessLoadCalculatorTest.kt`
- Stage only: `app/src/test/java/com/hrcoach/domain/engine/FitnessSignalEvaluatorTest.kt`

- [ ] **Step 1: Run existing tests to confirm green baseline**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/HRapp
./gradlew.bat :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL` (or only pre-existing failures unrelated to engine files)

- [ ] **Step 2: Commit orphaned files**

```bash
git add \
  app/src/main/java/com/hrcoach/domain/engine/HrArtifactDetector.kt \
  app/src/main/java/com/hrcoach/domain/engine/HrCalibrator.kt \
  app/src/main/java/com/hrcoach/domain/engine/EnvironmentFlagDetector.kt \
  app/src/main/java/com/hrcoach/domain/engine/FitnessLoadCalculator.kt \
  app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt \
  app/src/main/java/com/hrcoach/domain/bootcamp/TierCtlRanges.kt \
  app/src/main/java/com/hrcoach/ui/components/ActiveSessionCard.kt \
  app/src/main/java/com/hrcoach/ui/components/CardeaLoadingScreen.kt \
  app/src/test/java/com/hrcoach/domain/engine/HrArtifactDetectorTest.kt \
  app/src/test/java/com/hrcoach/domain/engine/HrCalibratorTest.kt \
  app/src/test/java/com/hrcoach/domain/engine/EnvironmentFlagDetectorTest.kt \
  app/src/test/java/com/hrcoach/domain/engine/FitnessLoadCalculatorTest.kt \
  app/src/test/java/com/hrcoach/domain/engine/FitnessSignalEvaluatorTest.kt
git commit -m "feat(engine): add adaptive engine phase 2 objects and UI components"
```

---

## Chunk 2: Artifact detection + calibration wiring

### Task 2: Extend AdaptiveProfile + wire HrArtifactDetector and HrCalibrator

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Add `hrRest` to AdaptiveProfile**

In `app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt`, add `hrRest` after `hrMaxIsCalibrated`:

```kotlin
data class AdaptiveProfile(
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val paceHrBuckets: Map<Int, PaceHrBucket> = emptyMap(),
    val totalSessions: Int = 0,
    val ctl: Float = 0f,
    val atl: Float = 0f,
    val hrMax: Int? = null,
    val hrMaxIsCalibrated: Boolean = false,
    val hrRest: Float? = null,
)
```

- [ ] **Step 2: Add HR sample buffer fields to WorkoutForegroundService**

After the existing `private var hrSampleSum` declarations (around line 87), add:

```kotlin
private val hrSampleBuffer = ArrayDeque<Int>()   // rolling 120-sample window for artifact detection
private val hrSessionSamples = mutableListOf<Int>() // full session samples for hrMax detection
private var cadenceLockSuspected: Boolean = false
```

- [ ] **Step 3: Accumulate HR samples in processTick()**

In `processTick()`, after the existing block `if (tick.connected && tick.hr > 0 && !isPaused) { hrSampleSum += tick.hr; hrSampleCount++ }`, add:

```kotlin
        if (tick.connected && tick.hr > 0 && !isPaused) {
            hrSampleBuffer.addLast(tick.hr)
            if (hrSampleBuffer.size > 120) hrSampleBuffer.removeFirst()
            hrSessionSamples.add(tick.hr)
            // Check for cadence lock every 10 new samples once we have 30+
            if (hrSampleBuffer.size >= 30 && hrSampleBuffer.size % 10 == 0) {
                cadenceLockSuspected = HrArtifactDetector.isArtifactSuspected(hrSampleBuffer.toList())
            }
        }
```

- [ ] **Step 4: Reset new fields in startWorkout()**

At the top of `startWorkout()`, after `trackPointRecorder.reset()`, add:

```kotlin
        hrSampleBuffer.clear()
        hrSessionSamples.clear()
        cadenceLockSuspected = false
```

- [ ] **Step 5: Add calibration in stopWorkout() — load track points once and run calibrators**

In `stopWorkout()`, the current code calls `repository.getTrackPoints(workoutId)` inside `MetricsCalculator.deriveFullMetrics(...)`. Refactor to load track points once and add calibration.

Replace the existing block starting at `val canonicalMetrics = MetricsCalculator.deriveFullMetrics(` through `metricsToSave?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }` with:

```kotlin
                var currentProfile = session?.updatedProfile ?: adaptiveProfileRepository.getProfile()

                // hrMax calibration — skip if cadence lock was suspected
                val newHrMax = HrCalibrator.detectNewHrMax(
                    currentHrMax = currentProfile.hrMax ?: 180,
                    recentSamples = hrSessionSamples.toList(),
                    cadenceLockSuspected = cadenceLockSuspected
                )
                if (newHrMax != null) {
                    currentProfile = currentProfile.copy(hrMax = newHrMax, hrMaxIsCalibrated = true)
                }

                // Load track points once — used for both metrics and hrRest
                val trackPoints = repository.getTrackPoints(workoutId)

                // hrRest calibration
                val restingProxy = MetricsCalculator.computeRestingHrProxy(trackPoints)
                if (restingProxy != null) {
                    val updatedRest = HrCalibrator.updateHrRest(
                        currentHrRest = currentProfile.hrRest ?: restingProxy,
                        candidate = restingProxy
                    )
                    currentProfile = currentProfile.copy(hrRest = updatedRest)
                }

                adaptiveProfileRepository.saveProfile(currentProfile)

                val canonicalMetrics = MetricsCalculator.deriveFullMetrics(
                    workoutId = workoutId,
                    recordedAtMs = now,
                    trackPoints = trackPoints
                )
                val metricsToSave = when {
                    canonicalMetrics != null && session != null -> canonicalMetrics.copy(
                        settleDownSec = session.metrics.settleDownSec,
                        settleUpSec = session.metrics.settleUpSec,
                        longTermHrTrimBpm = session.metrics.longTermHrTrimBpm,
                        responseLagSec = session.metrics.responseLagSec
                    )
                    canonicalMetrics != null -> canonicalMetrics
                    session != null -> session.metrics
                    else -> null
                }
                // Mark metrics unreliable if cadence lock was detected
                val reliableMetrics = metricsToSave?.copy(trimpReliable = !cadenceLockSuspected)
                reliableMetrics?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }
```

Also add imports at the top of `WorkoutForegroundService.kt`:
```kotlin
import com.hrcoach.domain.engine.HrArtifactDetector
import com.hrcoach.domain.engine.HrCalibrator
```

- [ ] **Step 6: Run compile check**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add \
  app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt \
  app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(engine): wire HrArtifactDetector and HrCalibrator into workout pipeline"
```

---

## Chunk 3: Environment flag + fitness load + signal evaluator

### Task 3: Wire EnvironmentFlagDetector, FitnessLoadCalculator, FitnessSignalEvaluator

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Add `lastTuningDirection` to AdaptiveProfile**

```kotlin
import com.hrcoach.domain.engine.TuningDirection

data class AdaptiveProfile(
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val paceHrBuckets: Map<Int, PaceHrBucket> = emptyMap(),
    val totalSessions: Int = 0,
    val ctl: Float = 0f,
    val atl: Float = 0f,
    val hrMax: Int? = null,
    val hrMaxIsCalibrated: Boolean = false,
    val hrRest: Float? = null,
    val lastTuningDirection: TuningDirection? = null,
)
```

- [ ] **Step 2: Add engine imports to WorkoutForegroundService**

Add after existing imports:
```kotlin
import com.hrcoach.domain.engine.EnvironmentFlagDetector
import com.hrcoach.domain.engine.FitnessLoadCalculator
import com.hrcoach.domain.engine.FitnessSignalEvaluator
```

- [ ] **Step 3: Add TRIMP, environment detection, CTL/ATL update, and fitness evaluation in stopWorkout()**

Replace the line `reliableMetrics?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }` and everything after it up to `WorkoutState.update { it.copy(completedWorkoutId = workoutId) }` with:

```kotlin
                // Compute TRIMP if not already present
                val durationMin = (now - workoutStartMs) / 60_000f
                val sessionAvgHr = reliableMetrics?.avgHr
                    ?: if (hrSampleCount > 0) (hrSampleSum.toFloat() / hrSampleCount) else null
                val hrMaxEst = currentProfile.hrMax?.toFloat() ?: 180f
                val trimpScore = reliableMetrics?.trimpScore
                    ?: if (sessionAvgHr != null && durationMin > 0f) {
                        val intensity = sessionAvgHr / hrMaxEst
                        durationMin * sessionAvgHr * intensity * intensity
                    } else null

                // Detect environment-affected session using recent metrics as baseline
                val recentMetrics = workoutMetricsRepository.getRecentMetrics(42)
                val baselinePace: Float? = if (sessionAvgHr != null) {
                    recentMetrics
                        .filter { m ->
                            m.workoutId != workoutId &&
                            m.avgHr != null &&
                            kotlin.math.abs(m.avgHr!! - sessionAvgHr) < 10f &&
                            m.avgPaceMinPerKm != null
                        }
                        .mapNotNull { it.avgPaceMinPerKm }
                        .sorted()
                        .takeIf { it.isNotEmpty() }
                        ?.let { paces -> paces[paces.size / 2] }
                } else null

                val environmentAffected = EnvironmentFlagDetector.isEnvironmentAffected(
                    aerobicDecoupling = reliableMetrics?.aerobicDecoupling,
                    sessionAvgGapPace = reliableMetrics?.avgPaceMinPerKm,
                    baselineGapPaceAtEquivalentHr = baselinePace
                )

                // Save complete metrics with TRIMP and environment flag
                val completeMetrics = reliableMetrics?.copy(
                    trimpScore = trimpScore,
                    environmentAffected = environmentAffected
                )
                completeMetrics?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }

                // Update CTL / ATL in profile
                if (trimpScore != null) {
                    val allWorkouts = repository.getAllWorkoutsOnce()
                    val prevWorkout = allWorkouts
                        .sortedByDescending { it.startTime }
                        .firstOrNull { it.id != workoutId }
                    val daysSinceLast = if (prevWorkout != null) {
                        ((now - prevWorkout.startTime) / 86_400_000L).toInt().coerceAtLeast(1)
                    } else 1
                    val loadResult = FitnessLoadCalculator.updateLoads(
                        currentCtl = currentProfile.ctl,
                        currentAtl = currentProfile.atl,
                        trimpScore = trimpScore,
                        daysSinceLast = daysSinceLast
                    )
                    currentProfile = currentProfile.copy(ctl = loadResult.ctl, atl = loadResult.atl)
                }

                // Fitness signal evaluation → store tuning direction for next Bootcamp session
                val updatedRecentMetrics = workoutMetricsRepository.getRecentMetrics(42)
                val fitnessEval = FitnessSignalEvaluator.evaluate(currentProfile, updatedRecentMetrics)
                currentProfile = currentProfile.copy(lastTuningDirection = fitnessEval.tuningDirection)
                adaptiveProfileRepository.saveProfile(currentProfile)

                WorkoutState.update { it.copy(completedWorkoutId = workoutId) }
```

- [ ] **Step 4: Compile check**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt \
  app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(engine): wire EnvironmentFlagDetector, FitnessLoadCalculator, FitnessSignalEvaluator into workout stop path"
```

---

## Chunk 4: ActiveSessionCard on Home

### Task 4: Show ActiveSessionCard in HomeScreen when a workout is running

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Add `isSessionRunning` to HomeUiState and HomeViewModel**

In `HomeViewModel.kt`, update `HomeUiState`:
```kotlin
data class HomeUiState(
    val greeting: String = "Good morning",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val efficiencyPercent: Int = 0,
    val isSessionRunning: Boolean = false
)
```

Replace `val uiState: StateFlow<HomeUiState>` with a `combine` over workouts + WorkoutState:

```kotlin
import com.hrcoach.service.WorkoutState
import kotlinx.coroutines.flow.combine

val uiState: StateFlow<HomeUiState> = combine(
    workoutRepository.getAllWorkouts(),
    WorkoutState.snapshot
) { workouts, snapshot ->
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val thisWeek = workouts.count { it.startTime >= weekStart }
    val target = 4
    val pct = ((thisWeek.toFloat() / target) * 100).toInt().coerceIn(0, 100)
    val greeting = when (now.hour) {
        in 0..11  -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
    }
    HomeUiState(
        greeting = greeting,
        lastWorkout = workouts.firstOrNull(),
        workoutsThisWeek = thisWeek,
        weeklyTarget = target,
        efficiencyPercent = pct,
        isSessionRunning = snapshot.isRunning
    )
}
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
```

- [ ] **Step 2: Add `onGoToWorkout` param and ActiveSessionCard to HomeScreen**

In `HomeScreen.kt`, add `onGoToWorkout: () -> Unit` to the function signature (after `onGoToBootcamp`):

```kotlin
@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onGoToProgress: () -> Unit,
    onGoToHistory: () -> Unit,
    onGoToAccount: () -> Unit,
    onGoToBootcamp: () -> Unit,
    onGoToWorkout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
)
```

Add the import:
```kotlin
import com.hrcoach.ui.components.ActiveSessionCard
```

In the Column, add the card as the first item (before the Header Row), so an in-progress run is impossible to miss:

```kotlin
            // Active session banner — shown only when a workout is in progress
            if (state.isSessionRunning) {
                ActiveSessionCard(onClick = onGoToWorkout)
            }

            // Header
            Row( ...
```

- [ ] **Step 3: Wire onGoToWorkout in NavGraph**

In `NavGraph.kt`, find the `HomeScreen(` composable call and add:
```kotlin
onGoToWorkout = { navController.navigate(Routes.WORKOUT) },
```

- [ ] **Step 4: Compile check**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt \
  app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(home): show ActiveSessionCard when a workout session is running"
```

---

## Chunk 5: Long-press delete in History

### Task 5: Long-press workout card to delete with confirmation

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

Note: `HistoryViewModel.deleteWorkout(id)` already exists and deletes both the workout row and its metrics.

- [ ] **Step 1: Add new imports to HistoryListScreen.kt**

Add after the existing import block:
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
```

- [ ] **Step 2: Add delete state in HistoryListScreen, update items block, add AlertDialog**

In `HistoryListScreen`, right before `Scaffold(`:
```kotlin
    var deleteModeId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
```

Replace the existing `items(workouts, key = { it.id }) { workout -> WorkoutCard(...) }` block with:
```kotlin
                        items(workouts, key = { it.id }) { workout ->
                            WorkoutCard(
                                workout = workout,
                                isDeleteMode = deleteModeId == workout.id,
                                onClick = {
                                    if (deleteModeId != null) {
                                        deleteModeId = null
                                    } else {
                                        onWorkoutClick(workout.id)
                                    }
                                },
                                onLongClick = { deleteModeId = workout.id },
                                onDeleteClick = {
                                    deleteModeId = workout.id
                                    showDeleteDialog = true
                                }
                            )
                        }
```

After the `LazyColumn` closing brace (still inside the `else` branch):
```kotlin
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeleteDialog = false
                                deleteModeId = null
                            },
                            title = { Text("Delete this run?") },
                            text = { Text("This will permanently remove all route data and stats.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        deleteModeId?.let { viewModel.deleteWorkout(it) }
                                        showDeleteDialog = false
                                        deleteModeId = null
                                    }
                                ) {
                                    Text("Delete", color = Color(0xFFEF4444))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeleteDialog = false
                                    deleteModeId = null
                                }) { Text("Cancel") }
                            },
                            containerColor = Color(0xFF141B27),
                            titleContentColor = Color.White,
                            textContentColor = CardeaTextSecondary
                        )
                    }
```

- [ ] **Step 3: Rework WorkoutCard to support delete mode**

Replace the `@Composable private fun WorkoutCard(workout: WorkoutEntity, onClick: () -> Unit)` signature and outer modifier with:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    isDeleteMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
        ) {
            // ... existing Column content unchanged ...
        }

        // X badge — positioned outside the card at top-right
        AnimatedVisibility(
            visible = isDeleteMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .zIndex(1f),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(26.dp)
                    .background(color = Color(0xFFEF4444), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete workout",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "feat(history): long-press card to reveal delete badge with confirmation dialog"
```
