# Active Workout Goal Info Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the runner their goal (session type, target HR, duration/distance, halfway turn marker) on the active workout screen via a unified Mission Card.

**Architecture:** Add `bootcampWeekNumber` to `PlannedSession` and `WorkoutConfig` so week context flows from the bootcamp entity through to the UI. Replace the header row and progress strip composables with a single `MissionCard` composable. Add a `ProgressBarWithTurnMarker` composable below it.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, StateFlow

**Spec:** `docs/superpowers/specs/2026-03-29-active-workout-goal-info-design.md`

---

### Task 1: Add `bootcampWeekNumber` to data models

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt` (PlannedSession)
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` (toPlannedSession + buildWorkoutConfig)
- Modify: `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt` (ActiveWorkoutUiState + deriveProgressInfo)

- [ ] **Step 1: Add `weekNumber` to `PlannedSession`**

In `app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt`, change:
```kotlin
data class PlannedSession(
    val type: SessionType,
    val minutes: Int,
    val presetId: String? = null,
    val weekNumber: Int? = null
)
```

- [ ] **Step 2: Add `bootcampWeekNumber` to `WorkoutConfig`**

In `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`, add after `sessionLabel`:
```kotlin
/** Bootcamp week number — for display only on the active workout screen. */
val bootcampWeekNumber: Int? = null
```

- [ ] **Step 3: Wire week number in `BootcampViewModel.toPlannedSession()`**

In `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`, find the `toPlannedSession()` extension (around line 1091) and add `weekNumber`:
```kotlin
private fun BootcampSessionEntity.toPlannedSession(): PlannedSession = PlannedSession(
    type = runCatching { SessionType.valueOf(sessionType) }.getOrDefault(SessionType.EASY),
    minutes = targetMinutes,
    presetId = presetId,
    weekNumber = weekNumber
)
```

- [ ] **Step 4: Wire week number in `buildWorkoutConfig()`**

In `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`, in `buildWorkoutConfig()` (around line 1163):

For the preset path (enriched config copy, around line 1170), add:
```kotlin
val enriched = config.copy(
    plannedDurationMinutes = config.plannedDurationMinutes ?: session.minutes,
    sessionLabel = config.sessionLabel
        ?: SessionType.displayLabelForPreset(presetId)
        ?: session.type.name.lowercase().replaceFirstChar { it.uppercase() },
    bootcampWeekNumber = session.weekNumber
)
```

For the fallback free-run path (around line 1181), add:
```kotlin
WorkoutConfig(
    mode = WorkoutMode.FREE_RUN,
    plannedDurationMinutes = session.minutes,
    sessionLabel = label,
    bootcampWeekNumber = session.weekNumber
)
```

- [ ] **Step 5: Add fields to `ActiveWorkoutUiState`**

In `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt`, add to `ActiveWorkoutUiState`:
```kotlin
val bootcampWeekNumber: Int? = null,
val remainingSeconds: Long? = null
```

- [ ] **Step 6: Wire new fields in `WorkoutViewModel`**

In `deriveProgressInfo()`, add `bootcampWeekNumber` to `ProgressInfo`:
```kotlin
private data class ProgressInfo(
    val totalDurationSeconds: Long? = null,
    val totalDistanceMeters: Float? = null,
    val workoutTypeLabel: String? = null,
    val bootcampWeekNumber: Int? = null
)
```

Then in the body of `deriveProgressInfo()`, add `bootcampWeekNumber = config.bootcampWeekNumber` to each `ProgressInfo(...)` constructor call.

In every `_uiState.update { }` block that sets `totalDurationSeconds` (there are 3: the ticker coroutine around line 83, `handleSnapshot` around line 140, and `loadActiveWorkoutMetadata` around line 170), also set:
```kotlin
bootcampWeekNumber = prog.bootcampWeekNumber,
remainingSeconds = prog.totalDurationSeconds?.let { total ->
    (total - newElapsed).coerceAtLeast(0L)
}
```

- [ ] **Step 7: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt \
       app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt \
       app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
       app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt
git commit -m "feat(workout): add bootcampWeekNumber to data flow for mission card"
```

---

### Task 2: Create MissionCard composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt`

- [ ] **Step 1: Create `MissionCard.kt`**

Create `app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt` with a `@Composable fun MissionCard(uiState: ActiveWorkoutUiState)` that renders:

1. A glass card (`GlassHighlight` background, `GlassBorder` border, 16dp corner radius) with a 3px Cardea gradient left edge (use `drawBehind` with `drawRoundRect` using `CardeaGradient` clipped to left 3px).

2. **Top row** (horizontal, space-between):
   - Left column: session label (`uiState.workoutTypeLabel` or fallback) in 17sp bold white; week label ("Week N") below in 11sp tertiary if `uiState.bootcampWeekNumber != null`
   - Right: zone badge pill — `Box` with zone-color-tinted background (12% alpha) and border (25% alpha), 8dp rounded, containing a 7dp glowing dot + zone status text in 11sp bold uppercase. Zone color derived from `uiState.snapshot.zoneStatus` using the existing `zoneColorFor()` logic. For free runs, show "FREE RUN" in `GradientBlue`.

3. **Center — hero timer** (only when `uiState.totalDurationSeconds != null` or `uiState.totalDistanceMeters != null`):
   - For time-based: `formatDurationSeconds(uiState.elapsedSeconds)` in 36sp/900-weight white + " / " separator in 22sp at 15% alpha + `formatDurationSeconds(totalDuration)` in 22sp/600-weight at 28% alpha
   - For distance-based: `formatDistanceKm(snapshot.distanceMeters)` in 36sp + " / " + `formatDistanceKm(totalDistance)` + " km"
   - If neither (open-ended free run): show just elapsed time in 36sp, no separator/total

