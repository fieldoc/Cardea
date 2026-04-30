package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ConfirmCadence
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import kotlin.math.abs

class CoachingEventRouter {
    // Settable from WFS so mid-workout AudioSettings changes propagate without restarting the
    // workout. Default STANDARD (180/300) matches the AudioSettings default.
    // TUNING: exposes FREQUENT (180/180) for users who want the legacy cadence or REDUCED
    // (180/600) for near-silent steady-state runs.
    var confirmCadence: ConfirmCadence = ConfirmCadence.STANDARD
    private var wasHrConnected: Boolean = false
    private var previousZoneStatus: ZoneStatus = ZoneStatus.NO_DATA
    private var hasBeenInZone: Boolean = false
    private var lastReturnToZoneMs: Long = 0L
    private var lastSegmentIndex: Int = -1
    private var lastPredictiveWarningTime: Long = 0L
    // Fresh-evidence gate: disarmed after each PW fires, rearmed on a tick where HR is within
    // PREDICTIVE_REARM_BAND_BPM of target. Ensures each PW corresponds to a distinct drift event
    // rather than sustained offset. Starts true so the first eligible drift after warmup can fire.
    private var predictiveArmed: Boolean = true

    // Informational cue state
    private var halfwayFired: Boolean = false
    private var completeFired: Boolean = false
    private var lastSplitAnnounced: Int = 0
    private var lastVoiceCueTimeMs: Long = 0L
    private var workoutStartMs: Long = 0L

    // IN_ZONE_CONFIRM cadence state. See IN_ZONE_CONFIRM_FIRST_MS / IN_ZONE_CONFIRM_REPEAT_MS.
    // inZoneConfirmCount also drives the LOW-confidence affirmation rotation so the runner
    // doesn't hear the same phrase every 3–5 min.
    private var inZoneConfirmCount: Int = 0

    companion object {
        const val RETURN_TO_ZONE_COOLDOWN_MS = 30_000L

        // IN_ZONE_CONFIRM cadence is now controlled by the user-settable ConfirmCadence enum
        // (see AudioSettings.inZoneConfirmCadence + CoachingEventRouter.confirmCadence).
        // Default STANDARD: first confirm at 3 min, subsequent at 5 min. Values above.

        // IN_ZONE_CONFIRM is skipped when HR is actively moving (|slope| ≥ this), because
        // a reassuring chime + "HR falling" voice reads as a mixed signal. The 3-min timer is
        // NOT reset — we just skip this tick and reassess next tick. Tied by reference to
        // AdaptivePaceController.SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN so the gate fires on
        // exactly the same condition that flips phrasing from "hold steady" to "HR rising/
        // falling" — preserving the original symmetry intent across the threshold change.
        const val IN_ZONE_CONFIRM_SLOPE_GATE_BPM_PER_MIN: Float =
            AdaptivePaceController.SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN

        // PREDICTIVE_WARNING tuning (2026-04-22, after the FULL-verbosity sim exposed 33 PWs in a
        // single 20-min run — same "HR climbing - ease off slightly" every ~60s while HR was held
        // flat above target). Three gates tightened:
        //
        //   1. COOLDOWN raised 60s → 180s — matches IN_ZONE_CONFIRM cadence. The runner already
        //      knows they're drifting from the first PW; a minute-by-minute reminder is nagging,
        //      not coaching.
        //   2. SLOPE GATE added — require the EMA to actually reflect motion toward the boundary
        //      (|slope| ≥ 0.5 BPM/min AND slope sign matches projected-drift direction). When HR
        //      is stable at an offset (slope ≈ 0), it has already drifted — that's a JOB for the
        //      reactive AlertPolicy, not for PW. PW's value is flagging drift *in progress*.
        //   3. FRESH-EVIDENCE gate — after PW fires, the runner must briefly return to within a
        //      narrow band of target before the next PW is eligible. Encoded via [predictiveArmed]:
        //      set false after each fire, set true again when HR is within ±PREDICTIVE_REARM_BAND_BPM
        //      of target for one tick. Prevents endless "still drifting high" repetition.
        const val PREDICTIVE_WARNING_COOLDOWN_MS: Long = 180_000L
        const val PREDICTIVE_SLOPE_GATE_BPM_PER_MIN: Float = 0.5f
        const val PREDICTIVE_REARM_BAND_BPM: Int = 4
    }

