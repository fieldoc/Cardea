# E2E Happy Path Audit — 2026-04-11

**Device:** Samsung Galaxy (R5CW715EPSB, 1080x2340)
**Method:** mobile-mcp automation with simulated GPS + HR (SimulationController)
**Sim speed:** 10x (Interval scenario)

---

## Bugs

### 1. Notification Leak After Workout Ends (Medium)

**What:** The foreground service notification persists after the workout stops. `dumpsys` shows `numRemovedByApp=1` — the system received the removal call but the notification remains visible.

**Root cause:** Race condition — high-frequency notification updates (every HR tick) likely post an update after `stopForeground(STOP_FOREGROUND_REMOVE)` is called at line 835 of `WorkoutForegroundService.kt`. The final update re-posts the notification after removal.

**Repro:** Start any workout (sim or real), let it complete or stop manually, observe notification shade.

**Fix:** Cancel the notification update coroutine/handler *before* calling `stopForeground()`, or add a `stopping` flag that short-circuits notification updates.

### 2. NavGraph Permission Gate Blocks Sim Workouts (Low)

**What:** `NavGraph.kt:362` checks `PermissionGate.hasAllRuntimePermissions()` before starting a workout. Sim workouts don't use BLE or GPS hardware, but are still blocked if those permissions are denied.

**Impact:** Low — sim mode is a dev/testing feature, not user-facing. But it forces granting BLE+Location permissions even for pure simulation.

**Fix:** Add `|| SimulationController.isActive` to the permission check:
```kotlin
if (!PermissionGate.hasAllRuntimePermissions(context) && !SimulationController.isActive) {
```

---

## Design System Violations

### 3. Wrong Gradient on Bootcamp Phase Chips (Medium)

**What:** The phase indicator chips on the Bootcamp Dashboard use the full 4-stop `CardeaGradient` (pink→purple→blue→cyan) instead of the 2-stop `CardeaCtaGradient` (red→pink). Per the Cardea design system, all buttons and active chips should use `CardeaCtaGradient`.

**Location:** Bootcamp dashboard screen — phase indicator chips.

**Fix:** Replace `CardeaGradient` with `CardeaCtaGradient` on these chip backgrounds.

### 4. Two Gradient Elements on Home Screen (Low)

**What:** The Home screen shows both a gradient-bordered `BootcampHeroCard` and gradient stat chips simultaneously. The 3-tier design hierarchy specifies exactly one gradient accent per screen.

**Impact:** Visual noise — two competing gradient elements dilute the visual hierarchy.

**Fix:** Demote stat chips to Tier 2 (white on glass) or Tier 3 (secondary text on glass).

---

## UI/UX Observations

### 5. Material Blue Sliders in Settings

**What:** Volume and speed sliders in Account/Settings use the default Material3 blue/purple color instead of the Cardea brand gradient or accent color.

**Where:** Account screen → Audio Settings, Simulation Settings.

### 6. Empty Stat Cards on Home Screen

**What:** StatChipsRow displays "0" values when no workout history exists (new user or post-cleanup). No empty state messaging or visual treatment to indicate "no data yet."

### 7. Ambiguous "42k" Label

**What:** A "42k" label appears on the Home screen stat area. Unclear if this means 42 kilometers total, a marathon goal, or something else. No unit label or context.

### 8. Navigation Tab Count

**What:** Bottom nav shows 4 tabs (Home, Workout, History, Account). CLAUDE.md documents 5 tabs (Home, Workout, History, Progress, Account). Either the doc is stale or the Progress tab was removed/merged.

**Action:** Update CLAUDE.md to reflect the actual 4-tab navigation.

### 9. Cyan Focus Borders on Inputs

**What:** Text fields and selection elements show a cyan/teal focus border that doesn't match the Cardea color palette. Likely a default Material3 focus indicator that wasn't themed.

---

## Test Coverage Matrix

| Screen / Feature | Tested | Notes |
|---|---|---|
| Splash screen | Yes | Logo renders, transitions to Home |
| Home dashboard | Yes | BootcampHeroCard, stat chips, layout verified |
| Workout setup (Training tab) | Yes | Config options, BLE scanner UI, Start Run button |
| Simulation settings | Yes | Speed slider, scenario selector, enable/disable |
| Active workout screen | Yes | HR display, zone indicator, timer, distance (at 10x) |
| Workout completion | Yes | Service stops, state resets |
| Post-run summary | No | Sim workouts auto-delete — no post-run nav triggered |
| History list | Yes | Verified empty state (correct for sim-only testing) |
| History detail + Maps heatmap | No | No persisted workouts to view |
| Bootcamp enrollment | Yes | Full flow: intro → goal → config → schedule → confirm |
| Bootcamp dashboard | Yes | Session cards, phase chips, week indicator |
| Bootcamp session generation | Yes | 3 sessions generated for Week 1 |
| Week completion flow | No | Requires completing all week sessions |
| Achievement display | No | No achievements earned during test |
| Light mode | No | Would require theme toggle mid-test |
| Dark mode | Yes | Default theme, all screens verified |
| Auto-pause | No | Sim scenario didn't include pause trigger |
| Edge: no BLE signal | No | Sim provides continuous HR signal |
| Edge: no GPS signal | No | Sim provides continuous GPS data |
| Edge: 0 distance | No | Would require custom sim scenario |
| Onboarding | No | App already onboarded; would require data clear |
| Audio prompts | Partial | Couldn't verify audio output via mobile-mcp |
| Notification during workout | Yes | Visible in notification shade |
| Notification cleanup | Yes | Bug found — persists after workout ends |

---

## What Worked Well

- **Simulation mode is solid** — SimulationClock correctly accelerates virtual time, distance accumulates realistically, HR follows the scenario curve
- **Sim auto-delete is well-designed** — prevents history pollution from test runs (by design, not a bug)
- **Bootcamp enrollment flow is smooth** — multi-step setup with proper back navigation, preset selection works
- **Dark theme is consistent** — glass-morphic cards, gradient accents, and typography all render correctly
- **Navigation transitions are clean** — bottom nav, screen transitions, and back navigation all work as expected
- **BLE scanner UI** — shows "Looking for devices..." with animation, properly indicates no devices found state

---

## Recommendations

1. **Fix notification leak** — highest priority, affects real user experience
2. **Audit gradient usage** — `search_for_pattern "CardeaGradient"` to find all ~30 inline uses that should be `CardeaCtaGradient`
3. **Theme Material3 defaults** — sliders, focus borders, and other Material components need Cardea color overrides
4. **Add sim post-run option** — consider a debug flag to preserve sim workouts temporarily for testing post-run/history screens
5. **Update CLAUDE.md** — reflect 4-tab navigation (not 5)
