# Training tab — segmented Bootcamp / Free Run
Date: 2026-04-24

## Intent

- Stop the Workout/Training tab from silently mode-switching based on enrollment. First-time users land on a tab that **shows both modes exist**.
- Make "switch to free run" a peer navigation action, not a buried link on a session card.
- Split the single destructive "End this program?" into three clearly-named actions per Graham's taxonomy: **Leave** (implicit — tap Free Run), **Pause** (no notifications, keep schedule), **Delete** (remove schedule + prefs, keep run logs + adaptive profile).
- Rename bottom-nav label "Workout" → "Training" to match internal conventions and match the bootcamp-is-primary framing already codified in `docs/claude-rules/ui-theme.md:74`.
- Lightly rework Home cross-links so they point at the right segment.

## Scope boundary

In:
- `ui/navigation/NavGraph.kt` — nav label, single routing target for Training tab, arg plumbing for initial segment.
- New `ui/training/TrainingScreen.kt` — shell that hosts the segmented `TabRow` and swaps between bootcamp and free-run content. Pulls in existing `BootcampScreen` and `SetupScreen` composables as panes.
- `ui/bootcamp/BootcampScreen.kt` — remove the buried "Manual run →" link row; replace overflow "End this program?" with Pause / Delete two-item menu; copy refinements on dialogs.
- `ui/bootcamp/BootcampViewModel.kt` — already has pause/resume + delete; surface pause state to the menu; no new business logic.
- `ui/setup/SetupScreen.kt` — small top-of-screen teaser card for unenrolled users inviting them to check Bootcamp (only shown when `!isBootcampEnrolled`).
- `ui/home/HomeScreen.kt` — update `NoBootcampCard` CTA + "Just run" strip copy so the CTA deep-links to Training with the correct segment pre-selected.
- `strings.xml` — `nav_workout` becomes `nav_training` (or new key + deprecate old). Segment labels, pause/delete copy, dialog bodies.

Out:
- No schema changes. No DAO changes.
- No changes to the bootcamp engine, phase logic, or any `service/` code.
- No icon change for the nav item this pass (stays `FavoriteBorder`) — evaluated separately to avoid icon-availability yak-shave.
- No changes to Account tab.
- No new adaptive logic; pause still uses existing `STATUS_PAUSED` path.

## Approach

**Segmented header at the top of the Training tab.** Two-tab `TabRow` using the sanctioned pattern in `docs/claude-rules/ui-theme.md:66`:

```
[ Bootcamp ]  [ Free Run ]          ← TabRow with GradientPink indicator
─────────────
  <pane content>                    ← BootcampPane or FreeRunPane
```

- **Bootcamp pane (enrolled):** existing `BootcampScreen` content minus the "Manual run →" link row (now redundant).
- **Bootcamp pane (not enrolled):** a ported, slightly reframed version of `NoBootcampCard` (currently on Home) — hero copy, 3 feature bullets, "Set Up Bootcamp" CTA. Same visual language.
- **Free Run pane:** existing `SetupScreen` content. For unenrolled users: small Tier-3 glass teaser above the pane content — "Prefer a structured plan? Check Bootcamp." No CTA button; the segment itself is the CTA.

**Default segment selection:**
- Enrolled → Bootcamp.
- Not enrolled → Bootcamp (discoverability wins; unenrolled card is friendly and free run is one tap away).
- Navigated here via Home "Just run" strip → Free Run.
- Navigated here via Home "Set Up Bootcamp" CTA → Bootcamp.

Initial segment passed as a nav argument: `Routes.TRAINING?segment={bootcamp|free}`.