    /**
     * Records that an external audio cue was played (e.g. a zone alert from AlertPolicy)
     * so that IN_ZONE_CONFIRM's 3-minute silence window is correctly reset.
     */
    fun noteExternalAlert(nowMs: Long) {
        lastVoiceCueTimeMs = nowMs
    }

    /**
     * Called when a reactive alert (SPEED_UP / SLOW_DOWN) fires from AlertPolicy.
     * Sets [lastPredictiveWarningTime] to 0L, which would normally open the predictive gate
     * immediately. In practice this value is overridden by the conditional re-entry reset in
     * [route]: when the runner returns to zone and the 60s cooldown was already expired (which
     * covers all realistic post-alert scenarios), [lastPredictiveWarningTime] is refreshed to
     * nowMs. Retained for call-site clarity and to restore its open-gate role if re-entry
     * logic ever changes.
     */
    fun resetPredictiveWarningTimer() {
        lastPredictiveWarningTime = 0L
    }

    fun reset(workoutStartMs: Long = 0L) {
        this.workoutStartMs = workoutStartMs
        wasHrConnected = false
        previousZoneStatus = ZoneStatus.NO_DATA
        hasBeenInZone = false
        lastReturnToZoneMs = 0L
        lastSegmentIndex = -1
        lastPredictiveWarningTime = 0L
        predictiveArmed = true
        halfwayFired = false
        completeFired = false
        lastSplitAnnounced = 0
        lastVoiceCueTimeMs = 0L
        inZoneConfirmCount = 0
    }

