# Audio Pipeline & Alert Architecture

Load when touching `service/audio/`, `service/workout/AlertPolicy.kt`, `service/workout/CoachingEventRouter.kt`, audio settings plumbing, or TTS/earcon behaviour.

## Two-system split (same `CoachingAudioManager` sink, separate state)

- **`AlertPolicy`** (`service/workout/`) — zone alert timing. Fires `SPEED_UP`/`SLOW_DOWN` after `alertDelaySec` continuous out-of-zone; then `alertCooldownSec` between repeats. Escalation resets on zone return via `onResetEscalation` → `CoachingAudioManager.resetEscalation()`. Cooldown persists across direction flips (ABOVE→BELOW) — intentional, prevents spam at threshold.
- **`CoachingEventRouter`** (`service/workout/`) — informational cues: splits, halfway, segment changes, RETURN_TO_ZONE, IN_ZONE_CONFIRM, predictive warnings. Tracks `lastVoiceCueTimeMs` to gate the 3-min IN_ZONE_CONFIRM silence window.
- **Bridging contract:** `AlertPolicy.onAlert` fires through `CoachingAudioManager` directly — router never sees these. After any `alertPolicy.onAlert`, call `coachingEventRouter.noteExternalAlert(nowMs)` or IN_ZONE_CONFIRM can fire within 3 min of an alert.
- **Live audio settings:** mid-workout changes go via `WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS` (`startService`). `AccountViewModel.saveAudioSettings()` does this. `CoachingAudioManager.applySettings()` receives — do not call from outside the service.
- **`AlertPolicy.handle()` only runs when `!isAutoPaused`** (WFS processTick guard). So walk-break suppression, self-correction suppression, and any future AlertPolicy gate is a no-op when auto-pause is engaged — intentional and non-conflicting, but new gates don't need to re-check auto-pause state.
- **`AudioSettings` has two persistence paths** — local (Gson JSON blob in SharedPreferences, missing fields auto-default) and cloud (field-by-field map in `CloudBackupManager.syncSettings` + field-by-field read in `CloudRestoreManager.restoreSettings`). Adding a new field requires touching BOTH cloud paths; local storage handles it automatically.

## Audio stream routing

- All components use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music without audio focus. Do NOT use `NOTIFICATION_EVENT` (gets ducked).
- **Exception:** `ToneGenerator` takes `AudioManager.STREAM_*`, not `USAGE_*` — use `STREAM_MUSIC`. The two constant spaces are distinct; mis-passing silently routes wrong.

## Three-component layered audio (`service/audio/`)

- **`EarconPlayer`** — SoundPool, short WAV zone alerts. `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`.
- **`VoicePlayer`** — TTS for briefings, zone alerts (using adaptive guidance text), splits, cues. Priority-gated via `VoiceEventPriority` (CRITICAL > NORMAL > INFORMATIONAL). Layers over music.
- **`StartupSequencer`** — MediaPlayer plays `countdown_321_go.wav` (custom marimba). Updates `WorkoutState.countdownSecondsRemaining` for UI.
- **`CoachingAudioManager`** — Orchestrator. Startup: TTS briefing (await) → countdown WAV (await) → return (timer starts). No delay between briefing and countdown — removed 500ms dead air that felt like a hang. `VoiceVerbosity.OFF` gates ALL audio (earcons + voice). `shouldPlayEarcon(verbosity)` is the single earcon gate.

## Invariants

