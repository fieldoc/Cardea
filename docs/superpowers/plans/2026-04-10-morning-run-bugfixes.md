# Morning Run Bugfixes — 2026-04-10

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 bugs discovered during this morning's run — 3 critical audio/coaching issues, 1 medium post-run UX gap, 1 mild maps styling issue, and 1 minor UI text clipping.

**Architecture:** Targeted fixes across the audio pipeline (VoiceCoach completion callback), autopause race condition (read state before WorkoutState update), WorkoutConfig.totalDurationSeconds() for FREE_RUN mode, post-run cooldown timer, maps dark-mode styling, and button text overflow.

**Tech Stack:** Kotlin, Jetpack Compose, Android MediaPlayer, TextToSpeech, Google Maps Compose SDK

---

## Task 1: Fix competing voice prompts (Bug 1 — CRITICAL)

**Root cause:** `CoachingAudioManager.fireEvent()` uses a hardcoded 1800ms delay between VoiceCoach (pre-recorded MP3) and TtsBriefingPlayer (system TTS). These use different voice genders and have no synchronization — both can play simultaneously.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt`
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`
- Test: `app/src/test/java/com/hrcoach/service/audio/VoiceCoachTest.kt` (create if absent)

- [ ] **Step 1: Add suspending `awaitCompletion()` to VoiceCoach**

In `VoiceCoach.kt`, add a `CompletableDeferred` that resolves when the MediaPlayer finishes:

```kotlin
import kotlinx.coroutines.CompletableDeferred

// Add field after line 16:
private var completionSignal: CompletableDeferred<Unit>? = null

// Replace the setOnCompletionListener block (lines 49-53) with:
mp.setOnCompletionListener {
    it.release()
    mediaPlayer = null
    currentPriority = VoiceEventPriority.INFORMATIONAL
    completionSignal?.complete(Unit)
    completionSignal = null
}

// Add new suspend method after isPlaying() (line 59):
/**
 * Suspends until the current voice clip finishes playing.
 * Returns immediately if nothing is playing.
 */
suspend fun awaitCompletion() {
    completionSignal?.await()
}
```

Also update `speak()` to create the signal before `mp.start()`:
```kotlin
// Before mp.start() (line 54), insert:
completionSignal?.complete(Unit)  // cancel any prior signal
completionSignal = CompletableDeferred()
```

And update `releasePlayer()` to complete the signal:
```kotlin
// In releasePlayer() (line 65), after setting mediaPlayer = null:
completionSignal?.complete(Unit)
completionSignal = null
```

- [ ] **Step 2: Replace hardcoded delay with awaitCompletion() in CoachingAudioManager**

In `CoachingAudioManager.kt`, replace lines 74-84:

```kotlin
scope.launch {
    delay(300L)
    voiceCoach.speak(event, guidanceText)
    // In FULL verbosity, follow the static clip with dynamic TTS guidance
    if (currentSettings.voiceVerbosity == VoiceVerbosity.FULL &&
        !guidanceText.isNullOrBlank()
    ) {
        voiceCoach.awaitCompletion()  // wait for MP3 to finish
        delay(200L)                   // small breath gap between clips
        ttsBriefingPlayer.speak(guidanceText)
    }
}
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt \
       app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "fix(audio): replace hardcoded 1800ms delay with VoiceCoach completion callback

VoiceCoach now exposes awaitCompletion() via CompletableDeferred.
CoachingAudioManager waits for the MP3 to finish before queuing TTS,
eliminating simultaneous playback of competing male/female voices."
```

---

## Task 2: Fix autopause race condition (Bug 4 — CRITICAL)

