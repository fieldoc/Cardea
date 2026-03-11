# Bootcamp Scheduling Orientation — Design Spec

**Date:** 2026-03-10
**Status:** Approved
**Scope:** `BootcampScreen`, `BootcampUiState`, `BootcampViewModel`

---

## Problem

The Bootcamp dashboard answers "what sessions do I have this week?" but users open it asking "where am I right now in my training?" These are different questions and the UI only answers one of them.

Specific failures:

- No current date or calendar date range shown anywhere
- The "Today" badge disappears from a session row once the session is completed — the temporal anchor vanishes exactly when the user returns to check in
- `scheduledRestDay` only fires when there is no session scheduled for today; it does not fire when today's session is already completed — so the two states that feel identical to a user ("nothing to do today") are handled completely differently
- `nextScheduledSession` surfaces a "Start Run" CTA for sessions that may be 2+ days away, with no relative time label
- Rest days are invisible — the week list shows only training days, leaving users unable to see the week's rhythm

---

## Design

### Information hierarchy (unchanged at top level)

1. **Phase header** — program context (goal, week N of M, phase, progress bar) — **untouched**
2. **Week strip** — 7-day calendar view with today indicator — **new**
3. **Today context card** — adaptive status + next session preview — **new, replaces two components**
4. **Coming up** — lookahead weeks — **untouched**

---

### Component: `WeekStripCard` (new)

Replaces `WeekSessionList`.

A glass card containing:

- **Header row**: "This Week" label + date range (e.g. "Mar 10–16") right-aligned
- **7 pill slots** M–S, each showing:
  - Day letter (M T W T F S S)
  - Pill dot — one of five states:
    - `rest` — empty, barely visible background
    - `run-upcoming` — dim filled, border, session type abbreviation (2 chars)
    - `run-done` — green tint, green border, checkmark
    - `today-run-upcoming` — pink tint, pink border, session type abbreviation
    - `today-run-done` — green tint, stronger green border, checkmark
    - `today-rest` — transparent, thin white border, "—"
- **Timeline row** below the pills:
  - Full-width hairline track (1dp, very dim white)
  - Vertical tick at today's column position (1.5dp wide, ~9dp tall, 72% white opacity)
  - Soft radial glow bloom behind the tick (18dp diameter, radial gradient 20%→0% white)
  - Tick position computed as `(dayOfWeek - 1) / 6f` across the track width

---

### Component: `TodayContextCard` (new)

Replaces `NextSessionCard` and the floating `scheduledRestDay` `StatusCard`.

Adapts to three states based on `TodayState`:

**`RunUpcoming`** — today has a session not yet started
- Accent: pink
- Status line: "Today — [Session Type]"
- Sub: "[N] min · Zone [Z]"
- Divider
- Preview label: "Today's session"
- Session title + duration + zone
- Session description paragraph
- "Start Run" CTA button (full width)
- Secondary row: "Reschedule" (left) · "Rest today" (right)

**`RunDone`** — today had a session, now completed
- Accent: green
- Status line: "✓ Today's run is done"
- Sub: "Next: [Type] · [Day] · [relative label]"
- Divider
- Preview label: "Up next — [Day]"
- Session title + duration + zone
- Session description paragraph
- No CTA button

**`RestDay`** — today has no session (scheduled rest, or all-week sessions complete)
- Accent: dim white/grey
- Status line: "Rest day"
- Sub: "Next: [Type] · [Day] · [relative label]"
- Divider
- Preview label: "Up next — [Day]"
- Session title + duration + zone
- Session description paragraph
- No CTA button

If there is no next session (week complete, end of program), the preview section is omitted and the sub line reads "Week complete" or "Program complete".

---

## Data model changes

### New: `TodayState` sealed class

```kotlin
sealed class TodayState {
    data class RunUpcoming(
        val session: PlannedSession
    ) : TodayState()

    data class RunDone(
        val nextSession: PlannedSession?,
        val nextSessionDayLabel: String?
    ) : TodayState()

    data class RestDay(
        val nextSession: PlannedSession?,
        val nextSessionDayLabel: String?
    ) : TodayState()
}
```