- **`CoachingAudioManager.skipNextBriefing`** — `@Volatile` companion flag, one-shot. Set by primer-dismiss handlers in `SetupViewModel.dismissPrimerThenProceed` AND `BootcampViewModel.dismissPrimerThenProceed` so the very next `playStartSequence` skips TTS briefing (primer just explained the audio system — briefing on top feels redundant). Cleared on read. This is the canonical cross-layer signal — do not plumb a parameter through Intent/Service layers to replicate.
- **Pause/resume tones honor `voiceVerbosity == OFF`** — `playPauseFeedback()` early-returns when verbosity is OFF; otherwise plays at `earconVolume`.
- **`AudioSettings`:** `earconVolume` and `voiceVolume` independent (0–100).
- **Verbosity:** OFF silent; MINIMAL = critical/normal only; FULL = all events including informational.
- **KM splits:** "Kilometer N" (STEADY_STATE/DISTANCE_PROFILE); "Kilometer N. Pace: X minutes Y." (FREE_RUN).
- **IN_ZONE_CONFIRM speaks the fixed string "In zone"** — hard-coded in `VoicePlayer.eventText` (ignores `guidanceText`); router emits with `null`. Do NOT reintroduce guidance-text relay — zone2's long preset string fires every 3-5 min via STANDARD cadence and reads as nagging. `LOW_CONFIDENCE_AFFIRMATIONS` removed 2026-04-21.
- **Preset static quips never leak to voice** — WFS computes a separate `voiceGuidance: String?` (null when the merged `guidance` came from a preset override like zone2's "Easy pace builds your aerobic engine. Hold a conversation." or the strides cue). Only `voiceGuidance` is passed to `CoachingEventRouter.route(guidance = …)`. With null guidance, RETURN_TO_ZONE and PREDICTIVE_WARNING fall back to VoicePlayer's fixed "Back in zone" / "Watch your pace". Without this split, the zone2 quip was spoken every 30s on zone re-entry (RETURN_TO_ZONE) and every 3 min on projected drift (PREDICTIVE_WARNING). On-screen `WorkoutState.guidanceText` is unaffected — it still shows the merged preset override. AlertPolicy continues to receive the full display `guidance` because it only fires when out-of-zone (never sees IN_ZONE preset quips).
- **FULL verbosity speaks voice on tier-1 zone alerts.** `CoachingAudioManager` SPEED_UP/SLOW_DOWN branch: `shouldSpeakAlert` is true for both MINIMAL (when `minimalTierOneVoice` is on) and FULL. The original "FULL uses gradual 3-tier escalation" design assumed counter accumulation across drift; in reality `AlertPolicy.onResetEscalation` fires every zone re-entry, so an oscillating runner never escaped tier-1 earcon-only and never heard voice. Tier-3 (earcon+voice+vibration) is still gated on sustained drift — only vibration escalates now.
- **`VoicePlayer` stale-priority watchdog** — `speakEvent` clears `currentPriority` if `currentPrioritySetAtMs` is older than `WATCHDOG_MAX_UTTERANCE_MS` (15s). Recovers from dropped TTS `onDone` (engine hang, audio-focus loss, doze), which would otherwise silence every non-CRITICAL cue for the rest of the run. Do not remove or shorten below ~10s. `speakAnnouncement` also stamps the timestamp.
- **`VoicePlayer.priorityLock` guards the watchdog→gate→state-set→speak compound action** in `speakEvent`/`speakAnnouncement`/`handleUtteranceEnd`. `@Volatile` alone is insufficient: two concurrent callers (HR tick + KM split landing in the same window after a stalled utterance) could both pass the watchdog, both clear, both set their own priority, and the second silently overrides the first inside `tts.speak`. Lock is held through `tts.speak` (fast JNI enqueue, not blocking on speech). Deferred completions in `handleUtteranceEnd` are done OUTSIDE the lock — `Deferred.complete` may resume continuations synchronously, and keeping the lock scope to state-mutation only avoids holding it across arbitrary resumed coroutine work.
- **`VoicePlayer.speakBriefing` 30s watchdog (`BRIEFING_WATCHDOG_MS`)** — wraps `deferred.await()` in `withTimeoutOrNull`. The `speakEvent` watchdog does NOT cover briefing (briefing has its own `pendingBriefingDeferred` and never stamps `currentPrioritySetAtMs`). A dropped briefing `onDone` would otherwise block the next workout's startup forever at the briefing-await step. On timeout: stop TTS, null `pendingBriefingDeferred`, defensive priority-state clear, return so the run can start.
- **`EarconPlayer.play()` gated on `loadedSampleIds`** populated by `setOnLoadCompleteListener`. `SoundPool.play()` on a not-yet-loaded sample returns streamId=0 silently, so early `SIGNAL_LOST` during briefing would drop with no log. Do not revert to unguarded `play()`.
- **When changing what's spoken on any `CoachingEvent`, audit ALL fallback branches in `VoicePlayer.eventText`** — `RETURN_TO_ZONE`, `PREDICTIVE_WARNING`, and `IN_ZONE_CONFIRM` all relay `guidanceText` (with different fallbacks: "Back in zone" / "Watch your pace" / "In zone"). A fix that only patches one branch (e.g. commit 39f3a13 hard-coding only IN_ZONE_CONFIRM) leaves the same preset string leaking via the others. Treat the three as a set.

## TTS Debug Log (`TtsDebugLogger`)

Per-run append log at `filesDir/tts_debug/run_YYYYMMDD_HHmmss[_sim].log`. Retention: **last 2 runs kept**; older deleted on next `startRun`. 5 MB per-file safety cap. Diagnostic-only — does NOT touch Room / cloud backup.

- Opened in `WFS.startWorkout` IO startup coroutine just before `playStartSequence` (briefing captured); closed in `cleanupManagers` (idempotent across stop, `handleStartFailure`, `onDestroy`).
- Captures: `LIFECYCLE` (START_SEQUENCE/END_SEQUENCE/PAUSE/RESUME), `FIRE event=... hr=... tgt=... zone=... slope=... verbosity=... t=...s guidance=...`, `TTS ... action=SPEAK|SKIP|DROP|WATCHDOG`, `EARCON ... action=PLAY|DROP`. Reads HR context from `WorkoutState.snapshot.value` at fireEvent entry.
- Pull: `adb shell "run-as com.hrcoach cat files/tts_debug/<filename>"`. Logcat `main` ring buffer (~5 MiB) rolls out within hours — rely on this file log, or `adb logcat -f /sdcard/cardea.log -v time` before the run, for any run older than ~an hour.
