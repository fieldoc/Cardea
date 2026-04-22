# Adaptive Engine Invariants

Load when touching `AdaptivePaceController`, `FitnessSignalEvaluator`, `SubMaxHrEstimator`, `PhaseEngine`, `MetricsCalculator`, `AdaptiveProfile*`, `CoachingEventRouter` predictive logic, or any physiological constant.

## Controller lifecycle

- **`AdaptivePaceController` is per-workout, not singleton** — new instance each run; state (`sessionBuckets`, settle lists, `lastProjectedHr`) resets.
- **`finishSession` uses `savedInitialProfile.copy()`** — carries all `AdaptiveProfile` fields the controller doesn't own (hrMax, ctl, atl, hrRest, hrMaxIsCalibrated, hrMaxCalibratedAtMs, lastTuningDirection). Callers patch computed values on top. Do NOT construct a bare `AdaptiveProfile(longTermHrTrimBpm=..., ...)` — silently zeros un-named fields.
- **`TickResult.guidance` is non-nullable `String`** — guard `adaptiveResult?.guidance != null` is always true when `adaptiveResult` non-null. Preset overrides (strides, zone2) MUST appear BEFORE `adaptiveResult != null` in the WFS guidance `when` or they're dead code.

## HRmax

- **Fallback** in WFS = `220 - age` when age known (`userProfileRepository.getAge()`); falls back to 180 only when age null.
- **Dual-store sync complete** — all 6 write sites (Service, Setup, Bootcamp, BootcampSettings, PostRunSummary, Onboarding) sync both `UserProfileRepository` and `AdaptiveProfileRepository`. No gaps.
- **`SubMaxHrEstimator` uses guarded upward-only margin** — sustained 2-min rolling peak must exceed current HRmax by `EVIDENCE_MARGIN_BPM = 2` before revision; new HRmax = `peak + 1`, capped at `HrMaxEstimator.inferenceCeiling(age)`. Old effort-fraction buckets (0.92/0.85/0.75) removed 2026-04-14 — they biased HRmax upward on moderate runs. Do NOT reintroduce buckets.

## HR slope

- **`hrSlopeBpmPerMin` clamp** = ±50 BPM/min (`slopeSampleClampBpmPerMin`, raised from ±30 on 2026-04-13 — sprint-onset slopes legitimately hit 47 BPM/min, and 3s minimum window already rejects BLE glitches). Do not lower below 40.
- **Decay on long gaps** — ×0.5 when `deltaMin > 1.5` (walk breaks, GPS-only, power saving). Prevents stale climb slope through long walks. Do not remove.
- **Slope EMA weights 0.60/0.40** (prev/inst). Do NOT revert to 0.75/0.25 — caused ~24s lag behind direction changes, giving wrong guidance during corrections.

## Predictive warnings

- **Warmup gate = 90 s** — `PREDICTIVE_WARNING` suppressed for `elapsedSeconds < 90`. Slope EMA accumulates cardiovascular warmup as positive trend; without gate, returning runners hear "ease off" within 30s every run. Do not shorten.
- **Zone-entry grace (60s conditional reset)** — on any zone transition into IN_ZONE, if the 60s predictive cooldown was already expired, reset `lastPredictiveWarningTime = nowMs`. Prevents simultaneous firing with zone entry. **Conditional** — if cooldown has NOT expired, leave it alone so rapid oscillators (<60s cycles) retain their timer. 90s warmup gate is a separate independent guard.
- **`CoachingEventRouter` has two alert-reset methods** — `noteExternalAlert(nowMs)` resets IN_ZONE_CONFIRM window; `resetPredictiveWarningTimer()` sets `lastPredictiveWarningTime = 0L`. Both called from `AlertPolicy.onAlert` in WFS. Do NOT conflate. `resetPredictiveWarningTimer()` is effectively overridden by the zone-entry conditional reset in realistic scenarios; retained for call-site clarity.

## Trim

- **`shortTermTrimBpm` error source = `lastBaseProjectedHr`** — NOT `lastProjectedHr`. Base = slope + longTermTrim + paceBias only (no shortTermTrim). Merging both causes compounding bias.
- **`AdaptiveProfileRepository.resetLongTermTrim()`** zeroes `longTermHrTrimBpm` only; other fields unchanged. Not yet UI-wired. Use after heat-block/overtraining recovery when trim drifted to environmental baseline.

