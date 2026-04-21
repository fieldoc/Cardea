# Post-Workout Audio Bookend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire the `WORKOUT_COMPLETE` earcon + a TTS run summary on every user-initiated Stop, not only when a target distance/duration is hit.

**Architecture:** `stopWorkout()` in `WorkoutForegroundService` (WFS) currently tears down audio silently. We add (1) a new `CoachingAudioManager.playEndSequence(summary)` that mirrors `playStartSequence`, plays the existing `earcon_workout_complete.wav`, then speaks a compact summary via the existing `VoicePlayer`; (2) a call site at the top of `stopWorkout()` gated on the same `enableWorkoutComplete` setting as the auto-fire path; (3) a pure summary-text builder in `VoicePlayer.Companion` with unit tests.

**Tech Stack:** Kotlin, Android, Compose (no UI), JUnit4, existing `CoachingAudioManager` / `VoicePlayer` / `EarconPlayer` infrastructure.

---

## File Structure

- Modify: `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt` — add `buildEndSummaryText()` in `companion object`.
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt` — add `suspend fun playEndSequence(...)`.
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt` — call `playEndSequence` at top of `stopWorkout()` before any teardown.
- Create: `app/src/test/java/com/hrcoach/service/audio/VoicePlayerEndSummaryTest.kt` — pure unit tests for the builder.

---

## Task 1: Build the summary-text builder (TDD)

**Files:**
- Create: `app/src/test/java/com/hrcoach/service/audio/VoicePlayerEndSummaryTest.kt`
- Modify: `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt` (add to `companion object`)

- [ ] **Step 1.1: Write the failing test**

```kotlin
package com.hrcoach.service.audio

import com.hrcoach.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlayerEndSummaryTest {

    @Test
    fun `km summary includes distance duration and avg hr`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 5200f,
            activeDurationSec = 1920L,
            avgHr = 152,
            unit = DistanceUnit.KM
        )
        assertEquals(
            "Workout complete. 5.2 kilometers in 32 minutes. Average heart rate 152.",
            text
        )
    }

    @Test
    fun `mile summary uses miles phrasing`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 1609f,
            activeDurationSec = 600L,
            avgHr = 140,
            unit = DistanceUnit.MI
        )
        assertTrue("should say miles: $text", text.contains("1.0 miles"))
    }

    @Test
    fun `sub-minute run rounds up to 1 minute`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 300f,
            activeDurationSec = 40L,
            avgHr = 128,
            unit = DistanceUnit.KM
        )
        assertTrue("should say 1 minute: $text", text.contains("1 minute"))
    }

    @Test
    fun `missing avg hr omits hr clause`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 3000f,
            activeDurationSec = 900L,
            avgHr = null,
            unit = DistanceUnit.KM
        )
        assertEquals("Workout complete. 3.0 kilometers in 15 minutes.", text)
    }

    @Test
    fun `zero-distance run still announces complete`() {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = 0f,
            activeDurationSec = 0L,
            avgHr = null,
            unit = DistanceUnit.KM
        )
        assertEquals("Workout complete.", text)
    }
}
```

- [ ] **Step 1.2: Run the test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VoicePlayerEndSummaryTest"`
Expected: FAIL with "Unresolved reference: buildEndSummaryText".

- [ ] **Step 1.3: Implement `buildEndSummaryText` in `VoicePlayer.companion`**

Add inside the `companion object` block in `VoicePlayer.kt` (immediately after `eventText(...)` function, around line 360):

```kotlin
/**
 * Builds a TTS-friendly end-of-workout summary.
 * Pure function — unit-tested in VoicePlayerEndSummaryTest.
 *
 * Returns "Workout complete." alone when distance and duration are both zero
 * (short/discarded run path — caller should skip TTS entirely in that case,
 * but we keep the fallback for safety).
 */
fun buildEndSummaryText(
    distanceMeters: Float,
    activeDurationSec: Long,
    avgHr: Int?,
    unit: DistanceUnit
): String {
    if (distanceMeters <= 0f && activeDurationSec <= 0L) {
        return "Workout complete."
    }
    val distanceInUnit = if (unit == DistanceUnit.MI) {
        distanceMeters / 1609.344f
    } else {
        distanceMeters / 1000f
    }
    val unitWord = if (unit == DistanceUnit.MI) "miles" else "kilometers"
    val distancePart = String.format("%.1f %s", distanceInUnit, unitWord)
    val minutes = maxOf(1L, (activeDurationSec + 30L) / 60L)  // round to nearest, min 1
    val minutesWord = if (minutes == 1L) "minute" else "minutes"
    val hrClause = avgHr?.let { " Average heart rate $it." } ?: ""
    return "Workout complete. $distancePart in $minutes $minutesWord.$hrClause"
}
```

