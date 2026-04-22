# Active Run Settings Menu
Date: 2026-04-21

## Intent
- Gear icon top-right of `ActiveWorkoutScreen` opens a `ModalBottomSheet` with run-time settings.
- Full audio cue personalization (all 9 `AudioSettings` fields) editable mid-run, live-applied via existing `ACTION_RELOAD_AUDIO_SETTINGS` pipeline.
- For bootcamp sessions: sheet also offers "End session early" — graceful finish that **credits** the session (via `bootcampSessionCompleter.complete()`) rather than discarding it.
- Persist identically to Account tab (local Gson + cloud field-by-field already handled by `AudioSettingsRepository` + `saveAudioSettings`).
- Option A factoring: extract shared stateless `AudioSettingsSection` composable used by both AccountScreen and the new sheet.

## Scope boundary
**In:** gear icon + sheet host on `ActiveWorkoutScreen`; `AudioSettingsSection` stateless composable extraction + AccountScreen refactor to consume it; `ActiveRunSettingsViewModel` reusing `AudioSettingsRepository`; `WorkoutForegroundService.ACTION_FINISH_BOOTCAMP_EARLY` + WFS handler; end-early CTA visible only when `workoutState.pendingBootcampSessionId != null`.
**Out:** new `AudioSettings` fields; cloud schema changes; non-audio settings beyond the bootcamp-finish-early CTA; post-run sheet UX; gear entry points on other screens.

## Files to touch
- `ui/components/settings/AudioSettingsSection.kt` — **NEW**, stateless `(state: AudioSettings, onEvent: (AudioSettingsEvent) -> Unit)` composable; all 9 sliders/toggles/segments.
- `ui/account/AccountScreen.kt` — replace inline audio section (~lines 298–405) with `AudioSettingsSection(...)`.
- `ui/workout/ActiveRunSettingsSheet.kt` — **NEW** `ModalBottomSheet` host; consumes VM state; hosts `AudioSettingsSection` + conditional `EndBootcampEarlyButton`.
- `ui/workout/ActiveRunSettingsViewModel.kt` — **NEW** `@HiltViewModel`; injects `AudioSettingsRepository` + `@ApplicationContext Context`; `save()` mirrors `AccountViewModel.saveAudioSettings()` (writes repo → `startService(ACTION_RELOAD_AUDIO_SETTINGS)`).
- `ui/workout/ActiveWorkoutScreen.kt` — add `IconButton(Icons.Default.Settings)` top-right anchored in safe-area; `var sheetOpen by remember { mutableStateOf(false) }` at top-level (NOT conditional); suppress gear while `countdownActive`.
- `service/WorkoutForegroundService.kt` — add `ACTION_FINISH_BOOTCAMP_EARLY` constant + handler: if `pendingBootcampSessionId != null`, run `bootcampSessionCompleter.complete(...)` then proceed through normal `stopWorkout()` path (saves workout, credits session).

## CLAUDE.md rules honored
- **Notification stop gate** — `ACTION_FINISH_BOOTCAMP_EARLY` funnels into existing `stopWorkout()` which already calls `WorkoutNotificationHelper.stop()` before `stopForeground`. No new stop path.
- **Live audio settings** — save writes via `AudioSettingsRepository` then `startService(ACTION_RELOAD_AUDIO_SETTINGS)` — same contract as AccountViewModel. Do not call `CoachingAudioManager.applySettings()` from outside the service.
- **Countdown overlay invariants** — `countdownActive` suppresses both the gear icon and the sheet (mirrors CueBannerOverlay suppression rule). Sheet state checked in `LaunchedEffect(countdownActive) { if (countdownActive) sheetOpen = false }`.
- **`remember` not in conditionals** — `rememberModalBottomSheetState()` + `sheetOpen` hoisted to top-level scope; sheet composable call is conditional but `remember` is not.
- **GlassCard double-border** — sheet content uses GlassCard with `borderColor = Color.Transparent` if wrapped in outer tinted border.
- **M3 button text color leak** — all `OutlinedButton`/`TextButton` inside sheet pass explicit `color = CardeaTheme.colors.textPrimary`.
- **DataStore slider rule** — persist on `onValueChangeFinished`, update in-memory `StateFlow` in `onValueChange` (carries over in `AudioSettingsSection` from AccountScreen).
- **AudioSettings dual persistence** — no new fields added, so cloud paths untouched.
- **Bootcamp session completion** — `complete(...)` call must pass `lastTuningDirection` from saved profile (mirror WFS sim path pattern, not HOLD default).

## Tests
- **Unit:** new WFS test covering `ACTION_FINISH_BOOTCAMP_EARLY` with `pendingBootcampSessionId` set → verifies `bootcampSessionCompleter.complete()` invoked with correct session + `lastTuningDirection`; without pendingId → falls through to normal stop.
- **Unit:** `ActiveRunSettingsViewModel.save()` writes repo + sends `ACTION_RELOAD_AUDIO_SETTINGS` intent (fake context / fake repo).
- **Device (mobile-mcp):** tap gear → sheet opens; adjust voice volume slider → close sheet → verify `adb logcat` shows `CoachingAudioManager` reload; during bootcamp run, tap "End session early" → verify session appears in history with completion credit (check `bootcamp_sessions` via run-as cp backup).

## Risk flags
- **WFS new action** — must dispatch through existing `stopWorkout()` so notification stop gate + dual-pause cleanup + metrics save all fire. Do NOT duplicate stop logic.
- **Dual-pause + sheet** — sheet visible during auto-pause is fine (no state coupling); no changes to `pauseWorkout()`/`resumeWorkout()` ordering.
- **Bootcamp session-identity contract** — read `pendingBootcampSessionId` from `WorkoutState.snapshot.value` at action handle time; it is set by `prepareStartWorkout()` and preserved through reset.
- **Cloud backup** — reusing existing field path; no `CloudBackupManager`/`CloudRestoreManager` edits required.
- **No DB schema touch** — skip DB backup step.

Ready to execute, or adjust?
