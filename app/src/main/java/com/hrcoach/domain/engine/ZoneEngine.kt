package com.hrcoach.domain.engine

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus

class ZoneEngine(private val config: WorkoutConfig) {

    fun evaluate(hr: Int, distanceMeters: Float): ZoneStatus {
        val target = config.targetHrAtDistance(distanceMeters) ?: return ZoneStatus.NO_DATA
        val low = target - config.bufferBpm
        val high = target + config.bufferBpm
        return when {
            hr < low -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else -> ZoneStatus.IN_ZONE
        }
    }
}