## Response lag / settle

- **`responseLagSec` default = 38f in all three sites** — `AdaptiveProfile`, `WorkoutAdaptiveMetrics`, and `MetricsCalculator.deriveFromPaceSamples` param default. Previously latter two defaulted 25f (inert-horizon landmine: `lag × 0.4f = 10f` = minimum clamp). Do not revert.
- **Settle cap = 10 min** — `trackSettling()` window `2_000L..600_000L` ms. Do not lower to 5 min — structured interval excursions commonly run 6-10 min; settle data was silently dropped, preventing `responseLagSec` calibration for interval runners.
- **Settle-time averaging is direction-equal** — `responseLagSec = (settleDownAvg + settleUpAvg) / 2`. Each physiological direction equal weight regardless of event count. Do not switch to count-weighted — 8 quick corrections would drown 1 slow build-up, under-estimating upward lag.
- **`lookupPaceBias`** uses sampleCount-weighted neighbour average (not simple mean over up to 3 neighbours). A 1-sample anomalous bucket must not equal a 500-sample baseline.

## TRIMP / metrics

- **TRIMP formula** = `duration * avgHR * (avgHR/HRmax)^2` (non-standard — not Bannister's exponential). Consistent across codebase; don't "fix" to literature values.
- **`MetricsCalculator.trimpFrom(durationMin, avgHr, hrMax)`** is the named home. WFS is authoritative invoker (only it has active-run-time duration = pauses subtracted). Do not inline elsewhere.
- **TRIMP fallback `durationMin` uses active run time** — `(now - workoutStartMs - totalPausedMs - totalAutoPausedMs).coerceAtLeast(0) / 60_000f`. Do NOT revert to wall-clock.

## Fitness signals

- **`FitnessSignalEvaluator.efTrend`** uses least-squares regression slope scaled to total span (not endpoint delta). Slope = `(n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)`, then `× (n-1)` → total estimated change; comparable to old threshold 0.04. Robust to single-session outliers.
- **TSB thresholds are conjunctive-gated** — `TSB_PUSH_THRESHOLD = 5f` alone looks aggressive, but PUSH_HARDER requires BOTH `tsb > 5` AND `efTrend > 0.04` (3+ reliable sessions / 42 days). EF requirement is the real filter. Don't flag +5 as "aggressive" in isolation. EASE at -25 is unilateral (Friel's "overreached").
- **`TierCtlRanges`** is 0-indexed internal (Foundation/Development/Performance → spec T1/T2/T3). Intentionally extends spec at both ends: Foundation floor = 0 (new users with CTL<10), Performance ceiling = 200 (terminal, no promotion). Documented in `TierCtlRanges.kt` KDoc.
- **HRR1 pathway dormant** — illness tier permanently `NONE` because `hrr1Bpm` never computed (120s post-workout cool-down hold not implemented). BootcampVM/Screen illness UI is dead code tagged `TODO(HRR1)`. Don't delete schema fields — needed when feature lands.

## Sample book-keeping

- **Cadence lock check uses explicit counter** — `hrSamplesSinceLastArtifactCheck`, NOT `hrSampleBuffer.size % 10`. Buffer cap is 120 (=12×10), so `size % 10 == 0` is permanently true at steady state, firing every tick.
- **`hrSampleSum`/`hrSampleCount` reset in `startWorkout()`** — not only `stopWorkout()`. `stopWorkout()` zeroes them before `observationJob` cancels; a race can add a sample to the zeroed sum and carry stale value into next session.

## Science Constants Register

Every physiological constant/coefficient/threshold/formula has a provenance entry in `docs/plans/2026-04-14-science-constants-register.md`. Valid sources: `published`, `internal-rationale`, `intentional-non-standard`, `unsourced` (failure state).

**Rule:** if you introduce, change, or move a science constant, update the register in the same commit. Procedure + history: `docs/plans/2026-04-14-science-fidelity-audit-findings.md`.