- [ ] **Step 1.4: Run the test and verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VoicePlayerEndSummaryTest"`
Expected: PASS (5 tests).

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt \
        app/src/test/java/com/hrcoach/service/audio/VoicePlayerEndSummaryTest.kt
git commit -m "feat(audio): add buildEndSummaryText for post-run TTS"
```

---

## Task 2: Add `playEndSequence` to `CoachingAudioManager`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`

- [ ] **Step 2.1: Add the `playEndSequence` method**

Insert immediately after `playStartSequence(...)` (around line 102, before the `var distanceUnit` declaration):

```kotlin
/**
 * Plays the end-of-workout bookend: earcon + TTS summary. Called by WFS at the top
 * of stopWorkout() for ALL stops (manual Stop, target hit, auto-end). Mirrors
 * playStartSequence so the runner gets symmetrical audio boundaries.
 *
 * Gated on [AudioSettings.enableWorkoutComplete] AND the existing verbosity rules.
 * OFF verbosity plays neither earcon nor voice. MINIMAL plays neither (WORKOUT_COMPLETE
 * is INFORMATIONAL; MINIMAL suppresses informational earcons — consistent with fireEvent).
 * FULL plays both. The summary text is suppressed when activeDurationSec <= 0 or
 * distanceMeters <= 0 (discarded/very-short runs — caller may also choose not to call).
 *
 * Does NOT suspend on voice completion — WFS teardown can proceed in parallel. TTS lives
 * on an android service scope; final utterances complete after the screen transitions.
 */
fun playEndSequence(
    distanceMeters: Float,
    activeDurationSec: Long,
    avgHr: Int?
) {
    if (currentSettings.enableWorkoutComplete == false) return

    val verbosity = currentSettings.voiceVerbosity
    if (verbosity == VoiceVerbosity.OFF) return

    // Count the event so SoundsHeardSection sees it on first-3 runs.
    cueCounts.merge(CoachingEvent.WORKOUT_COMPLETE, 1) { a, b -> a + b }

    // Earcon: FULL only (mirrors fireEvent's MINIMAL-suppresses-informational rule).
    if (verbosity == VoiceVerbosity.FULL) {
        earconPlayer.play(CoachingEvent.WORKOUT_COMPLETE)
    }

    // Voice summary: MINIMAL and FULL. MINIMAL users opted for fewer cues, but the
    // end-of-workout summary is high-value, low-frequency (once per run), and they
    // already accept zone alerts — this is symmetric with the start briefing.
    if (activeDurationSec > 0L || distanceMeters > 0f) {
        val text = VoicePlayer.buildEndSummaryText(
            distanceMeters = distanceMeters,
            activeDurationSec = activeDurationSec,
            avgHr = avgHr,
            unit = distanceUnit
        )
        voicePlayer.speakAnnouncement(text)
    }
}
```

