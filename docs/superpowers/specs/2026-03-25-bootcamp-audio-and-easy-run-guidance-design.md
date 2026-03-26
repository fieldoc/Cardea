# Bootcamp Audio & Easy-Run Guidance Fix

**Date:** 2026-03-25
**Status:** Approved
**Branch:** `fix/bootcamp-audio-and-easy-run-guidance`

## Problem

Two high-severity issues observed during a 26-minute timed easy run launched from the bootcamp:

1. **No audio prompts:** Zero coaching cues fired — no HALFWAY, no WORKOUT_COMPLETE, no time-remaining callouts. The runner had no guidance at all.
2. **No pace/HR guidance for "easy" runs:** The runner reported running too hard with no SLOW_DOWN alert. Easy runs should enforce an HR ceiling (Zone 2 cap).

## Root Cause Analysis

### Issue 1: No audio prompts

Two compounding failures suppress all coaching events:

**Failure A — FREE_RUN fallback when maxHr is missing:**
`buildConfigJson()` in `BootcampScreen.kt:2748` falls back to `WorkoutConfig(mode = FREE_RUN)` when `maxHr == null` or the preset lookup fails. In FREE_RUN mode:
- `totalDurationSeconds()` returns `null` → HALFWAY and WORKOUT_COMPLETE never fire
- `targetHrAt*()` returns `null` → `zoneStatus` is forced to `NO_DATA` → AlertPolicy returns immediately

**Failure B — Session duration not wired into config:**
Even when the preset resolves correctly (maxHr is known), the `zone2_base` preset builds a `STEADY_STATE` config with **no duration info**. The session's `minutes` field (e.g., 26) is never passed into the `WorkoutConfig`. So `totalDurationSeconds()` returns `null` even for properly configured workouts, and HALFWAY/WORKOUT_COMPLETE still never fire.

### Issue 2: No pace/HR guidance

In the service tick loop (`WorkoutForegroundService.kt:382`), when `target == null` (FREE_RUN), `zoneStatus` is forced to `NO_DATA`. `AlertPolicy.handle()` immediately returns on `NO_DATA` (line 26), so SLOW_DOWN/SPEED_UP never fire.

### Bonus: Missing `strides_20s` preset

`SessionPresetArray.stridesTier2()` and `stridesTier3()` reference `presetId = "strides_20s"`, but `PresetLibrary.ALL` has no entry for it. Any strides bootcamp session silently falls back to FREE_RUN.

## Solution: Approach A — Fix at the Config-Building Layer

All changes are concentrated in the config-building and UI layers. The service, ZoneEngine, AlertPolicy, and CoachingEventRouter remain untouched.

### Change 1: Wrap STEADY_STATE + duration into a timed segment

**File:** `ui/bootcamp/BootcampScreen.kt` — `buildConfigJson()`

After calling `preset.buildConfig(maxHr)`, if:
- the config is `STEADY_STATE`
- `steadyStateTargetHr` is non-null
- `session.minutes > 0`

…then wrap the target HR into a single time-based `HrSegment` and switch mode to `DISTANCE_PROFILE`:

```kotlin
config = config.copy(
    mode = WorkoutMode.DISTANCE_PROFILE,
    segments = listOf(
        HrSegment(
            durationSeconds = session.minutes * 60,
            targetHr = config.steadyStateTargetHr!!,
            label = preset.name
        )
    ),
    steadyStateTargetHr = null
)
```

**Why DISTANCE_PROFILE?** This mode already supports time-based segments — `norwegian4x4`, `hiit3030`, and `hillRepeats` all use it with `durationSeconds`. The service checks `isTimeBased()` and routes through `targetHrAtElapsedSeconds()` / `totalDurationSeconds()` accordingly. A single-segment "distance profile" is mechanically correct.

**Effect on the service tick loop (no code changes needed):**
- `workoutConfig.isTimeBased()` → `true`
- `targetHrAtElapsedSeconds()` → returns the target HR for the full duration
- `totalDurationSeconds()` → returns `minutes * 60`
- `CoachingEventRouter` fires HALFWAY at `minutes * 30` seconds and WORKOUT_COMPLETE at `minutes * 60`
- `ZoneEngine.evaluate()` gets a real target → produces IN_ZONE / ABOVE_ZONE / BELOW_ZONE
- `AlertPolicy` fires SLOW_DOWN / SPEED_UP when out of zone for > alertDelaySec

### Change 2: Block start when maxHr is missing

**File:** `ui/bootcamp/BootcampScreen.kt`

At both "Start Run" button callsites (~line 1674 session detail card, ~line 1987 today's hero card):

- Set `enabled = (maxHr != null)` on the Button
- When `maxHr == null`, show an inline `Text`: "Set your max heart rate in Account settings to enable coached runs"

This eliminates the silent FREE_RUN degradation path entirely from bootcamp flows.

### Change 3: Add missing `strides_20s` preset

**File:** `domain/preset/PresetLibrary.kt`

Add a `strides20s()` function and include it in the `ALL` list:

- **id:** `"strides_20s"`
- **Category:** `INTERVAL`
- **Structure:** Warm-up (5 min, 65% maxHr) → 6x [20s sprint at 90% maxHr + 60s recovery at 62% maxHr] → Cool-down (5 min, 60% maxHr)
- **Total duration:** ~18 min
- **Buffer:** 5 BPM

## Files Changed

| File | Change |
|------|--------|
| `ui/bootcamp/BootcampScreen.kt` | `buildConfigJson`: wrap STEADY_STATE + duration into timed segment. Disable start button when maxHr null. |
| `domain/preset/PresetLibrary.kt` | Add `strides20s()` preset and include in `ALL` |

## Testing Strategy

1. **Unit test: `buildConfigJson` wrapping** — Verify STEADY_STATE preset + session.minutes → DISTANCE_PROFILE config with correct single segment (durationSeconds = minutes * 60, targetHr = preset target)
2. **Unit test: CoachingEventRouter with timed segment** — Verify HALFWAY fires at 50% of totalDurationSeconds, WORKOUT_COMPLETE at 100%
3. **Unit test: AlertPolicy with real zone status** — Confirm SLOW_DOWN fires for ABOVE_ZONE, SPEED_UP for BELOW_ZONE (existing tests, verify no regressions)
4. **Unit test: strides_20s preset** — Verify buildConfig(maxHr=200) produces correct segment count, durations, and target HRs
5. **UI test: maxHr gate** — Verify start button is disabled when maxHr is null, enabled when set

## Out of Scope

- Adding a `plannedDurationSeconds` field to `WorkoutConfig` (rejected: more surface area for no benefit)
- Conservative default maxHr estimation (rejected: inaccurate coaching is worse than no coaching)
- HR ceiling for FREE_RUN mode (not needed once bootcamp always has proper configs)
- Freestyle/setup flow changes (those already handle mode selection correctly)
