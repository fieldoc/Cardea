package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ZoneStatus

class AlertPolicy {
    private var outOfZoneSince: Long = 0L
    private var lastAlertTime: Long = 0L
    private var lastOutOfZoneStatus: ZoneStatus? = null

    fun reset() {
        outOfZoneSince = 0L
        lastAlertTime = 0L
        lastOutOfZoneStatus = null
    }

    fun handle(
        status: ZoneStatus,
        nowMs: Long,
        alertDelaySec: Int,
        alertCooldownSec: Int,
        guidanceText: String,
        onResetEscalation: () -> Unit,
        onAlert: (CoachingEvent, String) -> Unit
    ) {
        if (status == ZoneStatus.IN_ZONE || status == ZoneStatus.NO_DATA) {
            outOfZoneSince = 0L
            lastOutOfZoneStatus = null
            onResetEscalation()
            return
        }

        if (status != lastOutOfZoneStatus) {
            lastOutOfZoneStatus = status
            outOfZoneSince = nowMs
            lastAlertTime = 0L  // reset cooldown so direction-flip doesn't suppress next alert
            return
        }

        if (nowMs - outOfZoneSince < alertDelaySec * 1_000L) return
        if (lastAlertTime > 0L && nowMs - lastAlertTime < alertCooldownSec * 1_000L) return

        when (status) {
            ZoneStatus.ABOVE_ZONE -> onAlert(CoachingEvent.SLOW_DOWN, guidanceText)
            ZoneStatus.BELOW_ZONE -> onAlert(CoachingEvent.SPEED_UP, guidanceText)
            else -> Unit
        }
        lastAlertTime = nowMs
    }
}
