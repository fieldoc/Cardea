package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.hrcoach.R
import com.hrcoach.service.WorkoutState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays a "3-2-1-GO" countdown WAV before the workout timer starts.
 * Uses [MediaPlayer] to play [R.raw.countdown_321_go] — a pre-recorded
 * countdown audio file that replaces the old [android.media.ToneGenerator] beeps.
 *
 * The countdown updates [WorkoutState.snapshot] with [countdownSecondsRemaining]
 * so the UI can display the countdown visually.
 */
class StartupSequencer(private val context: Context) {

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Plays the countdown WAV. Suspends until playback finishes.
     * Updates [WorkoutState] with countdown progress (3, 2, 1, 0) at ~1s intervals.
     *
     * @param volumePercent MediaPlayer volume (0-100). Defaults to 80.
     *   Pass [AudioSettings.earconVolume] to respect the user's earcon volume setting.
     */
    suspend fun playCountdown(volumePercent: Int = 80) {
        val scalar = volumePercent.coerceIn(0, 100) / 100f

        val player = try {
            MediaPlayer.create(context, R.raw.countdown_321_go, audioAttributes, 0)
        } catch (_: Exception) {
            null
        }

        if (player == null) {
            // MediaPlayer can fail on some devices; skip countdown silently
            clearCountdownState()
            return
        }

        player.setVolume(scalar, scalar)

        val completion = CompletableDeferred<Unit>()
        player.setOnCompletionListener {
            it.release()
            completion.complete(Unit)
        }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            completion.complete(Unit)
            true
        }

        // Show "3" on screen before audio starts so the overlay is already rendered
        // when the first beat plays. Without this, the launch{} scheduling delay
        // (~50-150ms) plus one Compose frame (~16ms) means users hear "3" before
        // they see it.
        WorkoutState.update { it.copy(countdownSecondsRemaining = 3) }
        delay(200L)  // ~12 frames at 60fps — enough for Compose to render the overlay

        player.start()

        // coroutineScope ensures both the countdown UI updates and the audio
        // completion run concurrently as sibling coroutines.
        coroutineScope {
            // Launch countdown UI updates as a child — runs concurrently with audio.
            // "3" is already showing; drive subsequent transitions from audio start.
            launch {
                // 2...
                delay(1000L)
                WorkoutState.update { it.copy(countdownSecondsRemaining = 2) }

                // 1...
                delay(1000L)
                WorkoutState.update { it.copy(countdownSecondsRemaining = 1) }

                // GO!
                delay(1000L)
                WorkoutState.update { it.copy(countdownSecondsRemaining = 0) }
            }

            // Wait for the MediaPlayer to finish (concurrent with the countdown updates)
            try {
                completion.await()
            } finally {
                // If cancelled, release player if still alive
                try { player.release() } catch (_: Exception) {}
                clearCountdownState()
            }
        }
    }

    private fun clearCountdownState() {
        WorkoutState.update { it.copy(countdownSecondsRemaining = null) }
    }
}
