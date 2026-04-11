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
 * TTS-based voice coaching player. Replaces both [VoiceCoach] (MP3 clips) and
 * [TtsBriefingPlayer]'s coaching-speech role with a single Android TTS wrapper.
 *
 * Pure-function text generation and verbosity filtering live in the [companion object]
 * so they can be unit-tested without an Android context.
 *
 * Uses [AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE] so speech layers
 * over music without stealing audio focus.
 */
class VoicePlayer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingBriefingConfig: WorkoutConfig? = null
    private var pendingBriefingDeferred: CompletableDeferred<Unit>? = null

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

    /** Tracks the priority of the currently speaking utterance. */
    private var currentPriority: VoiceEventPriority? = null

    /** Deferred that completes when the current utterance finishes. */
    private var utteranceDeferred: CompletableDeferred<Unit>? = null

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
                        currentPriority = null
                        utteranceDeferred?.complete(Unit)
                        utteranceDeferred = null
                        pendingBriefingDeferred?.complete(Unit)
                        pendingBriefingDeferred = null
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        currentPriority = null
                        utteranceDeferred?.complete(Unit)
                        utteranceDeferred = null
                        pendingBriefingDeferred?.complete(Unit)
                        pendingBriefingDeferred = null
                    }
                })

                // Flush any pending briefing that was queued before TTS was ready
                pendingBriefingConfig?.let { bufferedConfig ->
                    val text = buildBriefingText(bufferedConfig)
                    if (text.isNotBlank() && verbosity != VoiceVerbosity.OFF) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "briefing_delayed")
                    } else {
                        pendingBriefingDeferred?.complete(Unit)
                        pendingBriefingDeferred = null
                    }
                }
                pendingBriefingConfig = null
            } else {
                Log.w(TAG, "TTS init failed with status $status")
                pendingBriefingDeferred?.complete(Unit)
                pendingBriefingDeferred = null
            }
        }
    }

    /**
     * Sets the voice volume. Currently a no-op because TTS volume is
     * system-controlled via the navigation guidance audio stream.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setVolume(percent: Int) {
        // No-op: TTS volume is controlled by the system audio stream
    }

    /**
     * Speaks a briefing built from the workout config. Suspends until TTS
     * finishes speaking (or completes immediately if verbosity is OFF).
     */
    suspend fun speakBriefing(config: WorkoutConfig) {
        if (verbosity == VoiceVerbosity.OFF) return
        val text = buildBriefingText(config)
        if (text.isBlank()) return

        val deferred = CompletableDeferred<Unit>()
        pendingBriefingDeferred = deferred

        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "workout_briefing")
        } else {
            Log.d(TAG, "TTS not ready, buffering briefing until init completes")
            pendingBriefingConfig = config
        }

        deferred.await()
    }

    /**
     * Speaks a coaching event. Checks verbosity, builds text, and applies
     * priority gating: CRITICAL events use QUEUE_FLUSH to interrupt;
     * lower-priority events cannot interrupt higher-priority playback.
     */
    fun speakEvent(
        event: CoachingEvent,
        guidanceText: String?,
        workoutMode: WorkoutMode,
        paceMinPerKm: Float? = null
    ) {
        if (!shouldSpeak(event, verbosity)) return

        val text = if (event == CoachingEvent.KM_SPLIT) {
            // KM_SPLIT needs special text with pace info
            // km value is not passed here — caller should use kmSplitText() directly
            // For speakEvent, use eventText which gives a generic fallback
            eventText(event, guidanceText, workoutMode)
        } else {
            eventText(event, guidanceText, workoutMode)
        }

        if (text.isBlank()) return

        val priority = VoiceEventPriority.of(event)

        // Priority gating: if something is speaking, only allow higher priority
        val speaking = tts?.isSpeaking == true
        if (speaking && currentPriority != null) {
            if (priority.ordinal > currentPriority!!.ordinal) {
                // Lower priority — skip
                Log.d(TAG, "Skipping $event (priority $priority < current $currentPriority)")
                return
            }
        }

        val queueMode = if (priority == VoiceEventPriority.CRITICAL) {
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }

        currentPriority = priority

        if (ttsReady) {
            tts?.speak(text, queueMode, Bundle.EMPTY, "event_${event.name}_${System.nanoTime()}")
        }
    }

    /**
     * Speaks a km split announcement with pace information.
     */
    fun speakKmSplit(km: Int, workoutMode: WorkoutMode, paceMinPerKm: Float? = null) {
        if (!shouldSpeak(CoachingEvent.KM_SPLIT, verbosity)) return
        val text = kmSplitText(km, workoutMode, paceMinPerKm)
        if (text.isBlank()) return

        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, Bundle.EMPTY, "km_split_$km")
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    suspend fun awaitCompletion() {
        if (tts?.isSpeaking != true) return
        val deferred = CompletableDeferred<Unit>()
        utteranceDeferred = deferred
        deferred.await()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        currentPriority = null
        pendingBriefingConfig = null
        pendingBriefingDeferred?.complete(Unit)
        pendingBriefingDeferred = null
        utteranceDeferred?.complete(Unit)
        utteranceDeferred = null
    }

    companion object {
        private const val TAG = "VoicePlayer"

        /**
         * Returns whether the given [event] should be spoken at the given [verbosity].
         *
         * - OFF: nothing
         * - MINIMAL: CRITICAL and NORMAL priority events only
         * - FULL: everything
         */
        fun shouldSpeak(event: CoachingEvent, verbosity: VoiceVerbosity): Boolean {
            return when (verbosity) {
                VoiceVerbosity.OFF -> false
                VoiceVerbosity.MINIMAL -> {
                    val priority = VoiceEventPriority.of(event)
                    priority == VoiceEventPriority.CRITICAL || priority == VoiceEventPriority.NORMAL
                }
                VoiceVerbosity.FULL -> true
            }
        }

        /**
         * Builds km split text, varying by workout mode:
         * - STEADY_STATE / DISTANCE_PROFILE: "Kilometer N" (number only)
         * - FREE_RUN with pace: "Kilometer N. Pace: M minutes S."
         * - FREE_RUN without pace: "Kilometer N"
         */
        fun kmSplitText(km: Int, mode: WorkoutMode, paceMinPerKm: Float? = null): String {
            val base = "Kilometer $km"
            if (mode != WorkoutMode.FREE_RUN || paceMinPerKm == null) return base

            val totalSeconds = (paceMinPerKm * 60).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "$base. Pace: $minutes minutes $seconds."
        }

        /**
         * Builds spoken text for a coaching event.
         *
         * Zone-alert events (SPEED_UP, SLOW_DOWN, RETURN_TO_ZONE, PREDICTIVE_WARNING)
         * use the provided [guidanceText] if available, falling back to a fixed string.
         * Other events always use fixed text.
         */
        fun eventText(event: CoachingEvent, guidanceText: String?, mode: WorkoutMode): String {
            return when (event) {
                CoachingEvent.SPEED_UP -> guidanceText ?: "Speed up"
                CoachingEvent.SLOW_DOWN -> guidanceText ?: "Slow down"
                CoachingEvent.RETURN_TO_ZONE -> guidanceText ?: "Back in zone"
                CoachingEvent.PREDICTIVE_WARNING -> guidanceText ?: "Watch your pace"
                CoachingEvent.HALFWAY -> "Halfway"
                CoachingEvent.WORKOUT_COMPLETE -> "Workout complete"
                CoachingEvent.IN_ZONE_CONFIRM -> "Pace looks good"
                CoachingEvent.SIGNAL_LOST -> "Signal lost"
                CoachingEvent.SIGNAL_REGAINED -> "Signal regained"
                CoachingEvent.SEGMENT_CHANGE -> "Next segment"
                CoachingEvent.KM_SPLIT -> guidanceText ?: "Kilometer"
            }
        }

        // ---- Briefing text builders (carried over from TtsBriefingPlayer) ----

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
    }
}