**Root cause:** `WorkoutForegroundService.onHrTick()` calls `autoPauseDetector.update()` which triggers `WorkoutState.update { copy(isAutoPaused = true) }`, but then reads `WorkoutState.snapshot.value.isAutoPaused` on line 395 — the StateFlow may not have propagated yet, so coaching fires on the same tick autopause activates. Also, pause tones are silenced when verbosity is OFF.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`

- [ ] **Step 1: Use local variable instead of re-reading StateFlow**

In `WorkoutForegroundService.kt`, replace line 395:
```kotlin
val isAutoPaused = WorkoutState.snapshot.value.isAutoPaused
```

With a local variable that tracks the autopause state directly from the detector result this tick:

```kotlin
// After the autopause detection block (after line 393), replace line 395 with:
val autoPauseResult = if (sessionAutoPauseEnabled && nowMs >= autoPauseGraceUntilMs) {
    autoPauseDetector?.update(tick.speed, nowMs)
} else null
```

Then restructure: move the autopause detection into the variable assignment, and use the result directly:

Actually, simpler approach — track a local `isAutoPaused` boolean from the detector's internal state rather than re-reading StateFlow. Change lines 374-395 to:

```kotlin
// Auto-pause detection: run before elapsed-time math so state is fresh this tick
// Skip during grace period after start so runner can pocket phone without "Auto-Paused"
var isAutoPaused = WorkoutState.snapshot.value.isAutoPaused  // read BEFORE update
if (sessionAutoPauseEnabled && nowMs >= autoPauseGraceUntilMs) {
    when (autoPauseDetector?.update(tick.speed, nowMs)) {
        AutoPauseEvent.PAUSED -> {
            isAutoPaused = true  // use local var immediately
            autoPauseStartMs = nowMs
            locationSource?.setMoving(false)
            WorkoutState.update { it.copy(isAutoPaused = true) }
            coachingAudioManager?.playPauseFeedback(paused = true)
        }
        AutoPauseEvent.RESUMED -> {
            isAutoPaused = false  // use local var immediately
            totalAutoPausedMs += nowMs - autoPauseStartMs
            autoPauseStartMs = 0L
            locationSource?.setMoving(true)
            WorkoutState.update { it.copy(isAutoPaused = false) }
            coachingAudioManager?.playPauseFeedback(paused = false)
        }
        else -> Unit
    }
}
// Remove the old line 395: val isAutoPaused = WorkoutState.snapshot.value.isAutoPaused
```

- [ ] **Step 2: Always play pause tones regardless of verbosity**

In `CoachingAudioManager.kt`, line 123 currently reads:
```kotlin
if (currentSettings.voiceVerbosity == VoiceVerbosity.OFF) return
```

Change to:
```kotlin
// Pause/resume feedback is safety-critical — always play, even when voice is OFF
```

(Delete the early return entirely. The tones use earconVolume which is independent.)

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt \
       app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "fix(autopause): eliminate race condition between state update and coaching gate

Use local isAutoPaused variable set immediately from detector result
instead of re-reading StateFlow which may not have propagated yet.
Also: pause tones now play even when voice verbosity is OFF."
```

---

## Task 3: Fix missing time target on active run screen (Bug 5 — CRITICAL)

**Root cause:** `WorkoutConfig.totalDurationSeconds()` returns `null` for `FREE_RUN` mode (line 66: `if (mode == WorkoutMode.FREE_RUN) return null`). Bootcamp timed sessions use FREE_RUN mode with `plannedDurationMinutes` set, but `totalDurationSeconds()` ignores it. This breaks both the UI display and `CoachingEventRouter` halfway/complete triggers.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Test: `app/src/test/java/com/hrcoach/domain/model/WorkoutConfigTest.kt` (create or extend)

- [ ] **Step 1: Write failing test**

Create or extend `WorkoutConfigTest.kt`:

```kotlin
package com.hrcoach.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutConfigFreeRunDurationTest {

    @Test
    fun `totalDurationSeconds returns planned duration for FREE_RUN with plannedDurationMinutes`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.FREE_RUN,
            plannedDurationMinutes = 31
        )
        assertEquals(31L * 60, config.totalDurationSeconds())
    }

    @Test
    fun `totalDurationSeconds returns null for FREE_RUN without plannedDurationMinutes`() {
        val config = WorkoutConfig(mode = WorkoutMode.FREE_RUN)
        assertEquals(null, config.totalDurationSeconds())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.WorkoutConfigFreeRunDurationTest"`
