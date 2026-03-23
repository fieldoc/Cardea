# Bootcamp Schedule Flexibility — Design Spec

**Date:** 2026-03-22
**Status:** Approved (verbal)

## Problem

The bootcamp scheduling/rescheduling system has several UX gaps:
1. Week strip day pills look interactive but aren't clickable
2. Rescheduler only offers future days (can't move a run to today or pull forward)
3. Rest day and run-done hero sections are dead ends with no actions
4. Only the first missed session surfaces; dismissing hides it permanently
5. DEFERRED sessions have no follow-up mechanism
6. "Manual run" link is buried at the bottom in tertiary color

## Design

### 1. Tappable Week Strip Pills
- Add `onClick` to `WeekDayPill` for days that have a session
- Opens existing `SessionDetailSheet` via `onSessionClick`
- Rest days (no session) remain non-interactive

### 2. Enhanced SessionDetailSheet with Actions
Currently read-only. Add contextual action buttons below the description card:
- **Scheduled, not today, not past:** "Reschedule" + "Skip this session"
- **Scheduled, today:** "Start run" + "Reschedule" + "Rest today"
- **Missed (past, still SCHEDULED):** "Reschedule" + "Skip"
- **Completed/Skipped:** No actions (info only, already has "Completed" badge)

Actions reuse existing ViewModel methods: `requestReschedule()`, `swapTodayForRest()`, `dropSession()`.

### 3. SessionRescheduler: Allow Today as Target
Change `availableDays()` range from `(todayDayOfWeek + 1..7)` to `(todayDayOfWeek..7)`.
Today is only offered if it's not already occupied. This enables "pull forward" naturally.

### 4. Rest Day / Run-Done Hero CTAs
- **RestDay hero:** Add "Pull forward next run" TextButton when there's a future session this week. Clicking opens reschedule sheet for that session. Also add "Or set up a manual run" link.
- **RunDone hero:** Add "Pull forward next run" TextButton when there's another future session this week. No manual run link needed (already ran today).

Both use `requestReschedule(nextSessionId)` which now includes today in available days.

### 5. Missed/Deferred Card Improvements
- Track `missedDismissedIds: Set<Long>` instead of `missedDismissed: Boolean`
- Count all missed + DEFERRED sessions, not just the first
- Show count: "N session(s) to reschedule"
- Reschedule button opens the first unresolved one; after resolving, card updates to show remaining
- Include DEFERRED sessions in the missed detection logic (they need attention too)

### 6. UiState Changes
- Add `isPast: Boolean` to `SessionUiItem` (dayOfWeek < today)
- Add `isDeferred: Boolean` to `SessionUiItem`
- Add `missedSessionCount: Int` to `BootcampUiState` (replaces `missedSession: Boolean`)
- Add `missedSessionIds: List<Long>` to `BootcampUiState`
- Add `nextFutureSessionId: Long?` to `TodayState.RestDay` and `TodayState.RunDone`

## Files Modified
- `domain/bootcamp/SessionRescheduler.kt` — range fix
- `ui/bootcamp/BootcampUiState.kt` — new fields
- `ui/bootcamp/BootcampViewModel.kt` — new methods, state computation
- `ui/bootcamp/BootcampScreen.kt` — UI changes (pills, detail sheet, hero, missed card)
- `test/.../SessionReschedulerTest.kt` — new test for today inclusion

## What We're NOT Doing
- No cross-week rescheduling (too complex, gap advisor handles this)
- No "bonus run" entity creation (pull-forward via reschedule is simpler)
- No new screens or navigation routes
- No full calendar/date-picker view
