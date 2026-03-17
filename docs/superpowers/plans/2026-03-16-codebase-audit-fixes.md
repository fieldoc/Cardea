# Codebase Audit Fix Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all issues found across 7 audit dimensions (error paths, permissions, state restoration, consistency, duplicates, coroutine leaks, migrations) in descending priority order.

**Architecture:** Fixes are organized into independent tasks by audit area. Tasks are ordered P0→P3 by risk/impact. **Dependency note:** Tasks 2 and 5 modify overlapping files (PostRunSummaryViewModel, HistoryViewModel, ProgressViewModel, WorkoutViewModel) — they MUST run sequentially, not in parallel.

**Tech Stack:** Kotlin, Android Jetpack (Compose, Room, Lifecycle, Hilt), BLE APIs, FusedLocationProvider

---

## File Structure

| File | Responsibility |
|------|---------------|
| `service/WorkoutForegroundService.kt` | MODIFY — add error handling to stopWorkout, startWorkout, onDestroy; add logging; persist state |
| `service/BleHrManager.kt` | MODIFY — add scan error reporting, permission checks |
| `service/WorkoutState.kt` | MODIFY — no changes needed (state persistence goes via SharedPreferences) |
| `MainActivity.kt` | MODIFY — handle permission results, add rationale, add Settings intent |
| `util/PermissionGate.kt` | MODIFY — add permission group helpers, add missing permission descriptions |
| `util/Formatters.kt` | MODIFY — add formatDurationSeconds, metersToKm, WorkoutEntity extensions |
| `util/WorkoutEntityExt.kt` | CREATE — extension properties for WorkoutEntity |
| `ui/postrun/PostRunSummaryViewModel.kt` | MODIFY — add logging, use exception message |
| `ui/postrun/PostRunSummaryScreen.kt` | MODIFY — collectAsState → collectAsStateWithLifecycle |
| `ui/history/HistoryViewModel.kt` | MODIFY — add error handling to deleteWorkout, add logging |
| `ui/history/HistoryDetailScreen.kt` | MODIFY — use formatPaceMinPerKm |
| `ui/history/HistoryListScreen.kt` | MODIFY — use metersToKm |
| `ui/progress/ProgressViewModel.kt` | MODIFY — add logging, use metersToKm |
| `ui/home/HomeScreen.kt` | MODIFY — use metersToKm |
| `ui/home/HomeSessionStreak.kt` | DELETE — pure passthrough wrapper |
| `ui/home/HomeViewModel.kt` | MODIFY — import StreakCalculator directly |
| `ui/workout/ActiveWorkoutScreen.kt` | MODIFY — delete formatElapsedHms, use Formatters |
| `ui/workout/WorkoutViewModel.kt` | MODIFY — use WorkoutConfig.segmentAtElapsed |
| `ui/setup/SetupViewModel.kt` | MODIFY — add logging to BLE error paths |
| `ui/navigation/NavGraph.kt` | MODIFY — add logging to startForegroundService failures |
| `domain/model/WorkoutConfig.kt` | MODIFY — add segmentAtElapsed centralized method |
| `service/workout/CoachingEventRouter.kt` | MODIFY — use WorkoutConfig.segmentAtElapsed |
| `data/repository/UserProfileRepository.kt` | no change needed |
| `ui/bootcamp/BootcampSettingsViewModel.kt` | MODIFY — add logging |
| `data/db/AppDatabase.kt` | no change needed (migration tests are separate) |

---

### Task 1: Add logging + error handling to stopWorkout() [P0]

