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

    companion object {
        private const val TICK_TONE = ToneGenerator.TONE_PROP_BEEP
        private const val GO_TONE = ToneGenerator.TONE_PROP_BEEP2
        private const val TONE_DURATION_MS = 200
        private const val INTERVAL_MS = 1000L
        private const val VOLUME = 80 // 0-100, ToneGenerator volume
    }

    /**
     * Plays 3-2-1-GO countdown. Suspends for the full duration (~4 seconds).
     * Updates [WorkoutState] with countdown progress for UI display.
     */
    suspend fun playCountdown() {
        val toneGenerator = try {
            ToneGenerator(AudioAttributes.USAGE_NOTIFICATION_EVENT, VOLUME)
        } catch (_: RuntimeException) {
            // ToneGenerator can fail on some devices; skip countdown silently
            clearCountdownState()
            return
        }

        try {
            // 3...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 3) }
            toneGenerator.startTone(TICK_TONE, TONE_DURATION_MS)
            delay(INTERVAL_MS)

            // 2...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 2) }
            toneGenerator.startTone(TICK_TONE, TONE_DURATION_MS)
            delay(INTERVAL_MS)

            // 1...
            WorkoutState.update { it.copy(countdownSecondsRemaining = 1) }
            toneGenerator.startTone(TICK_TONE, TONE_DURATION_MS)
            delay(INTERVAL_MS)

            // GO!
            WorkoutState.update { it.copy(countdownSecondsRemaining = 0) }
            toneGenerator.startTone(GO_TONE, TONE_DURATION_MS)
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
