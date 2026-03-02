package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPolicyTest {

    @Test
    fun `alerts only after delay and respects cooldown`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        var resetCount = 0

        policy.handle(
            status = ZoneStatus.ABOVE_ZONE,
            nowMs = 1_000L,
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "slow down",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        policy.handle(
            status = ZoneStatus.ABOVE_ZONE,
            nowMs = 10_000L,
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "slow down",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        assertEquals(emptyList<CoachingEvent>(), events)

        policy.handle(
            status = ZoneStatus.ABOVE_ZONE,
            nowMs = 17_000L,
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "slow down",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)

        policy.handle(
            status = ZoneStatus.ABOVE_ZONE,
            nowMs = 35_000L,
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "slow down",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)

        policy.handle(
            status = ZoneStatus.IN_ZONE,
            nowMs = 36_000L,
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "in zone",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        assertEquals(1, resetCount)
    }
}
