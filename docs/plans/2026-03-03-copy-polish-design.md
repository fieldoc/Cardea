# Copy Polish Design — 2026-03-03

## Goal

Elevate every user-visible string from "cool project" to production-grade. Tone: **minimal and warm** (Whoop / Apple Fitness+ style — clear, confident, occasionally affirming; never chatty or tutorial-like).

## Rules Applied

1. No double dashes (`--`). Use em dash (`—`) or rewrite.
2. No exclamation points except genuine celebration moments (and even then, drop them).
3. No `Tip:` labels — rewrite as plain prose.
4. No trailing ellipsis on button labels or section headings.
5. `+/-` → `±`. `...` → `…` (proper Unicode).
6. No "still" (implies judgment), no "yet" when avoidable, no "keep running!" cheerleading.
7. Capitalize consistently: labels Title Case, body sentences Sentence case.
8. Drop "currently", "every", "all" qualifiers that add no meaning.

## Changes by File

### `strings.xml`
| Key | Before | After |
|---|---|---|
| `button_start_workout` | `Start Workout` | `Start Run` |
| `screen_setup_title` | `Start a Run` | `New Run` |
| `button_pause` | `PAUSE` | `Pause` |
| `button_resume` | `RESUME` | `Resume` |
| `button_stop` | `STOP` | `End Run` |
| `dialog_stop_workout_title` | `Stop workout?` | `End this run?` |
| `dialog_stop_workout_message` | `Your run will end and post-run summary will be generated.` | `This session will end and a summary will be saved.` |
| `dialog_delete_run_title` | `Delete this run?` | `Delete session?` |
| `dialog_delete_run_message` | `This removes the workout, route points, and saved metrics.` | `This permanently removes the session, route, and associated metrics.` |

### HomeScreen.kt
| Location | Before | After |
|---|---|---|
| Last Run empty state | `No runs yet -- start your first run below.` | `No sessions recorded yet.` |
| Weekly Activity subtitle | `${n} of ${t} runs` | `${n} of ${t} this week` |

### SetupScreen.kt
| Location | Before | After |
|---|---|---|
| Scan button | `Scan for Devices` | `Scan for Monitors` |
| Scanning state | `Scanning...` | `Scanning…` |
| HR waiting label | `Waiting for HR signal...` | `Waiting for signal…` |
| HR live label | `Live HR bpm` | `Live` |
| No-monitor hint | `No signal? You can still start - scanning continues during the run.` | `A monitor is optional — scanning continues during your run.` |
| AlertBehavior collapsed | `Audio alerts & timing options` | `Audio alerts and timing` |
| Buffer field label | `Buffer (+/- bpm)` | `HR Buffer (bpm)` |
| Delay field label | `Grace Period (sec)` | `Onset Delay (s)` |
| Cooldown field label | `Repeat Interval (sec)` | `Alert Interval (s)` |
| Volume label | `Alert Sound Volume` | `Alert Volume` |
| Custom button | `Custom segment editor...` | `Custom` |
| HRmax dialog title | `Your Max Heart Rate` | `Max Heart Rate` |
| HRmax dialog body | `Presets use % of your max HR to personalise targets.` | `Required to personalise preset heart rate targets.` |
| HRmax placeholder | `e.g. 185` | `185` |
| HRmax tip | `Tip: 220 - age is a rough guide. A field test gives better results.` | `220 − age is a good estimate. A field test is more accurate.` |

### ActiveWorkoutScreen.kt
| Location | Before | After |
|---|---|---|
| Next segment label | `next › $next` | `Up next — $next` |

### PostRunSummaryScreen.kt + ViewModel
| Location | Before | After |
|---|---|---|
| Default title | `Post-run Summary` | `Run Summary` |
| Celebration text | `Run Complete!` | `Run Complete` |
| Comparisons header | `vs Your Similar Runs` | `Compared to Similar Runs` |
| No-data headline | `Not enough data yet - keep running!` | `Not enough data yet.` |
| No-data body | `Complete a few more similar sessions to unlock comparisons.` | `Complete a few similar sessions to unlock this view.` |
| Map button | `View on Map` | `View Route` |
| Similar count label | `Based on ${n} runs` | `Based on ${n} similar sessions` |
| Decoupling insight (bad) | `Aerobic endurance can improve` | `Aerobic endurance has room to improve` |
| Recovery lag insight | `Estimated delay between pace change and HR response.` | `Estimated delay between pace change and HR response` |
| Trim insight | `Long-term personalization offset currently applied to projections.` | `Cumulative personalization offset applied to projections.` |
| Efficiency split insight | `Efficiency by half helps explain drift and endurance consistency.` | `Split efficiency reveals aerobic drift and endurance consistency.` |

### HistoryListScreen.kt
| Location | Before | After |
|---|---|---|
| Subtitle (empty) | `Every workout you save shows up here.` | `Completed sessions appear here.` |
| Subtitle (non-empty) | `${n} recorded sessions ready to review.` | `${n} sessions` |
| Empty state headline | `Your run archive is still empty` | `No sessions recorded yet` |
| Empty state body | `Start a workout to unlock route replay, post-run insights, and long-term progress trends.` | `Complete a session to view route replay, post-run insights, and training trends.` |
| Card hint text | `Tap for route and stats` | *(removed)* |
| Pace chip label | `Avg pace` | `Avg Pace` |

### AccountScreen.kt
| Location | Before | After |
|---|---|---|
| Maps key field | `Google Maps API key` | `Google Maps API Key` |
| Maps saved message | `Saved. Restart if map still appears blank.` | `Saved. Restart the app if maps remain unavailable.` |
| Maps helper | `Used only for route rendering in History.` | `Used for route rendering in History.` |
| Max HR placeholder | `e.g. 185` | `185` |
| Max HR saved message | `Used to personalise all preset HR targets.` | `Personalises preset heart rate targets.` |
| Volume label | `Alert Sound Volume` | `Alert Volume` |
| About description | `Heart rate zone coach for runners.` | `Precision heart rate coaching for runners.` |

### ProgressScreen.kt
| Location | Before | After |
|---|---|---|
| Empty state | `No workout data yet. Complete a few sessions to unlock your dashboard.` | `Record a few sessions to unlock your training dashboard.` |
| Performance subtitle | `Your highest-signal trends, surfaced first.` | `Key trends, prioritized by signal strength.` |
| Details subtitle | `Fingerprint, load, and consistency charts.` | `Training load, efficiency fingerprint, and consistency.` |
| VO2 disclaimer (×2) | `(estimated +/-10%)` | `±10% estimated` |
| HbKm delta (no prior) | `Current month vs last` | `vs. last period` |
| HbKm delta (with prior) | `vs last month …` | `vs. last month …` |
| Not enough data | `Not enough data yet` | `Insufficient data` |
| Delta flat label | `flat` | `Flat` |