**Exit model mapping (Graham's taxonomy):**

| Action | UI surface | Effect |
|---|---|---|
| Leave | Tap "Free Run" segment | Dismiss bootcamp view; schedule untouched; notifications unchanged. Pure navigation. |
| Pause | Overflow → "Pause program" | `STATUS_PAUSED`. No session notifications. Schedule and prefs retained. Resume from same menu. |
| Delete | Overflow → "Delete bootcamp" | `deleteEnrollment()`. Removes schedule + bootcamp preferences. **Preserves** `workouts`, `workout_metrics`, `AdaptiveProfile`. Confirmation dialog states this explicitly. |

**Bootcamp overflow menu** (replaces current single "End this program?" menu item):
- If `STATUS_ACTIVE`: [Pause program] [Delete bootcamp]
- If `STATUS_PAUSED`: [Resume program] [Delete bootcamp]

Copy drafts:
- Pause dialog: *"Pause this program? No scheduled sessions or reminders until you resume. Your progress and schedule are kept."*
- Delete dialog: *"Delete this bootcamp? Your schedule and bootcamp preferences will be removed. Your run history and fitness profile are kept — you can start a new program anytime."*

## Files to touch

- `ui/navigation/NavGraph.kt` — add `Routes.TRAINING`, rewire tab to that route, optional `segment` arg, keep back-compat for deep links to `Routes.BOOTCAMP` / `Routes.SETUP` by collapsing into Training + segment.
- `ui/training/TrainingScreen.kt` **(new)** — `TabRow` + pane content; delegates rendering to existing screens.
- `ui/bootcamp/BootcampScreen.kt` — remove manual-run link row (~lines 2448–2505 area); restructure overflow menu; two dialogs (pause/resume + delete) with new copy.
- `ui/bootcamp/BootcampViewModel.kt` — no new methods; expose `enrollment.status` to screen if not already surfaced.
- `ui/setup/SetupScreen.kt` — small optional header strip for unenrolled users.
- `ui/home/HomeScreen.kt` — CTA routes become `Routes.TRAINING?segment=bootcamp` / `?segment=free`.
- `res/values/strings.xml` — `nav_training`, segment labels, menu items, dialog bodies.

## CLAUDE.md rules honored

- **`docs/claude-rules/ui-theme.md:66`** — use `TabRow` (≤3 tabs) with `Box(Modifier.tabIndicatorOffset(...).height(2.dp).background(GradientPink))` indicator; wrap with `TabRowDefaults` for the offset. Do NOT use `ScrollableTabRow`.
- **`docs/claude-rules/ui-theme.md:15`** — single gradient primary CTA per screen; `BootcampEntryCard` CTA stays ghost-outlined; the Delete button is `OutlinedButton` with `ZoneRed` (`docs/claude-rules/ui-theme.md:43`).
- **`docs/claude-rules/ui-theme.md:64`** — pass explicit `color = textPrimary/textSecondary` to `Text()` inside any new `OutlinedButton`/`TextButton`.
- **`docs/claude-rules/ui-theme.md:51`** — no `remember*` in conditional branches (pane swap will use conditional *rendering* but `rememberSaveable(selectedSegment)` hoisted unconditionally).
- **`docs/claude-rules/ui-theme.md:30`** — typography: segment labels use `titleSmall` (15sp SemiBold); already defined.
- **`docs/claude-rules/ui-theme.md:28-29`** — all new copy ≥10sp; menu items and dialogs use `bodyMedium`.

## Tests

Unit (new or updated):
- `BootcampViewModelTest` — verify `pauseProgram()` sets `STATUS_PAUSED` and `resumeProgram()` restores `STATUS_ACTIVE` without modifying schedule rows.
- `BootcampViewModelTest` — verify `deleteBootcamp()` does NOT clear rows in `workouts`, `workout_metrics`, or `AdaptiveProfile` (check with fakes).

UI/device (mobile-mcp screenshot ladder):
- Unenrolled user: Training tab → Bootcamp segment default, shows onboarding card; Free Run segment shows Setup.
- Enrolled user: Training tab → Bootcamp segment default; dashboard visible; overflow shows Pause + Delete.
- Tap Free Run segment from bootcamp state → SetupScreen, no crash, can start a free run.
- Pause path: Pause program → menu flips to Resume; notifications verified silent (best-effort: check `BootcampSessionScheduler` / notification manager logs).
- Delete path: Delete dialog → confirm → row removed from `bootcamp_enrollments` and `bootcamp_sessions`; check `workouts`/`workout_metrics`/`adaptive_profile` rows untouched via `adb shell run-as com.hrcoach sqlite3`.
- Home "Just run" → opens Training with Free Run pre-selected; Home "Set Up Bootcamp" → Training with Bootcamp pre-selected.

## Risk flags

- Touches `service/` → **no**. Bootcamp pause already has a service-side path (scheduler observes `STATUS_PAUSED`); verify notifications actually suppress.
- Touches `audio/` → **no**.
- Touches `data/db/` → **no** schema change, but **delete** action writes — auto DB backup **is required** before running the delete test on device (see Phase 5.5).
- Touches cloud sync → **review**: `CloudBackupManager` and `CloudRestoreManager` already serialize enrollment + sessions. No field additions this pass, so no sync drift, but smoke-test a backup/restore round-trip after delete to confirm the deleted state persists correctly.
- Nav arg compatibility — existing deep-link callers of `Routes.BOOTCAMP` / `Routes.SETUP` must still work; add thin redirectors in `NavGraph` so `navigate(Routes.BOOTCAMP)` → `TRAINING?segment=bootcamp`.
- Back stack — ensure tapping Training tab while on an active-run screen doesn't pop into a broken state; keep the active-run auto-nav rules in `NavGraph` unchanged.
