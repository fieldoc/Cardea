package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.ZoneStatus

/**
 * Reactive zone-alert timing.
 *
 * Fires SPEED_UP / SLOW_DOWN after [alertDelaySec] continuous seconds out-of-zone, then
 * [alertCooldownSec] between repeats. Cooldown persists across direction flips (ABOVE→BELOW)
 * to prevent spam at the threshold.
 *
 * Two suppression gates are layered on top:
 *  1. **Self-correction** — if HR is moving back toward zone at a clearly intentional rate
 *     (|slope| ≥ SELF_CORRECTION_THRESHOLD_BPM_PER_MIN), suppress the alert because the runner
 *     is already doing the right thing. Pairing SLOW_DOWN earcon + "HR falling" voice felt
 *     contradictory to runners, which is the original complaint that led to this gate.
 *  2. **Walk break** — if pace exceeds WALKING_PACE_MIN_PER_KM for at least
 *     WALKING_SUSTAINED_MS, suppress SPEED_UP (but not SLOW_DOWN — above-zone during a walk
 *     is still a real safety signal). Auto-pause may not trigger for brisk walks, so users
 *     doing walk-runs or traffic-light stops would otherwise get nagged to speed up.
 *
 *     **Auto-pause interaction:** AlertPolicy is only reached when `!isAutoPaused` (see
 *     WorkoutForegroundService.processTick). So the walk gate only matters when auto-pause
 *     is either (a) disabled by the user, or (b) enabled but hasn't triggered yet (detection
 *     is stop-based, not pace-based, so brisk walks don't trip it). In both cases the walk
 *     gate is additive and non-conflicting: if auto-pause kicks in later, the entire alert
 *     path is suppressed anyway.
 *
 * Both gates respect the existing cooldown — the alert is fully suppressed for this tick, and
 * AlertPolicy's timer moves on. The next alert window reopens after [alertCooldownSec].
 */
class AlertPolicy {
    private var outOfZoneSince: Long = 0L
    private var lastAlertTime: Long = 0L
    private var lastOutOfZoneStatus: ZoneStatus? = null

    // Walk-break detection state — set to nowMs the first tick pace crosses the walking
    // threshold; reset to 0L only when pace is KNOWN not to be walking. Null pace (GPS
    // dropout) preserves the timer. SPEED_UP is suppressed only when the condition has HELD
    // for WALKING_SUSTAINED_MS, so a single slow sample doesn't silence a real alert.
    private var walkingSince: Long = 0L

    fun reset() {
        outOfZoneSince = 0L
        lastAlertTime = 0L
        lastOutOfZoneStatus = null
        walkingSince = 0L
    }

