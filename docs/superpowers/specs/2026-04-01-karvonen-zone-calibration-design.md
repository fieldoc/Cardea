# Karvonen Zone Calibration & HRmax Inference

**Date:** 2026-04-01
**Status:** Design
**Scope:** Zone formula, HRmax calibration, resting HR wiring, BASE phase strides, education copy

---

## Problem

Cardea's HR zone targets are too low for fit users, especially during BASE phase. A fit 29-year-old with HRmax 191 gets a Zone 2 easy-run target of 130 BPM — the awkward shuffle zone between walking and running. This is caused by two compounding issues:

1. **Simple %HRmax formula** ignores resting heart rate. `191 × 0.68 = 130 BPM` doesn't account for the user's fitness-adapted cardiac baseline.
2. **220-age HRmax** is a population average with ±10-12 BPM standard deviation. A fit 29-year-old's actual HRmax may be 200+.
3. **BASE phase monotony** — weeks of nothing but Zone 2 easy runs with no variation.

The 80/20 polarized training model is scientifically correct and stays. The issue is input calibration and formula choice, not training philosophy.

## Solution Overview

1. Switch all zone calculations from `%HRmax` to **Karvonen (% Heart Rate Reserve)**
2. Add **submaximal HRmax inference** from early workouts
3. Wire existing **resting HR measurement** into the Karvonen formula
4. Add **strides** to BASE phase for Tier 1+
5. Minor **education copy** improvements

---

## 1. Zone Formula: Karvonen (Heart Rate Reserve)

### Current
```
targetHr = HRmax × percentage
```

### New
```
targetHr = HRrest + (HRmax - HRrest) × percentage
```

This is the Karvonen method, also known as Heart Rate Reserve (HRR). The percentages in `PresetLibrary` stay the same — 68% for zone2_base, 84% for aerobic_tempo, etc. Only the formula changes.

### Why Karvonen

Karvonen naturally personalizes to fitness level. Two runners with the same HRmax of 191 but different resting HRs get different targets:

| Runner | HRrest | Reserve | Zone 2 (68%) | Zone 3 (84%) |
|--------|--------|---------|-------------|-------------|
| Sedentary (rest=75) | 75 | 116 | 154 | 172 |
| Fit (rest=55) | 55 | 136 | 147 | 169 |
| Current formula | — | — | 130 | 160 |

Both Karvonen targets are higher than the current formula, and the fit runner gets slightly higher targets. The sedentary runner's targets are still achievable but represent real easy running, not shuffling.

### Impact on All Presets

For a 29-year-old, HRmax=191, HRrest=60:

| Preset | %Reserve | Current | Karvonen | Delta |
|--------|----------|---------|----------|-------|
| zone2_base | 68% | 130 | 149 | +19 |
| aerobic_tempo | 84% | 160 | 170 | +10 |
| lactate_threshold | 90% | 172 | 178 | +6 |
| norwegian_4x4 work | 92% | 176 | 181 | +5 |
| norwegian_4x4 recovery | 65% | 124 | 145 | +21 |
| cooldown | 60% | 115 | 139 | +24 |

Easy/recovery targets get the largest lift. High-intensity targets converge. This is the correct behavior.

### Code Changes

**`PresetLibrary.kt`:** Every `buildConfig(maxHr: Int)` function signature becomes `buildConfig(maxHr: Int, restHr: Int)`. Internal calculations change from `(maxHr * pct).roundToInt()` to `(restHr + (maxHr - restHr) * pct).roundToInt()`.

**`ZoneEducationProvider.kt`:** `bpmRange()` and `zonePercentages()` switch to Karvonen. The provider needs `restHr` as an additional input alongside `maxHr`.

**All callers of `PresetLibrary.buildConfig()`:** Pass `restHr` from `AdaptiveProfile.hrRest` (or age-based default).

**`ZoneEngine`:** No changes. It already works with pre-computed `targetHr` values from `WorkoutConfig`.

---

## 2. HRmax Calibration: Three-Tier System

### Tier A — Self-Report (existing, unchanged)

Onboarding Page 1 and Bootcamp Setup Step 6 both offer an HRmax override field (120-220). If the user enters a value, store with `hrMaxIsCalibrated = true`. Submaximal inference does NOT override a calibrated value.

### Tier B — Submaximal Inference (new)

After each of the first 3 qualifying workouts (any mode — bootcamp, free run, custom; qualifying = ≥10 minutes of HR data), analyze the session:

**Algorithm:**

