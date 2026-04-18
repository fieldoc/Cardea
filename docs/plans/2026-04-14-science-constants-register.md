# Science Constants Register

**Purpose:** Every physiological constant, coefficient, threshold, or formula used in Cardea's adaptive engine must be listed here with its provenance. This is the anti-drift measure for AI-written exercise science code.

**Rule for agents:** If you introduce, change, or move a science constant, you MUST add or update its entry in this register in the same commit. If the constant has no provenance, label it `STATUS: unsourced` — do not invent a citation.

**Audit rule:** Any constant found in the engine files below without a matching register entry is AI drift and must be either sourced or removed.

---

## Format

Each entry:

```
### <constant name>
- **File:** path:line
- **Value:** current value (with units)
- **Source:** one of {published, internal-rationale, intentional-non-standard, unsourced}
- **Citation / commit:** URL, paper, or commit SHA where the decision was made
- **Effect if wrong:** one sentence on what the runner experiences if this value drifts
- **Last reviewed:** YYYY-MM-DD
```

Valid `Source` values:

- **published** — From a named paper, textbook, or standard (Friel, Seiler, Tanaka, Bannister, Karvonen). Citation required.
- **internal-rationale** — Chosen by the human project owner, documented in a design doc or CLAUDE.md. Citation is the doc path or commit SHA.
- **intentional-non-standard** — Deliberately diverges from published values. Requires a rationale in the `Effect if wrong` field explaining why the divergence is correct for Cardea.
- **unsourced** — AI agent picked the value with no decision trail. This is the only failure state. Entries in this state block promotion of the containing feature to "stable."

---

## Register entries

### HRmax fallback formula
- **File:** WorkoutForegroundService.kt (fallback), OnboardingViewModel.kt
- **Value:** `220 − age`
- **Source:** published
- **Citation:** Fox SM (1971). *Physical activity and the prevention of coronary heart disease*. Bulletin of the New York Academy of Medicine 44:950-967.
- **Effect if wrong:** Zones drift by ~10 bpm for users under 25 and over 55 (Fox systematically overestimates for young, underestimates for old). Known limitation — Tanaka `208 − 0.7 × age` is more accurate but less intuitive. Kept because users recognise the formula.
- **Last reviewed:** 2026-04-14

