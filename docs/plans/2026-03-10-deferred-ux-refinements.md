# Deferred UX Refinement Items

Items identified during the March 10 UX critique and structural review that were NOT implemented in the same session. Each item has a merit assessment and recommended approach.

---

## High Priority

### 1. Live Sensor Status on Home Screen

**Problem:** The Bluetooth icon added to Home header (2026-03-10) is a static nav shortcut — it always shows grey regardless of sensor state. Users want peace of mind that their strap is paired before deciding to run.

**Why deferred:** Sensor scanning only happens when `WorkoutForegroundService` is running. There is no persistent BLE connection state outside an active workout session. Showing a persistent "connected/disconnected" indicator would require either (a) a background BLE scan at app start or (b) persisting the last paired device address and showing a "last seen" status.

**Recommended approach:**
- Persist last-connected device address in `AudioSettingsRepository` (or a new `DevicePrefsRepository`)
- Add `lastPairedDeviceAddress: String?` and `lastPairedDeviceName: String?` to `SetupViewModel`'s preferences
- `HomeViewModel` reads from prefs; shows a colored dot on the Bluetooth icon: green (was connected recently), grey (never paired or unknown)
- Full live scanning on Home is out of scope — the "last seen" pattern is the right signal here

**Files to touch:**
- `data/repository/AudioSettingsRepository.kt` (add device prefs)
- `ui/home/HomeViewModel.kt` (expose `lastPairedDeviceName: String?`)
- `ui/home/HomeScreen.kt` (upgrade Bluetooth pill with color state + device name tooltip)

---

### 2. Audio Settings Consolidation (Setup → Account)

**Problem:** Alert behavior settings (HR buffer, alert delay, cooldown) appear in both `AccountScreen` (global defaults) and `SetupScreen` (per-session overrides). Overlap creates confusion about which values are used.

**Why deferred:** The per-session override use case is genuinely useful (e.g., you want a wider buffer for an easy long run without changing your default). Removing from Setup entirely would break this.

**Recommended approach:**
- Account: shows "Global Defaults" — buffer, delay, cooldown, volume, voice coaching, vibration
- Setup: shows only a **delta indicator** — "Using global defaults" with a small "Customize for this run" expander
- When the expander is open, Setup shows only the fields that _deviate_ from Account defaults, pre-populated with the global values
- This is a "progressive disclosure" pattern that reduces visual noise while preserving power-user access

**Files to touch:**
- `ui/setup/SetupViewModel.kt` (load defaults from `AudioSettingsRepository` as baseline)
- `ui/setup/SetupScreen.kt` (collapse `AlertBehaviorCard` to a single line + expander)
- No Account changes needed

---

### 3. Systematic Typography Token Pass

**Problem:** Several hardcoded `Color.White.copy(alpha = 0.9f)` and `MaterialTheme.colorScheme.onSurface` usages remain in the codebase. If the background ever shifts from pure matte black, text legibility could break.

**Target pattern:**
- `Color.White` → `CardeaTextPrimary`
- `Color.White.copy(alpha = 0.7f–0.9f)` → `CardeaTextSecondary`
- `Color.White.copy(alpha = 0.4f–0.5f)` → `CardeaTextTertiary`
- `MaterialTheme.colorScheme.onSurface` → `CardeaTextPrimary`
- Any `Color(0xFFB6C2D1)` etc → `CardeaTextSecondary`

**Approach:** Run `grep -rn "Color.White.copy\|colorScheme.onSurface\|0xFFB6C2D1\|0xFF8FA4B7" app/src/main/java/com/hrcoach/ui/` and work through each hit. This is a mechanical pass — no logic changes.

---

## Medium Priority

### 4. BootcampScreen — Persist "Last Viewed Tab" in Activity

**Problem:** When enrolled in Bootcamp, tapping Activity nav → "Trends" → back button → tab is now on History (Log). The Log|Trends toggle state is not preserved.

**Recommended approach:** Save `activityLastTab: String` ("log" | "trends") in a lightweight `rememberSaveable` in NavGraph's Activity composable scope, or use `NavBackStackEntry.savedStateHandle`.

---

### 5. Post-Run Summary — Contextual Bootcamp Message

**Problem:** After completing a Bootcamp session, the post-run summary has no acknowledgment that the session was part of a program. Users don't get the "you completed Week 3 Session 2" reinforcement.

**Recommended approach:**
- `BootcampViewModel` already fires `onBootcampWorkoutStarting()` before a session
- Store the `sessionId` in `WorkoutState` alongside `completedWorkoutId`
- `PostRunSummaryScreen` reads this flag; if it's a Bootcamp session, show a `GlassCard` header: "Bootcamp · Week 3 · Session 2 complete"

---

## Low Priority (Disagree / No Implementation Needed)

### Merging History + Progress into a single "Activity" tab

**Assessment:** Implemented as a Log | Trends toggle within the existing 4-tab structure (2026-03-10). The merged approach with a flat tab toggle was chosen over creating a brand new ActivityScreen with nested scaffolds. The current implementation achieves the navigation clarity goal without the scaffold nesting complexity.

**Status:** ✅ Done via toggle in-screen, March 2026.

---

### Sensor Management Move to Account

**Assessment:** BLE scanning is tightly coupled to `SetupViewModel`'s state machine (device scanning → connection → active workout). Moving the scan UI to Account would require a separate scan flow disconnected from the workout start. The sensor shortcut icon on Home (taps to Training tab) is the right level of access. Full relocation is not worth the refactoring cost.

**Status:** ❌ Deferred indefinitely. Sensor shortcut on Home is the accepted solution.