    /**
     * [guidance] is the VOICE guidance string — what should be spoken if this event routes
     * through TTS. Callers pass null (not the on-screen display text) when the current
     * guidance is a static preset quip that must NOT be spoken (e.g. zone2's "Easy pace
     * builds your aerobic engine. Hold a conversation."). When null, VoicePlayer falls back
     * to its fixed per-event phrasing ("Back in zone", "Watch your pace", etc.) — the same
     * rule that already applies to IN_ZONE_CONFIRM. The on-screen `WorkoutState.guidanceText`
     * is maintained separately in WFS and is unaffected by this null-ing.
     */
    fun route(
        workoutConfig: WorkoutConfig,
        connected: Boolean,
        distanceMeters: Float,
        elapsedSeconds: Long,
        zoneStatus: ZoneStatus,
        adaptiveResult: AdaptivePaceController.TickResult?,
        guidance: String?,
        nowMs: Long,
        distanceUnit: DistanceUnit = DistanceUnit.KM,
        // Added 2026-04-24. Defaults to 90L to preserve the original hardcoded value for
        // legacy test callers that don't thread the config-derived value through. WFS passes
        // [WorkoutConfig.effectiveWarmupGraceSec].
        warmupGraceSec: Int = 90,
        // Suppress below-direction PREDICTIVE_WARNING in cool-down / recovery-week contexts.
        // Above-direction predictive remains active — overshoot during these contexts is the
        // genuine risk.
        suppressBelowPredictive: Boolean = false,
        emitEvent: (CoachingEvent, String?) -> Unit
    ) {
        if (wasHrConnected && !connected) {
            emitEvent(CoachingEvent.SIGNAL_LOST, null)
            lastVoiceCueTimeMs = nowMs
        } else if (!wasHrConnected && connected) {
            emitEvent(CoachingEvent.SIGNAL_REGAINED, null)
            lastVoiceCueTimeMs = nowMs
        }

        if (zoneStatus == ZoneStatus.IN_ZONE) {
            if (previousZoneStatus != ZoneStatus.IN_ZONE) {
                // Reset voice-cue baselines on any zone entry (first or re-entry).
                //
                // lastVoiceCueTimeMs — always reset. Prevents IN_ZONE_CONFIRM from firing
                // immediately after a long absence where the baseline went stale (e.g. when
                // rapid out/in oscillations kept the RETURN_TO_ZONE cooldown busy).
                lastVoiceCueTimeMs = nowMs

                // lastPredictiveWarningTime — conditionally reset (only when the cooldown was
                // already expired). This suppresses PREDICTIVE_WARNING on the entry tick itself
                // (simultaneous cues sound confusing), while preserving the running cooldown for
                // rapid oscillators whose cooldown hasn't elapsed yet — they continue to receive
                // warnings once they've been in zone for the full cooldown window. Also guards
                // first zone entry: returning runners have adaptive projection confidence from
                // prior sessions and would otherwise hear a predictive cue the very first tick.
                if (nowMs - lastPredictiveWarningTime >= PREDICTIVE_WARNING_COOLDOWN_MS) {
                    lastPredictiveWarningTime = nowMs
                }

                // RETURN_TO_ZONE only fires on re-entry (not first entry), with a 30s cooldown
                // to absorb HR jitter near the zone boundary. The guidance string provides
                // contextual re-entry feedback (e.g. "In zone - HR falling" when the runner
                // overshot and is still coming down, vs. plain "Back in zone" on a clean entry).
                //
                // LOW-confidence sessions: suppress contextual guidance and fall through to
                // VoicePlayer's "Back in zone" fallback. The LOW-confidence in-zone guidance
                // ("Learning your patterns - hold steady") describes calibration status, not
                // re-entry — pairing it with the reassuring RETURN_TO_ZONE earcon reads as a
                // semantic mismatch. High/medium-confidence guidance is genuinely contextual
                // and carries its own information, so we pass it through.
                if (hasBeenInZone && nowMs - lastReturnToZoneMs >= RETURN_TO_ZONE_COOLDOWN_MS) {
                    val returnGuidance = if (adaptiveResult?.hasProjectionConfidence == true) guidance else null
                    emitEvent(CoachingEvent.RETURN_TO_ZONE, returnGuidance)
                    lastReturnToZoneMs = nowMs
                }
            }
            hasBeenInZone = true
        }

        if (workoutConfig.mode == WorkoutMode.DISTANCE_PROFILE) {
            val segmentIndex = if (workoutConfig.isTimeBased()) {
                segmentIndexByTime(workoutConfig, elapsedSeconds)
            } else {
                segmentIndexByDistance(workoutConfig, distanceMeters)
            }
            if (lastSegmentIndex >= 0 && segmentIndex >= 0 && segmentIndex != lastSegmentIndex) {
                emitEvent(CoachingEvent.SEGMENT_CHANGE, null)
                lastVoiceCueTimeMs = nowMs
            }
            lastSegmentIndex = segmentIndex
        } else {
            lastSegmentIndex = -1
        }

        val projectedAbove = adaptiveResult?.projectedZoneStatus == ZoneStatus.ABOVE_ZONE
        val projectedBelow = adaptiveResult?.projectedZoneStatus == ZoneStatus.BELOW_ZONE
        val projectedDrift = projectedAbove || projectedBelow
        // Suppress predictive coaching during cardiovascular warmup — the slope EMA reflects
        // the expected HR rise from rest, not a real drift, and would fire false warnings.
        // Shares [warmupGraceSec] with AlertPolicy so prescribed warmup, SPEED_UP suppression,
        // and PREDICTIVE_WARNING suppression all line up on the same boundary.
        val warmupComplete = elapsedSeconds >= warmupGraceSec

        // Slope must reflect motion toward the projected boundary. If HR is stable at an offset
        // (|slope| near zero), that's a present-tense condition for AlertPolicy, not a prediction.
        val slope = adaptiveResult?.hrSlopeBpmPerMin ?: 0f
        val slopeMatchesDrift = when {
            projectedAbove -> slope >= PREDICTIVE_SLOPE_GATE_BPM_PER_MIN
            projectedBelow -> slope <= -PREDICTIVE_SLOPE_GATE_BPM_PER_MIN
            else -> false
        }

        // Fresh-evidence rearm: after a PW fires, predictiveArmed stays false until HR visibly
        // returns toward target (IN_ZONE + low slope). This stops the "same drift, repeated cue"
        // loop — next PW requires a NEW drift event, not ongoing drift.
        if (!predictiveArmed &&
            zoneStatus == ZoneStatus.IN_ZONE &&
            abs(slope) < PREDICTIVE_SLOPE_GATE_BPM_PER_MIN) {
            predictiveArmed = true
        }

        val predictiveDirectionAllowed = !(suppressBelowPredictive && projectedBelow)

        if (zoneStatus == ZoneStatus.IN_ZONE &&
            adaptiveResult?.hasProjectionConfidence == true &&
            projectedDrift &&
            slopeMatchesDrift &&
            predictiveArmed &&
            warmupComplete &&
            predictiveDirectionAllowed &&
            nowMs - lastPredictiveWarningTime >= PREDICTIVE_WARNING_COOLDOWN_MS
        ) {
            emitEvent(CoachingEvent.PREDICTIVE_WARNING, guidance)
            lastPredictiveWarningTime = nowMs
            lastVoiceCueTimeMs = nowMs
            predictiveArmed = false
        }

        // ── Informational cues ──────────────────────────────────────

        // KM_SPLIT (or mile split when imperial)
        val splitThreshold = if (distanceUnit == DistanceUnit.MI) DistanceUnit.METERS_PER_MILE else DistanceUnit.METERS_PER_KM
        val currentSplit = (distanceMeters / splitThreshold).toInt()
        if (currentSplit > lastSplitAnnounced && currentSplit >= 1) {
            lastSplitAnnounced = currentSplit
            emitEvent(CoachingEvent.KM_SPLIT, currentSplit.toString())
            lastVoiceCueTimeMs = nowMs
        }

        // HALFWAY (only when a target exists)
        if (!halfwayFired) {
            val targetDistance = workoutConfig.totalDistanceMeters()
            val targetDuration = workoutConfig.totalDurationSeconds()
            val isHalfwayByDistance = targetDistance != null && targetDistance > 0 &&
                distanceMeters >= targetDistance / 2f
            val isHalfwayByTime = targetDuration != null && targetDuration > 0 &&
                elapsedSeconds >= targetDuration / 2
            if (isHalfwayByDistance || isHalfwayByTime) {
                halfwayFired = true
                emitEvent(CoachingEvent.HALFWAY, null)
                lastVoiceCueTimeMs = nowMs
            }
        }

        // WORKOUT_COMPLETE
        if (!completeFired) {
            val targetDistance = workoutConfig.totalDistanceMeters()
            val targetDuration = workoutConfig.totalDurationSeconds()
            val doneByDistance = targetDistance != null && targetDistance > 0 &&
                distanceMeters >= targetDistance
            val doneByTime = targetDuration != null && targetDuration > 0 &&
                elapsedSeconds >= targetDuration
            if (doneByDistance || doneByTime) {
                completeFired = true
                emitEvent(CoachingEvent.WORKOUT_COMPLETE, null)
                lastVoiceCueTimeMs = nowMs
            }
        }

        // IN_ZONE_CONFIRM — reassurance cue while in zone. Three gates:
        //   (a) projection stable (existing) — don't reassure when projection says drift is coming.
        //   (b) slope gate (new) — don't reassure when HR is actively moving ≥ 0.8 bpm/min; a
        //       "you're cruising" chime paired with "HR falling/rising" voice reads as mixed.
        //       The timer is not reset on this skip — we just wait for the slope to calm down.
        //   (c) adaptive cadence (new) — first confirm at 3 min, subsequent at 5 min.
        //       On a 30-min steady run this drops from ~6 events to ~3.
        // Spoken text is always the fixed "In zone" phrase built in VoicePlayer.eventText —
        // preset guidance strings (e.g. zone2's "Easy pace builds your aerobic engine. Hold a
        // conversation.") are long, static, and re-reading them every 3-5 min reads as nagging.
        val projectedStable = adaptiveResult?.projectedZoneStatus?.let {
            it != ZoneStatus.ABOVE_ZONE && it != ZoneStatus.BELOW_ZONE
        } ?: true
        val slopeCalm = adaptiveResult?.hrSlopeBpmPerMin?.let {
            abs(it) < IN_ZONE_CONFIRM_SLOPE_GATE_BPM_PER_MIN
        } ?: true
        if (zoneStatus == ZoneStatus.IN_ZONE && projectedStable && slopeCalm) {
            val baseline = if (lastVoiceCueTimeMs > 0L) lastVoiceCueTimeMs
                           else if (workoutStartMs > 0L) workoutStartMs
                           else nowMs
            val requiredInterval = if (inZoneConfirmCount == 0) confirmCadence.firstMs
                                   else confirmCadence.repeatMs
            if (nowMs - baseline >= requiredInterval) {
                emitEvent(CoachingEvent.IN_ZONE_CONFIRM, null)
                lastVoiceCueTimeMs = nowMs
                inZoneConfirmCount += 1
            }
        }

        wasHrConnected = connected
        previousZoneStatus = zoneStatus
    }

    private fun segmentIndexByTime(config: WorkoutConfig, elapsedSeconds: Long): Int {
        return config.segmentAtElapsed(elapsedSeconds)?.first ?: -1
    }

    private fun segmentIndexByDistance(config: WorkoutConfig, distanceMeters: Float): Int {
        if (config.segments.isEmpty()) return -1
        val index = config.segments.indexOfFirst { seg ->
            seg.distanceMeters?.let { d -> distanceMeters <= d } == true
        }
        return if (index >= 0) index else config.segments.lastIndex
    }
}
