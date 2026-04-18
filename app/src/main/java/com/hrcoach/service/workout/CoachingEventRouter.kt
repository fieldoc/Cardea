package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ConfirmCadence
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.audio.VoicePlayer
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
        // NOT reset — we just skip this tick and reassess next tick. TUNING: 0.8 matches
        // buildGuidance's phrasing threshold for symmetry.
        const val IN_ZONE_CONFIRM_SLOPE_GATE_BPM_PER_MIN: Float = 0.8f
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
        halfwayFired = false
        completeFired = false
        lastSplitAnnounced = 0
        lastVoiceCueTimeMs = 0L
        inZoneConfirmCount = 0
    }

    fun route(
        workoutConfig: WorkoutConfig,
        connected: Boolean,
        distanceMeters: Float,
        elapsedSeconds: Long,
        zoneStatus: ZoneStatus,
        adaptiveResult: AdaptivePaceController.TickResult?,
        guidance: String,
        nowMs: Long,
        distanceUnit: DistanceUnit = DistanceUnit.KM,
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

                // lastPredictiveWarningTime — conditionally reset (only when the 60s cooldown
                // was already expired). This suppresses PREDICTIVE_WARNING on the entry tick
                // itself (simultaneous cues sound confusing), while preserving the running
                // cooldown for rapid oscillators whose 60s hasn't elapsed yet — they continue
                // to receive warnings once they've been in zone for 60s. Also guards first zone
                // entry: returning runners have adaptive projection confidence from prior sessions
                // and would otherwise hear a predictive cue the very first tick they reach zone.
                if (nowMs - lastPredictiveWarningTime >= 60_000L) {
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

        val projectedDrift = adaptiveResult?.projectedZoneStatus in
            setOf(ZoneStatus.ABOVE_ZONE, ZoneStatus.BELOW_ZONE)
        // Suppress predictive coaching during cardiovascular warmup (first 90s) — the slope EMA
        // reflects the expected HR rise from rest, not a real drift, and would fire false warnings.
        val warmupComplete = elapsedSeconds >= 90L
        if (zoneStatus == ZoneStatus.IN_ZONE &&
            adaptiveResult?.hasProjectionConfidence == true &&
            projectedDrift &&
            warmupComplete &&
            nowMs - lastPredictiveWarningTime >= 60_000L
        ) {
            emitEvent(CoachingEvent.PREDICTIVE_WARNING, guidance)
            lastPredictiveWarningTime = nowMs
            lastVoiceCueTimeMs = nowMs
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
        // LOW-confidence substitution: on early sessions the guidance defaults to "Learning
        // your patterns - hold steady" for the whole workout. Looping that phrase every 3–5 min
        // reads as indecision, so we rotate through short affirmations instead.
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
                val isLowConfidence = adaptiveResult?.hasProjectionConfidence == false
                val spokenText = if (isLowConfidence) {
                    val pool = VoicePlayer.LOW_CONFIDENCE_AFFIRMATIONS
                    pool[inZoneConfirmCount % pool.size]
                } else {
                    guidance
                }
                emitEvent(CoachingEvent.IN_ZONE_CONFIRM, spokenText)
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
