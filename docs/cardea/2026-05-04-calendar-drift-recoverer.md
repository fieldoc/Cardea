# Calendar Drift Recoverer
Date: 2026-05-04

## Intent

- When the bootcamp engine falls behind the calendar (e.g. one residual `SCHEDULED` session blocks `BootcampSessionCompleter.weekComplete`), self-heal on app open instead of painting last-week's completions onto this-week's date strip.
- Auto-skip residual SCHEDULED sessions when the engine-week's latest completion lives in a strictly earlier calendar week than today, advance the engine, seed next week — silently. Match user mental model: a new calendar week is a fresh page.
- Preserve `GapAdvisor`'s ownership of the "user was absent" case (no completions in current engine week → recoverer no-ops, GapAdvisor's rewind logic still runs).
- Never interfere with an in-flight workout: gate on `WorkoutState.isRunning` and `pendingBootcampSessionId`.

## Deviation from initial proposal (2026-05-04 mid-impl)

The original spec used `enrollStartDate.with(MONDAY).plusWeeks(weekNumber - 1)` as the formula mapping `weekNumber` → calendar week, plus a defensive header clamp using the same formula. Investigating Graham's actual DB during implementation revealed the formula doesn't reliably hold:
- A pre-enrollment Sunday workout (Apr 12, before `enrollment.startDate = Apr 13`) attached to `weekNumber=1`, offsetting all subsequent weekNumber→calendar mappings by ~1 week.
- Pulling sessions forward via Reschedule reinforces the offset (sessions for `weekNumber=N` get completed in calendar week N-1).

For Graham's data, formula says `weekNumber=4` = May 4-10, but the actual `weekNumber=4` completions were Apr 28-May 3. Applied as a recoverer trigger, the formula would not fire today; applied as a strip-header clamp, it would replace one wrong header with another.