Expected: FAIL — first test returns `null` instead of `1860`

- [ ] **Step 3: Fix totalDurationSeconds() to respect plannedDurationMinutes**

In `WorkoutConfig.kt`, replace `totalDurationSeconds()` (lines 64-69):

```kotlin
/** Total target duration in seconds, or null if not time-based. */
fun totalDurationSeconds(): Long? {
    // FREE_RUN bootcamp sessions carry plannedDurationMinutes as the time target
    if (mode == WorkoutMode.FREE_RUN) {
        return plannedDurationMinutes?.let { it.toLong() * 60 }
    }
    val total = segments.mapNotNull { it.durationSeconds?.toLong() }.sum()
    return total.takeIf { it > 0 }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.WorkoutConfigFreeRunDurationTest"`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass. Verify that `CoachingEventRouter` tests (if any) still pass — `totalDurationSeconds()` is now non-null for FREE_RUN with duration, so HALFWAY and WORKOUT_COMPLETE will fire.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt \
       app/src/test/java/com/hrcoach/domain/model/WorkoutConfigFreeRunDurationTest.kt
git commit -m "fix(config): totalDurationSeconds() now returns plannedDurationMinutes for FREE_RUN

Bootcamp timed sessions use FREE_RUN mode with plannedDurationMinutes.
totalDurationSeconds() was unconditionally returning null for FREE_RUN,
breaking the active workout time display, halfway prompt, and completion
trigger in CoachingEventRouter."
```

---

## Task 4: Add cooldown timer to post-run screen (Bug 2 — MEDIUM)

**Root cause:** `HrrCooldownCard()` shows static text "Walk slowly for 120 seconds" but no countdown. The card needs a `LaunchedEffect` timer counting from `workout.endTime`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1: Add countdown parameter and timer to HrrCooldownCard**

Replace the `HrrCooldownCard()` composable (lines 359-388) with:

```kotlin
@Composable
private fun HrrCooldownCard(endTimeMs: Long) {
    val totalCooldownSec = 120
    var remainingSeconds by remember { mutableIntStateOf(totalCooldownSec) }

    LaunchedEffect(endTimeMs) {
        while (remainingSeconds > 0) {
            val elapsed = ((System.currentTimeMillis() - endTimeMs) / 1000).toInt()
            remainingSeconds = (totalCooldownSec - elapsed).coerceAtLeast(0)
            delay(1_000L)
        }
    }

    val isComplete = remainingSeconds <= 0

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isComplete) "Recovery measurement complete"
                           else "Calculating your 30-day recovery index\u2026",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isComplete) {
                    Text(
                        text = "You can stop walking now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HrCoachThemeTokens.subtleText
                    )
                } else {
                    Text(
                        text = "Walk slowly \u2014 ${remainingSeconds}s remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = HrCoachThemeTokens.subtleText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { 1f - (remainingSeconds.toFloat() / totalCooldownSec) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CardeaTheme.colors.glassBorder
                    )
                }
            }
        }
    }
}
```

Add these imports at the top of the file if missing:
```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
```

- [ ] **Step 2: Add workoutEndTimeMs to PostRunSummaryUiState and pass to HrrCooldownCard**

In `PostRunSummaryViewModel.kt`, add to `PostRunSummaryUiState`:
```kotlin
val workoutEndTimeMs: Long = 0L,
```

In the ViewModel where `_uiState.value = PostRunSummaryUiState(...)` is set (around line 106), add:
```kotlin
workoutEndTimeMs = workout.endTime,
```

In `PostRunSummaryScreen.kt`, update the call site (line 186):
```kotlin
HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(postrun): add 120s cooldown countdown timer to HRR card

