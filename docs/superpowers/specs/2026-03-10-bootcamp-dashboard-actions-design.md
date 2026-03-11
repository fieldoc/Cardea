# Bootcamp Dashboard Actions — Design Spec

**Date:** 2026-03-10
**Status:** Approved
**Scope:** `BootcampScreen`, `BootcampViewModel`, `NavGraph`

---

## Problem

Three actionable gaps on the Bootcamp dashboard:

1. **Missed session card is read-only** — `StatusCard` shows "Missed session detected" with no way to dismiss or act on it
2. **Preferred days strip is not interactive** — `PreferredDaysStrip` is a passive display; tapping it does nothing despite being the most obviously editable element on the dashboard
3. **`BootcampSettingsScreen` is unreachable** — the screen exists and is fully built but is not registered in `NavGraph` and has no entry point; the 3-dot menu only exposes pause/resume and end program

---

## Design

### Fix 1 — Missed session card

Replace the read-only `StatusCard` with a new private `MissedSessionCard` composable in `BootcampScreen.kt`.

**Dismiss:**
- `dismissed: Boolean` state via `remember { mutableStateOf(false) }` in `ActiveBootcampDashboard`
- Session-only — no DB write, resets on recompose
- Card shown only when `uiState.missedSession && !dismissed`
- ✕ `IconButton` positioned top-right sets `dismissed = true`

**Reschedule:**
- Full-width "Reschedule" button calls `onReschedule()` — the existing callback that opens `RescheduleBottomSheet`
- This is a new call site; `RescheduleBottomSheet` and its wiring are unchanged

**No ViewModel changes. No new `BootcampUiState` fields.**

---

### Fix 2 — Preferred days strip → bottom sheet

**`PreferredDaysStrip`:**
- Add `onClick: () -> Unit` parameter
- Wrap container in `Modifier.clickable(onClick)`
- Add a `"Tap to edit"` hint label below the pills (tertiary text, italic)

**Sheet trigger:**
- `showDaySheet: Boolean` via `remember { mutableStateOf(false) }` in `ActiveBootcampDashboard`
- Tapping the strip sets `showDaySheet = true`

**`ModalBottomSheet` contents:**
- Handle bar
- Title: "Preferred training days"
- Subtitle: "Tap to toggle · Long-press to block a day out"
- Legend row: run / open / blocked chips
- 7-day chip row (M–S)
  - Tap: `cycleDayPreference(day)`
  - Long-press + haptic: `toggleBlackoutDay(day)`
- "Done" button dismisses the sheet

**New `DayChipRow` composable:**
- Private composable in `BootcampScreen.kt`
- Visual states: `AVAILABLE` (pink gradient fill), `NONE` (glass border), `LONG_RUN_BIAS` (pink gradient + star badge), `BLACKOUT` (dark red tint + × badge)
- Mirrors the visual design of the existing `DayChipRow` in `BootcampSettingsScreen.kt` — that one stays private and unchanged

**`BootcampViewModel` additions:**
- `fun cycleDayPreference(day: Int)` — delegates to `BootcampRepository.cycleDayPreference(enrollmentId, day)`
- `fun toggleBlackoutDay(day: Int)` — delegates to `BootcampRepository.toggleBlackoutDay(enrollmentId, day)`
- Same repository calls already used by `BootcampSettingsViewModel`; `enrollmentId` is already available in the VM

---

### Fix 3 — Program settings navigation

**`NavGraph.kt`:**
- Add `composable("bootcampSettings") { BootcampSettingsScreen(onBack = { navController.popBackStack() }) }`

**`BootcampScreen` composable signature:**
- Add `onNavigateToSettings: () -> Unit` parameter

**3-dot `DropdownMenu`:**
- Add "Program settings" `DropdownMenuItem` above the existing pause/resume item
- Calls `onNavigateToSettings()`

**`NavGraph.kt` call site:**
- Wire `onNavigateToSettings = { navController.navigate("bootcampSettings") }` at the `BootcampScreen(...)` call

---

## What is NOT changing

- `RescheduleBottomSheet` — untouched, new call site only
- `BootcampSettingsScreen` / `BootcampSettingsViewModel` — untouched
- `BootcampUiState` — no new fields
- All DAO, repository, and domain logic — no changes

---

## Files affected

| File | Change |
|------|--------|
| `ui/bootcamp/BootcampScreen.kt` | New `MissedSessionCard`, new `DayChipRow`, sheet trigger state, `PreferredDaysStrip` onClick, settings menu item, `onNavigateToSettings` param |
| `ui/bootcamp/BootcampViewModel.kt` | Add `cycleDayPreference()` and `toggleBlackoutDay()` |
| `ui/navigation/NavGraph.kt` | Register `bootcampSettings` route, wire `onNavigateToSettings` |

---

## Testing

No pure logic to unit-test (all changes are UI wiring or thin VM delegation).

Verify manually:
1. Missed session card shows ✕ and Reschedule; ✕ hides card for the session; Reschedule opens bottom sheet
2. Tapping preferred days strip opens the day sheet; chips toggle correctly; Done closes sheet; changes persist on re-open
3. 3-dot menu shows "Program settings"; tapping navigates to `BootcampSettingsScreen`; back returns to dashboard
