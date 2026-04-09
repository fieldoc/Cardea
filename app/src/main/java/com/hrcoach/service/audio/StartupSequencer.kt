package com.hrcoach.service.audio

import android.media.AudioAttributes
import android.media.ToneGenerator
import com.hrcoach.service.WorkoutState
import kotlinx.coroutines.delay

/**
 * Plays a "3-2-1-GO" countdown beep sequence before the workout timer starts.
 * Uses [ToneGenerator] to synthesize tones at runtime — no asset files needed.
 *
 * The countdown updates [WorkoutState.snapshot] with [countdownSecondsRemaining]
 * so the UI can display the countdown visually.
 */
class StartupSequencer {

    private val tickTone = ToneGenerator.TONE_PROP_BEEP
    private val goTone = ToneGenerator.TONE_PROP_BEEP2
    private val toneDurationMs = 200
    private val intervalMs = 1000L

    /**
     * Plays 3-2-1-GO countdown. Suspends for the full duration (~4 seconds).
     * Updates [WorkoutState] with countdown progress for UI display.
     *
     * @param volumePercent ToneGenerator volume (0–100). Defaults to 80.
     *   Pass [AudioSettings.earconVolume] to respect the user's earcon volume setting.
     */
    suspend fun playCountdown(volumePercent: Int = 80) {
        val toneVolume = volumePercent.coerceIn(0, 100)
        val toneGenerator = try {
            ToneGenerator(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, toneVolume)
        } catch (_: RuntimeException) {
            // ToneGenerator can fail on some devices; skip countdown silently
            clearCountdownState()
            return
        }

        try {
            // 3...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 3) }
            toneGenerator.startTone(tickTone, toneDurationMs)
            delay(intervalMs)

            // 2...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 2) }
            toneGenerator.startTone(tickTone, toneDurationMs)
            delay(intervalMs)

            // 1...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 1) }
            toneGenerator.startTone(tickTone, toneDurationMs)
            delay(intervalMs)

            // GO!
            WorkoutState.update { it.copy(countdownSecondsRemaining = 0) }
            toneGenerator.startTone(goTone, toneDurationMs)
            delay(300L) // brief pause after GO before clearing
        } finally {
            toneGenerator.release()
            clearCountdownState()
        }
    }

    private fun clearCountdownState() {
        WorkoutState.update { it.copy(countdownSecondsRemaining = null) }
    }
}
