# Rescheduling & Blackout Days — Design

## Problem

The bootcamp program assumes you want to start immediately on enrollment and has no
proactive way to move a session before it's missed. Users who discover the feature at
9 pm, or who know they'll be swamped, have no recourse except to simply not run and
let GapAdvisor react after the fact. There is also no way to permanently block certain
days from ever being scheduled.

## Goals

- Let users block days from scheduling during setup and in settings (blackout days)
- Default enrollment start to the first upcoming non-blackout preferred day
- Let users proactively reschedule a session before missing it
- Rescheduling should be accessible but visually discouraged vs. "start the run"
- Never nag; if the user doesn't know when they can run, trust GapAdvisor on return
- Fix star badge clipping on circular day-picker buttons (discovered during design)

---

## 1. Data Model

### DaySelectionLevel — add BLACKOUT

```
NONE          untouched — not preferred, but usable in a pinch by the rescheduler
AVAILABLE     one tap — preferred run day
LONG_RUN_BIAS two taps — preferred AND biased for long runs
BLACKOUT      long press — hard block, never scheduled to
```

Tap cycle stays: NONE → AVAILABLE → LONG_RUN_BIAS → NONE.
Long press from any state → BLACKOUT. Long press on BLACKOUT → NONE.

Serialization uses the existing `"day:LEVEL"` format — BLACKOUT is a new valid level
name, so existing parsing handles it. One DB migration required to acknowledge the
schema version bump (data format is backwards-compatible).

### BootcampSessionEntity — add DEFERRED status

New constant: `STATUS_DEFERRED = "DEFERRED"`.

A session is DEFERRED when the user taps "I'm not sure yet." It is:
- Invisible to `BootcampReminderWorker` (no reminder notifications)
- Invisible to `GapAdvisor` for scheduling purposes
- Visible in history as a skipped/deferred session once the week passes

### BootcampEnrollmentEntity — startDate fix

Enrollment creation no longer defaults `startDate` to `System.currentTimeMillis()`.
It computes the first upcoming calendar day that is:
1. A preferred day (AVAILABLE or LONG_RUN_BIAS)
2. Not BLACKOUT
3. In the future (tomorrow or later if today is already evening, or today if it's morning)

UI shows: *"Your first run is scheduled for Thursday."*

No schema change required — startDate column already exists.

---

## 2. Day Picker UX

### Visual states

| State | Fill | Badge | Meaning |
|-------|------|-------|---------|
| NONE | Ghost / outline | — | Open — not preferred, rescheduler can use |
| AVAILABLE | Cardea gradient | — | Preferred run day |
| LONG_RUN_BIAS | Cardea gradient | ★ (top-right) | Preferred + long run bias |
| BLACKOUT | Dark charcoal (#1C1C1E) | ✕ (top-right) | Hard block — never schedule here |

### Star badge clipping fix

The star badge is currently positioned assuming square button corners. For circular
buttons, the badge offset must be computed from the circle's center and radius:

```
badgeCenter = buttonCenter + (radius * cos45°, -radius * sin45°)
```

Both ★ and ✕ badges use the same geometry.

### Legend strip

A single row of four mini chip examples sits above the day grid with no prose:

```
[○ Mon]  [● Tue]  [★ Wed]  [✕ Thu]
```

Each chip is ~28dp tall, labelled with a 3-letter day abbreviation and the
appropriate icon/fill. No caption text needed — the visual is self-explanatory.

### Interaction

- Single tap: cycles NONE → AVAILABLE → LONG_RUN_BIAS → NONE
- Long press: toggles BLACKOUT on/off (BLACKOUT → NONE, any other → BLACKOUT)
- Haptic feedback on long press (HapticFeedbackConstants.LONG_PRESS)

---

## 3. SessionRescheduler

Pure domain object (no Android dependencies, fully unit-testable).

### Inputs

```kotlin
data class RescheduleRequest(
    val session: BootcampSessionEntity,
    val enrollment: BootcampEnrollmentEntity,
    val todayDayOfWeek: Int,          // 1=Mon … 7=Sun
    val occupiedDaysThisWeek: Set<Int>, // days already having a session
    val allSessionsThisWeek: List<BootcampSessionEntity>
)
```

### Logic

1. Collect remaining days in the current week (today+1 through week end)
2. Filter to days that are:
   - Not BLACKOUT
   - Not already occupied by another session
   - Have ≥ 48h recovery gap from any adjacent hard session (TEMPO, INTERVALS)
3. If one or more valid days exist → pick the earliest, return `RescheduleResult.Moved(newDayOfWeek)`
4. If no valid days exist → drop the lowest-priority session in the week
   - Drop priority order: EASY first, TEMPO second, LONG_RUN last
   - Return `RescheduleResult.Dropped(droppedSessionId)`
5. "I'm not sure yet" path returns `RescheduleResult.Deferred` — caller marks the
   session DEFERRED, no further action

### Recovery gap rule

A session is "hard" if its `sessionType` is TEMPO or INTERVALS.
Gap between a hard session and any other session must be ≥ 2 days.
Easy ↔ Easy has no gap constraint.

---

## 4. Reschedule UI

### Session card (BootcampScreen)

Below the primary "Start Run" gradient button, a small muted text link:

```
[ ▶  Start Run  ████████████████ ]   ← full gradient, prominent

      can't make it today?           ← 12sp, Color.Gray, no background
```

Tapping "can't make it today?" opens a modal bottom sheet.

### Reschedule bottom sheet

```
╭─────────────────────────────────────╮
│  Move to [Thursday]                 │
│                                     │
│  [ Sounds good  ████████████████ ]  │  ← gradient button
│                                     │
│      Choose a different day         │  ← muted text link
│      I'm not sure yet               │  ← muted text link
╰─────────────────────────────────────╯
```

- "Sounds good" → SessionRescheduler.Moved, snackbar: *"Moved to Thursday — see you then."*
- "Choose a different day" → date picker (valid days only — no blackout, no occupied, within 14 days)
- "I'm not sure yet" → SessionRescheduler.Deferred, bottom sheet closes, no follow-up prompt

---

## 5. Notification Suppression

`BootcampReminderWorker` already fires daily reminders for upcoming sessions.
Add a check: skip any session with `status == STATUS_DEFERRED`.
No other changes to the notification system.

---

## Non-Goals (YAGNI)

- Blackout days do not persist across re-enrollment (Approach B) — deferred to later if needed
- No system calendar integration
- No "postpone the whole week" bulk action
- No push notification at 6pm asking "still planning to run today?"
