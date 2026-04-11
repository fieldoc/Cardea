# Unified Voice Coaching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dual voice system (MP3 VoiceCoach + TTS TtsBriefingPlayer) with a single TTS-only coaching voice, fix the startup sequence ordering, and use a curated countdown WAV.

**Architecture:** `TtsBriefingPlayer` is expanded and renamed to `VoicePlayer` — the single voice output for all spoken coaching. `VoiceCoach` and all 60+ MP3 clips are deleted. `StartupSequencer` switches from `ToneGenerator` to `MediaPlayer` playing `countdown_321_go.wav`. `CoachingAudioManager` is simplified to orchestrate earcons + VoicePlayer.

**Tech Stack:** Kotlin, Android TTS (`android.speech.tts.TextToSpeech`), Android MediaPlayer, JUnit 5

**Spec:** `docs/superpowers/specs/2026-04-10-unified-voice-coaching-design.md`

---

### Task 1: Create VoicePlayer with verbosity filtering and text generation

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt`
- Create: `app/src/test/java/com/hrcoach/service/audio/VoicePlayerTextTest.kt`

This is the core new class. It replaces both `VoiceCoach` (MP3 playback) and the coaching-speech role of `TtsBriefingPlayer`. Built from `TtsBriefingPlayer`'s TTS init/speak logic, expanded with event speech, priority gating, and verbosity filtering.

- [ ] **Step 1: Write failing tests for verbosity filtering**

```kotlin
// app/src/test/java/com/hrcoach/service/audio/VoicePlayerTextTest.kt
package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.*
import org.junit.Test

class VoicePlayerVerbosityFilterTest {

    @Test
    fun `OFF blocks all events`() {
        for (event in CoachingEvent.entries) {
            assertFalse(
                "OFF should block $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.OFF)
            )
        }
    }

    @Test
    fun `MINIMAL allows critical and normal events`() {
        val allowed = listOf(
            CoachingEvent.SPEED_UP, CoachingEvent.SLOW_DOWN,
            CoachingEvent.RETURN_TO_ZONE, CoachingEvent.PREDICTIVE_WARNING,
            CoachingEvent.SEGMENT_CHANGE, CoachingEvent.SIGNAL_LOST,
            CoachingEvent.SIGNAL_REGAINED
        )
        for (event in allowed) {
            assertTrue(
                "MINIMAL should allow $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.MINIMAL)
            )
        }
    }

    @Test
    fun `MINIMAL blocks informational events`() {
        val blocked = listOf(
            CoachingEvent.KM_SPLIT, CoachingEvent.HALFWAY,
            CoachingEvent.WORKOUT_COMPLETE, CoachingEvent.IN_ZONE_CONFIRM
        )
        for (event in blocked) {
            assertFalse(
                "MINIMAL should block $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.MINIMAL)
            )
        }
    }

    @Test
    fun `FULL allows all events`() {
        for (event in CoachingEvent.entries) {
            assertTrue(
                "FULL should allow $event",
                VoicePlayer.shouldSpeak(event, VoiceVerbosity.FULL)
            )
        }
    }
}

class VoicePlayerKmSplitTextTest {

