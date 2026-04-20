package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.service.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Per-workout counters of cues that passed the toggle filter. Drained by WFS at
    // stop time into WorkoutMetrics.cueCountsJson for the post-run "Sounds heard today"
    // recap. Single-threaded access through the fireEvent pipeline (WFS processTick)
    // — no synchronization required.
    private val cueCounts = mutableMapOf<CoachingEvent, Int>()

    /** Called by WFS at stop time. Returns a defensive copy and clears internal state. */
    fun consumeCueCounts(): Map<CoachingEvent, Int> {
        val snapshot = cueCounts.toMap()
        cueCounts.clear()
        return snapshot
    }

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
     * Plays the full startup sequence: TTS voice briefing of the workout config,
     * followed by a 3-2-1-GO countdown WAV. Suspends for the full duration.
     */
    suspend fun playStartSequence(config: WorkoutConfig) {
        currentWorkoutMode = config.mode
        voicePlayer.speakBriefing(config)
        delay(500L)
        startupSequencer.playCountdown(volumePercent = currentSettings.earconVolume)
    }

    var distanceUnit: DistanceUnit = DistanceUnit.KM

    fun fireEvent(event: CoachingEvent, guidanceText: String? = null, paceMinPerKm: Float? = null) {
        // Filter informational cues by individual toggles
        when (event) {
            CoachingEvent.HALFWAY -> if (currentSettings.enableHalfwayReminder == false) return
            CoachingEvent.KM_SPLIT -> if (currentSettings.enableKmSplits == false) return
            CoachingEvent.WORKOUT_COMPLETE -> if (currentSettings.enableWorkoutComplete == false) return
            CoachingEvent.IN_ZONE_CONFIRM -> if (currentSettings.enableInZoneConfirm == false) return
            else -> { /* coaching alerts always pass through */ }
        }

        // Count every cue that *passes* the toggle filter. Read by the post-run recap.
        cueCounts.merge(event, 1) { a, b -> a + b }

        // Flash the visual banner for every cue. Banner ignores voice verbosity — it's a
        // transparency feature. Users who silenced voice still want to know what fired
        // (e.g. SIGNAL_LOST vibration with a "Signal lost" banner).
        val copy = CueCopy.forEvent(event)
        WorkoutState.flashCueBanner(
            CueBanner(
                event = event,
                title = copy.title,
                subtitle = copy.subtitle,
                kind = copy.kind,
                firedAtMs = System.currentTimeMillis()
            )
        )

        val verbosity = currentSettings.voiceVerbosity

        when (event) {
            CoachingEvent.SPEED_UP,
            CoachingEvent.SLOW_DOWN -> {
                val escalationLevel = escalationTracker.onZoneAlert()
                if (shouldPlayEarcon(verbosity)) earconPlayer.play(event)

                // MINIMAL users explicitly opted for fewer events, so when an alert DOES fire it
                // should be maximally informative — skip the tier-1 silent-earcon and speak from
                // the first hit. FULL retains the 3-tier escalation (earcon / earcon+voice /
                // earcon+voice+vibration) because FULL users are already getting more content
                // and gradual escalation reduces nag at the threshold.
                // Gated on the user-controlled `minimalTierOneVoice` setting (default true):
                // setting it false restores the classic 3-tier escalation for users who prefer
                // gradual ramp-up.
                val minimalUpgrade = verbosity == VoiceVerbosity.MINIMAL && currentSettings.minimalTierOneVoice

                val shouldSpeakAlert =
                    escalationLevel == EscalationLevel.EARCON_VOICE ||
                    escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION ||
                    minimalUpgrade

                if (shouldSpeakAlert) {
                    // NonCancellable: a voice alert that has already started must complete even if
                    // destroy() cancels the scope mid-flight, so the runner isn't left with an
                    // earcon beep but no spoken cue.
                    scope.launch {
                        delay(300L)
                        withContext(NonCancellable) {
                            voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, distanceUnit = distanceUnit)
                        }
                    }
                }
                if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION) {
                    vibrationManager.pulseForEvent(event)
                }
            }

            CoachingEvent.RETURN_TO_ZONE -> {
                // Escalation reset is already handled by AlertPolicy.onResetEscalation callback
                // (which fires when IN_ZONE is first detected, before this event is emitted).
                if (shouldPlayEarcon(verbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, distanceUnit = distanceUnit)
            }

            CoachingEvent.SIGNAL_LOST -> {
                // SIGNAL_LOST is meta-info about the app's OWN state (coaching went blind), not
                // coaching content. It's safety-relevant: a runner with music + pocketed phone
                // would otherwise never know their HR strap died. Resolution:
                //   - Vibration always fires (respecting enableVibration) — tactile cue is the
                //     one channel that works with headphones on and phone buried. This is the
                //     safety override.
                //   - Earcon + voice respect verbosity. A user who chose OFF chose silence;
                //     we don't violate that with audio. MINIMAL/FULL get earcon + voice as
                //     normal CRITICAL-priority events.
                if (shouldPlayEarcon(verbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm, distanceUnit)
                vibrationManager.pulseAlert()
            }

            else -> {
                // Priority-aware earcon gate: INFORMATIONAL earcons (IN_ZONE_CONFIRM, HALFWAY,
                // KM_SPLIT, WORKOUT_COMPLETE) are suppressed at MINIMAL. Without this the chimes
                // fire every 3 min on a steady run — beeps with no voice = confusing noise at a
                // level the runner chose specifically to reduce audio. FULL still plays them.
                val priority = VoiceEventPriority.of(event)
                val earconSuppressedByMinimal =
                    verbosity == VoiceVerbosity.MINIMAL && priority == VoiceEventPriority.INFORMATIONAL

                if (shouldPlayEarcon(verbosity) && !earconSuppressedByMinimal) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm, distanceUnit)
            }
        }
    }

    fun resetEscalation() {
        escalationTracker.reset()
    }

    /**
     * Plays a short transition tone to confirm pause/resume to the runner.
     * Pause = two descending tones; Resume = two ascending tones.
     * Always plays regardless of voice verbosity — tones use earconVolume which is independent.
     */
    fun playPauseFeedback(paused: Boolean) {
        // Banner for pause/resume — users asked "what was that chime?" during pauses.
        // Reuses IN_ZONE_CONFIRM enum to satisfy the event field; this code path does NOT
        // route through fireEvent, so no cueCounts entry is recorded.
        WorkoutState.flashCueBanner(
            CueBanner(
                event = CoachingEvent.IN_ZONE_CONFIRM,
                title = if (paused) "Paused" else "Resumed",
                subtitle = if (paused) "Workout paused — tap resume when ready." else "Workout resumed.",
                kind = CueBannerKind.INFO,
                firedAtMs = System.currentTimeMillis()
            )
        )
        val volume = currentSettings.earconVolume.coerceIn(0, 100)
        scope.launch {
            // ToneGenerator takes an AudioManager.STREAM_* constant, not an AudioAttributes.USAGE_*
            // constant. STREAM_MUSIC routes through the media volume the runner controls during a run.
            val toneGenerator = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, volume)
            } catch (_: RuntimeException) {
                return@launch
            }
            try {
                if (paused) {
                    // Descending: high-pitch then low-pitch
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(250L)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(200L)
                } else {
                    // Ascending: low-pitch then high-pitch
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(250L)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(200L)
                }
            } finally {
                toneGenerator.release()
            }
        }
    }

    /**
     * Speaks an auto-pause/resume announcement via TTS. Only for auto-pause events —
     * not called for manual pause. Respects voice verbosity (OFF = silent).
     */
    fun speakAnnouncement(text: String) {
        voicePlayer.speakAnnouncement(text)
    }

    fun destroy() {
        scope.cancel()
        earconPlayer.destroy()
        voicePlayer.destroy()
        vibrationManager.destroy()
    }

    companion object {
        /** Returns true when earcons should fire. OFF suppresses earcons; other levels allow them. */
        fun shouldPlayEarcon(verbosity: VoiceVerbosity): Boolean =
            verbosity != VoiceVerbosity.OFF
    }
}