The entire 170-line `stopWorkout()` method has zero error handling. A single exception silently loses the workout.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt:464-631`

- [ ] **Step 1: Add Android Log import if missing**

Check for `import android.util.Log` at top of file. Add if absent.

- [ ] **Step 2: Wrap the core data-persistence block in stopWorkout**

In `stopWorkout()`, inside the `lifecycleScope.launch(Dispatchers.IO)` block, after the track point save and before `cleanupManagers()`, wrap the `if (workoutId > 0L)` block in `runCatching`:

```kotlin
if (workoutId > 0L) {
    runCatching {
        val now = System.currentTimeMillis()
        // ... existing body of the if block up to WorkoutState.update ...
    }.onFailure { e ->
        Log.e("WorkoutService", "Failed to save workout data", e)
        // Still set completedWorkoutId so UI navigates to post-run
        WorkoutState.update { it.copy(completedWorkoutId = workoutId) }
    }
}
```

This ensures the workout entity row (already created at start) survives even if metrics derivation crashes.

- [ ] **Step 3: Add inner try-catch around metrics derivation specifically**

Within the `if (workoutId > 0L)` block, split into two sub-blocks:
1. **Essential save** (workout entity update with endTime + distance) — must succeed
2. **Best-effort enrichment** (metrics derivation, TRIMP, CTL/ATL, fitness signals) — wrapped in its own `runCatching`

```kotlin
// Essential: save workout end state
val now = System.currentTimeMillis()
val currentWorkout = repository.getWorkoutById(workoutId)
if (currentWorkout != null) {
    repository.updateWorkout(
        currentWorkout.copy(
            endTime = now,
            totalDistanceMeters = WorkoutState.snapshot.value.distanceMeters
        )
    )
}

// Best-effort: metrics and profile updates
runCatching {
    val session = adaptiveController?.finishSession(workoutId = workoutId, endedAtMs = now)
    // ... rest of calibration, metrics, TRIMP, CTL/ATL, fitness signals ...
}.onFailure { e ->
    Log.e("WorkoutService", "Metrics derivation failed for workout $workoutId", e)
}
```

- [ ] **Step 4: Add logging to handleStartFailure**

```kotlin
private fun handleStartFailure(message: String, cause: Throwable? = null) {
    Log.e("WorkoutService", message, cause)
    runCatching { notificationHelper.update(message) }
        .onFailure { Log.w("WorkoutService", "Failed to update notification", it) }
    cleanupManagers()
    WorkoutState.reset()
    WorkoutState.clearCompletedWorkoutId()
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        .onFailure { Log.w("WorkoutService", "Failed to stop foreground", it) }
    stopSelf()
}
```

Update call sites to pass the exception:
- `onStartCommand` line ~138: `.onFailure { handleStartFailure("Unable to start workout.", it) }`
- `startWorkout` inner `.onFailure`: `handleStartFailure("Workout start failed. Check permissions and try again.", it)`

- [ ] **Step 5: Add error handling to onDestroy**

```kotlin
override fun onDestroy() {
    super.onDestroy()
    // If a workout was active but stopWorkout never ran, persist what we can
    if (workoutId > 0L && !isStopping) {
        Log.w("WorkoutService", "onDestroy called without stopWorkout — saving partial data")
        runCatching {
            // onDestroy runs on main thread; Room forbids main-thread DB access.
            // runBlocking is acceptable here as a last-resort save with a timeout to prevent ANR.
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val workout = repository.getWorkoutById(workoutId)
                    if (workout != null && workout.endTime == 0L) {
                        repository.updateWorkout(workout.copy(endTime = System.currentTimeMillis()))
                    }
                }
            }
        }.onFailure { Log.e("WorkoutService", "Failed to save partial workout in onDestroy", it) }
    }
    startupJob?.cancel()
    observationJob?.cancel()
    stopJob?.cancel()
    cleanupManagers()
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: All existing tests pass (no behavior change for success paths).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "fix: add error handling and logging to stopWorkout and service lifecycle"
```

---

### Task 2: Add logging to all ViewModel .onFailure blocks [P0]

Every `.onFailure` ignores the exception. Add `Log.e` and use `it.message` where appropriate.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt:147-152`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryViewModel.kt:75-80,84-92`
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressViewModel.kt:110-114`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt:350-354,370-374`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt:329-333,461-465`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt:307-308`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt:162-164`

- [ ] **Step 1: PostRunSummaryViewModel.load() — add logging + use exception message**

```kotlin
.onFailure { e ->
    Log.e("PostRunSummaryVM", "Failed to load post-run summary", e)
    _uiState.value = _uiState.value.copy(
        isLoading = false,
        errorMessage = e.message ?: "Unable to load post-run summary."
    )
}
```

