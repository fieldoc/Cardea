# Bootcamp Engine Hardening Plan

**Date:** 2026-04-12
**Scope:** Fix 4 structural holes in the adaptive bootcamp scheduling engine
**Safety constraint:** All changes must be safe for currently-enrolled testers — no data loss, no session corruption, no retroactive schedule rewrites.

---

## Context

A design review of the bootcamp scheduling engine identified several gaps where the adaptive logic can produce unsafe or suboptimal training prescriptions. This plan addresses the four most impactful issues, ordered by injury-risk severity.

### Key safety property (discovered by research agents)

**Sessions are generated week-by-week on demand, NOT pre-generated at enrollment.**
- `ensureCurrentWeekSessions()` creates sessions for the current week only
- `BootcampSessionCompleter.buildNextWeekEntities()` creates the next week when the current week completes
- Fixing `SessionSelector` / `SessionDayAssigner` logic automatically applies to all future weeks
- Already-generated sessions for an in-progress week are untouched

This means scheduling logic fixes are inherently migration-safe — they affect only future sessions.

---

## Fix 1: Enforce hard-day spacing in initial session assignment

**Priority:** CRITICAL — prevents back-to-back high-intensity sessions
**Risk:** LOW — no DB changes, no enrollment changes, only affects future week generation

### Problem

`SessionDayAssigner.assign()` (lines 18-65) uses a greedy max-spacing heuristic but has no hard constraint preventing consecutive hard sessions. Meanwhile, `SessionRescheduler.availableDays()` (line 47) already enforces `abs(hardDay - candidate) < 2` — but only during mid-week rescheduling.

If a runner's available days force it (e.g., Mon/Tue/Wed with TEMPO + LONG), the initial assignment CAN place two hard sessions on consecutive days.

### Fix

Port the 48-hour gap enforcement from `SessionRescheduler` into `SessionDayAssigner.assign()`:

1. After placing each hard session, validate that `abs(newDay - existingHardDay) >= 2` for all already-placed hard sessions
2. If no valid day exists that satisfies the gap, demote the hard session: replace it with an EASY session (`type = SessionType.EASY`, `presetId = "zone2_base"`) at the same duration (preserves weekly volume)
3. Log when demotion happens for debugging
4. Demotion order: the LAST hard session placed that can't find a valid gap is the one demoted (LONG is placed first and anchored, so it's never the demotion target)

### Files to change

| File | Change |
|------|--------|
| `domain/bootcamp/SessionDayAssigner.kt` | Add gap enforcement in `assign()`, add fallback demotion |
| `domain/bootcamp/SessionDayAssignerTest.kt` | Add test: forced-consecutive days → demotion; existing spacing tests still pass |

### Test cases

1. **Existing test passes:** 3 sessions on days 4,5,6 — LONG on 4, EASY on 5, TEMPO on 6 (gap = 2)
2. **New test — demotion:** 3 sessions (TEMPO + INTERVAL + EASY) on days 1,2,3 — one hard session demoted to EASY with `presetId = "zone2_base"`
3. **New test — 4-day spread:** TEMPO + INTERVAL + LONG + EASY on days 1,3,5,7 — all hard sessions ≥ 2 apart
4. **New test — impossible spacing:** 2 hard sessions with only 2 consecutive available days → one demoted
5. **New test — LONG_RUN_BIAS conflict:** bias=Tue, only days Mon/Tue/Wed, TEMPO+LONG — LONG anchored to Tue by bias, TEMPO on Mon or Wed (gap=1), TEMPO demoted to EASY

---

## Fix 2: CTL-aware gap recovery (prevent phase/fitness desync)

**Priority:** HIGH — prevents overtraining after breaks
**Risk:** MEDIUM — touches gap recovery path in ViewModel, but no schema change

### Problem

`GapAdvisor` rewinds phase position based on days-since-last-run, but never checks CTL against tier thresholds. CTL decays independently (tau=42 days). A runner with CTL=60 taking 20 days off decays to CTL≈37 — barely T1 for a 5K goal (range 35-65). The gap recovery rewinds the phase 1 week but leaves the tier at T1, so they get BUILD tempo sessions (84% HR) they may not be fit for.

Tier demotion prompt requires 3 consecutive weeks below range + user acceptance — by then, the runner has done 3 weeks of overly-hard training.

### Fix