1. Compute **sustained peak**: highest 2-minute rolling average HR from the session's track points.
2. Classify **effort level** from the sustained peak relative to current HRmax estimate:
   - `sustainedPeak < 0.70 × currentHRmax` → **skip** (not informative, too easy)
   - `0.70 ≤ sustainedPeak/currentHRmax < 0.80` → effort fraction = **0.75**
   - `0.80 ≤ sustainedPeak/currentHRmax < 0.88` → effort fraction = **0.85**
   - `sustainedPeak/currentHRmax ≥ 0.88` → effort fraction = **0.92**
3. Back-calculate: `estimatedMax = sustainedPeak / effortFraction`
4. Apply if: `estimatedMax > currentHRmax` AND session has ≥10 minutes of HR data AND `hrMaxIsCalibrated == false`

**Guardrails:**
- Floor: never estimate below `220 - age`
- Ceiling: never estimate above `220 - age + 20` (physiological cap for non-extreme outliers)
- Only revises upward, never down
- Requires sustained HR (2-min rolling avg), not spike artifacts
- Does not override self-reported (`hrMaxIsCalibrated = true`) values

**Example:** User (age 29, current HRmax=191) does a free run. Their 2-min peak is 168 BPM. `168/191 = 0.88` → effort fraction 0.92. Estimated max = `168/0.92 = 183`. Since 183 < 191 (current), no update. But if their peak were 180: `180/191 = 0.94` → effort fraction 0.92. Estimated max = `180/0.92 = 196`. Since 196 > 191 and below ceiling (211), HRmax updates to 196. This correctly catches the case where 220-age underestimates.

**Where this lives:** New class `SubMaxHrEstimator` in `domain/engine/`. Called by `AdaptiveProfileRebuilder.rebuild()` during post-workout processing. No new entry points.

### Tier C — Empirical Max (existing, unchanged)

`AdaptiveProfileRebuilder` continues tracking the absolute highest observed HR across all sessions. If `sessionMax > currentHRmax`, update. This is the long-term convergence mechanism.

### UX

Silent. No notifications. Zones quietly improve as the system learns. Users can view their current HRmax in Account settings (already displayed there).

One copy change on onboarding Page 1: label the 220-age estimate as **"Estimated — improves after your first run"**.

---

## 3. Resting HR Collection

### Existing Mechanism (unchanged)

`MetricsCalculator.computeRestingHrProxy(trackPoints)`:
- Analyzes first 60 seconds of workout HR data
- Requires ≥3 readings, variance ≤15 BPM, first reading ≤100 BPM
- Returns resting HR estimate or null

### One Adjustment

Change the offset formula from `(minHr - 10).coerceAtLeast(30)` to `(minHr - 5).coerceAtLeast(40)`. The current -10 offset is too aggressive for general population — a first-minute min of 62 BPM producing a resting HR of 52 is implausibly low for most users and makes the Karvonen reserve too large.

### Age-Based Default (before first measurement)

When `AdaptiveProfile.hrRest` is null (no workouts yet):

```
restHrDefault = (72 - 0.2 × age).roundToInt().coerceIn(55, 75)
```

This is a conservative population estimate. For age 29: `72 - 5.8 = 66 BPM`. Errs slightly high, which produces slightly higher Karvonen targets — better than too low.

### Storage

`AdaptiveProfile.hrRest` — already exists, already persisted. Updated by `AdaptiveProfileRebuilder` after each workout. Read by `PresetLibrary.buildConfig()` at workout config time.

---

## 4. BASE Phase Strides

### Current

BASE phase assigns all sessions as `"zone2_base"` regardless of tier. No quality work.

### New

For **Tier 1+** (not Tier 0/beginner), one easy run per week during BASE phase becomes `"zone2_with_strides"`.

**New preset `zone2_with_strides`:**
- Same zone target as `zone2_base` (68% HRR)
- Same duration
- `sessionLabel = "Easy + Strides"`
- `WorkoutConfig.guidanceTag = "strides"` (new nullable field)

**Strides protocol (coaching text only, not zone-enforced):**
- After 20 minutes of easy running, perform 4-6 × 20-second accelerations
- Between each stride, jog easy for 60-90 seconds
- Strides should feel smooth and fast but not sprinting (~85-90% effort)
- Resume easy running after the last stride

**Why coaching text, not zone segments:** Strides are 20 seconds each. The zone engine's 15-second alert delay and 30-second cooldown make segment-based enforcement meaningless for bursts this short. False "ABOVE_ZONE" alerts during intentional pickups would degrade the experience.

### SessionSelector Changes

```
BASE phase, Tier 0: all "zone2_base" (unchanged)
BASE phase, Tier 1+: last easy run of the week becomes "zone2_with_strides"
BUILD/PEAK/TAPER: unchanged
```

