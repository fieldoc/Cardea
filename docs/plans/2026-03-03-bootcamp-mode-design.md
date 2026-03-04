# Bootcamp Mode — Design Document

**Date:** 2026-03-03
**Status:** Approved design, pending implementation plan

## Overview

Bootcamp is a coached, multi-week training program that prescribes distance and HR profiles
based on the user's stated goal, current fitness (pace-HR data), and available time. The
program adapts session-by-session using real performance data, handles training gaps
gracefully, and scales in technicality with the ambition of the goal.

**Core architecture:** Phase Engine (Approach B) — a phase-based scheduler that maps goals
to phase arcs, selects sessions from the existing preset library, and uses a Gap Advisor to
handle breaks of any length.

---

## 1. Goals & Complexity Ladder

Four goal tiers. Training technicality and session variety scale with ambition:

| Goal | Tier | Phase Arc | Technical Workouts |
|---|---|---|---|
| **Cardio Health** | 1 | Base -> Build (cycles) | Zone 2 only, occasional Zone 3 |
| **5K / 10K** | 2 | Base -> Build -> Peak -> Taper | Zone 2 + Zone 4 tempo |
| **Half Marathon** | 3 | Base -> Build -> Peak -> Taper | Zone 2 + Zone 4 + long run progression |
| **Marathon** | 4 | Base -> Build -> Peak -> Taper (extended) | Full spectrum: Zone 2/4/5 + long runs + race sims |

**Time commitment** scales sessions within a tier. A 20 min/session Cardio Health user never
sees a 60-min long run; a marathon runner who can only give 30 min/session gets a compressed
but still periodized plan.

**Goal + time commitment are always editable.** Changing a goal mid-program prompts:
"Your current progress will carry over — we'll adjust your plan from here."

### Time Commitment Soft Limits

Per-goal minimums are warnings, not hard blocks:

| Goal | Suggested Min | Warn Below | Never Prescribe Below |
|---|---|---|---|
| Cardio Health | 20 min | 15 min | 10 min |
| 5K / 10K | 25 min | 20 min | 15 min |
| Half Marathon | 30 min | 25 min | 20 min |
| Marathon | 45 min | 30 min | 20 min |

**Warning behavior:** Setting a commitment below the warn threshold shows a friendly note:
"Marathon training typically needs at least 30 min per session to build the aerobic base.
You can continue with 25 min — some weeks will be adapted — but consider Half Marathon if
time is consistently tight." One tap to dismiss, no block.

**Persistent under-commitment:** If sessions are consistently shorter than the warn threshold
for 3+ weeks, a gentle prompt appears: "Looks like your sessions have been shorter lately.
Want to adjust your time commitment, switch to a [lower goal] plan, or just go free-form
for a while? You can always come back to Bootcamp."

Three exits, no guilt. "Free-form for a while" suspends Bootcamp without deleting progress.

**Long run exceptions:** Goals with long runs (half, marathon) will occasionally prescribe
sessions that exceed the committed time. These are flagged: "This week's long run is 65 min
— a bit over your usual 45. Tap to shorten or keep it."

---

## 2. Phase Engine Architecture

Each goal maps to a phase arc. The engine tracks the current phase and selects sessions from
the preset library to hit that phase's intensity targets.

### Phase Definitions

| Phase | Duration | 80/20 Target | Session Mix (per week) |
|---|---|---|---|
| **Base** | 3-6 weeks | 90% easy / 10% moderate | 2-3 Zone 2 runs + 1 easy long |
| **Build** | 4-6 weeks | 80% easy / 20% hard | 2 Zone 2 + 1 Zone 4 tempo + 1 long |
| **Peak** | 2-3 weeks | 75% easy / 25% hard | 2 Zone 2 + 1 Zone 4/5 + 1 long (race sim) |
| **Taper** | 1-2 weeks | 85% easy / 15% hard | Shorter versions of Build sessions, reduced volume |

**Cardio Health** cycles Base -> Build -> Base indefinitely. Never sees intervals or race sims.

**Preset mapping:** The existing `PresetCategory` enum maps directly to phases:
- `BASE_AEROBIC` -> Base phase
- `THRESHOLD` -> Build/Peak phase
- `INTERVAL` -> Peak only (Tier 3-4 goals)
- `RACE_PREP` -> Peak/Taper (Tier 3-4 goals)