    fun handle(
        status: ZoneStatus,
        nowMs: Long,
        alertDelaySec: Int,
        alertCooldownSec: Int,
        guidanceText: String,
        onResetEscalation: () -> Unit,
        onAlert: (CoachingEvent, String) -> Unit,
        // Added 2026-04-17. Defaults preserve existing test behavior (no suppression) when
        // callers haven't been updated. Production caller (WFS) threads real values through.
        hrSlopeBpmPerMin: Float = 0f,
        currentPaceMinPerKm: Float? = null
    ) {
        if (status == ZoneStatus.IN_ZONE || status == ZoneStatus.NO_DATA) {
            outOfZoneSince = 0L
            lastOutOfZoneStatus = null
            walkingSince = 0L
            onResetEscalation()
            return
        }

        // Walk-break observational tracking — runs on EVERY out-of-zone tick (even the first,
        // before the status-flip early-return, and during the delay/cooldown windows), so the
        // "30s sustained" check measures from when walking actually began. Null pace (GPS
        // dropout in tunnels / under trees / phone in back pocket) holds the timer as-is:
        // a dropout doesn't change whether the runner is walking, and resetting on null would
        // make the 30s counter restart every GPS glitch.
        val paceKnown = currentPaceMinPerKm != null
        val isWalkingPace = paceKnown && currentPaceMinPerKm!! >= WALKING_PACE_MIN_PER_KM
        if (isWalkingPace) {
            if (walkingSince == 0L) walkingSince = nowMs
        } else if (paceKnown) {
            // Only reset when pace is KNOWN to not be walking. Null (unknown) preserves state.
            walkingSince = 0L
        }
        val walkingSustained = walkingSince > 0L && (nowMs - walkingSince) >= WALKING_SUSTAINED_MS

        if (status != lastOutOfZoneStatus) {
            lastOutOfZoneStatus = status
            outOfZoneSince = nowMs
            // Keep lastAlertTime: the cooldown from the previous alert still applies after
            // a direction flip, preventing rapid alert spam when HR oscillates at the threshold.
            return
        }

        if (nowMs - outOfZoneSince < alertDelaySec * 1_000L) return
        if (lastAlertTime > 0L && nowMs - lastAlertTime < alertCooldownSec * 1_000L) return

        when (status) {
            ZoneStatus.ABOVE_ZONE -> {
                // Suppress SLOW_DOWN if HR is already dropping fast enough to self-correct.
                // TUNING: SELF_CORRECTION_THRESHOLD_BPM_PER_MIN = 1.5 (not 0.8, the buildGuidance
                // phrasing threshold). The phrasing threshold can be tripped by EMA residual for
                // 10–15s after a reversal; the alert-suppression threshold is stricter so we only
                // silence the nag when the runner is unambiguously correcting.
                if (hrSlopeBpmPerMin <= -SELF_CORRECTION_THRESHOLD_BPM_PER_MIN) {
                    // DEBOUNCE: advance lastAlertTime on suppression so the next alert window
                    // reopens after cooldownSec, NOT on the very next tick if slope eases. Without
                    // this, sustained self-correction silenced alerts forever (while slope stayed
                    // fast), and the moment slope eased an alert fired with no cooldown gap from
                    // the prior cycle. Now: every cooldownSec we re-evaluate — if still correcting,
                    // suppress again; if not, alert.
                    lastAlertTime = nowMs
                    return
                }
                onAlert(CoachingEvent.SLOW_DOWN, guidanceText)
            }
            ZoneStatus.BELOW_ZONE -> {
                // Suppress SPEED_UP if HR is already rising fast enough (mirror of ABOVE).
                if (hrSlopeBpmPerMin >= SELF_CORRECTION_THRESHOLD_BPM_PER_MIN) {
                    lastAlertTime = nowMs  // debounce — see ABOVE branch
                    return
                }
                // Suppress SPEED_UP during sustained walking. Intentional — user is walking by
                // choice (walk-run, traffic lights, cool-down) and doesn't need coaching to
                // speed up. SLOW_DOWN during walking stays active because above-zone while
                // walking is a genuine safety signal (overheating, cardiac issue, etc.).
                if (walkingSustained) {
                    lastAlertTime = nowMs  // debounce — see ABOVE branch
                    return
                }
                onAlert(CoachingEvent.SPEED_UP, guidanceText)
            }
            else -> Unit
        }
        lastAlertTime = nowMs
    }

    companion object {
        // TUNING: raise to 2.0 for more conservative suppression (alerts more often, fewer
        // contradictions avoided); lower to 1.0 for more aggressive suppression (fewer alerts
        // during self-correction, some real drift may be missed).
        const val SELF_CORRECTION_THRESHOLD_BPM_PER_MIN: Float = 1.5f

        // Walking = pace at or slower than 10 min/km (6 km/h, ~3.7 mph). A brisk walk is
        // ~5 km/h (12 min/km), a fast walk is ~6 km/h (10 min/km). Using `>=` at the boundary
        // includes fast walkers exactly at 10 min/km. Jogs are ~6-8 min/km and stay active.
        // TUNING: raise to 12f to only catch slow/stop walks.
        const val WALKING_PACE_MIN_PER_KM: Float = 10f

        // How long pace must stay above the walking threshold before SPEED_UP is suppressed.
        // Prevents a single slow-pace sample (GPS glitch, brief pause) from silencing a real
        // below-zone alert. TUNING: 30s matches the default alertDelaySec — a runner who genuinely
        // slows to a walk for 30s has made a choice, not a mistake.
        const val WALKING_SUSTAINED_MS: Long = 30_000L
    }
}
