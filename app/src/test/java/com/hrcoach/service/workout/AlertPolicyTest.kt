package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPolicyTest {

    private fun handle(
        policy: AlertPolicy,
        status: ZoneStatus,
        nowMs: Long,
        events: MutableList<CoachingEvent>,
        alertDelaySec: Int = 15,
        alertCooldownSec: Int = 30
    ) {
        policy.handle(
            status = status,
            nowMs = nowMs,
            alertDelaySec = alertDelaySec,
            alertCooldownSec = alertCooldownSec,
            guidanceText = "guidance",
            onResetEscalation = {},
            onAlert = { event, _ -> events += event }
        )
    }

    @Test
    fun `cooldown is preserved across zone direction flip`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        // First alert fires at delay (16s)
        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events)   // registers
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events)  // fires (delay met, no prior alert)
        assertEquals(1, events.size)

        // Direction flip: ABOVE → BELOW → ABOVE
        handle(policy, ZoneStatus.BELOW_ZONE, 17_000L, events)  // flip
        handle(policy, ZoneStatus.ABOVE_ZONE, 18_000L, events)  // flip back

        // 33s: delay met (33-18=15s), but cooldown from last alert (33-16=17s < 30s) → suppressed
        handle(policy, ZoneStatus.ABOVE_ZONE, 33_000L, events)
        assertEquals(1, events.size) // no new alert

        // 46s: delay met AND cooldown met (46-16=30s) → fires
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events)
        assertEquals(2, events.size)
        assertEquals(CoachingEvent.SLOW_DOWN, events[1])
    }

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
