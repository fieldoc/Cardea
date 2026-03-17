# Preferred Days: Onboarding Step & Settings Reslot

**Date:** 2026-03-16
**Status:** Approved

## Problem

The bootcamp onboarding wizard never asks the user which days of the week they want to run. Instead, `completeOnboarding()` calls `defaultPreferredDays(runsPerWeek)` which hardcodes defaults (e.g. Mon/Wed/Sat for 3 runs). Users must discover the Settings screen to change days after enrollment.

Additionally, when a user changes preferred days in Settings mid-program, the change updates the enrollment record but does not reslot the current week's scheduled sessions — so the calendar doesn't reflect the change until the next week.

## Solution

### 1. New Onboarding Step: Day Picker

Add a new final step to the onboarding wizard, after the Runs/Week frequency step and before `completeOnboarding()`.

**UI:**
- Reuses the existing `DayChipRow` component (tap cycles NONE → AVAILABLE → LONG_RUN_BIAS, long-press toggles BLACKOUT)
- Includes the existing legend strip (open / run / long / blocked)
- Shows count indicator: "Select exactly N days · K selected" with error color when count != runsPerWeek
- Minimal subtitle: "Tap to toggle, hold to block"
- Pre-populated with `defaultPreferredDays(runsPerWeek)` so the user has a reasonable starting point
- "Start Program" button disabled when selected count != runsPerWeek

**State:**
- New field `onboardingPreferredDays: List<DayPreference>` in `BootcampUiState`, initialized to `emptyList()`
- When `setOnboardingRunsPerWeek()` is called, also recompute `onboardingPreferredDays` via `defaultPreferredDays(newRuns)`
- When `setOnboardingGoal()` triggers the first run through, also initialize preferred days if empty

**ViewModel:**
- `cycleOnboardingDayPreference(day: Int)` — cycles the day through NONE → AVAILABLE → LONG_RUN_BIAS → NONE (same logic as `BootcampSettingsViewModel.cycleDayPreference`)
- `toggleOnboardingBlackoutDay(day: Int)` — toggles BLACKOUT on/off (same logic as Settings)
- `completeOnboarding()` reads `onboardingPreferredDays` from state instead of calling `defaultPreferredDays()`

**Step numbering:**
- Current flow: Step 0 (Goal) → Step 1 (Finishing Time, race only) → Step 2 (Easy Run Duration) → Step 3 (Frequency) → Done
- New flow: Step 0 (Goal) → Step 1 (Finishing Time, race only) → Step 2 (Easy Run Duration) → Step 3 (Frequency) → Step 4 (Day Picker) → Done
- The step index variables `timeStep` and `freqStep` stay the same; add `daysStep = freqStep + 1`

### 2. Settings Reslot on Day Change

When the user saves day preference changes in Settings, immediately reslot the current week's SCHEDULED/DEFERRED sessions.

**Logic (in `BootcampSettingsViewModel.saveSettings()`):**
1. After calling `bootcampRepository.updatePreferredDays()`, fetch current week sessions
2. Call `BootcampRepository.computeReslottedDays(sessions, newDays)` to get new day assignments
3. For each reslotted session where the day changed, call `bootcampRepository.rescheduleSession(sessionId, newDay)`

This uses the existing `computeReslottedDays` helper which already handles:
- Preserving completed sessions in place
- Excluding completed days from the available pool
- Falling back to original day when not enough new days available

## Files Changed

| File | Change |
|------|--------|
| `BootcampUiState.kt` | Add `onboardingPreferredDays` field |
| `BootcampViewModel.kt` | Add `cycleOnboardingDayPreference()`, `toggleOnboardingBlackoutDay()`; update `completeOnboarding()`, `setOnboardingRunsPerWeek()` |
| `BootcampScreen.kt` | Add `OnboardingStep4Days` composable; update `OnboardingWizard` step routing |
| `BootcampSettingsViewModel.kt` | Update `saveSettings()` to reslot current week sessions |

## Testing

- Unit test: `cycleOnboardingDayPreference` cycles levels correctly
- Unit test: `completeOnboarding` uses user-selected days, not defaults
- Unit test: Settings save with day changes reslots current week sessions
- Manual: walk through onboarding, verify days appear and validate correctly
- Manual: change days in Settings mid-program, verify current week updates immediately