- [ ] **Step 2.2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "feat(audio): add playEndSequence bookend on CoachingAudioManager"
```

---

## Task 3: Call `playEndSequence` from WFS `stopWorkout`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 3.1: Compute summary inputs and fire the bookend at the top of stopWorkout**

In `stopWorkout()` at `WorkoutForegroundService.kt:733`, insert BETWEEN the `isStopping = true` guard and the `stopJob?.cancel()` line (so it fires synchronously on the main thread before any teardown, while the audio manager is still alive):

Find:
```kotlin
    private fun stopWorkout() {
        if (isStopping) return
        isStopping = true
        val finalHrSampleSum = hrSampleSum
        val finalHrSampleCount = hrSampleCount
        hrSampleSum = 0L
        hrSampleCount = 0

        stopJob?.cancel()
```

Replace with:
```kotlin
    private fun stopWorkout() {
        if (isStopping) return
        isStopping = true
        val finalHrSampleSum = hrSampleSum
        val finalHrSampleCount = hrSampleCount
        hrSampleSum = 0L
        hrSampleCount = 0

        // Audio bookend: symmetrical with playStartSequence. Must fire BEFORE teardown
        // because cleanupManagers() destroys the CoachingAudioManager. speakAnnouncement
        // hands text to the TTS engine which outlives our scope — final utterance will
        // complete even after the service transitions.
        runCatching {
            val now = clock.now()
            val currentPauseMs = if (pauseStartMs > 0L) now - pauseStartMs else 0L
            val currentAutoPauseMs = if (autoPauseStartMs > 0L) now - autoPauseStartMs else 0L
            val activeSec = (now - workoutStartMs - totalPausedMs - totalAutoPausedMs
                - currentPauseMs - currentAutoPauseMs).coerceAtLeast(0L) / 1000L
            val distanceMeters = WorkoutState.snapshot.value.distanceMeters
            val avgHr = if (finalHrSampleCount > 0) {
                (finalHrSampleSum.toFloat() / finalHrSampleCount).toInt()
            } else null
            // Skip bookend for runs that will be discarded (< 200m AND < 1 min — same
            // threshold used below). A phantom "Workout complete" on a 10-second tap
            // would be confusing.
            val willBeDiscarded = distanceMeters < 200f && activeSec < 60L
            if (!willBeDiscarded) {
                coachingAudioManager?.playEndSequence(
                    distanceMeters = distanceMeters,
                    activeDurationSec = activeSec,
                    avgHr = avgHr
                )
            }
        }.onFailure { e ->
            Log.w("WorkoutService", "End-of-workout bookend failed (non-fatal)", e)
        }

        stopJob?.cancel()
```

- [ ] **Step 3.2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `coachingAudioManager` is not nullable in this file, drop the `?.`.

- [ ] **Step 3.3: Run full test suite to confirm no regressions**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS. In particular `AlertPolicyTest`, `CoachingEventRouterTest`, `VoicePlayer*` tests must still pass.

- [ ] **Step 3.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(service): fire audio bookend from stopWorkout for all stops"
```

---

## Task 4: Manual device verification

**Files:** (no code changes)

- [ ] **Step 4.1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4.2: Verify manual-stop path with FREE_RUN**

1. Launch app, start a FREE_RUN from the Workout tab (no target).
2. Let ~90 seconds elapse with music playing on the device for layering realism.
3. Tap Stop → Confirm.
4. **Expected:** earcon chime followed by TTS "Workout complete. X.Y kilometers in 2 minutes. Average heart rate Z."
5. PostRunSummary appears after audio starts (does not wait).

- [ ] **Step 4.3: Verify target-hit path still fires once (not twice)**

1. Start a STEADY_STATE workout with a 2-minute duration target.
2. Let it auto-complete (router fires `WORKOUT_COMPLETE`).
3. Tap Stop on the still-active post-target screen.
4. **Expected:** ONE earcon + ONE summary voice line. Router fires at target; stopWorkout's call re-fires the earcon + a *summary* announcement. Verify audibly — if the router's "Workout complete" collides with the summary, the summary wins because `speakAnnouncement` uses `QUEUE_ADD`.
5. If double-earcon is audible, note it as follow-up (gate to add: skip earcon in `playEndSequence` when `cueCounts[WORKOUT_COMPLETE] > 0` already — i.e. router already fired).

- [ ] **Step 4.4: Verify verbosity gates**

1. Account → Audio → set Voice Verbosity = OFF. Start & stop a short run. Expected: silence at stop.
2. Set Verbosity = MINIMAL. Start & stop. Expected: voice summary, no earcon.
3. Set `enableWorkoutComplete` = false. Start & stop. Expected: silence.

- [ ] **Step 4.5: Commit verification notes (optional)**

If Step 4.3 revealed a double-fire, add a follow-up note in `docs/TODO.md` or equivalent. Otherwise no commit needed.

---

## Self-review checklist

- [x] Every file path is absolute to repo root.
- [x] Every code step contains real code.
- [x] Test commands show expected outcomes.
- [x] Types used (`DistanceUnit`, `VoiceVerbosity`, `CoachingEvent.WORKOUT_COMPLETE`, `CoachingAudioManager`) all exist in the codebase.
- [x] Commits are bite-sized (one per functional unit).
- [x] Manual verification included because audio output is the deliverable and unit tests can't assert on it.