- [ ] **Step 2: HistoryViewModel.loadWorkoutDetail() — add logging**

```kotlin
.onFailure { e ->
    Log.e("HistoryVM", "Failed to load workout detail", e)
    _selectedWorkout.value = null
    _trackPoints.value = emptyList()
    _selectedMetrics.value = null
    _detailError.value = e.message ?: "Unable to load workout details."
}
```

- [ ] **Step 3: HistoryViewModel.deleteWorkout() — add error handling**

```kotlin
fun deleteWorkout(workoutId: Long) {
    viewModelScope.launch {
        runCatching {
            repository.deleteWorkout(workoutId)
            workoutMetricsRepository.deleteWorkoutMetrics(workoutId)
        }.onSuccess {
            _selectedWorkout.value = null
            _trackPoints.value = emptyList()
            _selectedMetrics.value = null
        }.onFailure { e ->
            Log.e("HistoryVM", "Failed to delete workout $workoutId", e)
            _detailError.value = "Failed to delete workout."
        }
    }
}
```

- [ ] **Step 4: ProgressViewModel.refresh() — add logging**

```kotlin
.onFailure { e ->
    Log.e("ProgressVM", "Failed to load progress", e)
    _uiState.value = _uiState.value.copy(
        isLoading = false,
        errorMessage = e.message ?: "Unable to load progress."
    )
}
```

- [ ] **Step 5: SetupViewModel — add logging to BLE error paths**

For `startScan()`:
```kotlin
.onFailure { e ->
    Log.e("SetupVM", "BLE scan failed", e)
    connectionError = when (e) {
        is SecurityException -> "Bluetooth permission required. Check Settings."
        else -> "Unable to scan. Check Bluetooth and permissions."
    }
}
```

For `connectToDevice()`:
```kotlin
.onFailure { e ->
    Log.e("SetupVM", "BLE connect failed", e)
    connectionError = when (e) {
        is SecurityException -> "Bluetooth permission required. Check Settings."
        else -> "Unable to connect. Try again."
    }
}
```

- [ ] **Step 6: NavGraph.kt — add logging to startForegroundService failures**

Both call sites (around lines 329 and 461):
```kotlin
.onFailure { e ->
    Log.e("NavGraph", "Failed to start workout service", e)
    val msg = when (e) {
        is android.app.ForegroundServiceStartNotAllowedException ->
            "Cannot start workout from background. Open the app first."
        else -> "Unable to start workout."
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
```

- [ ] **Step 7: BootcampSettingsViewModel — add logging**

```kotlin
catch (t: Throwable) {
    Log.e("BootcampSettingsVM", "Failed to save settings", t)
    _uiState.update { it.copy(isSaving = false, saveError = t.message ?: "Save failed") }
}
```

- [ ] **Step 8: WorkoutViewModel — add logging for config parse**

```kotlin
val config = runCatching {
    gson.fromJson(activeWorkout.targetConfig, WorkoutConfig::class.java)
}.onFailure { e ->
    Log.w("WorkoutVM", "Failed to parse workout config JSON", e)
}.getOrNull()
```

- [ ] **Step 9: BleHrManager.onScanFailed — report error**

```kotlin
override fun onScanFailed(errorCode: Int) {
    Log.e("BleHrManager", "BLE scan failed with error code: $errorCode")
    synchronized(stateLock) {
        isScanning = false
    }
}
```

- [ ] **Step 10: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt \
  app/src/main/java/com/hrcoach/ui/history/HistoryViewModel.kt \
  app/src/main/java/com/hrcoach/ui/progress/ProgressViewModel.kt \
  app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt \
  app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
  app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt \
  app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt \
  app/src/main/java/com/hrcoach/service/BleHrManager.kt
git commit -m "fix: add logging and meaningful error messages to all error paths"
```

---

### Task 3: Fix permission lifecycle [P0]

Permission result callback is a no-op. No rationale dialog. No "Open Settings" path. BLE uses `@SuppressLint` everywhere.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/MainActivity.kt`
- Modify: `app/src/main/java/com/hrcoach/util/PermissionGate.kt`

