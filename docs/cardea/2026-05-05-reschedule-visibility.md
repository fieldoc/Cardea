# Reschedule mechanics visibility
Date: 2026-05-05
Revision: v2 — addresses AI audit findings and adopts no-stacking path.

## Intent
- Show every future day of the current week in the reschedule sheet, annotated with its conflict status, so the user can see *why* days are or aren't picks rather than seeing a mystery shortlist.
- Eliminate the silent auto-drop of a different session: rescheduling never causes a session the user didn't touch to disappear.
- For *advisory* conflicts (BLACKOUT, RECOVERY_SPACING), the user can still pick the day after a one-tap confirm dialog that names the conflict in plain English.
- For *hard* conflicts (OCCUPIED — another session already on that day), the day is disabled with a label, not silently absent. Stacking two runs on a single day is **not** allowed in this revision because the dashboard's `TodayState`/`WeekDayItem` shapes are single-session and would silently hide the second.

## Scope boundary
**In:**
- `SessionRescheduler` API: replace `availableDays(): List<Int>` with `suggestions(): List<DaySuggestion>` carrying the annotation set. Remove `RescheduleResult.Dropped`. `reschedule()` returns `Moved(firstFreeDayOrNull: Int?)` — nullable contract, see Risk flags.
- Suggestions cover days `(today..7)` where today is `LocalDate.now().dayOfWeek`. Past days (Mon..today-1) are not surfaced (you can't reschedule into the past). Self-day (the day the session currently sits on) is excluded as a no-op.
- `BootcampViewModel.requestReschedule` / `confirmReschedule` rewired to thread suggestions through and drop the auto-drop plumbing.
- `BootcampUiState` field cleanup: replace `rescheduleAvailableDays`/`-Labels`/`-DropSessionId` with `rescheduleSuggestions: List<RescheduleDayUi>` (single source of truth). Add `rescheduleConfirmDay: Int?` for the advisory-confirm dialog state (rotation-safe).
- `RescheduleBottomSheet` UI: annotated chip strip; "Recommended" callout when at least one FREE day exists; AlertDialog for non-FREE picks.
- `SessionReschedulerTest`: every existing case is explicitly mapped to keep, rewrite, or delete (see Tests).

**Out:**
- `BootcampWeekSeeder` / `SessionDayAssigner` — initial week generation, different code path.
- Recovery cadence math, phase engine, session selection, adaptive engine.
- Cloud sync, schema, DB migration (none needed).
- Stacking on the same day — explicitly out, see Intent.
- "Calendar week vs enrollment week" semantics — pre-existing convention, not introduced by this change.
- `TodayState` / `WeekDayItem` shape changes — out of scope by virtue of the no-stacking decision.

## Files to touch
- `app/src/main/java/com/hrcoach/domain/bootcamp/SessionRescheduler.kt`
  - Add `enum class SuggestionReason { FREE, OCCUPIED, RECOVERY_SPACING, BLACKOUT }` and `data class DaySuggestion(val dayOfWeek: Int, val reason: SuggestionReason, val isPreferred: Boolean)`.
  - **Multi-reason precedence (single-valued field, most-restrictive wins):** `OCCUPIED` > `BLACKOUT` > `RECOVERY_SPACING` > `FREE`. Rationale: OCCUPIED is the only hard-reject the UI uses, so it must surface; BLACKOUT is user-set off-day (stronger user signal than a coach heuristic); RECOVERY_SPACING is an advisory coach rule.
  - `suggestions(req): List<DaySuggestion>` — returns `(req.todayDayOfWeek..7)` minus `req.session.dayOfWeek`, each annotated. Sorted by reason (FREE first), then by `dayOfWeek` ascending within group.
  - `reschedule(req): RescheduleResult` returns `Moved(firstFreeDayOrNull = suggestions.firstOrNull { it.reason == FREE }?.dayOfWeek)`. Removes `Dropped`.
  - Remove `lowestPrioritySession` and `dropPriority` (dead).
  - Keep `defer()` returning `Deferred`.

- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
  - Add `enum class RescheduleReasonUi { FREE, OCCUPIED, RECOVERY, BLACKOUT }` and `data class RescheduleDayUi(val day: Int, val label: String, val reason: RescheduleReasonUi, val isRecommended: Boolean, val isSelfDay: Boolean = false)`.
  - Replace `rescheduleAvailableDays: List<Int>` and `rescheduleAvailableLabels: List<String>` and `rescheduleDropSessionId: Long?` with `rescheduleSuggestions: List<RescheduleDayUi>`.
  - Add `rescheduleConfirmDay: Int? = null` (advisory-confirm dialog state).
  - Add `rescheduleConfirmReason: RescheduleReasonUi? = null` (drives dialog copy).
  - Keep `rescheduleSheetSessionId`, `rescheduleAutoTargetDay`, `rescheduleAutoTargetLabel`.

- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
  - `requestReschedule`: call `SessionRescheduler.suggestions(req)`, project to `RescheduleDayUi`, mark the first FREE as `isRecommended = true`.
  - Add `onChipTapped(day: Int)`: if reason is FREE, immediately call `bootcampRepository.rescheduleSession(sessionId, day)`. If reason is OCCUPIED, no-op (chip is disabled in the UI; this is a defensive guard). Else open the confirm dialog by setting `rescheduleConfirmDay` and `rescheduleConfirmReason`.
  - `confirmAdvisoryReschedule()`: reads `rescheduleConfirmDay`, calls `bootcampRepository.rescheduleSession(sessionId, day)`, clears state. Also calls `loadBootcampState()` to refresh.
  - `dismissAdvisoryConfirm()`: clears `rescheduleConfirmDay`/`-Reason` only (chip strip stays open).
  - `confirmReschedule(dayOverride)` and the `dropSession`/`droppedSessionId` branch are removed (no callers after the refactor; the only path was the gone "Drop for this week" button).
  - `clearRescheduleSheet`: also clears the new fields.

- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` — `RescheduleBottomSheet` rewritten:
  - Header: "Reschedule {sessionType}" (from session passed via `rescheduleSheetSessionId`).
  - **Recommendation callout (only when at least one FREE day exists):** "Recommended: Thu (open day)." with `CardeaButton` "Sounds good" → confirms move to that day.
  - **No-FREE-day fallback copy** (mandatory, was a critical audit gap): "No open days left this week. Pick a day below — we'll flag any conflicts — or defer this session to later." Below this, show the chip strip annotated as usual.
  - **Worst-case fallback (no chips at all** — happens when today is Sun and the session sits on Sun, or every future day is OCCUPIED**):** chip strip is hidden. Helper text: "There's no other day to move this run to. You can defer it instead." Defer is the only positive action; Cancel still dismisses.
  - **Chip strip** (one row, all future days through Sun, scrollable horizontally if cramped):
    - FREE chips: emphasized (full color, semibold), tap = immediate reschedule.
    - RECOVERY_SPACING and BLACKOUT chips: subdued styling + small caption beneath the chip (e.g. "Hard run nearby" / "Off day"), tap = open confirm dialog.
    - OCCUPIED chips: rendered with `enabled = false` styling and caption "Already a run". Not tappable.
    - The session's current day is **not** rendered in the strip (it's excluded by `suggestions()`).
  - **Advisory confirm dialog** (Material `AlertDialog`, driven by `rescheduleConfirmDay`/`-Reason`):
    - RECOVERY_SPACING: title "Run Wed?", body "Wednesday is right after a hard session. Stack runs that close together only if you feel ready."
    - BLACKOUT: title "Run Wed?", body "You marked Wednesday as a day off. We'll move this run there for this week only — your usual schedule isn't changed."
    - Buttons: "Confirm" → `viewModel.confirmAdvisoryReschedule()`; "Cancel" → `viewModel.dismissAdvisoryConfirm()`.
  - **Footer (unchanged behaviour):** Cancel (sheet dismiss, no state change) / Defer this session (flips status to DEFERRED). System-back and swipe-down route through `onDismissRequest` → Cancel semantics, NOT Defer. (Restating: existing comment block in BootcampScreen.kt:3122–3127 was added because conflating these used to be a bug — keep the separation.)
  - **Accessibility:**
    - Each chip's `Modifier.semantics { contentDescription = "$dayName, $reasonText" }`. Reason text strings: FREE = "open day"; OCCUPIED = "already has a run"; RECOVERY_SPACING = "right after a hard session"; BLACKOUT = "marked as a day off".
    - OCCUPIED chip uses `disabled = true` semantics.
    - Confirm dialog uses default Material `AlertDialog` semantics (announces title + body on open).

## CLAUDE.md rules honored
- **`bootcamp-scheduling.md` — date handling, ISO Mon-first, no `getNextSession()` regression.** Suggestions stay `1..7`. The reschedule flow doesn't read `getNextSession()`.
- **`ui-theme.md` — `clip()` before `background()`.** Day chips: `Modifier.clip(RoundedCornerShape).background(...)` order.
- **No DataStore writes inside `onValueChange`** — n/a (no sliders).
- **No service / WFS / audio / pause-race risk** — change is bounded to one feature flow.

## Tests
**`SessionReschedulerTest.kt`** — explicit mapping for every existing test (covers audit point #14):
- `moves_to_next_available_day` → **rewrite** to assert `suggestions()` lists day 2 as FREE and `reschedule()` returns `Moved(2)`.
- `skips_blackout_days` → **rewrite** to `marks_blackout_days_as_blackout_not_excluded`. Asserts day 2 appears with reason BLACKOUT, day 6 appears with reason FREE, and `Moved.firstFreeDayOrNull == 6`.
- `drops_lowest_priority_when_no_slots_available` → **rewrite** to `no_free_days_returns_moved_with_null_target`. Asserts every day is annotated (not absent) and the `Moved.firstFreeDayOrNull` is null. Replaces the auto-drop assertion entirely.
- `defer_returns_deferred` → **keep**.
- `respects_48h_recovery_gap_for_hard_sessions` → **rewrite** to `recovery_spacing_is_annotated_not_excluded`. Day 3 has reason RECOVERY_SPACING (not absent); is selectable.
- `offers_today_when_not_occupied` → **keep** (renamed: `today_appears_when_not_occupied`).
- `excludes_today_when_occupied` → **rewrite** to `today_appears_as_occupied_when_session_present`. Today is no longer absent; it's present with reason OCCUPIED.
- `allows_non_preferred_days_for_reschedule` → **keep** (now asserts FREE reason on non-preferred Tue/Thu/Fri).
- `still_skips_none_level_days_that_are_blackout` → **rewrite** to `none_level_blackout_marked_blackout`. Day 4 is present with reason BLACKOUT.
- `long_run_has_highest_drop_priority` → **delete** (drop priority is removed).
- `race_sim_has_highest_drop_priority` → **delete**.
- `respects_48h_recovery_gap_for_long_runs` → **rewrite** to `long_run_recovery_spacing_is_annotated`. Same shape as the hard-session test.

**New tests (covers audit point #11 — high-risk edges):**
- `all_days_occupied_returns_no_free_recommendation` — every future day has another session; `Moved.firstFreeDayOrNull` is null; every chip's reason is OCCUPIED.
- `today_is_sunday_session_on_sunday_returns_empty_suggestions` — `(7..7)` minus self-day = empty list.
- `empty_preferred_days_treats_all_non_blackout_as_free` — defensive case for new enrollments before any day prefs are set.
- `self_day_is_never_in_suggestions` — session on Thu, today is Wed; Thu is FREE per the engine but the suggestion list omits it.
- `multi_reason_precedence_picks_occupied_over_blackout` — day 5 is BLACKOUT and OCCUPIED; chip's `reason == OCCUPIED`.

**Device verification:**
1. Normal week, mid-week reschedule of an EASY run → chip strip shows future days, FREE recommendation present, "Sounds good" path works.
2. Force the worst case (every future day occupied + recovery-blocked + blackout) by manually editing prefs and seeding extra sessions in dev → confirm chip strip renders all days with reason captions; no "Drop for this week" button anywhere; Defer is the only positive action.
3. Tap a BLACKOUT chip → AlertDialog with the BLACKOUT copy. Cancel → sheet stays. Confirm → session moves; rotate device DURING the dialog and confirm dialog re-opens after recreate (rotation-safe state).
4. Tap a RECOVERY_SPACING chip → AlertDialog with RECOVERY copy.
5. Tap an OCCUPIED chip → no-op (disabled).
6. Defer this session → status flips to DEFERRED, sheet closes.
7. TalkBack pass: enable TalkBack, swipe through chips, confirm each announces day + reason.

## Risk flags
- **`Moved.newDayOfWeek` becomes nullable.** Single consumer (`BootcampViewModel.requestReschedule`); fully covered by recompile + test rewrite. Worth re-grepping for `Moved.newDayOfWeek` access after the change to make sure nothing else snuck in.
- **Behavioral change to scheduling discipline.** Recovery spacing and blackout are no longer absolute rejects — they become advisory. OCCUPIED stays a hard reject (no stacking). This is the intended semantic shift; AdaptivePaceController and run-time engines have no stacking-aware behavior, so this can't surprise the engine.
- **DataStore / persistence:** none. Changes write only through existing `bootcampRepository.rescheduleSession(sessionId, day)` and `bootcampRepository.deferSession(sessionId)`.
- **Cloud sync:** none. `rescheduleSession` already handles cloud sync via existing repo internals (no new fields).
- **No service / audio / KSP / DB / pause-race risks.**
- **Compose state:** AlertDialog visibility lives in UiState (`rescheduleConfirmDay`), not `rememberSaveable` local — explicit choice for rotation/process-death safety per audit point #11.
