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
        warmupGraceSec: Int = 0,
        inZoneGraceSec: Int = AlertPolicy.IN_ZONE_GRACE_SEC,
        onResetEscalation: () -> Unit = {}
    ) {
        policy.handle(
            status = status,
            nowMs = nowMs,
            alertDelaySec = alertDelaySec,
            alertCooldownSec = alertCooldownSec,
            guidanceText = "guidance",
            onResetEscalation = onResetEscalation,
            onAlert = { event, _ -> events += event },
            hrSlopeBpmPerMin = hrSlopeBpmPerMin,
            currentPaceMinPerKm = currentPaceMinPerKm,
            elapsedSeconds = elapsedSeconds,
            warmupGraceSec = warmupGraceSec,
            inZoneGraceSec = inZoneGraceSec
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
        // Grace window means onResetEscalation does NOT fire on the first IN_ZONE tick
        // (added 2026-04-26). It fires only after IN_ZONE has been sustained for the grace
        // duration. Default IN_ZONE_GRACE_SEC = 3.
        assertEquals(0, resetCount)
        policy.handle(
            status = ZoneStatus.IN_ZONE,
            nowMs = 40_000L, // +4s into IN_ZONE → grace elapsed
            alertDelaySec = 15,
            alertCooldownSec = 30,
            guidanceText = "in zone",
            onResetEscalation = { resetCount += 1 },
            onAlert = { event, _ -> events += event }
        )
        assertEquals(1, resetCount)
    }

    // ── SLOW_DOWN slope-suppression + first-alert bypass (2026-04-17, refined 2026-04-26) ──

    @Test
    fun `first SLOW_DOWN of an excursion fires despite fast-falling slope`() {
        // 2026-04-26: the first SLOW_DOWN of an excursion always fires — slope-suppression
        // assumes the runner is correcting *because they were told*, but they haven't been.
        // Pre-2026-04-26 this case would have suppressed.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = -2.0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(listOf(CoachingEvent.SLOW_DOWN), events)
    }

    @Test
    fun `SLOW_DOWN slope-suppression resumes for second alert after first fires`() {
        // After the first alert is delivered, slope-suppression behaves as before — a runner
        // who's been told and is now correcting fast enough doesn't need a repeat nag.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)         // register
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // first fires
        assertEquals(1, events.size)
        // 30s later (cooldown met), slope is now fast-falling → suppressed.
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(1, events.size)
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
        // Regression test for the original review finding: without lastAlertTime update on
        // suppression, an eased slope on the next tick fires with no cooldown gap from the
        // previous (suppressed) cycle. Reframed for the first-alert-bypass world: prime by
        // letting the first alert fire, then test debounce on the second.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)         // register
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // first fires
        assertEquals(1, events.size)
        // t=46s: cooldown met from first fire, slope=-2.0 → suppressed (debounce sets lastAlertTime=46s)
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(1, events.size)
        // t=47s: slope eases — without debounce this would fire (cooldown from t=16 met).
        handle(policy, ZoneStatus.ABOVE_ZONE, 47_000L, events, hrSlopeBpmPerMin = -1.0f)
        assertEquals(1, events.size)
        // t=76s: 30s after the suppression debounce point → fires.
        handle(policy, ZoneStatus.ABOVE_ZONE, 76_000L, events, hrSlopeBpmPerMin = -1.0f)
        assertEquals(2, events.size)
    }

    @Test
    fun `SPEED_UP first alert is suppressed by fast-rising slope (asymmetric to SLOW_DOWN)`() {
        // Intentional asymmetry (2026-04-26): SPEED_UP does NOT get the first-alert bypass.
        // Above-zone is always a real overshoot worth interrupting for; below-zone during
        // walk/warmup is the expected/intended state, so a coincidental fast-rising slope
        // (HR climbing up to zone naturally) should still suppress.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.BELOW_ZONE, 1_000L, events, hrSlopeBpmPerMin = 2.0f)
        handle(policy, ZoneStatus.BELOW_ZONE, 16_000L, events, hrSlopeBpmPerMin = 2.0f)
        assertEquals(emptyList<CoachingEvent>(), events)
    }

    // ── SLOW_DOWN force-fire after 2× cooldown (added 2026-04-26) ─────────

    @Test
    fun `slope-suppressed SLOW_DOWN force-fires after 2x cooldown of silence`() {
        // After the first SLOW_DOWN fires, a continuously fast-falling slope keeps suppressing
        // subsequent alerts. Without the safety net, a runner stays silent indefinitely (real
        // failure mode: 13 min above zone with no follow-up). Force-fire kicks in once
        // FORCE_REALERT_COOLDOWN_FACTOR × cooldown has elapsed since the last fired alert.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // first fires
        assertEquals(1, events.size)
        // t=46s: cooldown met from t=16, slope=-2.0 → suppress, lastAlertTime=46s
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(1, events.size)
        // t=76s: cooldown met from t=46 (76-46=30), slope=-2.0 still → bypass forces fire
        // since (76-16) >= 2×30. Without force-fire this would suppress.
        handle(policy, ZoneStatus.ABOVE_ZONE, 76_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(2, events.size)
    }

    @Test
    fun `force-fire window resets after each fired alert`() {
        // The 2× window measures from lastFiredAlertTimeMs (only updated on actual fires),
        // not from lastAlertTime (updated on every gate-passing tick). After force-fire at
        // t=76 fires another alert, the next force-fire is 2×cooldown later (t=136).
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // fire 1 (t=16)
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)     // suppress
        handle(policy, ZoneStatus.ABOVE_ZONE, 76_000L, events, hrSlopeBpmPerMin = -2.0f)     // force-fire (t=76, 2× from t=16)
        assertEquals(2, events.size)
        // t=106s: cooldown met from t=76, slope still -2.0 → suppress (2× from t=76 not reached)
        handle(policy, ZoneStatus.ABOVE_ZONE, 106_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(2, events.size)
        // t=136s: cooldown met, slope still -2.0, 2× from t=76 reached → force-fire
        handle(policy, ZoneStatus.ABOVE_ZONE, 136_000L, events, hrSlopeBpmPerMin = -2.0f)
        assertEquals(3, events.size)
    }

    @Test
    fun `force-fire does not affect normal cadence when slope is not suppressing`() {
        // If slope eases enough that suppression doesn't apply, alerts fire at normal 1×
        // cooldown cadence — force-fire is a safety net, not a baseline.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // fire 1
        // Subsequent alerts with slope=0 (not suppressing) fire every 30s, not every 60s.
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = 0f)        // fire 2 at 1× cooldown
        assertEquals(2, events.size)
        handle(policy, ZoneStatus.ABOVE_ZONE, 76_000L, events, hrSlopeBpmPerMin = 0f)        // fire 3 at 1× cooldown
        assertEquals(3, events.size)
    }

    // ── IN_ZONE grace window (added 2026-04-26) ──────────────────────────

    @Test
    fun `IN_ZONE blip under grace preserves first-alert flag`() {
        // A 1-2 tick blip into IN_ZONE doesn't end the excursion — the next ABOVE tick is
        // still the same excursion, so first-alert bypass does NOT re-trigger. Without grace
        // a runner oscillating around the threshold would re-arm bypass on every blip.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // first fires
        assertEquals(1, events.size)
        // 2s blip into IN_ZONE (under default 3s grace)
        handle(policy, ZoneStatus.IN_ZONE, 17_000L, events)
        handle(policy, ZoneStatus.IN_ZONE, 18_000L, events)
        // Back ABOVE; cooldown from t=16 met by t=46. Slope -2.0 should NOT bypass
        // (excursion is the same — first-alert flag still set), so suppression applies.
        handle(policy, ZoneStatus.ABOVE_ZONE, 19_000L, events, hrSlopeBpmPerMin = -2.0f)     // direction flip register
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)     // cooldown met but suppressed by slope
        assertEquals(1, events.size)
    }

    @Test
    fun `IN_ZONE sustained at grace boundary clears excursion state`() {
        // When IN_ZONE is held for the full grace duration, the excursion is genuinely over —
        // hasAlertedThisExcursion clears, and the next ABOVE alert is once again a "first alert"
        // that bypasses slope-suppression.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, hrSlopeBpmPerMin = 0f)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, hrSlopeBpmPerMin = 0f)        // first fires
        assertEquals(1, events.size)
        // Hold IN_ZONE for 4s — grace (3s) elapses, full reset.
        handle(policy, ZoneStatus.IN_ZONE, 17_000L, events)                                  // grace start
        handle(policy, ZoneStatus.IN_ZONE, 21_000L, events)                                  // grace elapsed
        // New excursion begins. Cooldown from the prior fire (t=16) is met by t=46. First
        // alert of THIS excursion should bypass slope.
        handle(policy, ZoneStatus.ABOVE_ZONE, 22_000L, events, hrSlopeBpmPerMin = -2.0f)     // register
        handle(policy, ZoneStatus.ABOVE_ZONE, 46_000L, events, hrSlopeBpmPerMin = -2.0f)     // first-of-new-excursion → fires
        assertEquals(2, events.size)
    }

    @Test
    fun `onResetEscalation NOT called during grace window`() {
        // Audio escalation tier should NOT reset on a 1-tick IN_ZONE blip — otherwise a runner
        // oscillating at threshold drops back to tier-1 (earcon-only) every blip and never
        // reaches the more aggressive tiers when the problem is sustained.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        var resetCount = 0
        val onReset = { resetCount += 1 }

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, onResetEscalation = onReset)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, onResetEscalation = onReset)  // fires
        assertEquals(0, resetCount)

        // Brief blip into zone — under grace, no reset.
        handle(policy, ZoneStatus.IN_ZONE, 17_000L, events, onResetEscalation = onReset)
        handle(policy, ZoneStatus.IN_ZONE, 19_000L, events, onResetEscalation = onReset)     // 2s into IN_ZONE
        assertEquals(0, resetCount)

        // Sustained 4s — grace elapsed, reset fires once.
        handle(policy, ZoneStatus.IN_ZONE, 21_000L, events, onResetEscalation = onReset)
        assertEquals(1, resetCount)

        // Subsequent IN_ZONE ticks after reset don't double-fire (inZoneSince was zeroed by
        // the reset, so a new grace window restarts; the next reset would only fire after
        // ANOTHER 3s of IN_ZONE, which we don't simulate here).
        handle(policy, ZoneStatus.IN_ZONE, 22_000L, events, onResetEscalation = onReset)
        assertEquals(1, resetCount)
    }

    @Test
    fun `out-of-zone tick clears partial grace window`() {
        // If the runner enters IN_ZONE briefly then drops back ABOVE before grace elapses,
        // the partial grace window is invalidated. A subsequent IN_ZONE tick starts a fresh
        // grace timer from zero, NOT from the previous partial accumulation.
        val policy = AlertPolicy()
        val events = mutableListOf<CoachingEvent>()
        var resetCount = 0
        val onReset = { resetCount += 1 }

        handle(policy, ZoneStatus.ABOVE_ZONE, 1_000L, events, onResetEscalation = onReset)
        handle(policy, ZoneStatus.ABOVE_ZONE, 16_000L, events, onResetEscalation = onReset)  // fires
        // Enter IN_ZONE for 2s (grace-1s remaining)
        handle(policy, ZoneStatus.IN_ZONE, 17_000L, events, onResetEscalation = onReset)
        handle(policy, ZoneStatus.IN_ZONE, 19_000L, events, onResetEscalation = onReset)
        // Drop back ABOVE — partial window invalidated
        handle(policy, ZoneStatus.ABOVE_ZONE, 20_000L, events, onResetEscalation = onReset)
        // Re-enter IN_ZONE for only 2s — would have triggered reset if partial accumulated.
        handle(policy, ZoneStatus.IN_ZONE, 21_000L, events, onResetEscalation = onReset)
        handle(policy, ZoneStatus.IN_ZONE, 23_000L, events, onResetEscalation = onReset)
        assertEquals(0, resetCount)
        // 4s sustained from t=21 → grace elapses at t=24
        handle(policy, ZoneStatus.IN_ZONE, 25_000L, events, onResetEscalation = onReset)
        assertEquals(1, resetCount)
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