The session selector picks by category + duration constraint rather than hardcoding specific
presets, keeping it flexible as the preset library grows.

### Progression Within Phases

Following evidence-based 2-on-1-off periodization: mileage/intensity increases for 2-3 weeks
then reduces by 20-30% for a recovery week before continuing. The engine tracks weekly load
and enforces this pattern.

Hard efforts are spaced at least 2 days apart. No back-to-back hard sessions.

---

## 3. Gap Advisor

Every time the user opens Bootcamp, the Gap Advisor evaluates days-since-last-run and
determines what happens next.

### Gap Tiers

| Gap | Label | Action |
|---|---|---|
| **0-3 days** | On track | Resume exactly where scheduled |
| **4-7 days** | Minor slip | Slide the missed session to today, keep the rest of the week intact |
| **8-14 days** | Short break | Insert one easy Zone 2 run as a "return session" before resuming. No phase change. |
| **15-28 days** | Meaningful break | Rewind 1 week in the current phase. First session back is always easy. Note: "Welcome back — let's ease in." |
| **29-60 days** | Extended break | Rewind to start of current phase. Optionally rewind to Base if metrics suggest fitness loss. |
| **61-120 days** | Long absence | Drop back to Base phase. Run a re-calibration "Check-In Run" to re-measure pace-HR before resuming. |
| **120+ days** | Full reset | Treat as new user. Run a Discovery Run. Prior goal and preferences remembered — only the program restarts. |

### Fitness Verification (gaps >= 29 days)

Rather than blindly trusting gap length, the first session back includes a quiet fitness
check: the app watches pace-HR efficiency during the run and compares to stored baseline.
If the user is fitter than the gap suggests (e.g. they were training without the app), the
plan advances accordingly. If less fit, it holds them back.

---

## 4. Calibration & Discovery Run

### New Users (< 3 prior sessions)

A "Discovery Run" disguised as a friendly first workout. Prescribes a gentle progression:
easy -> slightly harder -> easy cool-down. This gives the engine a broader HR range to
characterize from a single session. The user sees "Discovery Run — find your rhythm" with
no mention of calibration.

### Returning Users (>= 3 prior sessions)

Skip calibration entirely. Use existing pace-HR buckets from `AdaptiveProfile` to start
Week 1 immediately.

### Re-calibration After Long Gaps

Gaps of 61+ days trigger a "Check-In Run" — same as a Discovery Run but shorter (15-20
min), positioned as a return-to-running session rather than a first-timer experience.

---

## 5. Plan View — Hybrid Phase + Dynamic Sessions

Users see a high-level phase view: "Week 3 of 8 — Base Building" with a weekly session
list. But sessions are generated dynamically and can shift based on performance.

- **Current week:** Fully locked sessions with day, duration, type
- **Next 1-2 weeks:** Rough outline (session types and target days)
- **Future weeks:** Phase label only ("Build phase starts Week 5")

Sessions within the current week can be long-pressed to: move to another day, swap with
another session, or skip (triggering Gap Advisor minor-slip logic).

---

## 6. UX & Navigation

### First-Time Discovery (Home Screen)

Full-width hero card above existing Home content. Dismissible with a single tap, never
shown again after dismissal:

```
+---------------------------------------------+
|  Introducing Bootcamp                   [x]  |
|                                              |
|  A coaching program that builds your         |
|  fitness, adapts to your schedule, and       |
|  gets smarter every run.                     |
|                                              |
|          [ Start my program ]                |
+---------------------------------------------+
```

### Active Bootcamp Users (Home Screen)

Compact, always-visible status card:

```
+---------------------------------------------+
|  Bootcamp - Week 3 of 8 - Base Building      |
|  Today: 35 min Easy Run  -------- Zone 2     |
|                    [ Start Run ]             |
+---------------------------------------------+
```

### Workout Tab — Mode Selection

Bootcamp appears as the first option, visually distinguished (gradient border). The classic
three modes (Steady State, Distance Profile, Free Run) remain below, unchanged. Users who
dismissed the hero card and never enrolled see a smaller "Try Bootcamp" row.

### Onboarding Flow (~3 screens)

