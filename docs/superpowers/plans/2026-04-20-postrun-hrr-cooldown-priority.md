# HRR Cooldown Priority + Audio Bookends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the 120-second Heart-Rate-Recovery (HRR) cooldown prompt above all other PostRun content, and add audio bookends ("Walk slowly" start cue + "Recovery measurement complete" end cue) so a runner not looking at the phone still gets the measurement.

**Architecture:** Two independent changes: (1) re-order PostRun composition so the `HrrCooldownCard` sits *above* the hero when active, (2) on start of the HRR window, play a short earcon + TTS via a new `CoachingAudioManager.playHrrStart()` / `playHrrComplete()`. The HRR window is already triggered by `LaunchedEffect(uiState.workoutEndTimeMs)` in the Screen — we piggyback on that effect.

**Tech Stack:** Kotlin, Jetpack Compose, existing `VoicePlayer.speakAnnouncement`, existing earcon pool. We **reuse `earcon_in_zone_confirm.wav`** for HRR start and `earcon_workout_complete.wav` for HRR end — no new audio assets required.

---

## File Structure

- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — reorder composition, call audio manager on window transitions.
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt` — add `playHrrStart()` and `playHrrComplete()`.
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt` — inject `CoachingAudioManager` (or provider), expose trigger methods.

**Constraint:** `CoachingAudioManager` lives inside `WorkoutForegroundService` and is destroyed on `stopWorkout()`. We cannot reuse that instance. Solution: add an **`HrrAudio` Hilt-singleton** that wraps a minimal `VoicePlayer` + `EarconPlayer` lifecycle owned by the ViewModel scope.

- Create: `app/src/main/java/com/hrcoach/ui/postrun/HrrAudio.kt`
- Create: `app/src/test/java/com/hrcoach/ui/postrun/HrrAudioTest.kt`

---

## Task 1: Create a self-contained `HrrAudio` helper

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/postrun/HrrAudio.kt`

- [ ] **Step 1.1: Write the helper class**

```kotlin
package com.hrcoach.ui.postrun

import android.content.Context
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.service.audio.EarconPlayer
import com.hrcoach.service.audio.VoicePlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal post-workout audio — plays the HRR cooldown start ("Walk slowly")
 * and end ("Recovery measurement complete") bookends.
 *
 * Independent from the service's CoachingAudioManager because that manager
 * is destroyed the moment stopWorkout() completes. We own our own
 * EarconPlayer + VoicePlayer for the 2-minute post-run window.
 *
 * Respects user audio settings: OFF silences everything; MINIMAL plays voice
 * only (no earcon); FULL plays both.
 */
@Singleton
class HrrAudio @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioSettingsRepository: AudioSettingsRepository
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var earconPlayer: EarconPlayer? = null
    private var voicePlayer: VoicePlayer? = null

    private fun ensureInitialised(settings: AudioSettings) {
        if (earconPlayer == null) earconPlayer = EarconPlayer(context).also {
            it.setVolume(settings.earconVolume)
        }
        if (voicePlayer == null) voicePlayer = VoicePlayer(context).also {
            it.setVolume(settings.voiceVolume)
            it.verbosity = settings.voiceVerbosity
        }
    }

    fun playHrrStart() = scope.launch {
        val settings = audioSettingsRepository.getAudioSettings()
        if (settings.voiceVerbosity == VoiceVerbosity.OFF) return@launch
        ensureInitialised(settings)
        if (settings.voiceVerbosity == VoiceVerbosity.FULL) {
            earconPlayer?.play(CoachingEvent.IN_ZONE_CONFIRM)
        }
        voicePlayer?.speakAnnouncement("Walk slowly. Measuring your recovery.")
    }

    fun playHrrComplete() = scope.launch {
        val settings = audioSettingsRepository.getAudioSettings()
        if (settings.voiceVerbosity == VoiceVerbosity.OFF) return@launch
        ensureInitialised(settings)
        if (settings.voiceVerbosity == VoiceVerbosity.FULL) {
            earconPlayer?.play(CoachingEvent.WORKOUT_COMPLETE)
        }
        voicePlayer?.speakAnnouncement("Recovery measurement complete.")
    }

    fun release() {
        earconPlayer?.release()
        earconPlayer = null
        voicePlayer?.destroy()
        voicePlayer = null
    }
}
```

- [ ] **Step 1.2: Verify `EarconPlayer.release()` and `AudioSettingsRepository.getAudioSettings()` exist**

Run: `./gradlew :app:compileDebugKotlin`

If `EarconPlayer` has no `release()` method, substitute whatever it uses (likely `destroy()` — grep first). If `AudioSettingsRepository.getAudioSettings()` doesn't exist, use the actual method name (likely `getSettings()` or a `Flow<AudioSettings>` — grep and adjust to a suspend call).

- [ ] **Step 1.3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/HrrAudio.kt
git commit -m "feat(postrun): add HrrAudio helper for cooldown bookends"
```

---

## Task 2: Trigger HRR audio from the ViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`

- [ ] **Step 2.1: Inject `HrrAudio` and expose triggers**

In `PostRunSummaryViewModel.kt`, update the constructor (starts at line 68):

```kotlin
@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val hrrAudio: HrrAudio
) : ViewModel() {
```

Add public methods below `load()`:

