package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus

class CoachingEventRouter {
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

    companion object {
        const val RETURN_TO_ZONE_COOLDOWN_MS = 30_000L
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
            // Only fire RETURN_TO_ZONE when: runner was previously in zone, left, and came back.
            // Suppress on initial zone entry (warming up) and respect a 30s cooldown for HR jitter.
            if (previousZoneStatus != ZoneStatus.IN_ZONE && hasBeenInZone &&
                nowMs - lastReturnToZoneMs >= RETURN_TO_ZONE_COOLDOWN_MS
            ) {
                emitEvent(CoachingEvent.RETURN_TO_ZONE, null)
                lastReturnToZoneMs = nowMs
                lastVoiceCueTimeMs = nowMs
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
        if (zoneStatus == ZoneStatus.IN_ZONE &&
            adaptiveResult?.hasProjectionConfidence == true &&
            projectedDrift &&
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

        // IN_ZONE_CONFIRM (every 3+ minutes of voice silence while in zone)
        // Use workoutStartMs as initial baseline so the first confirm fires ~3 min into the workout.
        if (zoneStatus == ZoneStatus.IN_ZONE) {
            val baseline = if (lastVoiceCueTimeMs > 0L) lastVoiceCueTimeMs
                           else if (workoutStartMs > 0L) workoutStartMs
                           else nowMs
            if (nowMs - baseline >= 180_000L) {
                emitEvent(CoachingEvent.IN_ZONE_CONFIRM, null)
                lastVoiceCueTimeMs = nowMs
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