After applying the gap action in `applyGapAdjustmentIfNeeded()`, add a CTL/tier consistency check:

1. Read current CTL/ATL from `adaptiveProfileRepository.getProfile()` (the function is already `suspend` and the repo is already injected into BootcampViewModel)
2. Compute projected CTL: `FitnessLoadCalculator.updateLoads(currentCtl, currentAtl, trimpScore=0f, daysSinceLast=daysSinceLastRun)`
3. Find the appropriate tier for projected CTL via a new `TierCtlRanges.suggestedTierForCtl(goal, projectedCtl)` function
4. If suggested tier < current tier AND gap strategy is MEANINGFUL_BREAK or worse:
   - Auto-demote tier to suggested level
   - Update enrollment's `tierIndex`
   - Show welcome-back message: "Welcome back! We've eased your intensity to match your current fitness."
4. If suggested tier >= current tier, no change (don't auto-promote via gap recovery)

### Files to change

| File | Change |
|------|--------|
| `domain/bootcamp/TierCtlRanges.kt` | Add `suggestedTierForCtl(goal, ctl): Int` — returns highest tier whose lower bound ≤ ctl |
| `ui/bootcamp/BootcampViewModel.kt` | In `applyGapAdjustmentIfNeeded()`, add CTL projection + tier check after phase rewind |
| `domain/bootcamp/TierCtlRangesTest.kt` | Test `suggestedTierForCtl()` for each goal at boundary values |
| `ui/bootcamp/BootcampViewModelTest.kt` (or new test) | Test gap recovery with CTL decay → tier demotion |

### Design decisions

- **Auto-demote vs prompt:** Auto-demote. The runner has been away; asking them to make a tier decision on return adds friction. The welcome-back message explains the change. They can always be re-promoted through the normal CTL-based tier-up flow.
- **Only demote, never promote via gap recovery:** Prevents edge case where a gap advisor + stale high CTL accidentally promotes.
- **Only for MEANINGFUL_BREAK and worse:** Minor slips (0-14 days) don't warrant tier checks — CTL hasn't decayed enough to matter.
- **No interaction with Fix 3 (transition week):** Auto-demotion reduces intensity (T1→T0 = fewer hard sessions). Transition weeks are for the opposite direction (increasing intensity). A gap-demoted runner does NOT get a transition week — they get the simpler T0 curriculum immediately.
- **Boundary behavior:** At exact tier boundaries (e.g., CTL=35 for RACE_5K, which is T0 upper = T1 lower), `suggestedTierForCtl` returns the HIGHER tier. Runners at the boundary keep their current tier — demotion only fires when CTL falls strictly below.

---

## Fix 3: Tier promotion transition week (soften T0→T1 intensity cliff)

**Priority:** HIGH — prevents 68%→84% HR jump in one session
**Risk:** MEDIUM — requires a DB migration (additive column) + new preset

### Problem

When promoted from T0 to T1, the next generated week immediately includes a full tempo session at 84% HR reserve. The previous week was all-easy at 68%. That's a 16-point HR reserve jump with no bridge.

### Fix

**A) Add a transition preset:**
- `aerobic_tempo_intro`: 76% HR reserve, 12-minute main block (vs 20 min for full tempo), warm-up 65%, cool-down 60%. Same DISTANCE_PROFILE structure as aerobic_tempo but gentler.

**B) Track when tier changed:**
- Add `lastTierChangeWeek: Int?` column to `BootcampEnrollmentEntity` (nullable, default null)
- Set it to `absoluteWeek` when user accepts tier promotion
- Migration 16→17: `ALTER TABLE bootcamp_enrollments ADD COLUMN lastTierChangeWeek INTEGER DEFAULT NULL`

**C) SessionSelector checks transition window:**
- If `absoluteWeek - lastTierChangeWeek <= 1` (within 2 weeks of tier-up), use `aerobic_tempo_intro` instead of `aerobic_tempo` for TEMPO sessions
- After 2 weeks, normal `aerobic_tempo` kicks in automatically

### Files to change