4. **Bottom row** (horizontal, space-between):
   - Left: target HR pill — only when `!snapshot.isFreeRun && snapshot.targetHr > 0`. Heart character + "Target N bpm" in zone color, with zone-color background (10% alpha) and border (20% alpha), 8dp rounded
   - Right: remaining text — `formatDurationSeconds(remainingSeconds)` + " remaining" in 12sp tertiary, only when remainingSeconds is not null

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt
git commit -m "feat(workout): add MissionCard composable for goal display"
```

---

### Task 3: Create ProgressBarWithTurnMarker composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/ProgressBarWithTurnMarker.kt`

- [ ] **Step 1: Create `ProgressBarWithTurnMarker.kt`**

Create `app/src/main/java/com/hrcoach/ui/workout/ProgressBarWithTurnMarker.kt` with:

```kotlin
@Composable
fun ProgressBarWithTurnMarker(
    progress: Float,          // 0f..1f
    showTurnMarker: Boolean,  // true when there's a goal
    turnLabel: String?,       // e.g. "Turn 22:30" or "Turn 2.5 km"
    modifier: Modifier = Modifier
)
```

Implementation:
- A `Box` with 16dp bottom padding (to prevent overlap with HR ring below)
- Inside: a `Canvas` that draws:
  - Track: full-width rounded rect, 5dp height, `GlassHighlight` color
  - Fill: rounded rect at `progress * width`, Cardea gradient brush
  - If `showTurnMarker`: vertical line at 50% width, 2dp wide, 20dp tall, centered vertically, `Color.White.copy(alpha = 0.20f)`
- Below the canvas (not inside it): if `turnLabel != null`, a `Text` composable positioned at 50% with `Modifier.align(Alignment.CenterHorizontally)` offset to center, showing the label in 10sp, 40% white, semibold

The caller computes `turnLabel`:
- For time-based: `"Turn " + formatDurationSeconds(totalDurationSeconds / 2)`
- For distance-based: `"Turn " + formatDistanceKm(totalDistanceMeters / 2) + " km"`

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ProgressBarWithTurnMarker.kt
git commit -m "feat(workout): add ProgressBarWithTurnMarker composable"
```

---

### Task 4: Wire MissionCard and ProgressBar into ActiveWorkoutScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

- [ ] **Step 1: Replace header row and progress strip with MissionCard + ProgressBar**

In `ActiveWorkoutScreen.kt`, inside the main `Column`, replace these sections:

**Remove** the header `Row` (around lines 154-188) that shows zone badge + elapsed time.

**Remove** the `WorkoutProgressStrip(uiState = uiState, state = state)` call (around line 190).

**Insert** in their place:
```kotlin
MissionCard(uiState = uiState)

// Progress bar with halfway turn marker
val showProgress = uiState.totalDurationSeconds != null || uiState.totalDistanceMeters != null
if (showProgress) {
    val progress = when {
        uiState.totalDurationSeconds != null -> {
            val total = uiState.totalDurationSeconds
            (uiState.elapsedSeconds.toFloat() / total).coerceIn(0f, 1f)
        }
        uiState.totalDistanceMeters != null -> {
            val total = uiState.totalDistanceMeters
            (state.distanceMeters / total).coerceIn(0f, 1f)
        }
        else -> 0f
    }
    val turnLabel = when {
        uiState.totalDurationSeconds != null ->
            "Turn ${formatDurationSeconds(uiState.totalDurationSeconds / 2)}"
        uiState.totalDistanceMeters != null ->
            "Turn ${formatDistanceKm(uiState.totalDistanceMeters / 2f)} km"
        else -> null
    }
    ProgressBarWithTurnMarker(
        progress = progress,
        showTurnMarker = true,
        turnLabel = turnLabel
    )
}
```

- [ ] **Step 2: Keep segment card, guidance card, hero stat cards, tertiary row, buttons unchanged**

The segment countdown card (for interval workouts), Distance+Pace hero cards, guidance card, Avg HR + auto-pause tertiary row, and Pause/End Run buttons all remain exactly as they are.

- [ ] **Step 3: Clean up unused composables**

The `WorkoutProgressStrip` composable and `GradientProgressBar` composable are now unused. Delete them from `ActiveWorkoutScreen.kt`. Also remove the `zoneStatusLabel()` function if it's no longer called (it was used in the old header — check if MissionCard uses its own logic or calls it).

Keep `zoneColorFor()` — it's still used by MissionCard and other elements.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat(workout): wire MissionCard and ProgressBar, replace header and strip"
```

---

### Task 5: Deploy and verify on device

**Files:** None (verification only)

- [ ] **Step 1: Build and install**

```bash
./gradlew.bat assembleDebug
C:/Users/glm_6/AppData/Local/Android/Sdk/platform-tools/adb.exe -s R5CW715EPSB install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Manual smoke test**

Verify these scenarios on the device:
1. Start a bootcamp session — mission card shows session label, week number, target HR pill, timer with goal, progress bar with turn marker
2. Start a freestyle steady-state workout — mission card shows "Steady-state", no week number, target HR pill, timer with goal
3. Start a freestyle free run (open-ended) — mission card shows elapsed time only, no progress bar, no turn marker, no target HR
4. Verify the turn marker label does NOT overlap with the HR ring below

- [ ] **Step 3: Commit any fixes from smoke test**