```kotlin
    fun onHrrWindowStarted() {
        hrrAudio.playHrrStart()
    }

    fun onHrrWindowEnded() {
        hrrAudio.playHrrComplete()
    }

    override fun onCleared() {
        super.onCleared()
        // HrrAudio is @Singleton — do NOT release it here. The VM is short-lived
        // and other consumers (rotations, re-entry) may still need the instance.
        // It lives for the process lifetime. Remove this comment and add
        // hrrAudio.release() only if HrrAudio is ever moved out of @Singleton scope.
    }
```

- [ ] **Step 2.2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt
git commit -m "feat(postrun): wire HrrAudio into PostRunSummaryViewModel"
```

---

## Task 3: Move HRR card above the fold and fire audio

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 3.1: Fire audio on the HRR window transitions**

In `PostRunSummaryScreen.kt`, update the HRR `LaunchedEffect` (starts at line 101):

Find:
```kotlin
    LaunchedEffect(uiState.workoutEndTimeMs) {
        val endMs = uiState.workoutEndTimeMs
        if (endMs <= 0L) return@LaunchedEffect
        val remaining = hrrWindowMs - (System.currentTimeMillis() - endMs)
        if (remaining > 0L) {
            isHrrActive = true
            delay(remaining)
            isHrrActive = false
        }
    }
```

Replace with:
```kotlin
    LaunchedEffect(uiState.workoutEndTimeMs) {
        val endMs = uiState.workoutEndTimeMs
        if (endMs <= 0L) return@LaunchedEffect
        val remaining = hrrWindowMs - (System.currentTimeMillis() - endMs)
        if (remaining > 0L) {
            // Announce only if we're entering the window on a FRESH run.
            // Returning to this screen mid-window should not re-announce.
            val isFreshEntry = remaining > hrrWindowMs - 5_000L  // <5s since end
            isHrrActive = true
            if (isFreshEntry) viewModel.onHrrWindowStarted()
            delay(remaining)
            isHrrActive = false
            viewModel.onHrrWindowEnded()
        }
    }
```

- [ ] **Step 3.2: Move `HrrCooldownCard` rendering above the hero**

This is handled jointly with Plan 2 (primary-cta-and-hero). If Plan 2 has been executed, the HRR card is already in the scrollable body. Move it *above* `RunCompleteHero` in the composition order:

In the `PostRunContentState.CONTENT` branch, the order becomes:
1. `if (isHrrActive) HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)` **← first**
2. `RunCompleteHero(visible = showCelebration, distanceText = uiState.distanceText)`
3. achievements
4. hrMaxDelta
5. ... rest unchanged

Concretely, find the two blocks in that order and swap them:
```kotlin
RunCompleteHero(...)

if (uiState.newAchievements.isNotEmpty()) {
    NewAchievementsSection(achievements = uiState.newAchievements)
}

if (isHrrActive) {
    HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
}
```

becomes:

```kotlin
if (isHrrActive) {
    HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
}

RunCompleteHero(visible = showCelebration, distanceText = uiState.distanceText)

if (uiState.newAchievements.isNotEmpty()) {
    NewAchievementsSection(achievements = uiState.newAchievements)
}
```

- [ ] **Step 3.3: Make the HRR card visually dominant while active**

Currently `HrrCooldownCard` is a standard `GlassCard`. Since it now sits at the very top, uplift it with a gradient border to signal "you should be doing this NOW". At `PostRunSummaryScreen.kt:437`, replace the `GlassCard {` wrapper with:

```kotlin
GlassCard(
    borderColor = Color.Transparent,
    modifier = Modifier
        .fillMaxWidth()
        .border(
            width = 1.5.dp,
            brush = CardeaTheme.colors.gradient,
            shape = RoundedCornerShape(14.dp)
        )
) {
```

Add the `import androidx.compose.foundation.border` at the top if not already present.

- [ ] **Step 3.4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(postrun): elevate HRR cooldown above hero with gradient border"
```

---

## Task 4: Device verification

- [ ] **Step 4.1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4.2: Complete a sim run and observe HRR window**

1. Account → Simulation → Start a short sim run, let it run ≥30 seconds.
2. Tap Stop.
3. PostRun appears — **expected:** earcon + "Walk slowly. Measuring your recovery." TTS, HRR card pinned above hero with gradient border and countdown.
4. Wait 120 s (or scrub `workoutEndTimeMs` in Room — easier to just wait).
5. **Expected:** earcon + "Recovery measurement complete." TTS, card switches to "You can stop walking now."

- [ ] **Step 4.3: Re-enter screen mid-window — must NOT re-announce**

1. Start step 4.2 again.
2. During the 120 s window, press back, then re-navigate to the PostRun from HistoryDetail's "Post-run insights" button.
3. **Expected:** HRR card visible with correct remaining count. **No voice re-announcement.**

- [ ] **Step 4.4: Verify OFF verbosity silences everything**

Account → Audio → Voice Verbosity = OFF. Repeat 4.2. Expected: card visible, ZERO audio.

- [ ] **Step 4.5: Run full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.

---

## Self-review checklist

- [x] HrrAudio is `@Singleton` so it survives ViewModel re-creation.
- [x] Fresh-entry guard (`remaining > hrrWindowMs - 5_000L`) prevents re-announcement on re-entry.
- [x] Earcon reuses existing `R.raw.earcon_in_zone_confirm` (start) and `R.raw.earcon_workout_complete` (end) — no new assets.
- [x] OFF verbosity short-circuits before any audio load.
- [x] Release is not called in `onCleared` because the singleton lives for the process — keeps TTS warm for subsequent runs.
- [x] `AudioSettingsRepository.getAudioSettings()` is the assumed method name — verify via grep in Step 1.2.