| File | Change |
|------|--------|
| `domain/preset/PresetLibrary.kt` | Add `aeroTempoIntro()` preset — 76% HR, 12-min block |
| `data/db/BootcampEnrollmentEntity.kt` | Add `lastTierChangeWeek: Int? = null` field |
| `data/db/AppDatabase.kt` | Add MIGRATION_16_17 for new column |
| `di/AppModule.kt` | Register MIGRATION_16_17 in `addMigrations()` chain |
| `domain/bootcamp/SessionSelector.kt` | Accept `lastTierChangeWeek` param; use intro preset in transition window |
| `domain/bootcamp/PhaseEngine.kt` | Pass `lastTierChangeWeek` through to `SessionSelector` in both `planCurrentWeek()` and `lookaheadWeeks()` |
| `domain/bootcamp/BootcampSessionCompleter.kt` | Thread `lastTierChangeWeek` from enrollment into `buildNextWeekEntities()` → `PhaseEngine` |
| `ui/bootcamp/BootcampViewModel.kt` | Set `lastTierChangeWeek` in `acceptTierChange()`; thread it through `ensureCurrentWeekSessions()` |
| Tests for PresetLibrary, SessionSelector, migration |

### Migration safety

- Additive column with null default — existing enrollees get null
- Null means "no recent tier change" → no transition behavior → correct for runners who promoted weeks/months ago
- New enrollees who tier-up in the future get the transition automatically

---

## Fix 4: Relax strides gate from 4 to 3 runs/week

**Priority:** LOW — quality-of-life improvement
**Risk:** VERY LOW — single line change

### Problem

BUILD phase strides require `runsPerWeek >= 4` (SessionSelector.kt line 74). Strides are 20-second neuromuscular efforts with full recovery — among the lowest-stress quality sessions. A T2 runner training 3x/week in BUILD never gets strides, missing valuable form work.

### Fix

Change `runsPerWeek >= 4` to `runsPerWeek >= 3` in the strides inclusion condition. **However**, simply changing the gate creates a session-count overflow: at 3 runs/week with T2 in BUILD, the engine would generate EASY + TEMPO + STRIDES + LONG = 4 sessions for 3 days (because `easyCount` floors at 1 via `coerceAtLeast`).

**Fix:** When strides are included and would cause total sessions > `runsPerWeek`, strides REPLACE the easy run instead of being additive. Change the `easyCount` floor from `coerceAtLeast(1)` to `coerceAtLeast(0)` when strides are present. A strides session already includes easy jogging recovery between efforts, so it doubles as the "easy stimulus" for that day.

### Files to change

| File | Change |
|------|--------|
| `domain/bootcamp/SessionSelector.kt` line 74 | `runsPerWeek >= 4` → `runsPerWeek >= 3` |
| `domain/bootcamp/SessionSelector.kt` ~line 86 | Change `easyCount` floor: `coerceAtLeast(if (includeStrides) 0 else 1)` |
| `domain/bootcamp/SessionSelectorTest.kt` | Add test: 3 runs/week + T2 + BUILD → strides included, total sessions = 3 (no easy run) |
| `domain/bootcamp/SessionSelectorTest.kt` | Add test: 4 runs/week + T2 + BUILD → strides + 1 easy (existing behavior preserved) |
| Update existing test if one asserts strides excluded at 3 runs/week |

---

## Implementation order

```
Fix 1 (hard-day spacing)     — no DB change, pure logic
    ↓
Fix 4 (strides gate)         — no DB change, single line
    ↓
Fix 2 (CTL-aware gap)        — no DB change, ViewModel logic
    ↓
Fix 3 (transition week)      — DB migration 16→17, new preset
```

Rationale: DB-free fixes first (easier to validate, lower risk), migration last.

## Out of scope (deferred)

| Issue | Why deferred |
|-------|-------------|
| Static HR targets (no per-runner adaptation) | Major architectural change — needs its own design cycle |
| Non-standard TRIMP formula | Changing it recalibrates all CTL thresholds — too risky mid-enrollment |
| Weather/heat adjustment | Feature, not a bug — separate design doc |
| HRmax drift user feedback | HrCalibrator already auto-updates silently; UI feedback is a separate UX task |
| EVERGREEN at T0 | Rare edge case (Cardio Health beginners); acceptable as-is |

---

## Verification

After implementation:
1. `./gradlew testDebugUnitTest` — all new and existing tests pass
2. Manual review: existing `SessionSelectorTest`, `SessionDayAssignerTest`, `BootcampSessionCompleterTest` still green
3. No changes to `BootcampDao` interface → `FakeBootcampDao` in tests unaffected (except if Fix 3 adds a column, which Room handles via entity, not DAO method change)
4. Build: `./gradlew assembleDebug` — clean compile