### TRIMP per-session load formula
- **File:** MetricsCalculator.trimpFrom (formula); WorkoutForegroundService.kt ~768 (invocation)
- **Value:** `durationMin × avgHr × (avgHr / HRmax)²`
- **Source:** intentional-non-standard
- **Citation:** CLAUDE.md "Adaptive Engine Invariants" section; commit 21e9f2c
- **Effect if wrong:** CTL/ATL scales drift, affecting TSB-based EASE_BACK/PUSH_HARDER recommendations. Current formula intentionally simpler than Bannister's `duration × ΔHRratio × e^(b×ΔHRratio)` — reviewed and accepted as good-enough for recreational use.
- **Architecture:** the formula lives in `MetricsCalculator.trimpFrom()` (single named home, testable). WFS is the authoritative *invoker* because only it has access to active-run-time duration (pauses subtracted). Moved 2026-04-14 from an inline WFS fallback branch (which was always hit because `MetricsCalculator.deriveFromPaceSamples` didn't set `trimpScore` — the "primary path" in the original design never existed). Pace samples cannot compute TRIMP alone because they don't carry pause-boundary information.
- **Last reviewed:** 2026-04-14

### CTL/ATL time constants
- **File:** FitnessLoadCalculator.kt
- **Value:** CTL = 42 days, ATL = 7 days
- **Source:** published
- **Citation:** Bannister EW (1991). *Modeling elite athletic performance*. In: Physiological Testing of Elite Athletes. Human Kinetics.
- **Effect if wrong:** Time-scale of training-fitness vs training-fatigue tracking would misalign with established practice. 42/7 is the universal standard.
- **Last reviewed:** 2026-04-14

### Slope EMA weights
- **File:** AdaptivePaceController.kt (`AdaptiveTuningConfig`)
- **Value:** previous = 0.60, instant = 0.40
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"; rejected 0.75/0.25 (caused 24s lag on direction changes)
- **Effect if wrong:** Slope tracking lags behind actual HR direction changes, so predictive warnings fire during the wrong physiological state.
- **Last reviewed:** 2026-04-14

### hrSlopeBpmPerMin clamp
- **File:** AdaptivePaceController.kt (`AdaptiveTuningConfig.slopeSampleClampBpmPerMin`)
- **Value:** ±50 bpm/min
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"; raised from ±30 on 2026-04-13 because sprint-onset slopes legitimately hit 47 bpm/min.
- **Effect if wrong:** Real sprint-onset slopes get clipped, slope EMA under-reports rising HR, predictive warnings fire late.
- **Last reviewed:** 2026-04-14

### responseLagSec default
- **File:** AdaptiveProfile.kt
- **Value:** 38f (seconds)
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"; rejected 25f (makes `lag × 0.4f = 10.0f` hit the minimum clamp, inert horizon).
- **Effect if wrong:** Adaptive horizon becomes inert for new users; predictive coaching has nothing to project.
- **Last reviewed:** 2026-04-14

### Settle window cap
- **File:** AdaptivePaceController.kt
- **Value:** 2s..600s (10 min)
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"; raised from 5 min because interval excursions run 6–10 min.
- **Effect if wrong:** Settle-time data from long interval sessions silently discarded; responseLagSec never calibrates for interval runners.
- **Last reviewed:** 2026-04-14

### TRIMP durationMin basis
- **File:** WorkoutForegroundService.kt
- **Value:** active run time (`now − workoutStartMs − totalPausedMs − totalAutoPausedMs`)
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"
- **Effect if wrong:** Wall-clock time overstates training load proportional to pause fraction.
- **Last reviewed:** 2026-04-14

### lookupPaceBias neighbour averaging
- **File:** AdaptivePaceController.kt
- **Value:** sample-count-weighted mean over up to 3 neighbours
- **Source:** internal-rationale
- **Citation:** CLAUDE.md "Adaptive Engine Invariants"
- **Effect if wrong:** A 1-sample anomalous bucket gets equal vote with a 500-sample baseline; pace bias estimate distorted by outliers.
- **Last reviewed:** 2026-04-14

### EF trend threshold
- **File:** FitnessSignalEvaluator.kt:22
- **Value:** `EF_RISE_THRESHOLD = 0.04f`
- **Source:** intentional-non-standard
- **Citation:** Set at 0.04 after 2026-04-11 regression math change (slope scaled to total span, not endpoint delta); unchanged value, new math.
- **Effect if wrong:** Too low → PUSH_HARDER fires on noise. Too high → fit users never triggered to push.
- **Last reviewed:** 2026-04-14

### TSB fatigue / freshness thresholds
- **File:** FitnessSignalEvaluator.kt:20-42
- **Value:** `TSB_EASE_THRESHOLD = -25f`, `TSB_PUSH_THRESHOLD = 5f`
- **Source:** published
- **Citation:** Friel, *The Training Bible* (4th ed., ch. 12) "Performance Management Chart"; Coggan TSS/PMC model. +5 maps to the lower edge of Friel's "fresh enough for quality" window; -25 is a conservative mid-point in Friel's "overreached/transition" zone.
- **Effect if wrong:** PUSH too high → fit users never triggered. PUSH too low → aggressive pushing through fatigue. EASE too high (i.e. less negative) → unnecessary easy recommendations. EASE too low → users overreach undetected. Note PUSH is half of a **conjunctive gate** with `EF_RISE_THRESHOLD` — both must fire together, which dramatically softens the effective aggressiveness of +5.
- **Action:** audit 2026-04-14 originally flagged these as aggressive; re-verification showed the conjunctive EF-trend gate is the actual filter. Values kept as-is, citations added to code.
- **Last reviewed:** 2026-04-14

### EF trend reliability minimum sessions
- **File:** FitnessSignalEvaluator.kt:24
- **Value:** `MIN_RELIABLE_SESSIONS = 3`
- **Source:** internal-rationale
- **Citation:** Three points is the minimum for a least-squares slope that isn't degenerate.
- **Effect if wrong:** Too low → trend fires on noise. Too high → new users never get PUSH_HARDER credit.
- **Last reviewed:** 2026-04-14

### EF trend recency cutoff
- **File:** FitnessSignalEvaluator.kt:23
- **Value:** `RECENCY_CUTOFF_DAYS = 42`
- **Source:** internal-rationale
- **Citation:** Matches CTL time constant — same 42-day training-fitness window.
- **Effect if wrong:** Shorter window → trend chases recent noise. Longer → fitness improvements from old sessions still count after detraining.
- **Last reviewed:** 2026-04-14

### SubMax HRmax revision — evidence margin
- **File:** SubMaxHrEstimator.kt (EVIDENCE_MARGIN_BPM, UPWARD_STEP_BPM)
- **Value:** margin = 2 bpm, upward step = 1 bpm
- **Source:** internal-rationale
- **Citation:** Science audit 2026-04-14 replaced the previous effort-fraction bucket algorithm (see "SubMax effort-fraction buckets — REPLACED" below). Conservative values chosen so HRmax only revises when the runner has genuinely exceeded the current estimate.
- **Effect if wrong:** Margin too high → auto-calibration rarely fires. Margin too low → single-bpm flicker. Step too high → HRmax overshoots each qualifying run. Step too low → takes many runs to catch up.
- **Last reviewed:** 2026-04-14

### SubMax 2-minute rolling window + 50% sub-window fraction
- **File:** SubMaxHrEstimator.kt (ROLLING_WINDOW_MS, MIN_WINDOW_FRACTION)
- **Value:** window = 2 min, sub-window floor = 50% (= 1 min)
- **Source:** internal-rationale
- **Citation:** 2-min window rejects single-beat spikes (e.g., cadence lock artifacts). 50% sub-window fraction lets short interval peaks (30-60s) contribute without requiring 2 full minutes at peak.
- **Effect if wrong:** Longer window → short intervals never contribute. Shorter window → spikes dominate. Tighter sub-window → interval workouts silently excluded.
- **Last reviewed:** 2026-04-14

### SubMax effort-fraction buckets — REPLACED
- **File:** SubMaxHrEstimator.kt (historical, pre-2026-04-14)
- **Value:** `ratio ≥ 0.88 → 0.92; ratio ≥ 0.80 → 0.85; else → 0.75` (REMOVED)
- **Source:** **unsourced** — replaced 2026-04-14
- **Citation:** None.
- **Effect if wrong (historical):** Biased HRmax upward on moderate efforts. The lowest-ratio bucket produced the most aggressive revision (peak / 0.75 = peak × 1.33). Over a few weeks of moderate runs, HRmax drifted up by 10+ bpm; runner heard "speed up" at heart rates that used to be in-zone, chasing a phantom HRmax.
- **Resolution:** replaced by guarded upward-only rule (sustained peak must exceed current HRmax by EVIDENCE_MARGIN_BPM before revision fires). Commit: see 2026-04-14 science-fidelity audit fix.
- **Last reviewed:** 2026-04-14 (resolved)

### TierCtlRanges (all goals)
- **File:** TierCtlRanges.kt
- **Value:** Cardio 0-30/30-55/55-200; Race 0-35/35-65/65-200; Half 0-45/45-75/75-200; Marathon 0-55/55-90/90-200.
- **Source:** internal-rationale
- **Citation:** `docs/plans/2026-03-04-adaptive-bootcamp-design.md` "CTL-Based Tier Ranges" (updated 2026-04-14 to describe edge-case handling).
- **Effect if wrong:** A user's auto-promotion prompts fire at the wrong CTL. Too-narrow ranges thrash between tiers; too-wide ranges never promote.
- **Rationale for code/spec divergence:** Code uses 0-indexed tiers internally (mapping to `TierInfo` names Foundation/Development/Performance = spec's T1/T2/T3). Code extends the spec at both ends: Foundation floor = 0 (not spec-10) so new users with CTL < 10 have a valid starting tier; Performance ceiling = 200 (not spec-90) so the terminal tier has no "above tier" prompt. Both divergences were reviewed and documented 2026-04-14.
- **Last reviewed:** 2026-04-14

### AlertPolicy self-correction threshold
- **File:** `app/src/main/java/com/hrcoach/service/workout/AlertPolicy.kt` (`SELF_CORRECTION_THRESHOLD_BPM_PER_MIN`)
- **Value:** `1.5f` bpm/min
- **Source:** internal-rationale
- **Citation:** Code review 2026-04-17; in-code TUNING comment. Deliberately stricter than the buildGuidance phrasing threshold (0.8 bpm/min) because the phrasing threshold can be tripped by EMA residual for 10–15 s after HR reverses; the alert-suppression threshold must only fire when the runner is unambiguously correcting.
- **Effect if wrong:** Too low (<1.0): missed SPEED_UP/SLOW_DOWN alerts during EMA residual windows when HR is actually stable or reversing → runner stays drifted, no cue. Too high (>2.5): suppression rarely engages, runner keeps hearing contradictory "slow down" earcons when already slowing → original complaint re-emerges.
- **Debounce coupling:** when suppression triggers, `lastAlertTime = nowMs` is set, which means the next alert window opens `alertCooldownSec` later. Without that debounce, sustained suppression would either silence alerts for the entire above-zone period or fire back-to-back the instant slope eased.
- **Last reviewed:** 2026-04-17

### AlertPolicy walking-pace threshold
- **File:** `app/src/main/java/com/hrcoach/service/workout/AlertPolicy.kt` (`WALKING_PACE_MIN_PER_KM`)
- **Value:** `10f` min/km (≥, inclusive of fast-walk boundary at exactly 10 min/km ≈ 6 km/h)
- **Source:** internal-rationale
- **Citation:** Code review 2026-04-17; in-code TUNING comment. Based on published walking-pace norms: brisk walk ~5 km/h (12 min/km), fast walk ~6 km/h (10 min/km), jog begins ~7 km/h (~8.5 min/km). Threshold set at the fast-walk boundary so jogs stay active (no false SPEED_UP suppression).
- **Effect if wrong:** Too fast (< 10 min/km, e.g. 8): slow joggers get SPEED_UP suppressed during recovery intervals. Too slow (>12): runners taking genuine walk breaks still get nagged with SPEED_UP alerts (the exact bug the gate was built to fix).
- **Last reviewed:** 2026-04-17

### AlertPolicy walking-sustained window
- **File:** `app/src/main/java/com/hrcoach/service/workout/AlertPolicy.kt` (`WALKING_SUSTAINED_MS`)
- **Value:** `30_000L` ms (30 seconds)
- **Source:** internal-rationale
- **Citation:** Code review 2026-04-17; in-code TUNING comment. Matches the default `alertDelaySec` (15s) × 2 — a runner who slows to walking pace for 30 continuous seconds has made a deliberate choice, not a transient slowdown. GPS dropouts during this window preserve the counter (null pace holds state) to handle tunnels / tree cover.
- **Effect if wrong:** Too short (<15s): traffic-light stops trigger SPEED_UP suppression that persists into resumed running. Too long (>60s): real walk breaks go unsuppressed for half the walk.
- **Last reviewed:** 2026-04-17

### IN_ZONE_CONFIRM slope gate
- **File:** `app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt` (`IN_ZONE_CONFIRM_SLOPE_GATE_BPM_PER_MIN`)
- **Value:** `0.8f` bpm/min
- **Source:** internal-rationale
- **Citation:** Code review 2026-04-17; in-code TUNING comment. Matches the buildGuidance phrasing threshold for symmetry — if guidance would read "In zone - HR rising/falling" (slope ≥ 0.8), don't play the reassurance cue because the earcon "all good" + voice "HR falling" is a mixed signal.
- **Effect if wrong:** Too low (<0.5): confirms fire less often, runner feels starved for reassurance on noisy HR. Too high (>1.5): confirms routinely pair with active-slope phrasing, reintroducing the mixed-signal problem.
- **Last reviewed:** 2026-04-17

### IN_ZONE_CONFIRM adaptive cadence (ConfirmCadence enum)
- **File:** `app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt` (`ConfirmCadence` enum)
- **Value:** FREQUENT=180/180 ms, STANDARD=180/300 ms (default), REDUCED=180/600 ms (all in thousands — first/repeat intervals)
- **Source:** internal-rationale
- **Citation:** Code review 2026-04-17; spawned from AI audit finding on 30-min run event volume. Rationale: first reassurance at 3 min (quick feedback once settled); subsequent reassurances stretched to 5 min (reduces steady-state chatter from ~6 events per 30 min to ~3).
- **Effect if wrong:** STANDARD repeatMs too low (<240s): steady-state runs feel chatty, runners tune out and miss real alerts. Too high (>420s): runners who rely on 3-min check-ins feel abandoned on long steady efforts.
- **User-facing:** toggled via `AudioSettings.inZoneConfirmCadence` (no UI yet — field-testing defaults). Persists through both local Gson blob and cloud backup/restore.
- **Last reviewed:** 2026-04-17

### HRR1 post-workout measurement window
- **File:** Not implemented (design specifies 120s with samples at T=0/60/120s)
- **Value:** N/A — **feature absent**
- **Source:** intended-published, not built
- **Citation:** Cole CR et al. (1999). *Heart-rate recovery immediately after exercise as a predictor of mortality*. NEJM 341:1351-1357.
- **Effect if wrong:** Currently: `FitnessSignalEvaluator` has dead code for `IllnessSignalTier.SOFT`/`FULL` because it can't read HRR1. Runner never gets illness detection, never gets credit for improving recovery.
- **Action:** either build the full 120s BLE hold + cool-down UI, or delete `hrr1Bpm`, `IllnessSignalTier.SOFT`, `IllnessSignalTier.FULL` from the data model as dead schema. Do not leave in limbo.
- **Last reviewed:** 2026-04-14 (flagged for resolve)

---

## Register-update workflow

When you change a science constant:

1. Find its entry here. If none exists, create one.
2. Update `Value`, `Last reviewed`, and if the source changed, `Source` and `Citation`.
3. In the commit message, reference this register: `chore(science-register): update <constant> per <citation>`.
4. If you're adding an `unsourced` entry, open the work as a TODO — it must be sourced or replaced before the containing feature is considered stable.

## Audit invocation

Run a science-fidelity audit whenever:

- An adaptive engine file (domain/engine/, domain/bootcamp/, service/workout/) receives >50 lines of AI-written changes in one session.
- A user reports training feedback that seems miscalibrated (pushed too hard on easy day, held back on hard day, wrong zone prompts).
- Before a release that touches `FitnessSignalEvaluator`, `AdaptivePaceController`, `SubMaxHrEstimator`, or `HrCalibrator`.

Audit procedure is documented in this file's companion: `docs/plans/2026-04-14-science-fidelity-audit-findings.md`.