1. **What's your goal?** — Four cards: Cardio Health / 5K-10K / Half Marathon / Marathon
2. **How much time per run?** — Slider with inline soft-limit warnings
3. **How often can you run?** — 2 / 3 / 4 / 5+ days per week

If >= 3 prior sessions exist -> jump to Week 1. Otherwise -> Discovery Run is first session.

---

## 7. Notifications & Scheduling

### Scheduling Philosophy

Bootcamp suggests a rhythm, never demands one. Users set preferred days during onboarding
(e.g. Mon/Wed/Sat). Sessions are assigned to those slots as soft targets.

### Notification Tiers

| Trigger | Notification | Follow-up |
|---|---|---|
| Scheduled run today | Morning: "Easy 30 min run on deck today" | None if dismissed |
| Missed run | Next morning: "Yesterday's run moved to today. Tap to reschedule." | No further nagging |
| Good streak | After 3 completed in a row: "3 in a row — nice consistency." | None |
| Approaching absence | Day 5: "Your next run is a quick 25-min easy session whenever you're ready." | One more at day 10, then silence |
| Phase milestone | Phase transition: "Base phase complete — moving into Build next week." | None |

### Rules

- Never more than one notification per day
- Never guilt-trips
- Goes silent after the day-10 reminder — Gap Advisor handles their return
- Never interrupts a designated rest day

### Schedule Editing

Long-press any upcoming session to: move to another day, swap with a different session that
week, or skip. Skipping triggers Gap Advisor's minor-slip logic.

---

## 8. Data Model

### New Room Entities

**BootcampEnrollment:**
- `id: Long` (PK, auto-generate)
- `goalType: String` (CARDIO_HEALTH, 5K_10K, HALF_MARATHON, MARATHON)
- `targetMinutesPerRun: Int`
- `runsPerWeek: Int`
- `preferredDays: String` (JSON array of day-of-week ints)
- `startDate: Long` (epoch millis)
- `currentPhaseIndex: Int` (0 = Base, 1 = Build, 2 = Peak, 3 = Taper)
- `currentWeekInPhase: Int`
- `status: String` (ACTIVE, PAUSED, COMPLETED)
- `heroCardDismissed: Boolean` (SharedPreferences, not Room)

**BootcampSession:**
- `id: Long` (PK, auto-generate)
- `enrollmentId: Long` (FK to BootcampEnrollment)
- `weekNumber: Int`
- `dayOfWeek: Int`
- `sessionType: String` (EASY, LONG, TEMPO, INTERVAL, DISCOVERY, CHECK_IN)
- `presetId: String?` (nullable, links to PresetLibrary)
- `targetMinutes: Int`
- `status: String` (SCHEDULED, COMPLETED, SKIPPED, RESCHEDULED)
- `completedWorkoutId: Long?` (FK to workouts table, nullable until completed)

### Integration With Existing Data

Completed Bootcamp sessions link to the same `WorkoutEntity` and `TrackPointEntity` tables
that free-form runs use. History, Progress, and PostRun screens work identically. Bootcamp
adds context (which phase, which session type) but does not duplicate workout data.

The `AdaptiveProfile` pace-HR buckets continue to accumulate across all sessions (Bootcamp
and free-form). The phase engine reads from `AdaptiveProfile` and `WorkoutMetricsEntity` to
inform session selection and fitness verification.

---

## 9. Exercise Science Foundations

The program design is grounded in established training science:

- **80/20 polarized training**: 80% easy (Zone 1-2), 20% hard (Zone 4-5). Avoids the
  "murky middle" of Zone 3 except during Build phase moderate sessions.
- **Periodization**: 2-on-1-off weekly load progression (increase 2-3 weeks, reduce 20-30%
  for recovery week). Supported by research showing ~12% performance gains vs. unstructured.
- **Detraining curves**: Minimal fitness loss in first 2 weeks; measurable VO2max decline
  at 2-4 weeks; 10-25% loss at 1-3 months. Gap Advisor thresholds track these curves.
- **Recovery science**: 48-72 hours between hard efforts. No back-to-back hard sessions.
  Long runs count as hard efforts for recovery purposes.
- **Beginner safeguards**: Tier 1-2 goals enforce slower progression, longer Base phase,
  and exclude high-intensity intervals until Build phase.
