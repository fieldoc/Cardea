package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus

class CoachingEventRouter {
    private var wasHrConnected: Boolean = false
    private var previousZoneStatus: ZoneStatus = ZoneStatus.NO_DATA
    private var lastSegmentIndex: Int = -1
    private var lastPredictiveWarningTime: Long = 0L

    fun reset() {
        wasHrConnected = false
        previousZoneStatus = ZoneStatus.NO_DATA
        lastSegmentIndex = -1
        lastPredictiveWarningTime = 0L
    }

    fun route(
        workoutConfig: WorkoutConfig,
        connected: Boolean,
        distanceMeters: Float,
        zoneStatus: ZoneStatus,
        adaptiveResult: AdaptivePaceController.TickResult?,
        guidance: String,
        nowMs: Long,
        emitEvent: (CoachingEvent, String?) -> Unit
    ) {
        if (wasHrConnected && !connected) {
            emitEvent(CoachingEvent.SIGNAL_LOST, null)
        } else if (!wasHrConnected && connected) {
            emitEvent(CoachingEvent.SIGNAL_REGAINED, null)
        }

        if (
            (previousZoneStatus == ZoneStatus.ABOVE_ZONE || previousZoneStatus == ZoneStatus.BELOW_ZONE) &&
            zoneStatus == ZoneStatus.IN_ZONE
        ) {
            emitEvent(CoachingEvent.RETURN_TO_ZONE, guidance)
        }

        if (workoutConfig.mode == WorkoutMode.DISTANCE_PROFILE) {
            val segmentIndex = currentSegmentIndex(workoutConfig, distanceMeters)
            if (lastSegmentIndex >= 0 && segmentIndex >= 0 && segmentIndex != lastSegmentIndex) {
                emitEvent(CoachingEvent.SEGMENT_CHANGE, null)
            }
            lastSegmentIndex = segmentIndex
        } else {
            lastSegmentIndex = -1
        }

        val projectedDrift = adaptiveResult?.projectedZoneStatus == ZoneStatus.ABOVE_ZONE ||
            adaptiveResult?.projectedZoneStatus == ZoneStatus.BELOW_ZONE
        if (
            zoneStatus == ZoneStatus.IN_ZONE &&
            adaptiveResult?.hasProjectionConfidence == true &&
            projectedDrift &&
            nowMs - lastPredictiveWarningTime >= 60_000L
        ) {
            emitEvent(CoachingEvent.PREDICTIVE_WARNING, guidance)
            lastPredictiveWarningTime = nowMs
        }

        wasHrConnected = connected
        previousZoneStatus = zoneStatus
    }

    private fun currentSegmentIndex(config: WorkoutConfig, distanceMeters: Float): Int {
        if (config.segments.isEmpty()) return -1
        val segment = config.segments.indexOfFirst { distanceMeters <= it.distanceMeters }
        return if (segment >= 0) segment else config.segments.lastIndex
    }
}
