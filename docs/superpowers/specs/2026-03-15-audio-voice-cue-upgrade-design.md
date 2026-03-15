# Audio & Voice Cue Upgrade — Design Spec

**Date:** 2026-03-15
**Status:** Approved

## Problem

Cardea's audio coaching feels unpolished:
1. Android TTS voice is robotic and generic
2. The app only speaks reactively (zone violations) — no proactive informational cues
3. Earcon tones are thin pure sine waves

## Solution Overview

Replace runtime TTS with pre-recorded neural voice assets, add configurable informational cues (halfway, km splits, workout complete, in-zone confirmation), and upgrade earcons to richer pre-recorded audio.

## Voice: Pre-recorded Neural TTS

### Voice Selection

Microsoft Edge TTS `en-US-ChristopherNeural` at `-5%` speech rate.
Personality: Calm Coach — minimal, composed, short phrases.

### Generation Pipeline

A Python script `scripts/generate-voice-assets.py` uses the `edge-tts` package to generate all coaching lines as `.ogg` files into `app/src/main/res/raw/`.

- Run once at dev time; output committed to repo
- Re-run when lines are added or changed
- Produces OGG Vorbis files (small, Android-native, no decode overhead)

### Utterance Catalog

Fixed coaching lines:

| Key | Text | File |
|-----|------|------|
| SPEED_UP | "Pick it up" | `voice_speed_up.ogg` |
| SLOW_DOWN | "Ease back a little" | `voice_slow_down.ogg` |
| RETURN_TO_ZONE | "Back in zone" | `voice_return_to_zone.ogg` |
| PREDICTIVE_WARNING | "Watch your pace" | `voice_predictive_warning.ogg` |
| SEGMENT_CHANGE | "Next segment" | `voice_segment_change.ogg` |
| SIGNAL_LOST | "Signal lost" | `voice_signal_lost.ogg` |
| SIGNAL_REGAINED | "Signal back" | `voice_signal_regained.ogg` |
| HALFWAY | "Halfway" | `voice_halfway.ogg` |
| WORKOUT_COMPLETE | "Workout complete" | `voice_workout_complete.ogg` |
| IN_ZONE_CONFIRM | "Still in zone" | `voice_in_zone_confirm.ogg` |

Km split lines (1–50):

| Key | Text | File |
|-----|------|------|
| KM_SPLIT_1 | "Kilometer one" | `voice_km_1.ogg` |
| KM_SPLIT_2 | "Kilometer two" | `voice_km_2.ogg` |
| ... | ... | ... |
| KM_SPLIT_50 | "Kilometer fifty" | `voice_km_50.ogg` |

### Architecture Change: VoiceCoach

**Before:** `VoiceCoach` wraps `android.speech.tts.TextToSpeech`. Calls `tts.speak(utterance, QUEUE_FLUSH, ...)` at runtime.

**After:** `VoiceCoach` uses `android.media.MediaPlayer` to play `R.raw.voice_*` resources.

- Constructor takes `Context` (for resource access), no TTS initialization
- `speak(event, guidanceText)` maps `CoachingEvent` to `R.raw.*` resource ID and plays it
- For `KM_SPLIT`, the km number is passed via a new parameter or embedded in the event
- `QUEUE_FLUSH` behavior: calling `speak()` stops any in-progress playback before starting the new clip
- `verbosity` filtering remains: OFF returns early, MINIMAL plays subset, FULL plays all
- `destroy()` releases MediaPlayer

The `isReady` / TTS initialization callback / `Locale.US` / `setSpeechRate` code is all deleted.

### MINIMAL vs FULL Verbosity

Same split as today — MINIMAL only speaks for SPEED_UP, SLOW_DOWN, SEGMENT_CHANGE, SIGNAL_LOST. FULL speaks for all events including informational cues. The informational cue toggles apply on top of verbosity (a cue must be both enabled in settings AND permitted by the verbosity level).

Informational cues (HALFWAY, KM_SPLIT, WORKOUT_COMPLETE, IN_ZONE_CONFIRM) are only spoken at FULL verbosity.

## New Informational Cue Events

### New CoachingEvent Entries

```kotlin
enum class CoachingEvent {
    // Existing
    SPEED_UP,
    SLOW_DOWN,
    RETURN_TO_ZONE,
    PREDICTIVE_WARNING,
    SEGMENT_CHANGE,
    SIGNAL_LOST,
    SIGNAL_REGAINED,
    // New
    HALFWAY,
    KM_SPLIT,
    WORKOUT_COMPLETE,
    IN_ZONE_CONFIRM
}
```