- [ ] **Step 1: Add permission group helpers to PermissionGate**

```kotlin
object PermissionGate {
    // ... existing methods ...

    /** Permissions required for workout functionality (BLE + Location). */
    fun workoutPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions
    }

    /** Has all permissions needed for a workout (BLE + Location). */
    fun hasWorkoutPermissions(context: Context): Boolean {
        return workoutPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Human-readable description for a denied permission. */
    fun describePermission(permission: String): String = when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION -> "Location (for GPS tracking)"
        Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan (for HR monitor)"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect (for HR monitor)"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications (for workout alerts)"
        else -> permission.substringAfterLast('.')
    }
}
```

- [ ] **Step 2: Handle permission results in MainActivity**

Replace the no-op callback:

```kotlin
private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    val denied = results.filterValues { !it }.keys
    if (denied.isNotEmpty()) {
        val permanentlyDenied = denied.any { perm ->
            !shouldShowRequestPermissionRationale(perm)
        }
        if (permanentlyDenied) {
            // User selected "Don't ask again" — direct to Settings
            Toast.makeText(
                this,
                "Permissions required. Tap to open Settings.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } else {
            val names = denied.joinToString { PermissionGate.describePermission(it) }
            Toast.makeText(
                this,
                "Denied: $names. Needed for full functionality.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
```

Add required imports: `android.content.Intent`, `android.net.Uri`, `android.provider.Settings`, `android.widget.Toast`.

- [ ] **Step 3: Run the build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/MainActivity.kt app/src/main/java/com/hrcoach/util/PermissionGate.kt
git commit -m "fix: handle permission denial with rationale and Settings redirect"
```

---

### Task 4: Fix hrMax dual-store divergence [P1]

Auto-calibration writes to `AdaptiveProfile` only, skipping `UserProfileRepository`. One-line fix.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt:505-512`

- [ ] **Step 1: Add UserProfileRepository injection**

The service already has injected repositories. `UserProfileRepository` uses `@Inject constructor(context)` — Hilt can provide it automatically via constructor injection. Add to `WorkoutForegroundService`:

```kotlin
@Inject lateinit var userProfileRepository: UserProfileRepository
```

(It will be provided as an unscoped dependency by Hilt. Since it wraps SharedPreferences, this is safe — each instance reads/writes the same backing store.)

- [ ] **Step 2: Sync hrMax to UserProfileRepository after auto-calibration**

After line ~511 (where `newHrMax != null` is handled), add:

```kotlin
if (newHrMax != null) {
    currentProfile = currentProfile.copy(hrMax = newHrMax, hrMaxIsCalibrated = true)
    userProfileRepository.setMaxHr(newHrMax) // Sync to SharedPreferences
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "fix: sync auto-calibrated hrMax to UserProfileRepository"
```

---

### Task 5: Consolidate semantic duplicates [P2]

9 duplicate groups found. Fix the 5 HIGH-confidence groups.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/util/Formatters.kt`
- Create: `app/src/main/java/com/hrcoach/util/WorkoutEntityExt.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Modify: `app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt`
- Delete: `app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressViewModel.kt`

- [ ] **Step 1: Add formatDurationSeconds to Formatters.kt**

```kotlin
fun formatDurationSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
```

- [ ] **Step 2: Add metersToKm helper to Formatters.kt**

```kotlin
fun metersToKm(meters: Float): Float = meters / 1000f
```

- [ ] **Step 3: Create WorkoutEntityExt.kt with shared extensions**

```kotlin
package com.hrcoach.util

import com.hrcoach.data.db.WorkoutEntity

/** Safe end timestamp: falls back to startTime if endTime is invalid. */
val WorkoutEntity.recordedAtMs: Long
    get() = if (endTime > startTime) endTime else startTime

/** Wall-clock duration in minutes (includes paused time). */
val WorkoutEntity.durationMinutes: Float
    get() = (endTime - startTime).coerceAtLeast(0L) / 60_000f
```

- [ ] **Step 4: Replace formatElapsedHms in ActiveWorkoutScreen.kt**

