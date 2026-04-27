# AlertPolicy: first-alert bypass + IN_ZONE grace + 2× force-fire (SLOW_DOWN)
Date: 2026-04-26

## Intent
- The current SLOW_DOWN self-correction suppression assumes the runner is correcting *because they were told*. In practice a runner who hasn't been alerted yet can have a coincidental falling slope, get suppressed, and stay above zone unnoticed (real run: 13 min above zone, first SLOW_DOWN at +1073s).
- **Rule 1 — first SLOW_DOWN per excursion bypasses slope-suppression.** Information the runner doesn't yet have can't be coaching them to do anything.
- **Rule 2 — 3 s IN_ZONE grace.** A 1–2 tick blip into zone doesn't end the excursion; the next ABOVE tick is still the same excursion (preserves the "first alert already fired" flag and audio escalation).
- **Rule 3 — force-fire SLOW_DOWN every 2× cooldown of suppressed slope.** Sustained out-of-zone shouldn't go silent forever just because slope keeps trending down.
- **SPEED_UP is unchanged.** Above zone always means the runner has overshot and should ease off; below-zone during warmup or walking is the expected/intended state (warmup's purpose is to climb up to zone). All existing SPEED_UP gates (slope, walk, warmup) stay active and the first-alert bypass does NOT apply.

## Scope boundary
**In:**
- `AlertPolicy.handle()` state machine
- New companion constants for grace + force-fire factor
- New unit tests covering the three rules and IN_ZONE grace edges
- WFS call site only if signature changes (it shouldn't — grace stays internal default)

**Out:**
- User-configurable grace duration (hardcoded 3 s for now; revisit if real users complain)
- Magnitude-based override (e.g. ">10 bpm over → never suppress") — can layer later if 2× force-fire isn't enough
- TTS Debug Logger format changes — existing FIRE/EARCON lines already capture slope and bypass timing; we infer "first alert" from the absence of prior FIRE in the excursion
- `CoachingEventRouter`, `CoachingAudioManager`, audio escalation tier behaviour
- BLE / GPS / WFS lifecycle / Room / cloud sync

## Files to touch
- `app/src/main/java/com/hrcoach/service/workout/AlertPolicy.kt` — add three state fields (`hasAlertedThisExcursion`, `inZoneSince`, `lastFiredAlertTimeMs`), grace-aware reset, SLOW_DOWN bypass logic, force-fire, two new companion constants
- `app/src/test/java/com/hrcoach/service/workout/AlertPolicyTest.kt` — add 8 new tests; verify existing tests still pass

## CLAUDE.md / rule files honored
- **`audio-pipeline.md` "AlertPolicy.handle() only runs when !isAutoPaused" (line 11):** all new logic still gated behind WFS's auto-pause guard. No change to that contract.
- **`audio-pipeline.md` "After any alertPolicy.onAlert, call coachingEventRouter.noteExternalAlert" (line 9):** WFS already does this on every onAlert callback; force-fire path goes through the same callback so the bridge is preserved.
- **`audio-pipeline.md` escalation reset coupling:** `onResetEscalation` is called only after grace fully elapses, NOT on the first IN_ZONE tick. A 3 s blip preserves audio escalation tier — matches the principle that the excursion isn't really over.
- **CLAUDE.md "DataStore / Slider":** N/A (no UI).
- **`adb-data-backup.md`:** N/A (no schema change).

## Behaviour spec (precise semantics)

### State (AlertPolicy fields, additive)
```kotlin
private var hasAlertedThisExcursion: Boolean = false  // SLOW_DOWN-only bypass key
private var inZoneSince: Long = 0L                    // first IN_ZONE tick of current grace window; 0L = not in grace
private var lastFiredAlertTimeMs: Long = 0L           // last tick where onAlert actually invoked (NOT debounce-only)
```

`reset()` clears all three.

### IN_ZONE / NO_DATA branch (new flow)
```
if (status == IN_ZONE || status == NO_DATA) {
    if (inZoneSince == 0L) inZoneSince = nowMs
    val graceMs = inZoneGraceSec * 1000L
    if (nowMs - inZoneSince >= graceMs) {
        // Full reset — excursion is genuinely over.
        outOfZoneSince = 0L
        lastOutOfZoneStatus = null
        walkingSince = 0L
        hasAlertedThisExcursion = false
        lastFiredAlertTimeMs = 0L
        inZoneSince = 0L
        onResetEscalation()
    }
    return
}
// Out-of-zone tick: clear inZoneSince (we left zone before grace elapsed, or never entered)
inZoneSince = 0L
```

`lastAlertTime` is intentionally NOT cleared on grace-elapsed reset — cooldown still carries across true zone returns, matching today's "cooldown persists across direction flips" property.

### ABOVE_ZONE branch (modified)
```
val cooldownMs = alertCooldownSec * 1000L
val canBypassSlopeFirstAlert = !hasAlertedThisExcursion
val canBypassSlopeForceFire =
    lastFiredAlertTimeMs > 0L &&
    (nowMs - lastFiredAlertTimeMs) >= 2 * cooldownMs
val bypass = canBypassSlopeFirstAlert || canBypassSlopeForceFire

if (hrSlopeBpmPerMin <= -SELF_CORRECTION_THRESHOLD_BPM_PER_MIN && !bypass) {
    lastAlertTime = nowMs   // existing debounce
    return
}
onAlert(SLOW_DOWN, guidanceText)
hasAlertedThisExcursion = true
lastFiredAlertTimeMs = nowMs
```

### BELOW_ZONE branch (UNCHANGED)
Slope check, walk check, warmup check, alert. Crucially: no first-alert bypass and no 2× force-fire. Walk/warmup gates may suppress the very first SPEED_UP — this is intentional: warmup is *about* climbing toward the zone, so below-zone is the expected state during it; walking is a deliberate user choice. Above-zone in either context is a genuine overshoot the runner needs to know about, which is why SLOW_DOWN already lacks those gates.

### `lastAlertTime` semantics
Unchanged: still updated on every tick that passes delay+cooldown gates regardless of fire/suppress, ensuring the existing 30 s spam-prevention property holds.

### Constants (companion, both `// TUNING:` documented)
```kotlin
const val IN_ZONE_GRACE_SEC: Int = 3
const val FORCE_REALERT_COOLDOWN_FACTOR: Int = 2
```

### `handle()` parameter shape
Add `inZoneGraceSec: Int = IN_ZONE_GRACE_SEC` as a defaulted param (mirrors `warmupGraceSec` pattern for testability). WFS call site does NOT need to change — default applies.

## Tests (AlertPolicyTest.kt additions)

**SLOW_DOWN first-alert bypass:**
1. `first SLOW_DOWN fires despite fast-falling slope` — start ABOVE_ZONE with slope=−2.0, expect alert at delay-met tick (today: suppressed).
2. `second SLOW_DOWN suppression resumes after first fires` — same setup, advance past first fire, then verify slope=−2.0 suppresses subsequent ones.

**2× force-fire:**
3. `slope-suppressed SLOW_DOWN force-fires after 2x cooldown` — fire first, then keep slope=−2.0 across two cooldown windows, verify alert at t=delay+2×cooldown despite slope.
4. `force-fire only kicks in when slope-suppressed` — if slope eases to −1.0 after first fire, normal 1× cooldown cadence resumes; no extra force-fire.

**IN_ZONE grace:**
5. `IN_ZONE blip under grace preserves first-alert flag` — fire SLOW_DOWN, drop to IN_ZONE for 2 s, return to ABOVE — next alert is NOT a "first alert" (slope-suppression applies).
6. `IN_ZONE sustained ≥ grace clears state` — fire SLOW_DOWN, IN_ZONE for 4 s, return to ABOVE — next alert IS a first alert (slope-suppression bypassed).
7. `onResetEscalation NOT called during grace window` — verify callback count: 0 calls during grace, 1 call after grace elapsed.

**SPEED_UP unchanged (regression):**
8. `first SPEED_UP still suppressed by fast-rising slope` — explicit asymmetry test: BELOW_ZONE with slope=+2.0, no alert fires (today's behavior preserved).
9. (existing) walk-break + warmup tests must pass unmodified.

**Edge:**
10. `direction flip ABOVE → BELOW → ABOVE within grace` — flip is NOT through IN_ZONE, so grace logic doesn't engage; cooldown carries as today.

## Risk flags
- **Touches `service/workout/`** — yes, but only `AlertPolicy.kt`. Doesn't enter the WFS pause-race / state-update areas. Same-tick race rule (`WorkoutState.snapshot.value` vs `update {}`) doesn't apply: AlertPolicy doesn't touch WorkoutState.
- **Audio pipeline** — `onResetEscalation` timing changes (delayed by grace). New behavior: a 3 s IN_ZONE blip no longer drops audio back to tier-1 mid-excursion. Audit: `CoachingAudioManager.resetEscalation()` should be safe to NOT-call for 3 s; verify it doesn't have a watchdog that requires periodic invocation.
- **Existing tests** — `cooldown is preserved across zone direction flip` (line 38) goes ABOVE→BELOW→ABOVE without IN_ZONE; grace logic doesn't engage; should pass unchanged.
- **TTS Debug Log** — no schema change; the FIRE entries naturally show whether slope-suppression was bypassed (alert fires when previously it wouldn't have).

## Verification plan (Phase 5)
- **Layer 0** grep diff for new TODOs / Log.d / hex
- **Layer 1** `./gradlew testDebugUnitTest --tests "com.hrcoach.service.workout.AlertPolicyTest"`
- **Layer 2** Haiku diff review (duplicate helpers, dead branches, test realism)
- **Layer 3** Sonnet review — touches `service/workout/`, so warranted
- **Layer 4** Skip — no schema, no cloud, no auth
- **Layer 5** `./gradlew assembleDebug`
- **Layer 6** Real-device sim run via `SimulationController` — script: zone-target = 147, ramp ABOVE for 90 s with falling slope, expect first SLOW_DOWN at delay-met (~15 s), no alerts in next 60 s, force-fire at ~75 s. Pull TTS debug log and confirm.

## Reporting back
Behavior summary draft: "First SLOW_DOWN now fires every excursion regardless of how fast HR is falling; suppression only kicks in for follow-ups. If you stay above zone with falling HR, you'll get a re-alert about every 60 s instead of going silent. SPEED_UP behavior unchanged."
