# Copy Polish Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace every chatty, informal, or inconsistent user-visible string with minimal-warm, production-grade copy across all screens.

**Architecture:** Pure copy changes only — no logic, no new files, no structural changes. Each task touches one file (or a tightly coupled pair), then commits. Design doc: `docs/plans/2026-03-03-copy-polish-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, Android string resources (strings.xml)

---

### Task 1: strings.xml — shared button and dialog strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Apply all changes**

Replace these strings exactly:

| Key | New value |
|---|---|
| `button_start_workout` | `Start Run` |
| `screen_setup_title` | `New Run` |
| `button_pause` | `Pause` |
| `button_resume` | `Resume` |
| `button_stop` | `End Run` |
| `dialog_stop_workout_title` | `End this run?` |
| `dialog_stop_workout_message` | `This session will end and a summary will be saved.` |
| `dialog_delete_run_title` | `Delete session?` |
| `dialog_delete_run_message` | `This permanently removes the session, route, and associated metrics.` |

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL (no reference errors)

**Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "polish: update shared button and dialog copy"
```

---

### Task 2: HomeScreen.kt — empty state and weekly subtitle

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

**Step 1: Apply changes**

Line ~148 — Last Run empty state text:
```kotlin
// Before
"No runs yet -- start your first run below."
// After
"No sessions recorded yet."
```

Line ~129 — Weekly Activity subtitle:
```kotlin
// Before
"${state.workoutsThisWeek} of ${state.weeklyTarget} runs"
// After
"${state.workoutsThisWeek} of ${state.weeklyTarget} this week"
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "polish: clean up HomeScreen copy"
```

---

### Task 3: SetupScreen.kt — sensor card, alert card, HRmax dialog

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

**Step 1: Apply changes — HrMonitorCard**

```kotlin
// "Scan for Devices" → "Scan for Monitors"
text = if (state.isScanning) "Scanning…" else "Scan for Monitors"

// "Waiting for HR signal..." → "Waiting for signal…"
text = if (state.liveHr > 0) "Live" else "Waiting for signal…"
// Note: "Live HR bpm" → "Live" (just the word, the number is already shown above)

// "No signal? You can still start - scanning continues during the run."
text = "A monitor is optional — scanning continues during your run."
```

**Step 2: Apply changes — AlertBehaviorCard**

```kotlin
// Collapsed subtitle
text = "Audio alerts and timing"

// Field labels
label = { Text("HR Buffer (bpm)") }      // was "Buffer (+/- bpm)"
label = { Text("Onset Delay (s)") }       // was "Grace Period (sec)"
label = { Text("Alert Interval (s)") }    // was "Repeat Interval (sec)"

// Volume row label
text = "Alert Volume"                      // was "Alert Sound Volume"
```

**Step 3: Apply changes — TargetCard / custom button**

```kotlin
// TextButton label — "Custom segment editor..." → "Custom"
) { Text("Custom") }
```

**Step 4: Apply changes — HRmax dialog**

```kotlin
title = { Text("Max Heart Rate") }        // was "Your Max Heart Rate"

// Body text
Text("Required to personalise preset heart rate targets.")  // was "Presets use % of your max HR to personalise targets."

// Placeholder
placeholder = { Text("185") }             // was Text("e.g. 185")

// Tip text
Text(
    text = "220 − age is a good estimate. A field test is more accurate.",
    // was "Tip: 220 - age is a rough guide. A field test gives better results."
```

Note: use `−` (Unicode minus U+2212) not a hyphen in `220 − age`.

**Step 5: Verify build**

```bash
./gradlew assembleDebug
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "polish: clean up SetupScreen copy"
```

---

### Task 4: ActiveWorkoutScreen.kt — segment next label

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

**Step 1: Apply change**

Line ~226:
```kotlin
// Before
text = "next › $next"
// After
text = "Up next — $next"
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "polish: clean up ActiveWorkoutScreen copy"
```

---

### Task 5: PostRunSummaryScreen.kt — celebration and action copy

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

**Step 1: Apply changes**

```kotlin
// Celebration headline (~line 179)
text = "Run Complete"        // was "Run Complete!"

// Comparisons header (~line 209)
text = "Compared to Similar Runs"    // was "vs Your Similar Runs"

// No-data headline (~line 228)
text = "Not enough data yet."        // was "Not enough data yet - keep running!"

// No-data body (~line 233)
text = "Complete a few similar sessions to unlock this view."
// was "Complete a few more similar sessions to unlock comparisons."

// Map button (~line 294)
Text("View Route")           // was "View on Map"

// Similar count label (~line 241)
text = "Based on ${uiState.similarRunCount} similar sessions"
// was "Based on ${uiState.similarRunCount} runs"
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "polish: clean up PostRunSummaryScreen copy"
```

---

### Task 6: PostRunSummaryViewModel.kt — insight strings

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`

**Step 1: Apply changes**

```kotlin
// Default title (~line 33)
val titleText: String = "Run Summary"    // was "Post-run Summary"

