# Adaptive Bootcamp Design
**Date:** 2026-03-04
**Status:** Active strategic design for the Bootcamp system

This remains the authoritative strategy doc for adaptive Bootcamp behavior. For broader app-level product direction, read `docs/plans/2026-03-10-product-direction.md` alongside it.

---

## Vision

Transform the Bootcamp plan from a fixed calendar into a living, adaptive training program — one that behaves like a coach with direct access to the runner's physiology. The system continuously observes measured fitness signals, silently tunes session intensity within the current program, and prompts the user to change programs when their measured fitness significantly outpaces or lags their current tier.

The user controls their goal (what they're training for) and their tier (how hard the program is). The system earns trust by explaining its reasoning and respecting the user's choices.

---

## Design Decisions

### Two-Level Adaptation

**Level 1 — Silent tuning (automatic, within-tier):**
Preset selection within each session slot adjusts based on the rolling fitness signal. The session type label the user sees (e.g. "Tempo") is unchanged; the specific workout assigned is harder or easier within that type. Recovery weeks may also trigger early if fatigue signals are elevated. No user prompt, no explanation required — this is the coach making small adjustments.

**Level 2 — Tier prompt (user-accepted, explicit):**
When the rolling fitness signal has been significantly above or below the current tier's expected range for a sustained period, the system surfaces a prompt in the Bootcamp dashboard. The user accepts or dismisses it. The program only changes with explicit user consent. Users may be running with a friend, managing an injury, or simply enjoying an easier phase — the system respects all of these.

**Snooze logic:**
- First dismiss: snooze 2 weeks
- Second dismiss: snooze 3 weeks
- Third and subsequent dismisses: snooze 4 weeks (hard cap — 8 weeks would permanently mute within a 16-week training block)
- If fitness signal re-enters the current tier's range before snooze expires: cancel prompt entirely

---

## Fitness Signal Architecture

### Per-Session Metrics (already collected)

| Metric | Source | Role |
|---|---|---|
| Efficiency Factor (EF) | `MetricsCalculator` | Aerobic efficiency trend |
| Aerobic Decoupling | `MetricsCalculator` | Aerobic base quality (runs >45 min only) |
| Avg HR vs target HR | `AdaptivePaceController` | Session compliance |
| `longTermHrTrimBpm` | `AdaptiveProfile` | Systematic prediction offset |
| `responseLagSec` | `AdaptiveProfile` | Cardiac inertia |

### New Per-Session Metric: HRR1

Heart Rate Recovery at 1 minute post-run — HR drop from cessation to T+60s. Strongly peer-reviewed (Cole et al., NEJM 1999). Fit athletes: 30–50 bpm drop. Measures autonomic recovery capacity between sessions.

**Implementation requirement:** `WorkoutForegroundService` holds the BLE connection open for 120 seconds after workout stop, sampling HR at T=0, T=60s, T=120s. A brief cool-down UI is shown rather than immediately navigating away. HRR1 stored in `WorkoutAdaptiveMetrics.hrr1Bpm`.

**UX protocol enforcement:** The cool-down screen must explicitly instruct "Walk slowly for 2 minutes." A user who sits in a car immediately post-run will produce an artificially fast HR drop (passive cooling + gravity assist on venous return), inflating HRR1. The instruction is required for measurement validity. If the user exits early, mark HRR1 as `null` rather than recording a corrupted value.

### Per-Session Load: Banister TRIMP

Replaces ad-hoc averaging of EF with a peer-reviewed load unit (Morton et al., 1990):

```
TRIMP = duration_min × ΔHR_ratio × e^(b × ΔHR_ratio)
ΔHR_ratio = (HR_avg - HR_rest) / (HR_max - HR_rest)
b = 1.92 (default; future: user-input sex)
```

Requires: `HR_rest` (dynamically maintained — see HRmax/HRrest Auto-Detection below), `HR_max` (auto-detected — see below), per-second HR stream (already available).

Exponentially weights high-intensity sessions — a HIIT session correctly costs more than an easy run of the same duration.

### Chronic / Acute Load: CTL, ATL, TSB

Based on the Banister fitness-fatigue model (validated in PMC8894528):

```
CTL_today = CTL_yesterday × e^(-1/42) + TRIMP_today × (1 - e^(-1/42))
ATL_today = ATL_yesterday × e^(-1/7)  + TRIMP_today × (1 - e^(-1/7))
TSB = CTL - ATL
```

- **CTL (τ = 42 days):** Chronic fitness. Aerobic adaptations develop and detrain on this timescale. After 6 weeks of no training, CTL decays to ~50% of peak automatically — no special gap handling needed at the signal level.
- **ATL (τ = 7 days):** Acute fatigue. Dissipates in 5–10 days.
- **TSB:** Positive and rising = fresh and gaining fitness. Very negative (< -25) = overreaching risk.

These values are persisted in `AdaptiveProfile` and updated incrementally after each session.

### Temporal Gating

The research confirms: recency beats sample size after a significant gap. The CTL decay handles this automatically — a single run after a 5-week layoff is correctly weighted as the dominant current signal because old TRIMP contributions have decayed toward zero. No special-case code required.

### Composite Fitness Signal

| Signal | Role in tier assessment |
|---|---|
| CTL | Absolute fitness level — which tier does this runner belong in? |
| TSB | Readiness — should this session be harder or easier within the tier? |
| EF trend (last 4–6 sessions, time-gated) | Confirming adaptation — is the runner responding to current load? |
| HRR1 (where available) | Autonomic health — is recovery between sessions adequate? |
| Decoupling (runs >45 min only) | Aerobic base quality — is the runner building or struggling? |

---

## Program Tier Definitions

### Universal Tier Structure

Three tiers per goal, defined by intensity distribution model and session types:

| Tier | Intensity Model | Key Differentiator |
|---|---|---|
| 1 — Beginner | HVLIT: 100% Z1–Z2 | Walk/run intervals; no threshold work; HR cap enforced |
| 2 — Intermediate | Pyramidal: 80% Z1–Z2 / 15% Z3–Z4 / 5% Z5 | Tempo sessions introduced; long run distinct from easy run |
| 3 — Advanced | Polarized: 80% Z1–Z2 / 20% Z4–Z5 (skips Z3) | VO2max intervals; cruise intervals; doubles (HM/marathon) |

**Tier 1 → Tier 2 boundary:** Introduction of sustained threshold/tempo work (Z3–Z4).
**Tier 2 → Tier 3 boundary:** Introduction of VO2max intervals (Z5); quality work skips Z3 (polarized).

### Session Types by Tier

| Session | T1 | T2 | T3 |
|---|---|---|---|
| Walk/run intervals | ✓ | — | — |
| Easy run | ✓ | ✓ | ✓ |
| Long run (distinct session) | — | ✓ | ✓ |
| Long run w/ fast-finish | — | — | ✓ |
| Tempo / sustained threshold (Z3–Z4) | — | ✓ | ✓ |
| Cruise intervals | — | — | ✓ |
| VO2max intervals (Z5) | — | — | ✓ |
| Doubles (easy AM run) | — | — | ✓ (HM/marathon) |
| Strides | — | ✓ (late plan) | ✓ (2×/week) |

### HR Zone Reference (% HRmax, enforced via BLE monitor)

| Zone | % HRmax | Training Domain |
|---|---|---|
| Z1 | 55–62% | Active recovery |
| Z2 | 63–73% | Aerobic base (below LT1) |
| Z3 | 74–84% | Aerobic threshold / tempo approach |
| Z4 | 85–91% | Lactate threshold |
| Z5 | 92–100% | VO2max |

### Goal-Specific Tier Profiles

#### CARDIO_HEALTH
| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Sessions/week | 3 | 4 | 5 |
| Weekly duration | 60–90 min | 3–5 hrs | 5–7 hrs |
| Primary stimulus | Aerobic development | LT development | VO2max development |
| Long run ceiling | 30–45 min | 45–70 min | 70–90 min |
| Key session | Walk/run, easy run | Easy + tempo + long | Easy + VO2max intervals + threshold + long |
| Source | PubMed 40560504, JACC 2015 | PMC3912323, PMC11329428 | PubMed 17414804, PMC3912323 |

#### RACE_5K_10K
| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| 5K finish time | >30 min (M), >35 min (F) | 20–30 min (M), 22–35 min (F) | <20 min (M), <22 min (F) |
| 10K finish time | >65 min (M), >75 min (F) | 42–65 min (M), 48–75 min (F) | <42 min (M), <48 min (F) |
| Weekly volume | 15–25 km | 30–50 km | 60–100 km |
| Key session | Walk/run intervals | Tempo + race-pace | VO2max intervals + LT |
| Source | PubMed 29863593, PMC3912323 | PMC11329428 | PMC11329428, PubMed 23752040 |

*Note: 5K and 10K are physiologically distinct (5K ≈ 95% VO2max; 10K ≈ 90% VO2max / threshold blend). Splitting into two separate goals is a future improvement.*

#### HALF_MARATHON
| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Finish time | 2:15–3:00+ | 1:45–2:15 | Sub-1:30 (M), sub-1:45 (F) |
| Race HR effort | Z3 (83–87%) | Z4 (87–91%) | Z4–Z5 (90–93%) |
| Weekly volume | 25–44 km | 45–70 km | 75–110 km |
| Plan length | 16 weeks | 12–14 weeks | 14–18 weeks |
| Key sessions | Easy run + long run | Tempo + fast-finish long | Cruise intervals + VO2max + long w/ fast-finish |
| Source | PMC7496388, PMC10390894 | PMC9299127, PMC4621419 | PMC9299127, Seiler & Tønnesson 2015 |

#### MARATHON
| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Finish time | 5:30–7:00+ | 3:45–5:00 | 2:45–3:30 |
| Race HR effort | Z2–Z3 (70–78%) | Z3–Z4 (83–88%) | Z4 (87–92%) |
| Weekly volume | 20–50 km | 48–80 km | 80–130 km |
| Plan length | 18–20 weeks | 18 weeks | 18–20 weeks |
| Key sessions | Run/walk + long run | MP runs + tempo + long | VO2max + LT intervals + MP long run + doubles |
| Source | PubMed 7253871, Galloway | PMC11065819, Esteve-Lanao 2007 | PMC4621419, PMC10000870, PubMed 35418513 |

---

## MMR Gap Detection

### CTL-Based Tier Ranges

Each tier within each goal has an expected CTL range. These are approximate and will be refined with real user data post-launch:

| Goal | Tier 1 CTL | Tier 2 CTL | Tier 3 CTL |
|---|---|---|---|
| Cardio Health | 10–30 | 30–55 | 55–90 |
| 5K/10K | 15–35 | 35–65 | 65–110 |
| Half Marathon | 20–45 | 45–75 | 75–120 |
| Marathon | 25–55 | 55–90 | 90–140 |

### Prompt Triggers

**Prompt up** when all three are true for 3+ consecutive weeks within the recency window:
- CTL is above the current tier's CTL ceiling
- TSB is positive (fresh, not just a short peak)
- EF trend is rising (confirmed adaptation, not a fluke week)

**Prompt down** when either is true:
- CTL has dropped below the tier's CTL floor AND been there for 2+ weeks
- Decoupling is chronically >10% on runs that should be easy (for runs >45 min), sustained over 2+ sessions

### Prompt UX

Appears in the Bootcamp dashboard (not a modal interrupt). Contains:
1. **Signal summary** — "Your last 4 runs show your aerobic efficiency is consistently above what this plan expects."
2. **What changes** — "Moving to intermediate introduces tempo runs once a week and extends your long run. Easy runs stay the same."
3. **Two actions** — "Move up" / "Not now"

Snooze escalation: 2 weeks → 4 weeks → 8 weeks on repeated dismissal.

---

## Silent Tuning Logic

`FitnessSignalEvaluator` (new, pure domain object) returns a `TuningDirection`:
- `PUSH_HARDER` — TSB positive + EF rising: assign harder preset within session type
- `HOLD` — signals neutral: assign standard preset
- `EASE_BACK` — TSB very negative OR HRR1 declining: assign easier preset; consider early recovery week

`SessionSelector` gains a `tuningDirection: TuningDirection` parameter. Within each session type, there is a range of presets:
- EASY slot: `zone2_base` (standard) → can ease to walk/run or push to steady-state with narrower buffer
- TEMPO slot: `aerobic_tempo` (standard) → can ease to `zone2_base` or push to `lactate_threshold`
- INTERVAL slot: `norwegian_4x4` (standard) → can ease to `hiit_3030` or push to full Norwegian protocol

---

## Data Model Changes

### `AdaptiveProfile` — add fields
```kotlin
val ctl: Float = 0f           // Chronic Training Load (42-day EWMA of TRIMP)
val atl: Float = 0f           // Acute Training Load (7-day EWMA)
val lastTRIMP: Float = 0f     // Most recent session TRIMP score
val hrRest: Float = 60f       // Resting HR (user input or estimated)
```

### `WorkoutAdaptiveMetrics` — add field
```kotlin
val hrr1Bpm: Float? = null    // HR drop at T+60s post-run
val trimpScore: Float? = null // Banister TRIMP for this session
```

### `BootcampEnrollmentEntity` — add fields
```kotlin
val tierIndex: Int = 0                    // 0=beginner, 1=intermediate, 2=advanced
val tierPromptSnoozedUntilMs: Long = 0L  // 0 = no snooze active
val tierPromptDismissCount: Int = 0       // Escalates snooze on repeated dismissal
```

### New domain object: `FitnessSignalEvaluator`
Pure function — takes `AdaptiveProfile` + recent `WorkoutAdaptiveMetrics` list, returns:
- `currentFitnessLevel: FitnessLevel`
- `tsb: Float`
- `tuningDirection: TuningDirection`
- `shouldPromptTierChange: Boolean`
- `promptDirection: TierPromptDirection` (UP / DOWN / NONE)

### `WorkoutForegroundService` — behavioral change
After workout stop: hold BLE connection, enter 120-second cool-down state. Sample HR at T=0, T=60s, T=120s. Compute HRR1 = HR(0) − HR(60). Store in `WorkoutAdaptiveMetrics`. Show cool-down UI during this period.

---

## Key Implementation Notes

### Interval HR Lag (critical)
Confirmed across all research: HR lags 60–90 seconds behind effort onset. For Z4–Z5 interval sessions, **GPS pace must gate the start of each rep** — not HR. HR is used as a post-rep compliance check (did HR reach the target zone before the rep ended?). The existing `CoachingEventRouter` will need an interval-aware mode for Tier 3 sessions.

### Walk/Run as First-Class Session Type
Walk/run intervals are prescribed for Tier 1 across all goals. Currently the app has no concept of a coached walk interval. `WorkoutForegroundService` needs a walk/run mode: alternating run and walk segments with HR-gated transitions (start the walk when HR exceeds ceiling; resume running when HR returns below recovery target).

### 5K/10K Split (deferred)
Noted for future implementation: 5K is ~95% VO2max-limited; 10K is ~90% VO2max / threshold blend. These require genuinely different peak-phase sessions. Current combined `RACE_5K_10K` goal is an acceptable placeholder.

### Decoupling Caveat
Aerobic decoupling is only meaningful for runs >45 minutes. The signal evaluator must check session duration before including decoupling in any assessment. Short runs contribute zero to the decoupling signal.

---

## HRmax / HRrest Auto-Detection

The 220-age formula has a standard deviation of ±10–12 bpm. Because Banister TRIMP weights intensity exponentially, a 20 bpm HRmax error compounds into a completely corrupted CTL/ATL. Onboarding HRmax is an estimate only — the system must continuously self-correct.

### HRmax Auto-Detection
- After every workout, compare peak observed HR against stored `hrMax`
- If sustained above stored `hrMax` for >8 consecutive seconds (filters BLE spike artifacts): permanently update `hrMax` in `AdaptiveProfile`
- Surface a non-blocking notification: "New max HR detected (193 bpm). Your training zones have been updated."
- Recalculate CTL/ATL/TSB retroactively for the last 14 sessions using the new HRmax

### HRrest Auto-Detection
- During the first 5 minutes of any workout (pre-effort), take the lowest 30-second rolling average of HR as a resting HR candidate
- Maintain a 30-day rolling minimum of these pre-workout candidates
- Update `hrRest` in `AdaptiveProfile` only when the new value is lower than the stored value by >2 bpm (prevents noise from elevated pre-workout HR due to warmup or excitement)

---

## Environmental Signal Correction

EF and aerobic decoupling assume controlled conditions. Outdoor running is not controlled.

### Grade Adjusted Pace (GAP)
EF must be calculated using GAP rather than raw pace. GPS gives elevation; GAP corrects for gradient using the standard formula (approximately +10–12 sec/km per 1% grade uphill, -8 sec/km per 1% downhill).

`MetricsCalculator` must replace raw `paceMinPerKm` with `gapMinPerKm` in all EF and decoupling calculations. Raw pace is retained for display; GAP is used only for signal computation.

### Heat / Environmental Flagging
High-temperature sessions cause cardiac drift that has nothing to do with fitness. Two-layer detection:

**Automatic heuristic:** If decoupling >10% AND session GAP-pace is slower than the runner's rolling baseline for equivalent HR by >15 sec/km → flag the session as `environmentAffected = true`. Exclude decoupling from the rolling fitness signal for this session.

**User confirmation prompt:** After any session flagged as environment-affected, surface a single post-run question: "Did this run feel unusually hot or humid?" Confirmation locks the exclusion. Denial clears it and includes the session normally.

This makes the automatic heuristic primary (doesn't rely on self-report) while allowing the runner to override false positives.

---

## Silent Tuning Lever Mapping (Corrected)

**Critical principle:** The physiological stimulus is defined by the HR zone, not the duration. Changing an easy run from Z2 to Z3 to "challenge" a fitter runner accumulates junk fatigue and destroys the polarized model. Zones are fixed per session type. The levers are volume and structure within the zone.

### Easy Runs and Long Runs (Z1–Z2)
**Lever: Duration**
- `PUSH_HARDER`: increase duration by 10–15% (e.g. 40 min → 46 min)
- `EASE_BACK`: decrease duration by 15–20%, or insert walk breaks (e.g. 40 min → 32 min, or 5:1 run/walk)
- Zone target: unchanged at Z2

### Tempo / Threshold Runs (Z3–Z4)
**Lever: Continuous time-in-zone OR interval structure**
- `PUSH_HARDER`: extend continuous block (e.g. 20 min → 25 min Z4), or add a rep to cruise intervals (3×8 min → 4×8 min Z4)
- `EASE_BACK`: break continuous block into intervals with Z1 recovery (e.g. 20 min continuous Z4 → 2×10 min Z4 / 2 min Z1 jog between)
- Zone target: unchanged at Z3–Z4

### VO2max Intervals (Z5)
**Levers: Rep count, recovery duration, recovery intensity**
- `PUSH_HARDER`: add 1 rep, OR shorten recovery by 30s, OR upgrade passive walk recovery to active Z2 jog
- `EASE_BACK`: drop 1 rep, OR extend recovery by 30–60s, OR downgrade active recovery to full passive walk (ensures HR returns to Z1 before next rep)
- Zone target: unchanged at Z5 (HR is already maximal — there is no Z6)

### Hard Caps and Level 2 Trigger
Each session type has a duration/volume ceiling per tier. When `PUSH_HARDER` would exceed the ceiling, the tuning is held at the cap instead. If the runner is consistently at the cap across 3+ sessions while TSB is positive and EF is rising, this is the precise trigger for a Level 2 tier-change prompt.

Example caps (Tier 2, 5K/10K):
| Session type | Cap |
|---|---|
| Easy run | 55 min |
| Tempo run | 35 min continuous, or 5 reps |
| Long run | 90 min |
| VO2max intervals | 6 reps |

### Discrete Preset Index Model
Rather than computing workouts dynamically, each session type for each tier is represented as an ordered array of discrete preset configurations. Silent tuning moves the index up or down within the array. The session type label shown to the user never changes.

Example — Tier 2 Tempo array:
```
Index 0 (ease): warmup Z1 / 2×8 min Z3 / 2 min Z1 between / cooldown Z1
Index 1 (base): warmup Z1 / 20 min continuous Z3 / cooldown Z1
Index 2 (push): warmup Z1 / 25 min continuous Z3 / cooldown Z1
Index 3 (hard): warmup Z1 / 30 min continuous Z3 / cooldown Z1
```
`FitnessSignalEvaluator` returns `TuningDirection`; `SessionSelector` applies ±1 index shift, capped at array bounds.

---

## Anomaly and Illness Detection

Metrics crashing while TSB is positive is not a fitness signal — it is a health signal. The two must be distinguished.

**Illness/stress flag triggers when all are true:**
- TSB > 0 (runner should feel fresh based on load history)
- EF has dropped >8% from their 14-day rolling average
- HRR1 has worsened by >10 bpm from their 14-day rolling average
- This is not explained by a flagged environmental session

**Response:** Surface a third prompt type (distinct from tier-change prompts):
> "Your heart rate is responding differently than usual. Are you feeling under the weather or dealing with extra stress?"

If confirmed: mark the session as `anomalous`, exclude from CTL/ATL/TRIMP accumulation, assign a recovery session as the next scheduled workout regardless of plan position. If denied: include normally.

---

## HR Artifact Rejection

Optical wrist HR monitors (Garmin, Apple Watch via BLE) are vulnerable to cadence lock — the sensor locks onto step rate (~160–180 bpm) instead of cardiac rate. This produces a flat, implausibly constant HR at running cadence that inflates TRIMP scores severely.

**Detection heuristic in `WorkoutForegroundService`:**
- If HR jumps >25 bpm in a single sample AND
- Remains within ±3 bpm for >8 consecutive seconds AND
- GPS pace is consistent with easy effort (<75% expected HRmax for that pace based on `paceHrBuckets`)
→ Flag the affected HR window as `artifactSuspected`

**Response:** Exclude affected samples from TRIMP calculation. Fall back to pace-based load estimation for the flagged window using the runner's historical pace-HR buckets. Mark `WorkoutAdaptiveMetrics.trimpReliable = false` for the session. Do not use this session's EF or decoupling in the rolling fitness signal.

---

## Menstrual Cycle Awareness (Future Feature — Not In Initial Scope)

During the luteal phase, resting HR rises 2–6 bpm, core temperature increases, and cardiovascular strain is higher at equivalent paces. This depresses EF for approximately one week per cycle — an artifact that looks like fitness decline.

**Implementation:**
- Optional opt-in during onboarding or settings; never surfaced by default
- User tags the start of their luteal phase (or the app infers from a 28-day cycle if the user provides a reference date)
- During tagged luteal-phase sessions: `environmentAffected = true` for EF and decoupling signals; TRIMP accumulates normally (load is real)
- HR zone targets are never adjusted — Z2 builds mitochondrial density regardless of cycle phase; only the fitness signal interpretation changes

---

## Sources (Key)

- Banister TRIMP model: Morton et al. (1990); PMC8894528 review
- CTL/ATL time constants: TrainingPeaks/Coggan empirical; PMC8894528
- HRR1 clinical validity: Cole et al. NEJM 1999; PMC6932299
- EF longitudinal validity: arXiv 2509.05961
- Decoupling mechanism: PubMed 11337829; PMC12271085
- Intensity distribution by tier: PMC3912323, PMC11329428, PMC4621419, PMC9299127
- VO2max interval protocol: PubMed 17414804 (Helgerud et al.)
- Walk/run method: Galloway (1974); PubMed 25467199
- Marathon periodization: PMC11065819, PubMed 35418513
- Half marathon predictors: PMC7496388, PMC10390894
