package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import java.util.Locale

/**
 * Wraps Android [TextToSpeech] to speak a dynamic workout briefing at run start.
 *
 * Example output: "26 minute easy run. Aim for heart rate below 145. Take it easy."
 *
 * Respects [VoiceVerbosity] — if OFF, the briefing is skipped.
 * Uses [AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE] so speech layers
 * over music without stealing audio focus.
 */
class TtsBriefingPlayer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

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
            } else {
                Log.w(TAG, "TTS init failed with status $status")
            }
        }
    }

    /**
     * Speaks a briefing built from the workout config. Non-blocking — returns immediately
     * while TTS plays in the background.
     */
    fun speakBriefing(config: WorkoutConfig) {
        if (verbosity == VoiceVerbosity.OFF) return
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready, skipping briefing")
            return
        }

        val text = buildBriefingText(config)
        if (text.isBlank()) return

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "workout_briefing")
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

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
        private const val TAG = "TtsBriefingPlayer"
    }
}
