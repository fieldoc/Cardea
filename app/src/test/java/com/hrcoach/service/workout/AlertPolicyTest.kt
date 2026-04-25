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
        alertCooldownSec: Int = 30,
        hrSlopeBpmPerMin: Float = 0f,
        currentPaceMinPerKm: Float? = null,
        elapsedSeconds: Long = Long.MAX_VALUE,
        warmupGraceSec: Int = 0
    ) {
        policy.handle(
            status = status,
            nowMs = nowMs,
            alertDelaySec = alertDelaySec,
            alertCooldownSec = alertCooldownSec,
            guidanceText = "guidance",
            onResetEscalation = {},
            onAlert = { event, _ -> events += event },
            hrSlopeBpmPerMin = hrSlopeBpmPerMin,
            currentPaceMinPerKm = currentPaceMinPerKm,
            elapsedSeconds = elapsedSeconds,
            warmupGraceSec = warmupGraceSec
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

    // ── Self-correction suppression (added 2026-04-17) ────────────────────

    @Test
    fun `SLOW_DOWN suppressed when HR is dropping at or beyond threshold`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        // Build up above-zone for delay window.
        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = -2.0f)
        // At 16s — delay met, but slope is fast-falling (self-correction). Should suppress.
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(emptyList<CoachingEvent>(), events)
    }

    @Test
    fun `SLOW_DOWN fires when slope is below suppression threshold`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = -1.0f)
        // slope -1.0 is below suppression threshold (|1.5|), so the alert should fire.
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = -1.0f)
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)
    }

    @Test
    fun `suppression debounce prevents immediate re-fire once slope eases`() {
        // Regression test for the review finding: without lastAlertTime update on suppression,
        // an eased slope on the next tick fires an alert with no cooldown gap from the previous
        // (suppressed) cycle.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events)                                  // register
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = -2.0f)       // suppressed
        // 1s later slope eases — without debounce this would fire.
        handle(policy, ZoneStatus.ABOVE_ZONE, 17_000L, events, hrSlopeBpmPerMin = -1.0f)
        assertEquals(emptyList<CoachingEvent>(), events)

        // Only after cooldown (30s) from the suppression tick should the alert fire.
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -1.0f)
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)
    }

    @Test
    fun `SPEED_UP self-correction is mirror of SLOW_DOWN`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, hrSlopeBpmPerMin = 2.0f)
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, hrSlopeBpmPerMin = 2.0f)
        assertEquals(emptyList<CoachingEvent>(), events)
    }

    // ── Walk-break suppression (added 2026-04-17) ─────────────────────────

    @Test
    fun `SPEED_UP suppressed on cooldown expiry when walking has become sustained`() {
        // The first alert always fires at delay-met (walking cannot be sustained before the 15s
        // delay elapses). After that, during cooldown, walking accumulates sustained time. At
        // cooldown expiry the walk gate suppresses the next alert. Verifies the "walk tracking
        // runs even during cooldown" property (item #3 in the review).
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, currentPaceMinPerKm = 11f)
        // t=16s: delay met, walking sustained=15s (not yet) → alert fires.
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, currentPaceMinPerKm = 11f)
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
        // t=46s: cooldown met, walking sustained=45s (≥30s) → SPEED_UP suppressed.
        handle(policy, ZoneStatus.BELOW_ZONE, 46_000L, events, currentPaceMinPerKm = 11f)
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)  // unchanged — no second alert
    }

    @Test
    fun `walking boundary is inclusive of exactly WALKING_PACE_MIN_PER_KM`() {
        // Pace exactly 10 min/km — the fast-walk boundary. Using `>=`, this counts as walking.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, currentPaceMinPerKm = 10.0f)
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, currentPaceMinPerKm = 10.0f)   // alert 1
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
        handle(policy, ZoneStatus.BELOW_ZONE, 46_000L, events, currentPaceMinPerKm = 10.0f)   // suppressed (sustained)
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
    }

    @Test
    fun `GPS dropout during walk preserves sustained timer`() {
        // Regression test for the review finding: null pace during a walk must not reset the
        // walkingSince counter, or tunnel / tree-cover GPS dropouts would restart the 30s window.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, currentPaceMinPerKm = 11f)
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, currentPaceMinPerKm = 11f)   // alert 1
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
        // GPS dropout during cooldown — if null pace zeroed walkingSince, the 30s counter
        // would restart and the alert at t=46s would NOT be suppressed.
        handle(policy, ZoneStatus.BELOW_ZONE, 25_000L, events, currentPaceMinPerKm = null)
        handle(policy, ZoneStatus.BELOW_ZONE, 35_000L, events, currentPaceMinPerKm = null)
        handle(policy, ZoneStatus.BELOW_ZONE, 46_000L, events, currentPaceMinPerKm = 11f)
        // walkingSince preserved → sustained → SPEED_UP suppressed.
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
    }

    @Test
    fun `known non-walking pace resets walk timer`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, currentPaceMinPerKm = 11f)    // walking
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, currentPaceMinPerKm = 11f)   // alert 1 fires
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
        // Runner picks up to jogging pace 40s in → walking timer resets.
        handle(policy, ZoneStatus.BELOW_ZONE, 40_000L, events, currentPaceMinPerKm = 7f)
        // At t=46s, cooldown met, walking was reset at 40s (sustained=6s, not enough) → alert fires.
        handle(policy, ZoneStatus.BELOW_ZONE, 46_000L, events, currentPaceMinPerKm = 7f)
        assertEquals(listOf(CoachingEvent.SPEED_UP, CoachingEvent.SPEED_UP), events)
    }

    // ── Warmup grace suppression (added 2026-04-24) ───────────────────────

    @Test
    fun `SPEED_UP suppressed during warmup grace period`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        // 120s warmup; runner is below zone from second 1.
        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events,
            elapsedSeconds = 1L, warmupGraceSec = 120)
        // Default delay (15s) would normally fire at t=16s, but we're still in warmup → suppressed.
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events,
            elapsedSeconds = 16L, warmupGraceSec = 120)
        // Even at t=119s (still inside the 120s warmup) → still suppressed.
        handle(policy, ZoneStatus.BELOW_ZONE, 119_000L, events,
            elapsedSeconds = 119L, warmupGraceSec = 120)
        assertEquals(emptyList<CoachingEvent>(), events)
    }

    @Test
    fun `SPEED_UP fires after warmup plus alertDelaySec`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        // Below zone throughout. Warmup 120s, delay 15s → first alert ≥ t=135s.
        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events,
            elapsedSeconds = 1L, warmupGraceSec = 120)
        handle(policy, ZoneStatus.BELOW_ZONE, 119_000L, events,
            elapsedSeconds = 119L, warmupGraceSec = 120)
        // At t=130s warmup is over but the post-warmup debounce hasn't elapsed (130-120=10s < 15s).
        handle(policy, ZoneStatus.BELOW_ZONE, 130_000L, events,
            elapsedSeconds = 130L, warmupGraceSec = 120)
        assertEquals(emptyList<CoachingEvent>(), events)
        // At t=136s: warmup over (16s ago) AND post-warmup debounce of 15s elapsed → fires.
        handle(policy, ZoneStatus.BELOW_ZONE, 136_000L, events,
            elapsedSeconds = 136L, warmupGraceSec = 120)
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
    }

    @Test
    fun `SLOW_DOWN is NOT suppressed during warmup`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        // Going too hard early IS a real safety signal — must alert even mid-warmup.
        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events,
            elapsedSeconds = 1L, warmupGraceSec = 120)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events,
            elapsedSeconds = 16L, warmupGraceSec = 120)
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)
    }

    @Test
    fun `warmup gate does not interfere with cooldown after alert`() {
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        // No warmup configured (default = 0) → standard delay/cooldown behavior.
        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events,
            elapsedSeconds = 1L)
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events,
            elapsedSeconds = 16L)
        assertEquals(listOf(CoachingEvent.SPEED_UP), events)
    }
}