// Decoupling insight — bad case (~line 188)
"Aerobic endurance has room to improve"  // was "Aerobic endurance can improve"

// Recovery lag insight (~line 202) — remove trailing period
"Estimated delay between pace change and HR response"
// was "Estimated delay between pace change and HR response."

// Trim insight (~line 210)
"Cumulative personalization offset applied to projections."
// was "Long-term personalization offset currently applied to projections."

// Efficiency split insight (~line 218)
"Split efficiency reveals aerobic drift and endurance consistency."
// was "Efficiency by half helps explain drift and endurance consistency."
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt
git commit -m "polish: clean up PostRunSummaryViewModel insight copy"
```

---

### Task 7: HistoryListScreen.kt — empty state, subtitle, card copy

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

**Step 1: Apply changes — list subtitle**

```kotlin
// Lines ~91-97
text = if (workouts.isEmpty()) {
    "Completed sessions appear here."     // was "Every workout you save shows up here."
} else {
    "${workouts.size} sessions"           // was "${workouts.size} recorded sessions ready to review."
},
```

**Step 2: Apply changes — empty state card**

```kotlin
// Headline (~line 139)
text = "No sessions recorded yet"
// was "Your run archive is still empty"

// Body (~line 143)
text = "Complete a session to view route replay, post-run insights, and training trends."
// was "Start a workout to unlock route replay, post-run insights, and long-term progress trends."
```

**Step 3: Remove the "Tap for route and stats" hint**

Delete (do not replace) the `Text` composable around line 215-219:
```kotlin
Text(
    text = "Tap for route and stats",
    style = MaterialTheme.typography.bodySmall,
    color = Color(0xFF8FA4B7)
)
```

**Step 4: Fix pace chip capitalization**

```kotlin
HistoryMetricChip(
    label = "Avg Pace",    // was "Avg pace"
    ...
)
```

**Step 5: Verify build**

```bash
./gradlew assembleDebug
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "polish: clean up HistoryListScreen copy"
```

---

### Task 8: AccountScreen.kt — labels, saved messages, about text

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

**Step 1: Apply changes**

```kotlin
// Maps key field label
label = { Text("Google Maps API Key") }    // was "Google Maps API key"

// Maps saved message
if (state.mapsApiKeySaved) "Saved. Restart the app if maps remain unavailable."
else "Used for route rendering in History."
// was "Saved. Restart if map still appears blank." / "Used only for route rendering in History."

// Max HR placeholder
placeholder = { Text("185") }             // was Text("e.g. 185")

// Max HR saved/helper message
if (state.maxHrSaved) "Saved." else "Personalises preset heart rate targets."
// was "Saved." / "Used to personalise all preset HR targets."

// Alert Volume label
Text("Alert Volume")                       // was "Alert Sound Volume"

// About description
Text("Precision heart rate coaching for runners.")
// was "Heart rate zone coach for runners."
```

**Step 2: Verify build**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "polish: clean up AccountScreen copy"
```

---

### Task 9: ProgressScreen.kt — empty state, chart subtitles, delta labels

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt`

**Step 1: Apply changes — empty state**

```kotlin
text = "Record a few sessions to unlock your training dashboard."
// was "No workout data yet. Complete a few sessions to unlock your dashboard."
```

**Step 2: Apply changes — section headers**

```kotlin
SectionHeader("Performance", "Key trends, prioritized by signal strength.", ...)
// was "Your highest-signal trends, surfaced first."

SectionHeader("Details", "Training load, efficiency fingerprint, and consistency.", ...)
// was "Fingerprint, load, and consistency charts."
```

**Step 3: Apply changes — KeyMetricsStrip**

```kotlin
// VO2 disclaimer
Text("±10% estimated", ...)    // was "(estimated +/-10%)"

// HbKm no-prior delta text
"vs. last period"              // was "Current month vs last"

// HbKm with-prior delta — add period after "vs"
"vs. last month ${deltaLabel(...)}"   // was "vs last month …"
```

**Step 4: Apply changes — Vo2MaxCard disclaimer**

```kotlin
Text("±10% estimated", ...)    // was "(estimated +/-10%)" — second occurrence inside Vo2MaxCard
```

**Step 5: Apply changes — NotEnoughDataText**

```kotlin
text = "Insufficient data"     // was "Not enough data yet"
```

**Step 6: Apply changes — deltaLabel helper**

```kotlin
// Line ~574 in the deltaLabel private function
if (delta == 0f) return "Flat"    // was return "flat"
```

**Step 7: Verify build**

```bash
./gradlew assembleDebug
```

**Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt
git commit -m "polish: clean up ProgressScreen copy"
```

---

## Verification

After all tasks complete, do a final sanity check:

```bash
./gradlew assembleDebug
```

Then grep for any remaining informal patterns:

```bash
grep -rn "\-\-\|keep running\|Tip:\|e\.g\.\|\+/-\|\.\.\." \
  app/src/main/java/com/hrcoach/ui/ \
  app/src/main/res/values/strings.xml
```

Expected: no matches (or only matches inside comments/code, not string literals).