Delete the private `formatElapsedHms` function (lines 729-734).
Replace its call site with `formatDurationSeconds(elapsedSeconds)`.
Add import: `import com.hrcoach.util.formatDurationSeconds`.

- [ ] **Step 5: Add segmentAtElapsed to WorkoutConfig**

```kotlin
/** Returns (index, segment) for the segment active at the given elapsed seconds, or null. */
fun segmentAtElapsed(elapsedSeconds: Long): Pair<Int, HrSegment>? {
    if (segments.isEmpty()) return null
    var cumulative = 0L
    segments.forEachIndexed { index, seg ->
        val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
        cumulative += dur
        if (elapsedSeconds < cumulative) return index to seg
    }
    return segments.lastIndex to segments.last()
}
```

- [ ] **Step 6: Update targetHrAtElapsedSeconds to use segmentAtElapsed**

```kotlin
fun targetHrAtElapsedSeconds(elapsedSeconds: Long): Int? {
    return segmentAtElapsed(elapsedSeconds)?.second?.targetHr
}
```

- [ ] **Step 7: Update CoachingEventRouter.segmentIndexByTime to use WorkoutConfig**

```kotlin
private fun segmentIndexByTime(config: WorkoutConfig, elapsedSeconds: Long): Int {
    return config.segmentAtElapsed(elapsedSeconds)?.first ?: -1
}
```

- [ ] **Step 8: Update WorkoutViewModel.deriveSegmentInfo to use segmentAtElapsed**

```kotlin
private fun deriveSegmentInfo(config: WorkoutConfig?, elapsed: Long): SegmentInfo {
    if (config == null || !config.isTimeBased()) return SegmentInfo()
    val result = config.segmentAtElapsed(elapsed) ?: return SegmentInfo()
    val (index, seg) = result
    var cumulative = 0L
    for (i in 0..index) {
        cumulative += config.segments[i].durationSeconds?.toLong() ?: 0L
    }
    val nextLabel = config.segments.drop(index + 1).firstOrNull { it.label != null }?.label
    return SegmentInfo(seg.label, cumulative - elapsed, nextLabel)
}
```

- [ ] **Step 9: Replace inline meters/1000f with metersToKm across all files**

Replace in each file (use `import com.hrcoach.util.metersToKm`):
- `PostRunSummaryViewModel.kt:103` → `String.format("%.2f km", metersToKm(workout.totalDistanceMeters))`
- `HomeScreen.kt:106,123` → `metersToKm(it.totalDistanceMeters)` inside format
- `HistoryDetailScreen.kt:465` → `metersToKm(workout.totalDistanceMeters)`
- `HistoryListScreen.kt:104` → `metersToKm(items.sumOf { it.totalDistanceMeters.toDouble() }.toFloat())`
- `HistoryListScreen.kt:321` → `metersToKm(workout.totalDistanceMeters)`
- `ProgressViewModel.kt:253,310` → `metersToKm(item.workout.totalDistanceMeters)`
- `WorkoutViewModel.kt:226` → `metersToKm(it)`

