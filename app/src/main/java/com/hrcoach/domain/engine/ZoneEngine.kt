package com.hrcoach.domain.engine

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus

class ZoneEngine(private val config: WorkoutConfig) {

    fun evaluate(hr: Int, distanceMeters: Float): ZoneStatus {
        val target = config.targetHrAtDistance(distanceMeters) ?: return ZoneStatus.NO_DATA
        return evaluate(hr, target)
    }

    fun evaluate(hr: Int, targetHr: Int): ZoneStatus {
        val low  = targetHr - config.bufferBpm
        val high = targetHr + config.bufferBpm
        return when {
            hr < low  -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else      -> ZoneStatus.IN_ZONE
        }
    }
}
