package com.hrcoach.domain.engine

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus

class ZoneEngine(private val config: WorkoutConfig) {

    companion object {
        const val HYSTERESIS_BPM = 2
    }

    private var lastStatus: ZoneStatus = ZoneStatus.NO_DATA

    fun evaluate(hr: Int, distanceMeters: Float): ZoneStatus {
        val target = config.targetHrAtDistance(distanceMeters) ?: return ZoneStatus.NO_DATA
        return evaluate(hr, target)
    }

    fun evaluate(hr: Int, targetHr: Int): ZoneStatus {
        val low  = targetHr - config.bufferBpm
        val high = targetHr + config.bufferBpm
        val next = if (lastStatus == ZoneStatus.IN_ZONE) {
            when {
                hr > high + HYSTERESIS_BPM -> ZoneStatus.ABOVE_ZONE
                hr < low - HYSTERESIS_BPM  -> ZoneStatus.BELOW_ZONE
                else                        -> ZoneStatus.IN_ZONE
            }
        } else {
            when {
                hr < low  -> ZoneStatus.BELOW_ZONE
                hr > high -> ZoneStatus.ABOVE_ZONE
                else      -> ZoneStatus.IN_ZONE
            }
        }
        lastStatus = next
        return next
    }
}