Do NOT change `Formatters.kt:22` (it's the definition), `CoachingEventRouter.kt:86` (different semantic — km count), or `MetricsCalculator`/`AdaptivePaceController` (pace calculation, different domain).

- [ ] **Step 10: Replace inline recordedAtMs computations**

In each of these files, replace the `if (workout.endTime > workout.startTime) workout.endTime else workout.startTime` pattern with `workout.recordedAtMs` (import `com.hrcoach.util.recordedAtMs`):
- `HistoryViewModel.kt:63-66`
- `ProgressViewModel.kt:131`
- `PostRunSummaryViewModel.kt:161` (if present — the `getOrDeriveMetrics` method)

- [ ] **Step 11: Replace HistoryDetailScreen inline pace formatting**

Replace lines 467-471 of `HistoryDetailScreen.kt` with a call to `formatPaceMinPerKm()`:
```kotlin
val paceLabel = if (distanceKm > 0f) {
    formatPaceMinPerKm(durationMinutes / distanceKm)
} else "--"
```

- [ ] **Step 12: Delete HomeSessionStreak.kt and update HomeViewModel**

Delete `app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt`.

In `HomeViewModel.kt`, replace:
```kotlin
import com.hrcoach.ui.home.computeSessionStreak
```
with:
```kotlin
import com.hrcoach.domain.achievement.StreakCalculator
```
And update the call site from `computeSessionStreak(...)` to `StreakCalculator.computeSessionStreak(...)`.

- [ ] **Step 13: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 14: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 15: Commit**

```bash
git add app/src/main/java/com/hrcoach/util/Formatters.kt \
  app/src/main/java/com/hrcoach/util/WorkoutEntityExt.kt \
  app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt \
  app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt \
  app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt \
  app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt \
  app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt \
  app/src/main/java/com/hrcoach/ui/history/HistoryViewModel.kt \
  app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt \
  app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt \
  app/src/main/java/com/hrcoach/ui/progress/ProgressViewModel.kt
git commit -m "refactor: consolidate semantic duplicates (formatters, segment lookup, extensions)"
```

---

### Task 6: Fix collectAsState in PostRunSummaryScreen [P3]

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt:82`

- [ ] **Step 1: Replace collectAsState with collectAsStateWithLifecycle**

Change line 82:
```kotlin
// Before:
val uiState by viewModel.uiState.collectAsState()
// After:
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

Ensure `import androidx.lifecycle.compose.collectAsStateWithLifecycle` is present (remove unused `collectAsState` import if it was the only usage).

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "fix: use collectAsStateWithLifecycle in PostRunSummaryScreen"
```

---

### Task 7: Add orphan workout cleanup on app launch [P3]

Workouts with `endTime == 0L` are orphaned from process death. Clean them up.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt` — add query
- Modify: `app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt` — add method
- Modify: `app/src/main/java/com/hrcoach/HrCoachApp.kt` — add startup cleanup

- [ ] **Step 1: Add DAO query for orphaned workouts**

```kotlin
@Query("SELECT * FROM workouts WHERE endTime = 0")
suspend fun getOrphanedWorkouts(): List<WorkoutEntity>
```

- [ ] **Step 2: Add repository method to WorkoutRepository**

`WorkoutRepository` wraps both `workoutDao` and `trackPointDao`. Use `getTrackPoints(workoutId)` which delegates to `trackPointDao.getPointsForWorkout()`:

```kotlin
suspend fun cleanupOrphanedWorkouts() {
    val orphans = workoutDao.getOrphanedWorkouts()
    for (orphan in orphans) {
        val trackPoints = getTrackPoints(orphan.id)
        val estimatedEnd = trackPoints.maxOfOrNull { it.timestamp } ?: orphan.startTime
        val estimatedDistance = trackPoints.maxOfOrNull { it.distanceMeters } ?: 0f
        updateWorkout(orphan.copy(endTime = estimatedEnd, totalDistanceMeters = estimatedDistance))
    }
}
```

- [ ] **Step 3: Call on app startup via EntryPointAccessors**

`HrCoachApp` is currently a one-line `@HiltAndroidApp` class. Add:

```kotlin
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HrCoachApp : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun workoutRepository(): WorkoutRepository
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                this@HrCoachApp, AppEntryPoint::class.java
            )
            entryPoint.workoutRepository().cleanupOrphanedWorkouts()
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt \
  app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt \
  app/src/main/java/com/hrcoach/HrCoachApp.kt
git commit -m "fix: clean up orphaned workout records on app startup"
```

---

## Tasks NOT included (deferred)

These require larger architectural changes and should be planned separately:

1. **Full state restoration / START_REDELIVER_INTENT** — Requires persisting all workout accumulators, BLE device address, and config to SharedPreferences on every tick. Large scope.
2. **Per-operation BLE permission checks** — Replacing all 8 `@SuppressLint("MissingPermission")` requires restructuring `BleHrManager` to propagate errors. Separate feature.
3. **Mid-session permission revocation handling** — Requires a lifecycle-aware permission observer. Separate feature.
4. **Room migration tests** — Requires setting up `MigrationTestHelper` infrastructure. Separate task.
5. **Decouple permission gates** — Splitting PermissionGate into BLE/Location/Notification groups changes the service start flow. Separate feature.