### New: `WeekDayItem`

```kotlin
data class WeekDayItem(
    val dayOfWeek: Int,         // 1=Mon … 7=Sun
    val dayLabel: String,       // "M" "T" "W" …
    val calendarDate: Int,      // day-of-month number
    val isToday: Boolean,
    val session: SessionUiItem? // null = rest day
)
```

### `BootcampUiState` diff

| Field | Action |
|---|---|
| `currentWeekSessions: List<SessionUiItem>` | **Replaced** by `currentWeekDays: List<WeekDayItem>` |
| `nextSession: PlannedSession?` | **Removed** — lives in `TodayState` |
| `nextSessionDayLabel: String?` | **Removed** — lives in `TodayState` |
| `scheduledRestDay: Boolean` | **Removed** — absorbed into `TodayState.RestDay` |
| `todayState: TodayState` | **Added** |
| `currentWeekDateRange: String` | **Added** — formatted e.g. "Mar 10–16" |
| `nextSessionRelativeLabel: String?` | **Added** — "today" / "tomorrow" / "in N days" |
| `nextSessionDescription: String?` | **Added** — session description text, computed in VM using existing `SessionDescription.forType()` logic |

All other fields unchanged.

### `BootcampViewModel` — `todayState` computation

Replaces the current `scheduledRestDay` + `nextScheduledSession` block:

```kotlin
val today = LocalDate.now().dayOfWeek.value  // 1=Mon, 7=Sun

val todayState: TodayState = when {
    scheduledSessions.any { it.dayOfWeek == today
            && it.status != STATUS_SCHEDULED } ->
        TodayState.RunDone(
            nextSession = nextScheduledSession?.toPlannedSession(),
            nextSessionDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) }
        )
    scheduledSessions.any { it.dayOfWeek == today } ->
        TodayState.RunUpcoming(
            session = scheduledSessions.first { it.dayOfWeek == today }.toPlannedSession()
        )
    else ->
        TodayState.RestDay(
            nextSession = nextScheduledSession?.toPlannedSession(),
            nextSessionDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) }
        )
}
```

`currentWeekDays` iterates `1..7`, resolves `LocalDate` for each slot from `weekStart = LocalDate.now().with(DayOfWeek.MONDAY)`, and maps each day's session (if any) to a `SessionUiItem`.

`nextSessionRelativeLabel` is computed from the difference between today and `nextScheduledSession.dayOfWeek`:
- 0 → "today"
- 1 → "tomorrow"
- N → "in N days"

---

## What is removed

- `WeekSessionList` composable
- `NextSessionCard` composable
- Floating `scheduledRestDay` `StatusCard` (the condition in `ActiveBootcampDashboard`)

These are fully replaced by `WeekStripCard` + `TodayContextCard`.

---

## What is NOT changing

- `PhaseHeader` — untouched
- `ComingUpCard` — untouched
- `PausedCard` — untouched
- `missedSession` `StatusCard` — untouched (separate concern: past missed sessions)
- `TierPromptCard`, `IllnessPromptCard` — untouched
- `RescheduleBottomSheet` — untouched
- `SessionDetailSheet` — untouched
- All DAO, repository, and domain logic — no changes needed

---

## Testing

The core logic change is `todayState` computation in the ViewModel. Three unit test cases:

1. Today has a session with `STATUS_SCHEDULED` → `TodayState.RunUpcoming`
2. Today has a session with `STATUS_COMPLETED` → `TodayState.RunDone` with correct `nextSession`
3. Today has no session → `TodayState.RestDay` with correct `nextSession`

Edge cases:
- No sessions remaining in week → `nextSession = null` in `RunDone`/`RestDay`
- Today is Sunday and next session is Monday (next week) → `nextSessionRelativeLabel = "in N days"` (cross-week lookahead uses existing `nextScheduledSession` which already looks beyond current week)