### Trigger Logic (in CoachingEventRouter)

**HALFWAY:**
- Fires once per workout when elapsed distance >= 50% of target distance, OR elapsed time >= 50% of target time
- Only applicable when the workout has a defined target (steady state with target distance/time, distance profile)
- Free run: never fires (no target)
- Guard: `halfwayFired: Boolean` flag, reset on `reset()`

**KM_SPLIT:**
- Fires each time `floor(distanceMeters / 1000)` increases
- Carries the km number (1-based) — passed to `CoachingAudioManager.fireEvent` via a new `splitKm: Int?` parameter or by encoding in the guidance text
- Guard: `lastKmAnnounced: Int` tracking var

**WORKOUT_COMPLETE:**
- Fires once when the target distance or target time is reached
- Guard: `completeFired: Boolean` flag

**IN_ZONE_CONFIRM:**
- Fires when all of:
  - HR is in zone (`ZoneStatus.IN_ZONE`)
  - At least 3 minutes since last voice cue of any kind
  - No zone alert is currently escalating
- Does NOT reset the escalation tracker
- Guard: `lastVoiceCueTime: Long` updated by all voice-producing events

### Event Routing Through CoachingAudioManager

Informational events skip escalation entirely — they always play earcon + voice (no vibration). They go through the `else` branch in `fireEvent()`, same as SEGMENT_CHANGE today.

## New Informational Cue Settings

### AudioSettings Model

```kotlin
data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    // New
    val enableHalfwayReminder: Boolean = true,
    val enableKmSplits: Boolean = true,
    val enableWorkoutComplete: Boolean = true,
    val enableInZoneConfirm: Boolean = true
)
```

### Settings Filtering

`CoachingAudioManager.fireEvent()` checks the relevant toggle before playing informational events. If the toggle is off, the event is silently dropped.

### UI: New "Informational Cues" Section

In both `SetupScreen` and `AccountScreen`, below the existing audio controls:

**Section header:** "Informational Cues"

Four `Switch` rows:
- "Halfway reminder" — toggle for `enableHalfwayReminder`
- "Kilometer splits" — toggle for `enableKmSplits`
- "Workout complete" — toggle for `enableWorkoutComplete`
- "In-zone confirmation" — toggle for `enableInZoneConfirm`

The entire section is visually dimmed / disabled when `voiceVerbosity == OFF`.

## Earcon Upgrade

### Approach

Replace `EarconSynthesizer` (runtime sine wave generation via `AudioTrack`) with `EarconPlayer` using `SoundPool` to play pre-recorded `.ogg` earcon assets from `res/raw/`.

### Generation

The same `scripts/generate-voice-assets.py` script also generates earcon `.ogg` files using Python audio synthesis (numpy + scipy or similar):
- 2–3 harmonics per tone (fundamental + octave + fifth)
- Softer ADSR envelope (longer attack, gentler release)
- Light low-pass filter for warmth
- Same musical intervals as today (C4–G5 range)

### Earcon Files

| Event | File |
|-------|------|
| SPEED_UP | `earcon_speed_up.ogg` |
| SLOW_DOWN | `earcon_slow_down.ogg` |
| RETURN_TO_ZONE | `earcon_return_to_zone.ogg` |
| PREDICTIVE_WARNING | `earcon_predictive_warning.ogg` |
| SEGMENT_CHANGE | `earcon_segment_change.ogg` |
| SIGNAL_LOST | `earcon_signal_lost.ogg` |
| SIGNAL_REGAINED | `earcon_signal_regained.ogg` |
| HALFWAY | `earcon_halfway.ogg` |
| KM_SPLIT | `earcon_km_split.ogg` |
| WORKOUT_COMPLETE | `earcon_workout_complete.ogg` |
| IN_ZONE_CONFIRM | `earcon_in_zone_confirm.ogg` |

### Architecture Change: EarconSynthesizer → EarconPlayer

**Before:** `EarconSynthesizer` generates `ShortArray` PCM data at runtime, writes to `AudioTrack`.

**After:** `EarconPlayer` wraps `SoundPool`, loads all earcon resources on init, plays by resource ID.

- `SoundPool` is designed for short, low-latency sound effects — perfect for earcons
- Volume control via `SoundPool.play(soundId, volume, volume, ...)`
- `destroy()` releases the SoundPool