Shows remaining seconds, progress bar, and completion state instead of
static 'Walk slowly for 120 seconds' text."
```

---

## Task 5: Fix maps display issues (Bug 3 — MILD)

**Root cause:** No `MapProperties` configured on GoogleMap — POI labels visible, no dark mode style. Legend overlays are too opaque and large.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

- [ ] **Step 1: Add dark map style JSON**

Create a raw resource `app/src/main/res/raw/map_style_dark.json` with a minimal dark style that hides POIs and businesses:

```json
[
  { "elementType": "geometry", "stylers": [{ "color": "#1a1a2e" }] },
  { "elementType": "labels.text.fill", "stylers": [{ "color": "#8a8a8a" }] },
  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#1a1a2e" }] },
  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#2a2a3e" }] },
  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [{ "color": "#6a6a7a" }] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#0e0e1a" }] },
  { "featureType": "poi", "stylers": [{ "visibility": "off" }] },
  { "featureType": "poi.business", "stylers": [{ "visibility": "off" }] },
  { "featureType": "transit", "stylers": [{ "visibility": "off" }] }
]
```

- [ ] **Step 2: Apply MapProperties with dark style and disabled POIs**

In `HistoryDetailScreen.kt`, in `HrHeatmapRouteMap()` (around line 402), add:

```kotlin
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.android.gms.maps.model.MapStyleOptions

// Inside HrHeatmapRouteMap, before the GoogleMap composable:
val context = LocalContext.current
val mapStyle = remember {
    runCatching {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
    }.getOrNull()
}
```

Then add `properties` parameter to the `GoogleMap` call (line 423):

```kotlin
GoogleMap(
    modifier = Modifier.fillMaxSize(),
    cameraPositionState = cameraPositionState,
    onMapLoaded = { isMapLoaded = true },
    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    properties = MapProperties(
        mapStyleOptions = mapStyle,
        mapType = MapType.NORMAL
    )
) {
```

Add import: `import androidx.compose.ui.platform.LocalContext`

- [ ] **Step 3: Shrink and fade the overlays**

Replace `MapHeaderOverlay` (lines 331-354) — make it smaller and more translucent:

```kotlin
@Composable
private fun BoxScope.MapHeaderOverlay(hasWorkoutTarget: Boolean) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.TopStart)
            .background(Color(0xCC121212), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (hasWorkoutTarget) "Target heatmap" else "Heart-rate route",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
    }
}
```

Replace `MapLegendOverlay` (lines 357-378) — make it compact, anchored bottom-end:

```kotlin
@Composable
private fun BoxScope.MapLegendOverlay(hasWorkoutTarget: Boolean) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .background(Color(0xCC121212), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasWorkoutTarget) {
            LegendChip("On target", ZoneGreen)
            LegendChip("Caution", ZoneAmber)
            LegendChip("Redline", ZoneRed)
        } else {
            LegendChip("Easy", ZoneGreen)
            LegendChip("Moderate", ZoneAmber)
            LegendChip("High", ZoneRed)
        }
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/raw/map_style_dark.json \
       app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "design(maps): apply dark theme, hide POIs, shrink overlays

Adds dark map style JSON that hides business POIs and transit.
Shrinks header/legend overlays from 60% opaque boxes to compact chips.
Legend moved to bottom-end to avoid blocking route view."
```

---

## Task 6: Fix "View Progress" button text clipping (Bug 6 — MINOR)

**Root cause:** The `OutlinedButton` has `height(44.dp)` which clips multi-line text. "View\nProgress" wraps because the button is `weight(1f)` sharing space with "Delete Run".

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

- [ ] **Step 1: Add maxLines and overflow to the button text**

In `MoreActionsCard()` (line 570-573), change the View Progress button text:

```kotlin
Text(
    text = stringResource(R.string.button_view_progress),
    style = MaterialTheme.typography.labelLarge,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

Add import if missing: `import androidx.compose.ui.text.style.TextOverflow`

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "fix(ui): prevent View Progress button text from wrapping and clipping"
```
