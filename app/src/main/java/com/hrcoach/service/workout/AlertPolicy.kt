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
 * Suppression gates layered on top:
 *  1. **Self-correction** (slope) — if HR is moving back toward zone at a clearly intentional
 *     rate (|slope| ≥ SELF_CORRECTION_THRESHOLD_BPM_PER_MIN), suppress the alert because the
 *     runner is already doing the right thing. Pairing SLOW_DOWN earcon + "HR falling" voice
 *     felt contradictory to runners, which is the original complaint that led to this gate.
 *  2. **Walk break** — if pace exceeds WALKING_PACE_MIN_PER_KM for at least
 *     WALKING_SUSTAINED_MS, suppress SPEED_UP (but not SLOW_DOWN — above-zone during a walk
 *     is still a real safety signal). Auto-pause may not trigger for brisk walks, so users
 *     doing walk-runs or traffic-light stops would otherwise get nagged to speed up.
 *  3. **Warmup grace** — if [elapsedSeconds] < [warmupGraceSec], suppress SPEED_UP only
 *     (above-zone SLOW_DOWN still fires). The runner is naturally below zone for the first
 *     1–2 minutes as HR climbs from rest; coaching them to "speed up" mid-warmup is
 *     counterproductive. This gate ALSO holds outOfZoneSince at the warmup boundary so the
 *     normal alertDelaySec debounce starts ticking from when warmup ends, not from second 0
 *     (a runner who's still below zone exactly at warmup end shouldn't get an alert on the
 *     same tick).
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
 *
 * **SLOW_DOWN-only bypasses (added 2026-04-26):**
 *  - **First-alert per excursion** — slope-suppression is skipped for the FIRST SLOW_DOWN of
 *    each excursion. Rationale: the slope gate assumes the runner is correcting *because they
 *    were told*. Until the first alert, any falling slope is coincidental, not corrective.
 *    Real-run failure mode that triggered this: 13 minutes above zone, hr 162→173, no SLOW_DOWN
 *    fired because slope was −2 to −4 the whole time.
 *  - **Force-fire after 2× cooldown** — if SLOW_DOWN keeps getting slope-suppressed while the
 *    runner stays above zone, fire one anyway every (FORCE_REALERT_COOLDOWN_FACTOR × cooldown)
 *    of silence. Sustained out-of-zone shouldn't go silent forever.
 *  - **IN_ZONE grace (IN_ZONE_GRACE_SEC)** — a brief dip into zone (≤3s) doesn't end the
 *    excursion; the next ABOVE tick is still the same excursion (preserves the "first alert
 *    already fired" flag and the audio escalation tier — `onResetEscalation` is held until
 *    grace fully elapses). Without grace, a 1-tick oscillation would re-arm first-alert
 *    bypass on every tick and reset audio aggressiveness mid-problem.
 *
 * SPEED_UP is intentionally NOT given these bypasses. Above-zone is always a real overshoot;
 * below-zone during walk or warmup is expected behavior (warmup's purpose is to climb up to
 * zone). The asymmetry is principled, not an oversight.
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

    // SLOW_DOWN bypass state (added 2026-04-26). All three are SLOW_DOWN-only — SPEED_UP
    // does not consult them.
    //
    // hasAlertedThisExcursion: flips true on the FIRST onAlert(SLOW_DOWN) of an excursion.
    //   Clears only after IN_ZONE has been sustained for IN_ZONE_GRACE_SEC. While true,
    //   slope-suppression applies normally; while false, slope-suppression is bypassed.
    //
    // inZoneSince: timestamp of the first IN_ZONE/NO_DATA tick of the current grace window;
    //   0L when out-of-zone or when grace has fully elapsed and state was reset. Out-of-zone
    //   ticks zero this so a future IN_ZONE entry starts a fresh grace window.
    //
    // lastFiredAlertTimeMs: distinct from lastAlertTime — only updated when onAlert is
    //   actually invoked. Drives the 2×-cooldown force-fire timer; using lastAlertTime would
    //   reset the window every time slope-suppression debounces, defeating the safety net.
    private var hasAlertedThisExcursion: Boolean = false
    private var inZoneSince: Long = 0L
    private var lastFiredAlertTimeMs: Long = 0L

    fun reset() {
        outOfZoneSince = 0L
        lastAlertTime = 0L
        lastOutOfZoneStatus = null
        walkingSince = 0L
        hasAlertedThisExcursion = false
        inZoneSince = 0L
        lastFiredAlertTimeMs = 0L
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
        currentPaceMinPerKm: Float? = null,
        // Added 2026-04-24. Warmup grace gates SPEED_UP only (BELOW_ZONE). Defaults preserve
        // existing test behavior — elapsedSeconds defaults to a large value so warmup is never
        // "active" unless callers thread it through.
        elapsedSeconds: Long = Long.MAX_VALUE,
        warmupGraceSec: Int = 0,
        // Added 2026-04-26. IN_ZONE grace window — see class kdoc. Default matches the
        // companion constant so callers don't have to thread a value; tests override.
        inZoneGraceSec: Int = IN_ZONE_GRACE_SEC
    ) {
        if (status == ZoneStatus.IN_ZONE || status == ZoneStatus.NO_DATA) {
            // Grace gate: a brief IN_ZONE blip is treated as still-in-excursion. Only after
            // HR has been IN_ZONE for inZoneGraceSec do we fully reset state (including
            // hasAlertedThisExcursion, walkingSince, and the audio escalation tier via
            // onResetEscalation). Pre-2026-04-26 this branch reset everything on the very
            // first IN_ZONE tick — which let a 1-tick oscillation (a) re-trigger the
            // first-alert bypass repeatedly and (b) drop audio back to tier-1 mid-problem.
            if (inZoneSince == 0L) inZoneSince = nowMs
            val graceMs = inZoneGraceSec * 1_000L
            if (nowMs - inZoneSince >= graceMs) {
                outOfZoneSince = 0L
                lastOutOfZoneStatus = null
                walkingSince = 0L
                hasAlertedThisExcursion = false
                lastFiredAlertTimeMs = 0L
                inZoneSince = 0L
                onResetEscalation()
            }
            // lastAlertTime intentionally preserved either way — cooldown still carries
            // across true zone returns, matching the existing direction-flip property.
            return
        }
        // Out-of-zone tick: any pending grace window is invalidated. The next IN_ZONE entry
        // starts a fresh grace timer.
        inZoneSince = 0L

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
                //
                // Two SLOW_DOWN-only bypasses (2026-04-26) skip slope-suppression:
                //  (a) FIRST alert of the excursion — runner can't be intentionally correcting
                //      something they haven't been told about; coincidental falling slope
                //      shouldn't hide the cue.
                //  (b) FORCE-FIRE after 2× cooldown of silence — sustained above-zone shouldn't
                //      go silent indefinitely just because slope keeps trending down.
                val cooldownMs = alertCooldownSec * 1_000L
                val firstAlertBypass = !hasAlertedThisExcursion
                val forceFireBypass = lastFiredAlertTimeMs > 0L &&
                    (nowMs - lastFiredAlertTimeMs) >= FORCE_REALERT_COOLDOWN_FACTOR * cooldownMs
                val slopeBypass = firstAlertBypass || forceFireBypass

                if (hrSlopeBpmPerMin <= -SELF_CORRECTION_THRESHOLD_BPM_PER_MIN && !slopeBypass) {
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
                hasAlertedThisExcursion = true
                lastFiredAlertTimeMs = nowMs
            }
            ZoneStatus.BELOW_ZONE -> {
                // Suppress SPEED_UP if HR is already rising fast enough (mirror of ABOVE).
                // No first-alert bypass and no force-fire here: warmup's purpose is to climb
                // up to zone, so below-zone during it is the expected/intended state, and a
                // walk is a deliberate user choice. ABOVE-zone is a genuine overshoot that
                // SLOW_DOWN already lacks these gates for — the asymmetry is principled.
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
                // Warmup grace: HR climbs from rest naturally; suppress SPEED_UP for the first
                // [warmupGraceSec] seconds. Hold outOfZoneSince at the warmup boundary so the
                // normal alertDelaySec debounce starts from when warmup ends, not from second 0.
                // We do NOT advance lastAlertTime here — there's been no actual alert yet, so
                // the alertCooldownSec gate has nothing to track.
                if (elapsedSeconds < warmupGraceSec) {
                    val warmupEndMs = nowMs + (warmupGraceSec - elapsedSeconds) * 1_000L
                    if (outOfZoneSince < warmupEndMs) outOfZoneSince = warmupEndMs
                    return
                }
                onAlert(CoachingEvent.SPEED_UP, guidanceText)
                lastFiredAlertTimeMs = nowMs
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

        // How long HR must remain IN_ZONE / NO_DATA before AlertPolicy considers the excursion
        // over and resets the per-excursion bypass state. Set short so the system feels
        // responsive when the runner does genuinely return; long enough to absorb a 1-tick
        // oscillation around the threshold. TUNING: raise to 5–10s if oscillating runners get
        // re-triggered too often; drop below 3s only if recoveries feel slow.
        const val IN_ZONE_GRACE_SEC: Int = 3

        // Multiplier on alertCooldownSec for the SLOW_DOWN duration safety-net. After the first
        // SLOW_DOWN of an excursion fires, slope-suppression silences subsequent alerts; once
        // (factor × cooldown) has elapsed since the last fired alert, force-fire one regardless
        // of slope. TUNING: 2 means a sustained-above runner hears one alert per ~60s instead
        // of going silent; raise for less interruption, lower for more frequent reminders.
        const val FORCE_REALERT_COOLDOWN_FACTOR: Int = 2
    }
}