### Workout Screen Guidance

When `guidanceTag == "strides"`, the guidance text area shows the strides protocol at the appropriate time (after 20 minutes elapsed). This uses the existing guidance text system — no new UI components.

---

## 5. Education & Copy

### Onboarding Page 1

Label change for the 220-age estimate: **"Estimated — improves after your first run"**

### Onboarding Page 2 (Zone Education)

BPM ranges automatically update when `ZoneEducationProvider` switches to Karvonen. No copy changes needed — the numbers become more realistic on their own.

### Zone 2 Workout Guidance

For all Zone 2 sessions, include a contextual one-liner in the guidance text area:

> "Easy pace builds your aerobic engine. It should feel comfortable enough to hold a conversation."

This appears during the run, not during onboarding. Contextual education beats front-loaded theory.

---

## 6. Migration & Edge Cases

### Existing Users Mid-Bootcamp

When the app updates:
- `PresetLibrary.buildConfig()` is called fresh each time a workout starts. It reads current `hrMax` and `hrRest` from `AdaptiveProfile`. No stored zone targets need migration.
- If `hrRest` is already populated from previous workouts, Karvonen kicks in immediately.
- If `hrRest` is null (user has workouts but MetricsCalculator never got a valid reading), the age-based default is used.
- Bootcamp sessions are regenerated from enrollment parameters on each app launch. No session entity migration needed.

### No Resting HR Available and No Age

If both `hrRest` and `age` are null (should not happen — age is required in onboarding), fall back to `restHr = 65` as a safe universal default.

### HRmax Self-Report Interaction with Submaximal Inference

- `hrMaxIsCalibrated = true`: submaximal inference skipped. Empirical max (Tier C) still updates if a higher absolute HR is observed.
- `hrMaxIsCalibrated = false`: submaximal inference active for first 3 workouts.
- After 3 workouts, submaximal inference stops. Empirical max continues indefinitely.

### Very Low Resting HR (<45 BPM)

Elite athletes or users with bradycardia may have very low resting HRs. The Karvonen formula handles this correctly — it produces higher targets, which matches the reality that these users need higher absolute HR to reach the same relative intensity.

### Very High Resting HR (>80 BPM)

Deconditioned or medically affected users. Karvonen produces lower targets, which is appropriate — their usable HR range is smaller. The `coerceAtLeast(40)` floor on measured resting HR and `coerceIn(55, 75)` on the default prevent extreme values.

### Spike Artifacts in Resting HR Measurement

Already handled by the existing ≤15 BPM variance check and ≤100 BPM first-reading check in `MetricsCalculator`. No changes needed.

---

## Files Changed (Summary)

| File | Change |
|------|--------|
| `domain/preset/PresetLibrary.kt` | `buildConfig(maxHr, restHr)` — Karvonen formula |
| `domain/education/ZoneEducationProvider.kt` | `bpmRange()` — Karvonen formula, takes restHr |
| `domain/engine/SubMaxHrEstimator.kt` | **New file** — submaximal HRmax inference |
| `domain/engine/AdaptiveProfileRebuilder.kt` | Call SubMaxHrEstimator during rebuild |
| `domain/engine/MetricsCalculator.kt` | Adjust resting HR offset from -10 to -5, floor from 30 to 40 |
| `domain/model/WorkoutConfig.kt` | Add `guidanceTag: String?` field |
| `domain/preset/SessionPresetArray.kt` | Add `zone2_with_strides` preset entry |
| `domain/bootcamp/SessionSelector.kt` | BASE Tier 1+: one run/week → `zone2_with_strides` |
| `ui/onboarding/OnboardingPages.kt` | Copy tweak: "Estimated — improves after your first run" |
| `ui/workout/` (guidance display) | Handle `guidanceTag == "strides"` and Zone 2 easy-run message |
| All callers of `PresetLibrary.buildConfig()` | Pass `restHr` parameter |
| Test files | Update/add tests for Karvonen math, SubMaxHrEstimator, strides selection |

---

## Scientific References

- **Karvonen method:** Karvonen, Kentala & Mustala (1957). Original HR reserve formula.
- **80/20 polarized training:** Seiler & Tonnesson (2009); Stöggl & Sperlich (2014) PMC3912323.
- **220-age limitations:** Robergs & Landwehr (2002). Standard deviation ±10-12 BPM.
- **Submaximal HRmax estimation:** Nes et al. (2013). Submaximal protocols for HRmax prediction.
- **Daniels' Running Formula:** Daniels (2013). Duration scaling and training intensity distribution.
- **Strides in base building:** Pfitzinger & Douglas (2009). Neuromuscular maintenance during aerobic base.
