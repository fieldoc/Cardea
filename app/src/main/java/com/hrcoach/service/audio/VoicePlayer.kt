package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.DistanceUnit
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
    @Volatile private var ttsReady = false
    private var pendingBriefingConfig: WorkoutConfig? = null
    @Volatile private var pendingBriefingDeferred: CompletableDeferred<Unit>? = null

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

    /** Tracks the priority of the currently speaking utterance. */
    @Volatile private var currentPriority: VoiceEventPriority? = null

    /**
     * Wall-clock ms when [currentPriority] was last set. Used by the stale-priority watchdog in
     * [speakEvent]: if TTS onDone/onError never fires (engine hang, audio-focus loss mid-utterance,
     * doze mode) currentPriority stays non-null and the priority gate silently drops every
     * subsequent informational cue — exactly the "voice goes silent after warmup" failure mode.
     * On entry to speakEvent we force-clear if the age exceeds [WATCHDOG_MAX_UTTERANCE_MS].
     */
    @Volatile private var currentPrioritySetAtMs: Long = 0L

    /** Deferred that completes when the current utterance finishes. */
    @Volatile private var utteranceDeferred: CompletableDeferred<Unit>? = null

    /** TTS per-utterance volume scalar (0.0–1.0). */
    private var volumeScalar: Float = 0.8f

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
                        handleUtteranceEnd(utteranceId)
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        handleUtteranceEnd(utteranceId)
                    }
                })

                // Flush any pending briefing that was queued before TTS was ready
                pendingBriefingConfig?.let { bufferedConfig ->
                    val text = buildBriefingText(bufferedConfig)
                    if (text.isNotBlank() && verbosity != VoiceVerbosity.OFF) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, speechParams(), "briefing_delayed")
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
     * Routes utterance completion/error to the correct deferred based on utterance ID prefix.
     */
    private fun handleUtteranceEnd(utteranceId: String?) {
        currentPriority = null
        currentPrioritySetAtMs = 0L
        when {
            utteranceId?.startsWith("briefing") == true -> {
                pendingBriefingDeferred?.complete(Unit)
                pendingBriefingDeferred = null
            }
            utteranceId?.startsWith("event_") == true -> {
                utteranceDeferred?.complete(Unit)
                utteranceDeferred = null
            }
            utteranceId?.startsWith("announcement_") == true -> {
                // Fire-and-forget — no deferred to complete. currentPriority already nulled above.
            }
            else -> {
                utteranceDeferred?.complete(Unit)
                utteranceDeferred = null
                pendingBriefingDeferred?.complete(Unit)
                pendingBriefingDeferred = null
            }
        }
    }

    /**
     * Builds a Bundle with the current volume scalar for TTS per-utterance volume control.
     */
    private fun speechParams(): Bundle {
        return Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeScalar)
        }
    }

    /**
     * Sets the voice volume.
     *
     * @param percent Volume level (0-100). Applied to subsequent TTS utterances
     *   via [TextToSpeech.Engine.KEY_PARAM_VOLUME].
     */
    fun setVolume(percent: Int) {
        volumeScalar = percent.coerceIn(0, 100) / 100f
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

        if (ttsReady) {
            pendingBriefingDeferred = deferred
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, speechParams(), "briefing_workout")
        } else {
            // Set deferred BEFORE config so the init callback can complete it
            // if TTS init fires synchronously before await() is reached.
            pendingBriefingDeferred = deferred
            pendingBriefingConfig = config
            Log.d(TAG, "TTS not ready, buffering briefing until init completes")
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
        paceMinPerKm: Float? = null,
        distanceUnit: DistanceUnit = DistanceUnit.KM,
        hrSlopeBpmPerMin: Float = 0f,
    ) {
        if (!shouldSpeak(event, verbosity)) return

        val text = when {
            event == CoachingEvent.KM_SPLIT -> {
                // guidanceText carries the split number as a string (e.g. "5")
                val splitNum = guidanceText?.toIntOrNull()
                if (splitNum != null) {
                    splitText(splitNum, workoutMode, distanceUnit, paceMinPerKm)
                } else {
                    eventText(event, guidanceText, workoutMode, distanceUnit)
                }
            }
            event == CoachingEvent.PREDICTIVE_WARNING && guidanceText.isNullOrBlank() -> {
                // Direction-aware fallback when adaptive guidance is absent.
                // Positive slope = HR climbing → runner should ease off.
                // Negative slope = HR dropping → runner should pick up pace.
                // Zero (unlikely in practice) defaults to climbing phrasing for safety.
                if (hrSlopeBpmPerMin < 0f) {
                    "Pace dropping. Pick it up to hold zone."
                } else {
                    "Pace climbing. Ease off to hold zone."
                }
            }
            else -> eventText(event, guidanceText, workoutMode, distanceUnit)
        }

        if (text.isBlank()) return

        val priority = VoiceEventPriority.of(event)
        val nowMs = System.currentTimeMillis()

        // Stale-priority watchdog. If TTS onDone/onError failed to fire (engine hang, audio-focus
        // loss mid-utterance, doze) currentPriority would stay non-null and the priority gate
        // below would silently drop every subsequent informational cue for the rest of the run.
        // No real utterance should exceed WATCHDOG_MAX_UTTERANCE_MS; if we're past that, assume
        // the callback dropped and reset state so the next cue can play.
        if (currentPriority != null && currentPrioritySetAtMs > 0L &&
            nowMs - currentPrioritySetAtMs > WATCHDOG_MAX_UTTERANCE_MS) {
            Log.w(TAG, "Voice watchdog: clearing stale priority=$currentPriority after ${nowMs - currentPrioritySetAtMs}ms")
            currentPriority = null
            currentPrioritySetAtMs = 0L
            utteranceDeferred?.complete(Unit)
            utteranceDeferred = null
        }

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
        currentPrioritySetAtMs = nowMs

        if (ttsReady) {
            tts?.speak(text, queueMode, speechParams(), "event_${event.name}_${System.nanoTime()}")
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Speaks arbitrary text via TTS, queued after current speech.
     * Respects verbosity: skipped when OFF or TTS not ready.
     */
    fun speakAnnouncement(text: String) {
        if (verbosity == VoiceVerbosity.OFF) return
        if (!ttsReady) {
            Log.w(TAG, "Dropping announcement — TTS not ready: $text")
            return
        }
        currentPriority = VoiceEventPriority.INFORMATIONAL
        currentPrioritySetAtMs = System.currentTimeMillis()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, speechParams(), "announcement_${System.nanoTime()}")
    }

    suspend fun awaitCompletion() {
        // Check existing deferred first — avoids race where onDone fires
        // between the isSpeaking check and setting utteranceDeferred.
        val existing = utteranceDeferred
        if (existing != null) {
            existing.await()
            return
        }
        if (tts?.isSpeaking != true) return
        // Still speaking but no deferred — set it before re-checking
        val deferred = CompletableDeferred<Unit>()
        utteranceDeferred = deferred
        // Double-check after setting — if speaking stopped in the gap, complete immediately
        if (tts?.isSpeaking != true) {
            deferred.complete(Unit)
        }
        deferred.await()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        currentPriority = null
        currentPrioritySetAtMs = 0L
        pendingBriefingConfig = null
        pendingBriefingDeferred?.complete(Unit)
        pendingBriefingDeferred = null
        utteranceDeferred?.complete(Unit)
        utteranceDeferred = null
    }

    companion object {
        private const val TAG = "VoicePlayer"

        /**
         * Maximum plausible TTS utterance age before the stale-priority watchdog force-clears
         * [currentPriority]. Real utterances top out around 3-5s; 15s is a generous safety net
         * that won't fire during normal operation but will recover within one cue of a dropped
         * onDone callback.
         */
        private const val WATCHDOG_MAX_UTTERANCE_MS: Long = 15_000L

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
         * - FREE_RUN with pace: "Kilometer N. Pace: M minutes S seconds."
         * - FREE_RUN with pace and 0 seconds: "Kilometer N. Pace: M minutes."
         * - FREE_RUN without pace: "Kilometer N"
         */
        fun kmSplitText(km: Int, mode: WorkoutMode, paceMinPerKm: Float? = null): String =
            splitText(km, mode, DistanceUnit.KM, paceMinPerKm)

        fun splitText(splitNum: Int, mode: WorkoutMode, unit: DistanceUnit, paceMinPerKm: Float? = null): String {
            val unitWord = if (unit == DistanceUnit.MI) "Mile" else "Kilometer"
            val base = "$unitWord $splitNum"
            if (mode != WorkoutMode.FREE_RUN || paceMinPerKm == null) return base

            // Convert pace to the target unit if imperial
            val pace = if (unit == DistanceUnit.MI)
                paceMinPerKm * (DistanceUnit.METERS_PER_MILE / DistanceUnit.METERS_PER_KM)
            else paceMinPerKm

            val totalSeconds = (pace * 60).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (seconds == 0) {
                "$base. Pace: $minutes minutes."
            } else {
                "$base. Pace: $minutes minutes $seconds seconds."
            }
        }

        /**
         * Builds spoken text for a coaching event.
         *
         * Zone-alert events (SPEED_UP, SLOW_DOWN, RETURN_TO_ZONE, PREDICTIVE_WARNING)
         * use the provided [guidanceText] if available, falling back to a fixed string.
         * Other events always use fixed text.
         */
        fun eventText(event: CoachingEvent, guidanceText: String?, mode: WorkoutMode, unit: DistanceUnit = DistanceUnit.KM): String {
            return when (event) {
                CoachingEvent.SPEED_UP -> guidanceText ?: "Speed up"
                CoachingEvent.SLOW_DOWN -> guidanceText ?: "Slow down"
                CoachingEvent.RETURN_TO_ZONE -> guidanceText ?: "Back in zone"
                CoachingEvent.PREDICTIVE_WARNING -> guidanceText ?: "Watch your pace"
                CoachingEvent.HALFWAY -> "Halfway"
                CoachingEvent.WORKOUT_COMPLETE -> "Workout complete"
                CoachingEvent.IN_ZONE_CONFIRM -> "In zone"
                CoachingEvent.SIGNAL_LOST -> "Heart-rate signal lost"
                CoachingEvent.SIGNAL_REGAINED -> "Heart-rate signal back"
                CoachingEvent.SEGMENT_CHANGE -> "Next interval"
                CoachingEvent.KM_SPLIT -> guidanceText ?: if (unit == DistanceUnit.MI) "Mile" else "Kilometer"
            }
        }

        // ---- Briefing text builders (carried over from TtsBriefingPlayer) ----

        internal fun buildBriefingText(config: WorkoutConfig): String {
            return when (config.mode) {
                WorkoutMode.STEADY_STATE -> buildSteadyStateBriefing(config)
                WorkoutMode.DISTANCE_PROFILE -> buildSegmentedBriefing(config)
                WorkoutMode.FREE_RUN -> {
                    val duration = config.plannedDurationMinutes
                    if (duration != null) {
                        "$duration minute run. No heart rate target. Enjoy your run."
                    } else {
                        "Free run. No heart rate target. Enjoy your run."
                    }
                }
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

        private fun formatDuration(seconds: Long): String {
            val minutes = seconds / 60
            return when {
                minutes < 1 -> if (seconds == 1L) "1 second" else "$seconds seconds"
                minutes == 1L -> "1 minute"
                else -> "$minutes minutes"
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