    @Test
    fun `km split text for STEADY_STATE is number only`() {
        assertEquals("Kilometer 5", VoicePlayer.kmSplitText(5, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `km split text for DISTANCE_PROFILE is number only`() {
        assertEquals("Kilometer 10", VoicePlayer.kmSplitText(10, WorkoutMode.DISTANCE_PROFILE))
    }

    @Test
    fun `km split text for FREE_RUN includes pace when provided`() {
        val result = VoicePlayer.kmSplitText(5, WorkoutMode.FREE_RUN, paceMinPerKm = 5.67f)
        assertEquals("Kilometer 5. Pace: 5 minutes 40.", result)
    }

    @Test
    fun `km split text for FREE_RUN without pace is number only`() {
        assertEquals("Kilometer 3", VoicePlayer.kmSplitText(3, WorkoutMode.FREE_RUN, paceMinPerKm = null))
    }

    @Test
    fun `pace formats correctly for exact minutes`() {
        val result = VoicePlayer.kmSplitText(1, WorkoutMode.FREE_RUN, paceMinPerKm = 6.0f)
        assertEquals("Kilometer 1. Pace: 6 minutes 0.", result)
    }
}

class VoicePlayerEventTextTest {

    @Test
    fun `zone alert uses guidance text directly`() {
        assertEquals(
            "Above zone - ease off now",
            VoicePlayer.eventText(CoachingEvent.SPEED_UP, "Above zone - ease off now", WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `zone alert with null guidance uses fallback`() {
        val text = VoicePlayer.eventText(CoachingEvent.SLOW_DOWN, null, WorkoutMode.STEADY_STATE)
        assertEquals("Slow down", text)
    }

    @Test
    fun `RETURN_TO_ZONE with guidance uses it`() {
        assertEquals(
            "HR settling back - hold steady",
            VoicePlayer.eventText(CoachingEvent.RETURN_TO_ZONE, "HR settling back - hold steady", WorkoutMode.STEADY_STATE)
        )
    }

    @Test
    fun `HALFWAY has fixed text`() {
        assertEquals("Halfway", VoicePlayer.eventText(CoachingEvent.HALFWAY, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `WORKOUT_COMPLETE has fixed text`() {
        assertEquals("Workout complete", VoicePlayer.eventText(CoachingEvent.WORKOUT_COMPLETE, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `IN_ZONE_CONFIRM has fixed text`() {
        assertEquals("Pace looks good", VoicePlayer.eventText(CoachingEvent.IN_ZONE_CONFIRM, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `SIGNAL_LOST has fixed text`() {
        assertEquals("Signal lost", VoicePlayer.eventText(CoachingEvent.SIGNAL_LOST, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `SIGNAL_REGAINED has fixed text`() {
        assertEquals("Signal regained", VoicePlayer.eventText(CoachingEvent.SIGNAL_REGAINED, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `SEGMENT_CHANGE has fixed text`() {
        assertEquals("Next segment", VoicePlayer.eventText(CoachingEvent.SEGMENT_CHANGE, null, WorkoutMode.STEADY_STATE))
    }

    @Test
    fun `PREDICTIVE_WARNING uses guidance text`() {
        assertEquals(
            "HR drifting up - ease off slightly.",
            VoicePlayer.eventText(CoachingEvent.PREDICTIVE_WARNING, "HR drifting up - ease off slightly.", WorkoutMode.STEADY_STATE)
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VoicePlayerVerbosityFilterTest" --tests "com.hrcoach.service.audio.VoicePlayerKmSplitTextTest" --tests "com.hrcoach.service.audio.VoicePlayerEventTextTest" 2>&1 | tail -20`
Expected: Compilation error — `VoicePlayer` class doesn't exist yet.

- [ ] **Step 3: Implement VoicePlayer**

```kotlin
// app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt
package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale

/**
 * Unified TTS voice player for all spoken coaching — briefings, zone alerts,
 * km splits, and informational cues. Replaces both VoiceCoach (MP3) and
 * TtsBriefingPlayer's coaching-speech role.
 *
 * Uses [AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE] to layer
 * over music without stealing audio focus.
 */
class VoicePlayer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingText: String? = null
    private var pendingBriefingConfig: WorkoutConfig? = null

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL
    private var currentPriority: VoiceEventPriority = VoiceEventPriority.INFORMATIONAL
    private var completionSignal: CompletableDeferred<Unit>? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        currentPriority = VoiceEventPriority.INFORMATIONAL
                        completionSignal?.complete(Unit)
                        completionSignal = null
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        currentPriority = VoiceEventPriority.INFORMATIONAL
                        completionSignal?.complete(Unit)
                        completionSignal = null
                    }
                })
                pendingText?.let { queued ->
                    if (queued.isNotBlank() && verbosity != VoiceVerbosity.OFF) {
                        tts?.speak(queued, TextToSpeech.QUEUE_ADD, Bundle.EMPTY, "pending_${System.nanoTime()}")
                    }
                }
                pendingText = null
                pendingBriefingConfig?.let { config ->
                    val text = buildBriefingText(config)
                    if (text.isNotBlank() && verbosity != VoiceVerbosity.OFF) {
                        completionSignal?.complete(Unit)
                        completionSignal = CompletableDeferred()
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "briefing_delayed")
                    }
                }
                pendingBriefingConfig = null
            } else {
                Log.w(TAG, "TTS init failed with status $status")
            }
        }
    }

    fun setVolume(percent: Int) {
        // TTS volume is controlled via AudioAttributes stream volume, not per-utterance.
        // We don't have per-utterance volume control with Android TTS.
        // Volume is controlled at the system level via USAGE_ASSISTANCE_NAVIGATION_GUIDANCE.
    }

    /**
     * Speaks a briefing built from the workout config. Suspendable —
     * caller can await completion before proceeding to countdown.
     */
    suspend fun speakBriefing(config: WorkoutConfig) {
        if (verbosity == VoiceVerbosity.OFF) return
        val text = buildBriefingText(config)
        if (text.isBlank()) return
        if (ttsReady) {
            completionSignal?.complete(Unit)
            completionSignal = CompletableDeferred()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "workout_briefing")
            completionSignal?.await()
        } else {
            Log.d(TAG, "TTS not ready, buffering briefing until init completes")
            pendingBriefingConfig = config
        }
    }

    /**
     * Speaks a coaching event. Checks verbosity, builds appropriate text,
     * and applies priority gating.
     *
     * @param event The coaching event type.
     * @param guidanceText Dynamic guidance from AdaptivePaceController, or km number for KM_SPLIT.
     * @param workoutMode Current workout mode (affects km split text).
     * @param paceMinPerKm Current pace for FREE_RUN km splits, or null.
     */
    fun speakEvent(
        event: CoachingEvent,
        guidanceText: String?,
        workoutMode: WorkoutMode,
        paceMinPerKm: Float? = null
    ) {
        if (!shouldSpeak(event, verbosity)) return

        val text = if (event == CoachingEvent.KM_SPLIT) {
            val km = guidanceText?.toIntOrNull() ?: return
            kmSplitText(km, workoutMode, paceMinPerKm)
        } else {
            eventText(event, guidanceText, workoutMode)
        }

        if (text.isBlank()) return

        val incomingPriority = VoiceEventPriority.of(event)

        // Priority gating: don't interrupt higher-priority speech
        if (isSpeaking() && incomingPriority.ordinal > currentPriority.ordinal) {
            return
        }

        currentPriority = incomingPriority

        if (ttsReady) {
            val queueMode = if (incomingPriority == VoiceEventPriority.CRITICAL) {
                TextToSpeech.QUEUE_FLUSH  // critical interrupts
            } else {
                TextToSpeech.QUEUE_ADD
            }
            completionSignal?.complete(Unit)
            completionSignal = CompletableDeferred()
            tts?.speak(text, queueMode, Bundle.EMPTY, "event_${System.nanoTime()}")
        } else {
            pendingText = text
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    suspend fun awaitCompletion() {
        completionSignal?.await()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingText = null
        pendingBriefingConfig = null
        completionSignal?.complete(Unit)
        completionSignal = null
    }

    // ── Briefing text (carried over from TtsBriefingPlayer) ─────────

    internal fun buildBriefingText(config: WorkoutConfig): String {
        return when (config.mode) {
            WorkoutMode.STEADY_STATE -> buildSteadyStateBriefing(config)
            WorkoutMode.DISTANCE_PROFILE -> buildSegmentedBriefing(config)
            WorkoutMode.FREE_RUN -> "Free run. No heart rate target. Enjoy your run."
        }
    }

    private fun buildSteadyStateBriefing(config: WorkoutConfig): String {
        val parts = mutableListOf<String>()
        val duration = config.totalDurationSeconds()
        val targetHr = config.steadyStateTargetHr
        if (duration != null) {
            parts += "${formatDuration(duration)} run"
        } else {
            parts += "Steady state run"
        }
        if (targetHr != null) {
            parts += "Aim for heart rate around $targetHr"
        }
        return parts.joinToString(". ") + "."
    }

    private fun buildSegmentedBriefing(config: WorkoutConfig): String {
        val parts = mutableListOf<String>()
        val duration = config.totalDurationSeconds()
        val distance = config.totalDistanceMeters()
        val segmentCount = config.segments.size
        when {
            duration != null -> parts += "${formatDuration(duration)} workout"
            distance != null -> parts += "${formatDistance(distance)} workout"
            else -> parts += "Interval workout"
        }
        if (segmentCount > 1) {
            parts += "$segmentCount segments"
        }
        val firstTarget = config.segments.firstOrNull()?.targetHr
        if (firstTarget != null) {
            parts += "First segment target heart rate: $firstTarget"
        }
        return parts.joinToString(". ") + "."
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        return when {
            minutes < 1 -> "$seconds second"
            minutes == 1L -> "1 minute"
            else -> "$minutes minute"
        }
    }

    private fun formatDistance(meters: Float): String {
        val km = meters / 1000f
        return if (km == km.toLong().toFloat()) {
            "${km.toLong()} kilometer"
        } else {
            "${"%.1f".format(km)} kilometer"
        }
    }

    companion object {
        private const val TAG = "VoicePlayer"

        /**
         * Returns true if the given event should be spoken at the given verbosity.
         * OFF = nothing. MINIMAL = critical + normal priority. FULL = everything.
         */
        fun shouldSpeak(event: CoachingEvent, verbosity: VoiceVerbosity): Boolean {
            if (verbosity == VoiceVerbosity.OFF) return false
            if (verbosity == VoiceVerbosity.FULL) return true
            // MINIMAL: allow CRITICAL and NORMAL, block INFORMATIONAL
            return VoiceEventPriority.of(event) != VoiceEventPriority.INFORMATIONAL
        }

        /**
         * Builds the text to speak for a km split, varying by workout mode.
         */
        fun kmSplitText(km: Int, mode: WorkoutMode, paceMinPerKm: Float? = null): String {
            if (mode == WorkoutMode.FREE_RUN && paceMinPerKm != null) {
                val minutes = paceMinPerKm.toInt()
                val seconds = ((paceMinPerKm - minutes) * 60).toInt()
                return "Kilometer $km. Pace: $minutes minutes $seconds."
            }
            return "Kilometer $km"
        }

        /**
         * Builds the text to speak for a coaching event.
         * Zone alerts use guidanceText from AdaptivePaceController.
         * Other events use fixed text.
         */
        fun eventText(event: CoachingEvent, guidanceText: String?, mode: WorkoutMode): String {
            return when (event) {
                CoachingEvent.SPEED_UP -> guidanceText ?: "Speed up"
                CoachingEvent.SLOW_DOWN -> guidanceText ?: "Slow down"
                CoachingEvent.RETURN_TO_ZONE -> guidanceText ?: "Back in zone"
                CoachingEvent.PREDICTIVE_WARNING -> guidanceText ?: "Watch your pace"
                CoachingEvent.SEGMENT_CHANGE -> "Next segment"
                CoachingEvent.SIGNAL_LOST -> "Signal lost"
                CoachingEvent.SIGNAL_REGAINED -> "Signal regained"
                CoachingEvent.HALFWAY -> "Halfway"
                CoachingEvent.WORKOUT_COMPLETE -> "Workout complete"
                CoachingEvent.IN_ZONE_CONFIRM -> "Pace looks good"
                CoachingEvent.KM_SPLIT -> "Kilometer" // shouldn't reach here; handled in speakEvent
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VoicePlayerVerbosityFilterTest" --tests "com.hrcoach.service.audio.VoicePlayerKmSplitTextTest" --tests "com.hrcoach.service.audio.VoicePlayerEventTextTest" 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt \
       app/src/test/java/com/hrcoach/service/audio/VoicePlayerTextTest.kt
git commit -m "feat(audio): add VoicePlayer with TTS coaching, verbosity filter, km split text"
```

---

### Task 2: Update StartupSequencer to use countdown WAV via MediaPlayer

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/StartupSequencer.kt`
- Asset: `app/src/main/res/raw/countdown_321_go.wav` (already committed)

The countdown WAV is already in `res/raw/`. `StartupSequencer` needs to switch from `ToneGenerator` to `MediaPlayer`.

- [ ] **Step 1: Rewrite StartupSequencer to use MediaPlayer**

```kotlin
// app/src/main/java/com/hrcoach/service/audio/StartupSequencer.kt
package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.hrcoach.R
import com.hrcoach.service.WorkoutState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * Plays a curated "3-2-1-GO" countdown WAV before the workout timer starts.
 * Uses [MediaPlayer] with [AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE]
 * to layer over music without stealing audio focus.
 *
 * The countdown updates [WorkoutState.snapshot] with [countdownSecondsRemaining]
 * so the UI can display the countdown visually.
 *
 * The WAV is ~4 seconds: three marimba ticks at 1-second intervals, then a
 * sustained GO tone.
 */
class StartupSequencer(private val context: Context) {

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Plays the countdown WAV. Suspends for the full duration (~4 seconds).
     * Updates [WorkoutState] with countdown progress for UI display.
     *
     * @param volumePercent Volume 0–100. Applied as MediaPlayer volume scalar.
     */
    suspend fun playCountdown(volumePercent: Int = 80) {
        val volumeScalar = volumePercent.coerceIn(0, 100) / 100f
        val completionSignal = CompletableDeferred<Unit>()

        val mediaPlayer = try {
            MediaPlayer.create(context, R.raw.countdown_321_go, audioAttributes, 0)
        } catch (_: Exception) {
            clearCountdownState()
            return
        }

        if (mediaPlayer == null) {
            clearCountdownState()
            return
        }

        mediaPlayer.setVolume(volumeScalar, volumeScalar)
        mediaPlayer.setOnCompletionListener {
            it.release()
            completionSignal.complete(Unit)
        }

        // Start playback
        mediaPlayer.start()

        // Update UI countdown in sync with the audio (~1s per beat)
        // The WAV is: tick(0ms) - tick(1000ms) - tick(2000ms) - GO(3000ms)
        WorkoutState.update { it.copy(countdownSecondsRemaining = 3) }
        delay(1000L)
        WorkoutState.update { it.copy(countdownSecondsRemaining = 2) }
        delay(1000L)
        WorkoutState.update { it.copy(countdownSecondsRemaining = 1) }
        delay(1000L)
        WorkoutState.update { it.copy(countdownSecondsRemaining = 0) }

        // Wait for the MediaPlayer to finish (the GO tone tail)
        completionSignal.await()
        clearCountdownState()
    }

    private fun clearCountdownState() {
        WorkoutState.update { it.copy(countdownSecondsRemaining = null) }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. (StartupSequencer now requires a `Context` parameter — compilation will fail because `CoachingAudioManager` creates it without one. That's fixed in Task 3.)

Note: This step may fail at compile due to the constructor change. That's expected — Task 3 wires it up. If it fails, proceed to Task 3.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/StartupSequencer.kt
git commit -m "refactor(audio): switch StartupSequencer from ToneGenerator to countdown WAV"
```

---

### Task 3: Rewire CoachingAudioManager to use VoicePlayer

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`

Replace `VoiceCoach` and `TtsBriefingPlayer` with `VoicePlayer`. Simplify `fireEvent()`. Fix startup sequence ordering (briefing → await → countdown → return). Pass `Context` to `StartupSequencer`.

- [ ] **Step 1: Rewrite CoachingAudioManager**

```kotlin
// app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
package com.hrcoach.service.audio

import android.content.Context
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoachingAudioManager(
    context: Context,
    settings: AudioSettings
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val earconPlayer = EarconPlayer(context)
    private val voicePlayer = VoicePlayer(context)
    private val vibrationManager = VibrationManager(context)
    private val startupSequencer = StartupSequencer(context)
    private val escalationTracker = EscalationTracker()
    private var currentSettings: AudioSettings = settings
    private var currentWorkoutMode: WorkoutMode = WorkoutMode.STEADY_STATE

    init {
        applySettings(settings)
    }

    fun applySettings(settings: AudioSettings) {
        currentSettings = settings
        earconPlayer.setVolume(settings.earconVolume)
        voicePlayer.setVolume(settings.voiceVolume)
        voicePlayer.verbosity = settings.voiceVerbosity
        vibrationManager.enabled = settings.enableVibration
    }

    fun setWorkoutMode(mode: WorkoutMode) {
        currentWorkoutMode = mode
    }

    /**
     * Plays the full startup sequence:
     * 1. TTS briefing (awaits completion)
     * 2. Countdown WAV (3-2-1-GO, awaits completion)
     *
     * Suspends for the full duration. Timer should only start after this returns.
     */
    suspend fun playStartSequence(config: WorkoutConfig) {
        currentWorkoutMode = config.mode
        // Step 1: Briefing — awaits TTS completion
        voicePlayer.speakBriefing(config)
        // Small gap between briefing and countdown
        delay(500L)
        // Step 2: Countdown — awaits MediaPlayer completion
        startupSequencer.playCountdown(volumePercent = currentSettings.earconVolume)
    }

    fun fireEvent(event: CoachingEvent, guidanceText: String? = null, paceMinPerKm: Float? = null) {
        // Filter informational cues by individual toggles
        when (event) {
            CoachingEvent.HALFWAY -> if (currentSettings.enableHalfwayReminder == false) return
            CoachingEvent.KM_SPLIT -> if (currentSettings.enableKmSplits == false) return
            CoachingEvent.WORKOUT_COMPLETE -> if (currentSettings.enableWorkoutComplete == false) return
            CoachingEvent.IN_ZONE_CONFIRM -> if (currentSettings.enableInZoneConfirm == false) return
            else -> { /* coaching alerts always pass through */ }
        }

        when (event) {
            CoachingEvent.SPEED_UP,
            CoachingEvent.SLOW_DOWN -> {
                val escalationLevel = escalationTracker.onZoneAlert()
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                if (
                    escalationLevel == EscalationLevel.EARCON_VOICE ||
                    escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION
                ) {
                    scope.launch {
                        delay(300L)
                        voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode)
                    }
                }
                if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION) {
                    vibrationManager.pulseAlert()
                }
            }

            CoachingEvent.RETURN_TO_ZONE -> {
                escalationTracker.reset()
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode)
            }

            else -> {
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm)
            }
        }
    }

    fun resetEscalation() {
        escalationTracker.reset()
    }

    /**
     * Plays a short transition tone to confirm pause/resume to the runner.
     * Always plays regardless of voice verbosity — uses earconVolume.
     */
    fun playPauseFeedback(paused: Boolean) {
        val volume = currentSettings.earconVolume.coerceIn(0, 100)
        scope.launch {
            val toneGenerator = try {
                android.media.ToneGenerator(
                    android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, volume
                )
            } catch (_: RuntimeException) {
                return@launch
            }
            try {
                if (paused) {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(250L)
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(200L)
                } else {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(250L)
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(200L)
                }
            } finally {
                toneGenerator.release()
            }
        }
    }

    fun destroy() {
        scope.cancel()
        earconPlayer.destroy()
        voicePlayer.destroy()
        vibrationManager.destroy()
    }

    companion object {
        fun shouldPlayEarcon(verbosity: VoiceVerbosity): Boolean =
            verbosity != VoiceVerbosity.OFF
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. (If `WorkoutForegroundService` calls `fireEvent` with only 2 args and the new signature adds `paceMinPerKm`, the default value handles it. Check for compilation errors.)

- [ ] **Step 3: Run existing tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.*" 2>&1 | tail -20`
Expected: `EarconVerbosityGatingTest` and `VoiceEventPriorityTest` pass. `VoiceCoachVerbosityTest` and `VoiceCoachVolumeTest` still pass (VoiceCoach not deleted yet).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "refactor(audio): rewire CoachingAudioManager to use VoicePlayer, fix startup ordering"
```

---

### Task 4: Update WorkoutForegroundService to pass pace data for km splits

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

The service needs to:
1. Call `coachingAudioManager?.setWorkoutMode(workoutConfig.mode)` at startup
2. Pass `paceMinPerKm` to `fireEvent` for `KM_SPLIT` events

- [ ] **Step 1: Find the emitEvent lambda and update it**

In `WorkoutForegroundService.kt`, the `emitEvent` lambda at the `coachingEventRouter.route()` call site (around line 498-500) currently passes `(event, eventGuidance)`. Update to also pass pace:

```kotlin
// Around line 498-500 — the emitEvent lambda in coachingEventRouter.route()
emitEvent = { event, eventGuidance ->
    val pace = adaptiveResult?.currentPaceMinPerKm
    coachingAudioManager?.fireEvent(event, eventGuidance, paceMinPerKm = pace)
}
```

And the `alertPolicy.handle` onAlert lambda (around line 509-510):

```kotlin
onAlert = { event, eventGuidance ->
    val pace = adaptiveResult?.currentPaceMinPerKm
    coachingAudioManager?.fireEvent(event, eventGuidance, paceMinPerKm = pace)
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(audio): pass pace data to fireEvent for FREE_RUN km splits"
```

---

### Task 5: Delete VoiceCoach, TtsBriefingPlayer, MP3 assets, and update tests

**Files:**
- Delete: `app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt`
- Delete: `app/src/main/java/com/hrcoach/service/audio/TtsBriefingPlayer.kt`
- Delete: `app/src/test/java/com/hrcoach/service/audio/VoiceCoachVerbosityTest.kt`
- Delete: `app/src/test/java/com/hrcoach/service/audio/VoiceCoachVolumeTest.kt`
- Delete: `app/src/test/java/com/hrcoach/service/audio/TtsBriefingPlayerAdHocTest.kt`
- Delete: All `app/src/main/res/raw/voice_*.mp3` files (60+ files)

- [ ] **Step 1: Delete VoiceCoach and TtsBriefingPlayer source files**

```bash
rm app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt
rm app/src/main/java/com/hrcoach/service/audio/TtsBriefingPlayer.kt
```

- [ ] **Step 2: Delete old tests that reference deleted classes**

```bash
rm app/src/test/java/com/hrcoach/service/audio/VoiceCoachVerbosityTest.kt
rm app/src/test/java/com/hrcoach/service/audio/VoiceCoachVolumeTest.kt
rm app/src/test/java/com/hrcoach/service/audio/TtsBriefingPlayerAdHocTest.kt
```

Note: `VoiceCoachVerbosityTest.kt` also contains `EarconVerbosityGatingTest`. Move it to a new file first:

```kotlin
// app/src/test/java/com/hrcoach/service/audio/EarconVerbosityGatingTest.kt
package com.hrcoach.service.audio

import com.hrcoach.domain.model.VoiceVerbosity
import org.junit.Assert.assertEquals
import org.junit.Test

class EarconVerbosityGatingTest {

    @Test
    fun `earcon is suppressed when verbosity is OFF`() {
        assertEquals(false, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.OFF))
    }

    @Test
    fun `earcon plays when verbosity is MINIMAL`() {
        assertEquals(true, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `earcon plays when verbosity is FULL`() {
        assertEquals(true, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.FULL))
    }
}
```

- [ ] **Step 3: Delete all voice MP3 assets**

```bash
rm app/src/main/res/raw/voice_*.mp3
```

This removes: `voice_speed_up.mp3`, `voice_slow_down.mp3`, `voice_return_to_zone.mp3`, `voice_predictive_warning.mp3`, `voice_segment_change.mp3`, `voice_signal_lost.mp3`, `voice_signal_regained.mp3`, `voice_halfway.mp3`, `voice_workout_complete.mp3`, `voice_in_zone_confirm.mp3`, and `voice_km_1.mp3` through `voice_km_50.mp3`.

- [ ] **Step 4: Build to verify no dangling references**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. No references to deleted `R.raw.voice_*` or deleted classes remain.

- [ ] **Step 5: Run all audio tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.*" 2>&1 | tail -20`
Expected: All pass — `EarconVerbosityGatingTest`, `EscalationTrackerTest`, `VoiceEventPriorityTest`, `VoicePlayerVerbosityFilterTest`, `VoicePlayerKmSplitTextTest`, `VoicePlayerEventTextTest`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(audio): delete VoiceCoach, TtsBriefingPlayer, and 60+ MP3 assets

Single TTS VoicePlayer now handles all spoken coaching.
Earcon verbosity gating test preserved in its own file."
```

---

### Task 6: Clean up tmp_audio and run full test suite

**Files:**
- Delete: `tmp_audio/` directory (iteration artifacts)

- [ ] **Step 1: Remove iteration audio files**

```bash
rm -rf tmp_audio/
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests pass. No regressions.

- [ ] **Step 3: Build release-quality APK**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit cleanup**

```bash
git add -A
git commit -m "chore: remove countdown iteration WAV files from tmp_audio"
```

---

### Task 7: Update CLAUDE.md audio pipeline documentation

**Files:**
- Modify: `CLAUDE.md` (project root)

- [ ] **Step 1: Update the Audio Pipeline section**

Replace the existing "Audio Pipeline" section in CLAUDE.md with:

```markdown
## Audio Pipeline

Three-component layered audio system in `service/audio/`:

- **`EarconPlayer`** — SoundPool, plays short WAV clips for zone alerts. `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`.
- **`VoicePlayer`** — Android TTS for all spoken coaching: workout briefings, zone alerts (using adaptive guidance text), km splits, and informational cues. Priority-gated via `VoiceEventPriority` (CRITICAL > NORMAL > INFORMATIONAL). Uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music.
- **`StartupSequencer`** — MediaPlayer playing `countdown_321_go.wav` (custom marimba 3-2-1-GO). Updates `WorkoutState.countdownSecondsRemaining` for UI countdown display.
- **`CoachingAudioManager`** — Orchestrator. Startup sequence: TTS briefing (await) → countdown WAV (await) → return (timer starts). `VoiceVerbosity.OFF` gates ALL audio (earcons + voice). `shouldPlayEarcon(verbosity)` is the single gate for earcon playback.
- **`AudioSettings`** — `earconVolume` and `voiceVolume` are independent (both 0–100 int percent).
- **Verbosity levels:** OFF (silent), MINIMAL (earcons + voice for critical/normal events only), FULL (earcons + voice for all events including informational).
- **KM splits:** Simple "Kilometer N" for STEADY_STATE/DISTANCE_PROFILE. Rich "Kilometer N. Pace: X minutes Y." for FREE_RUN.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md audio pipeline section for unified TTS voice system"
```