**Resolution:** data-driven trigger (compare today's `with(MONDAY)` against the engine-week's latest COMPLETED session's `with(MONDAY)`); drop the strip-header clamp entirely. The SKIP timestamp also moves from "formula's engine-week Sunday" to "latest completion's calendar-week Sunday 23:59." Recoverer behavior for formula-aligned users is unchanged; behavior for users with offset histories now matches their mental model.

## Scope boundary

**In:**
- New `domain/bootcamp/CalendarDriftRecoverer.kt` — pure domain logic, takes a clock, returns an outcome.
- New `domain/bootcamp/BootcampWeekSeeder.kt` — extracted from `BootcampSessionCompleter.buildNextWeekEntities` so both the completer and the recoverer use a single seeding path.
- Wire into `BootcampViewModel.refreshFromEnrollment` and `HomeViewModel`'s flow so both screens see consistent state.
- New `BootcampRepository.autoSkipSession(sessionId, completedAtMs)` — explicit-timestamp variant of `dropSession`, distinct entry point.
- Past-dated `completedAtMs` on auto-skips (set to the latest completion's calendar Sunday 23:59 local — keeps `StreakCalculator` honest).
- Unit tests for 6 cases (see Tests).

**Removed from original scope:**
- The strip-header date clamp (`BootcampViewModel.kt:266` — `weekStart` derivation). See deviation note above.
- The `isToday` calendar-week gate (was paired with the clamp).

**Out:**
- `STATUS_SKIPPED` semantics refactor (no new `STATUS_EXPIRED`). Existing UI surfaces SKIPPED uniformly. Documented as conscious choice.
- Cloud-sync of auto-skipped sessions. Existing user-initiated `dropSession` and `swapSessionToRestDay` also bypass cloud sync — the recoverer matches that pattern. Cloud parity is its own pre-existing gap, tracked separately.
- "Catching up" UI affordance, missed-run banner, or "Last week: 1 missed" chip. Per scope decision, auto-skip is silent; the missed run is recorded in history only.
- Refactor of `engine.absoluteWeek` to be calendar-derived. That's a larger change with sim-mode and rollover-test implications; deferred.
- `BootcampSessionCompleter.weekComplete` change. The recoverer feeds the same advance pipeline; we don't loosen the completer's invariants.

## Files to touch

- `app/src/main/java/com/hrcoach/domain/bootcamp/CalendarDriftRecoverer.kt` — **new**. Pure function: given enrollment + this-week sessions + clock, returns either `NoChange`, `AdvanceWeek(skipsToWrite, advance)`, or loops via the caller.
- `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt` — add `autoSkipSession(sessionId, completedAtMs)` (separate from user-initiated `dropSession` so timestamps are explicit; same DB write semantics).
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` — call `CalendarDriftRecoverer` at top of `refreshFromEnrollment`, before `ensureCurrentWeekSessions`. Change `weekStart` computation at line 266 from `LocalDate.now().with(MONDAY)` to `enrollMonday.plusWeeks(displayEngine.absoluteWeek - 1)`.
- `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` — same recoverer call at top of the active-enrollment path inside the flow (before `getSessionsForWeek` reads).
- `app/src/test/java/com/hrcoach/domain/bootcamp/CalendarDriftRecovererTest.kt` — **new**. 6 cases.

## CLAUDE.md rules honored

- **WFS race gate (`docs/claude-rules/wfs-lifecycle.md`)** — recoverer no-ops when `WorkoutState.snapshot.value.isActive` OR `WorkoutState.snapshot.value.pendingBootcampSessionId != null`. Reason: `BootcampSessionCompleter.complete()` rejects non-SCHEDULED/DEFERRED targets and silently drops the workout's bootcamp attribution.
- **Bootcamp session identity contract (`docs/claude-rules/bootcamp-scheduling.md`)** — we don't touch `pendingBootcampSessionId`; the gate above protects it.
- **Date-aware home lookup** — Home's `getScheduledAndDeferredSessions()` + computed-date filter is preserved. Recoverer runs before the lookup, so it operates on already-normalized data.
- **Test timezone safety (`docs/claude-rules/test-fakes.md`)** — `CalendarDriftRecoverer` takes a `Clock` (or `LocalDate today + ZoneId zone`); tests pass UTC explicitly. No `ZoneId.systemDefault()` inside the recoverer.
- **Same-tick race (WFS rules)** — N/A; recoverer doesn't touch `WorkoutState`.

## Tests

**Unit (`CalendarDriftRecovererTest.kt`):**

1. `stuck_week_with_completions_skips_residual_and_advances` — engine week N has 3 COMPLETED (most recent in calendar week W) + 1 SCHEDULED; today's Monday is strictly after W's Monday; recoverer returns Recovered with 1 SKIP write + advance to N+1. Verifies SKIP timestamp == W's Sunday 23:59.
2. `fresh_week_no_completions_no_ops` — engine week N has 4 SCHEDULED, no completions, today is in some later calendar week. Recoverer returns `NoChange`. (GapAdvisor's territory.)
3. `multi_week_drift_loops` — engine week N has completions in calendar week W; engine week N+1 also has completions in calendar week W+1; today is in calendar week W+2. Recoverer advances N→N+1; on the next loop iteration the freshly-seeded N+1 has no completions yet (loop "no completions" guard) — wait, actually N+1 already has completions from the user's data prior to this run, so it advances again to N+2; N+2 has no completions, loop exits. Net: 2 advances. Verifies stable termination.
4. `graduation_bailout` — engine week is the final week of the final phase, has completions in a strictly earlier calendar week than today. Recoverer returns `NoChange` (let `BootcampSessionCompleter` handle graduation through normal completion path). Asserts no `SKIP` is written.
5. `active_workout_gates_recoverer` — `isWorkoutActive=true` (or `pendingSessionId != null`). Trigger conditions otherwise met. Recoverer returns `NoChange`. Asserts no DB write attempted.
6. `same_calendar_week_as_latest_completion_no_ops` — engine week N has completions; today's `with(MONDAY)` equals the latest completion's `with(MONDAY)` (e.g. user just ran an hour ago). Recoverer returns `NoChange` — engine is in sync.

**Device verification (Phase 5, manual):**

- Pre-stage: write `STATUS_SCHEDULED` to a session in week N (via adb sqlite, after backing up DB) so engine is "stuck" at N while calendar shows N+1. Force-stop app, relaunch, observe Bootcamp tab opens to week N+1 with the residual marked SKIPPED in week N.
- Negative case: clear the active enrollment's week, leave 4 SCHEDULED untouched, advance device clock by 7 days, relaunch. Should NOT auto-skip (no completions in current engine week → GapAdvisor path).
- Race case: start a workout against the residual SCHEDULED session. Send the app to background while active. Bring it back. Recoverer must NOT have skipped the in-flight session. Complete the workout, confirm normal advance via `BootcampSessionCompleter`.

## Risk flags

- **Touches `service/`?** No. The recoverer is pure domain logic + a repository method. WFS untouched.
- **Touches `audio/`?** No.
- **Touches `data/db/`?** No schema change. New repository method only. **DB backup required before `adb install -r`** anyway because we're writing new SKIPPED rows during verification — backup avoids losing the intentional "stuck" pre-stage state if rollback is needed.
- **Touches cloud sync?** No new cloud writes. Auto-skips bypass cloud (matches existing `dropSession` behavior). Tracked as known gap, not regressed.
- **Touches `domain/engine/`?** No (`PhaseEngine` unchanged). Recoverer lives in `domain/bootcamp/` next to `BootcampSessionCompleter`.
- **Race surfaces:** active workout, pending session, two VMs both calling the recoverer concurrently. Recoverer must be idempotent — second call after a successful skip+advance sees no completions (week has been seeded fresh) and returns `NoChange`.
- **Layered review tier:** Layer 2 Haiku sufficient. No schema migration, no cloud field add, no auth/security change → does not warrant full `ai-review`.
