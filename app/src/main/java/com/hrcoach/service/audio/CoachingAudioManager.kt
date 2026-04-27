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

    private val debugLogger = TtsDebugLogger(context)
    private val earconPlayer = EarconPlayer(context, debugLogger)
    private val voicePlayer = VoicePlayer(context, debugLogger)
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

    /**
     * Most-recent HR slope (BPM/min) observed on the current tick, set by WFS before
     * each fireEvent call. Used by VoicePlayer's PREDICTIVE_WARNING fallback to pick
     * between "ease off" and "pick it up" phrasing when guidanceText is null/blank.
     * Single-threaded access through the WFS processTick pipeline — no sync needed.
     */
    private var lastHrSlopeBpmPerMin: Float = 0f

    /** Called by WFS each tick so direction-aware fallbacks have current slope. */
    fun setHrSlope(slope: Float) {
        lastHrSlopeBpmPerMin = slope
    }

    /** Opens a per-run TTS debug log. Called by WFS at workout start. See [TtsDebugLogger]. */
    fun startDebugLog(isSimulation: Boolean, workoutMode: String?) {
        debugLogger.startRun(isSimulation, workoutMode)
    }

    /** Closes the current TTS debug log. Called by WFS at workout stop. */
    fun endDebugLog(detail: String) {
        debugLogger.endRun(detail)
    }

    /** Writes a lifecycle or external-context line to the TTS debug log. */
    fun logDebug(line: String) {
        debugLogger.logLine(line)
    }

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
     *
     * When [skipBriefing] is true, the TTS briefing is skipped and we go straight
     * to the countdown — used on first-ever workout start, right after the audio
     * primer dialog has already explained the audio system. A briefing on top of
     * that feels redundant and stretches the pre-run audio to ~10s.
     */
    suspend fun playStartSequence(config: WorkoutConfig, skipBriefing: Boolean = false) {
        currentWorkoutMode = config.mode
        // Consume the cross-layer primer-just-dismissed flag. Either explicit param OR the
        // transient flag triggers a skip; flag is cleared after read so it can't leak into a
        // later session.
        val pendingSkip = skipNextBriefing
        skipNextBriefing = false
        debugLogger.logLine("LIFECYCLE action=START_SEQUENCE mode=${config.mode.name} skipBriefing=${skipBriefing || pendingSkip}")
        if (!skipBriefing && !pendingSkip) {
            voicePlayer.speakBriefing(config)
        }
        // Start the countdown WAV immediately after the briefing (or immediately if skipped) —
        // no gap. A half-second of silence before "three" used to feel like the app had hung.
        startupSequencer.playCountdown(volumePercent = currentSettings.earconVolume)
    }

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
        debugLogger.logLine("LIFECYCLE action=END_SEQUENCE distance=${distanceMeters.toInt()}m active=${activeDurationSec}s avgHr=${avgHr ?: "null"}")
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

    var distanceUnit: DistanceUnit = DistanceUnit.KM

    fun fireEvent(
        event: CoachingEvent,
        guidanceText: String? = null,
        paceMinPerKm: Float? = null,
        currentHr: Int? = null,
        targetHr: Int? = null,
    ) {
        // Snapshot HR/zone context up-front for the debug log. Reads WorkoutState directly — it's
        // the single source of truth WFS already writes to every tick, so CAM doesn't need a
        // wider signature. Cheap StateFlow read.
        val snap = WorkoutState.snapshot.value
        val ctx = "hr=${snap.currentHr} tgt=${snap.targetHr} zone=${snap.zoneStatus.name} " +
            "slope=${"%+.1f".format(lastHrSlopeBpmPerMin)} paused=${snap.isPaused} autoPaused=${snap.isAutoPaused} " +
            "verbosity=${currentSettings.voiceVerbosity.name} t=${snap.elapsedSeconds}s"


        // Filter informational cues by individual toggles
        when (event) {
            CoachingEvent.HALFWAY -> if (currentSettings.enableHalfwayReminder == false) {
                debugLogger.logLine("FIRE event=${event.name} $ctx action=BLOCK reason=toggle-halfway-off")
                return
            }
            CoachingEvent.KM_SPLIT -> if (currentSettings.enableKmSplits == false) {
                debugLogger.logLine("FIRE event=${event.name} $ctx action=BLOCK reason=toggle-kmsplits-off")
                return
            }
            CoachingEvent.WORKOUT_COMPLETE -> if (currentSettings.enableWorkoutComplete == false) {
                debugLogger.logLine("FIRE event=${event.name} $ctx action=BLOCK reason=toggle-workoutcomplete-off")
                return
            }
            CoachingEvent.IN_ZONE_CONFIRM -> if (currentSettings.enableInZoneConfirm == false) {
                debugLogger.logLine("FIRE event=${event.name} $ctx action=BLOCK reason=toggle-inzoneconfirm-off")
                return
            }
            else -> { /* coaching alerts always pass through */ }
        }

        debugLogger.logLine(
            "FIRE event=${event.name} $ctx guidance=" + quoteOrNull(guidanceText)
        )

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
                // the first hit (gated by user-controlled `minimalTierOneVoice`, default true;
                // setting it false restores the classic 3-tier escalation for users who prefer
                // gradual ramp-up).
                //
                // FULL now ALSO speaks from tier-1. The original "gradual escalation on FULL"
                // design (bug, fixed 2026-04-24) assumed the escalation counter would accumulate
                // across an out-of-zone drift; in practice AlertPolicy.onResetEscalation fires
                // the moment HR touches IN_ZONE, so any realistic run (oscillating near a
                // threshold) never escapes tier-1 — the FULL user heard earcons but never voice.
                // The 2026-04-22 audit ("slow down not firing") traces back to this gate.
                // Tier-3 (earcon+voice+vibration) still requires sustained drift and remains
                // escalated because vibration is a stronger signal reserved for runners who are
                // not responding to the first voice cue.
                val minimalUpgrade = verbosity == VoiceVerbosity.MINIMAL && currentSettings.minimalTierOneVoice
                val fullUpgrade = verbosity == VoiceVerbosity.FULL

                val shouldSpeakAlert =
                    escalationLevel == EscalationLevel.EARCON_VOICE ||
                    escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION ||
                    minimalUpgrade ||
                    fullUpgrade

                if (shouldSpeakAlert) {
                    // NonCancellable: a voice alert that has already started must complete even if
                    // destroy() cancels the scope mid-flight, so the runner isn't left with an
                    // earcon beep but no spoken cue.
                    scope.launch {
                        delay(300L)
                        withContext(NonCancellable) {
                            voicePlayer.speakEvent(
                                event,
                                guidanceText,
                                currentWorkoutMode,
                                distanceUnit = distanceUnit,
                                hrSlopeBpmPerMin = lastHrSlopeBpmPerMin,
                                currentHr = currentHr,
                                targetHr = targetHr,
                            )
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
                voicePlayer.speakEvent(
                    event,
                    guidanceText,
                    currentWorkoutMode,
                    distanceUnit = distanceUnit,
                    hrSlopeBpmPerMin = lastHrSlopeBpmPerMin,
                )
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
                voicePlayer.speakEvent(
                    event,
                    guidanceText,
                    currentWorkoutMode,
                    paceMinPerKm,
                    distanceUnit,
                    hrSlopeBpmPerMin = lastHrSlopeBpmPerMin,
                )
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
                voicePlayer.speakEvent(
                    event,
                    guidanceText,
                    currentWorkoutMode,
                    paceMinPerKm,
                    distanceUnit,
                    hrSlopeBpmPerMin = lastHrSlopeBpmPerMin,
                )
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
        if (currentSettings.voiceVerbosity == VoiceVerbosity.OFF) return
        debugLogger.logLine("LIFECYCLE action=${if (paused) "PAUSE" else "RESUME"}")
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

    // ── Strides timer audio ────────────────────────────────────────────────
    // Per-rep chimes are gated on BOTH the standard verbosity earcon gate AND the
    // user-controlled stridesTimerEarcons toggle. The announcement (one-shot at the
    // start of the strides block) is NOT gated by stridesTimerEarcons — that toggle
    // is only for the per-rep beeps. Verbosity OFF still silences the announcement.

    fun playStridesGo() {
        if (!shouldPlayEarcon(currentSettings.voiceVerbosity)) return
        if (!currentSettings.stridesTimerEarcons) return
        earconPlayer.playStridesEvent(StridesEarcon.GO)
    }

    fun playStridesEase() {
        if (!shouldPlayEarcon(currentSettings.voiceVerbosity)) return
        if (!currentSettings.stridesTimerEarcons) return
        earconPlayer.playStridesEvent(StridesEarcon.EASE)
    }

    fun playStridesSetComplete() {
        if (!shouldPlayEarcon(currentSettings.voiceVerbosity)) return
        if (!currentSettings.stridesTimerEarcons) return
        earconPlayer.playStridesEvent(StridesEarcon.SET_COMPLETE)
    }

    /**
     * Speaks the one-shot strides-timer announcement at the start of the strides block.
     * Respects voice verbosity (OFF = silent) but is NOT gated by stridesTimerEarcons —
     * that toggle is for the per-rep chimes only.
     */
    fun playStridesAnnouncement(totalReps: Int) {
        val word = when (totalReps) {
            4 -> "four"
            5 -> "five"
            6 -> "six"
            7 -> "seven"
            8 -> "eight"
            9 -> "nine"
            10 -> "ten"
            else -> totalReps.toString()
        }
        val text = "Strides time. $word pickups, twenty seconds smooth and relaxed. Jog one minute between each."
        voicePlayer.speakAnnouncement(text)
    }

    fun destroy() {
        scope.cancel()
        earconPlayer.destroy()
        voicePlayer.destroy()
        vibrationManager.destroy()
    }

    private fun quoteOrNull(s: String?): String =
        if (s == null) "null" else "\"" + s.replace("\"", "\\\"") + "\""

    companion object {
        /** Returns true when earcons should fire. OFF suppresses earcons; other levels allow them. */
        fun shouldPlayEarcon(verbosity: VoiceVerbosity): Boolean =
            verbosity != VoiceVerbosity.OFF

        /**
         * Process-lifetime hint set by SetupViewModel after the audio primer is dismissed and
         * the runner taps "Got it — start my run". The next [playStartSequence] call reads and
         * clears this flag, skipping its TTS briefing so the runner isn't given a briefing
         * right after a 30-second primer explaining the audio system. One-shot; cleared on read.
         */
        @Volatile
        var skipNextBriefing: Boolean = false
    }
}