`EarconWaveforms.kt` is deleted (no longer needed).

## What Stays the Same

- `AlertPolicy` — delay/cooldown gating, untouched
- `EscalationTracker` — earcon→voice→vibration escalation ladder, untouched
- `VibrationManager` — untouched
- `CoachingAudioManager` — same orchestration pattern, just more events and asset-based playback
- Earcon volume slider — stays, now controls SoundPool volume
- Voice verbosity segmented button (Off/Minimal/Full) — stays
- Vibration toggle — stays
- `AudioSettingsRepository` — stays, serializes the expanded `AudioSettings`

## Upgrade Safety: Gson Deserialization

Gson deserializes missing JSON fields as Java defaults (`false` for boolean), not Kotlin data class defaults. Existing users upgrading would get all four informational cue toggles set to `false` (disabled) instead of the intended `true`.

**Fix:** `AudioSettingsRepository.getAudioSettings()` applies Kotlin defaults for any null boolean fields after deserialization. Change the four new fields to `Boolean?` internally during deserialization and coalesce with `?: true`.

Alternatively, use a custom deserializer or post-deserialize fixup:

```kotlin
fun getAudioSettings(): AudioSettings {
    val raw = prefs.getString(PREF_AUDIO_SETTINGS_JSON, null) ?: return AudioSettings()
    return runCatching {
        val parsed = JsonCodec.gson.fromJson<AudioSettings>(raw, audioSettingsType)
        // Ensure new fields default to true for existing users
        parsed
    }.getOrElse { AudioSettings() }
}
```

The simplest approach: make the four new fields nullable (`Boolean?`) in the data class, default to `null`, and have all consumers treat `null` as `true`. The settings UI and `CoachingAudioManager` both use `settings.enableHalfwayReminder != false` instead of `settings.enableHalfwayReminder`.

## Dynamic Guidance Text

Pre-recorded voice lines replace dynamic `guidanceText` that was passed through for SPEED_UP, SLOW_DOWN, and PREDICTIVE_WARNING. This is an intentional trade-off: the current dynamic text was rarely different from the default anyway (it came from `ZoneEngine` guidance which produced generic phrases). The quality improvement from neural TTS outweighs the loss of rarely-used dynamic phrasing.

If context-specific coaching text becomes important in the future, a hybrid approach could use pre-recorded lines for common phrases and fall back to runtime TTS for rare/dynamic content. This is explicitly out of scope for now.

## KM_SPLIT Parameter Passing

The km number flows through the full pipeline:

```
CoachingEventRouter.route() → emitEvent(CoachingEvent.KM_SPLIT, kmNumber.toString())
  → CoachingAudioManager.fireEvent(event, guidanceText = "3")
    → VoiceCoach.speak(event, guidanceText = "3")
      → parse guidanceText as Int → play R.raw.voice_km_3
```

The existing `guidanceText: String?` parameter on `fireEvent` and `speak` carries the km number as a string. No signature changes needed — just the interpretation changes for `KM_SPLIT`.

## APK Size Impact

- ~60 voice lines × ~10KB each = ~600KB
- ~11 earcon clips × ~5KB each = ~55KB
- Total: ~650KB added to APK — negligible

## Files Changed

### New Files
- `scripts/generate-voice-assets.py`
- `app/src/main/res/raw/voice_*.ogg` (generated assets)
- `app/src/main/res/raw/earcon_*.ogg` (generated assets)
- `app/src/main/java/com/hrcoach/service/audio/EarconPlayer.kt`

### Modified Files
- `CoachingEvent.kt` — add 4 new enum entries
- `AudioSettings.kt` — add 4 boolean toggle fields
- `VoiceCoach.kt` — rewrite: TTS → MediaPlayer with resource lookup
- `CoachingAudioManager.kt` — use EarconPlayer, filter informational events by settings
- `CoachingEventRouter.kt` — add halfway/km-split/complete/in-zone-confirm trigger logic
- `WorkoutForegroundService.kt` — pass audio settings to manager, pass distance/time info to router
- `SetupScreen.kt` / `SetupViewModel.kt` — add informational cues UI section
- `AccountScreen.kt` / `AccountViewModel.kt` — add informational cues UI section
- `AudioSettingsRepository.kt` — no code change needed (Gson serializes new fields automatically)

### Deleted Files
- `EarconSynthesizer.kt`
- `EarconWaveforms.kt`
