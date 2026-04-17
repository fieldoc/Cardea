# Lockscreen Media Notification — Design

**Status:** Approved, ready for implementation
**Date:** 2026-04-14
**Author:** Claude (brainstormed with Graham)
**Visual reference:** `.superpowers/brainstorm/816-1776227880/content/approaches-v5.html`

## Summary

Replace the current single-line workout notification with a Spotify-style **MediaStyle** notification that shows rich, at-a-glance workout data on the lockscreen. The runner can see current HR, target HR, elapsed / total time, zone state, and progress without unlocking the phone — and can tap a pause/resume button directly from the lockscreen.

The visual language matches the Cardea gradient aesthetic: every zone state keeps the full 4-stop Cardea palette (red → pink → blue → cyan). Only the stop positions shift to signal zone state, so the badge never becomes a solid-colour alert object.

## Goals

1. Runner can see HR, target, elapsed / total, zone state on the lockscreen without unlocking.
2. Runner can pause / resume from the lockscreen without unlocking.
3. Zone state (BELOW / IN_ZONE / ABOVE) is glanceable through sweat — via two non-competing signals:
   - **Gradient slide** — colour channel (which direction)
   - **Rim accent + header dot** — spatial / chromatic accent channel (reinforces direction)
4. The notification feels like a Cardea object at all times — never a generic Android alert.
5. The existing workout text-update path (`notificationHelper.update(text)`) is replaced but the service-level contract (`startForeground` → `update` → `stop`) stays the same so the service orchestration code barely changes.

## Non-Goals

- **Asymmetric halo bleed** shown in the mockup is CSS-only — Android MediaStyle clips large icons to a rounded square and cannot draw outside it. The spec does not attempt to reproduce the outer halo; the gradient slide and rim accent carry the full signal load in the shipped version.
- No custom `RemoteViews` layout. MediaStyle gives us everything we need and ships better cross-OEM behaviour than custom layouts.
- No tracking of skip-forward / skip-backward actions. Only pause/resume.
- No rich album-art animation. The badge bitmap re-renders on HR change only, not every tick.
- Lockscreen widget (separate from notification) — deferred. True lockscreen widgets don't exist on modern Android.

## Architecture Overview

```
WorkoutForegroundService
  ├── MediaSessionCompat  (owned; created in onCreate, released in onDestroy)
  │     └── PlaybackState.position  ← elapsedSeconds (drives the progress bar)
  │
  └── WorkoutNotificationHelper  (upgraded)
        ├── BadgeBitmapRenderer  (new)  ← renders gradient HR badge to Bitmap
        ├── NotifContentFormatter  (new)  ← builds title/text/progress strings
        └── PendingIntent factory  (new)  ← pause/resume intents → service
```

**Data flow** — same as today:
1. `WorkoutForegroundService.processTick()` produces a `WorkoutSnapshot`
2. Service computes a `NotifPayload` (new data class) from the snapshot + workout config + total-duration
3. Service calls `notificationHelper.update(payload)` (signature change)
4. Helper builds a `NotificationCompat.Builder` with MediaStyle, a pre-rendered bitmap, title/text/progress, and pause/resume action
5. NotificationManager pushes the update

## Visual Specification

**Authoritative mockup:** `.superpowers/brainstorm/816-1776227880/content/approaches-v5.html`

### The badge (large icon / artwork)

- **Shape:** rounded square, 14 px corner radius (relative to badge size)
- **Size on device:** 144 × 144 px bitmap for the notification large-icon badge (rendered at device density). The MediaSession lockscreen artwork is rendered at 320×320 px (matches MediaMetadataCompat's scaleBitmapIfTooBig threshold so no rescaling occurs).
- **Content:**
  - Main: current HR number, white, Geist Bold, ~40 % of badge height, tabular numerals
  - Below: "BPM" label, Geist Mono SemiBold, ~11 % of badge height, letter-spaced 0.22 em
  - Top-right corner: small filled heart glyph, 12 % of badge size, 70 % white opacity
- **Gradient stops** (driven by `ZoneStatus`, 135° diagonal):
  | Zone state | Stops                                       |
  |------------|---------------------------------------------|
  | `BELOW`    | `#FF4D5A 0% → #FF2DA6 12% → #4D61FF 38% → #00E5FF 82% → #00E5FF 100%` |
  | `IN_ZONE`  | `#FF4D5A 0% → #FF2DA6 33% → #4D61FF 66% → #00E5FF 100%` |
  | `ABOVE`    | `#FF4D5A 0% → #FF4D5A 18% → #FF2DA6 50% → #4D61FF 85% → #00E5FF 100%` |
  | `NO_DATA`  | Greyscale: `#2A2A30 0% → #3A3A44 50% → #2A2A30 100%` |
  Every non-`NO_DATA` state keeps all four Cardea stops. Only positions shift.
- **Top sheen:** radial highlight from the top-centre (rgba(255,255,255,0.4) → transparent), baked into the bitmap
- **Bottom shadow:** radial darkening from the bottom-centre (rgba(0,0,0,0.25) → transparent)
- **Grain overlay:** subtle noise at 35 % opacity, overlay blend — optional polish, can be skipped if it hurts file size (< 1 kb noise tile, though)
- **Rim accent** (zone signal):
  - `IN_ZONE`: no rim
  - `BELOW`: 2 px horizontal luminous line along the **bottom edge**, `#00E5FF`, with a 10-pixel cyan glow falloff (20 % width gap each end)
  - `ABOVE`: 2 px horizontal luminous line along the **top edge**, `#FF2DA6`, with a 10-pixel pink glow falloff (20 % width gap each end)
  - **Never use pure `#FF4D5A`** for the rim — pink leads the warm signal, red only appears in the badge gradient itself. This is the lesson from v4 → v5 iteration.
- **Paused / auto-paused state:** the badge is rendered with the zone gradient desaturated to ~40 % saturation + 70 % opacity overlay of `#1A1A1F`. A small "⏸" glyph replaces the heart in the top-right corner. No rim.

### The notification chrome

Uses the standard Android Material 3 notification chrome — we do not override the container. Only the content inside it is customised.

- **Small icon:** `ic_notif_cardea` (existing drawable)
- **Accent colour** (`setColor`): `#FF2DA6` (Cardea pink; this tints the app-name strip and action buttons)
- **App name:** "Cardea" (automatic from package)
- **Timestamp:** `setShowWhen(true)` with `setUsesChronometer(false)` — we manage our own elapsed time in the subtitle

### Title text

Format: `{sessionLabel} · Target {targetHr}`

Fallbacks when fields are missing:
| Workout mode + data            | Title                          |
|--------------------------------|--------------------------------|
| Bootcamp with `sessionLabel`   | `Aerobic Tempo · Target 145`   |
| Steady-state, no label         | `Workout · Target 145`         |
| Distance-profile, no label     | `Workout · Target 145`         |
| Free run                       | `Free Run`                     |
| `NO_DATA` (no HR signal)       | `Get HR signal…`               |
| Paused                         | `{sessionLabel} · Paused`      |
| Auto-paused                    | `{sessionLabel} · Auto-paused` |
| Countdown                      | `Starting in {N}…` or `Get ready…` |

### Subtitle (content text)

Format: `{elapsed} / {total} · {deltaLabel}`

Where:
- `elapsed`: `mm:ss` formatted from `WorkoutSnapshot.elapsedSeconds`
- `total`: `mm:ss` formatted from the planned duration (see "Total duration resolution" below), or `∞` for free run / unknown
- `deltaLabel`:
  - `IN_ZONE` → `ON TARGET`
  - `ABOVE` → `+{delta} BPM` (e.g. `+13 BPM`)
  - `BELOW` → `-{delta} BPM` (e.g. `-7 BPM`)
  - `NO_DATA` → `—`

Examples:
- `18:30 / 45:00 · +13 BPM`
- `18:30 / ∞ · ON TARGET` (free run)
- `00:14 / 45:00 · —` (just started, no HR data yet)

### Progress bar

`NotificationCompat.Builder.setProgress(max, current, indeterminate)` with:
- `max` = total planned duration in seconds (0 if free run / unknown)
- `current` = `WorkoutSnapshot.elapsedSeconds` clamped to `[0, max]`
- `indeterminate` = `true` when `max == 0` (free run — shows a moving indeterminate bar)

Android will render this as a thin horizontal bar below the title/text. The system colour is applied automatically from `setColor(#FF2DA6)`.

### Action button

Compact view shows one action: Pause/Resume. No Stop button on the lockscreen.
- **When running:** `⏸ Pause` — fires `ACTION_PAUSE` to `WorkoutForegroundService`
- **When paused or auto-paused:** `▶ Resume` — fires `ACTION_RESUME` to `WorkoutForegroundService`
- Icon: use Material system icons `ic_media_pause` / `ic_media_play` (already available via `android.R.drawable`)
- Action title: `"Pause"` / `"Resume"`

## Total duration resolution

`WorkoutNotificationHelper` needs a total-duration value for the subtitle and progress bar. The resolution order (computed once in the service when the workout starts, cached for the duration of the workout):

1. `workoutConfig.plannedDurationMinutes * 60` if set
2. `workoutConfig.segments.sumOf { it.durationSeconds ?: 0 }` if > 0 (time-based segments)
3. `null` (treated as free run / unknown — display `∞`, use indeterminate progress)

This must be computed from `WorkoutConfig`, **not** from `WorkoutSnapshot` (which has no total-duration field).

## Badge rendering

### Rendering strategy

Render the badge as a Bitmap using Android's `android.graphics.Canvas` + `LinearGradient` APIs. **Not** Jetpack Compose — this must run in a service context without a composition.

### Cache strategy

Badge bitmaps are cached by `(zoneStatus, currentHrBucket)` where `currentHrBucket = currentHr` (exact HR). The cache is an LRU of up to 16 entries — new HR tick ⇒ cache lookup ⇒ render on miss. This avoids re-rendering an identical bitmap every second.

On paused / auto-paused, use a dedicated cached paused bitmap keyed off `(zoneStatus, PAUSED)`.

On `NO_DATA` (no HR signal), use a single shared grey bitmap.

### Rendering code path

A new class `BadgeBitmapRenderer` in `com.hrcoach.service.workout.notification`:

```kotlin
class BadgeBitmapRenderer(private val context: Context) {
    fun render(
        currentHr: Int,
        zoneStatus: ZoneStatus,
        paused: Boolean = false,
    ): Bitmap
}
```

Responsibilities:
- Allocate a 144 × 144 px `Bitmap.Config.ARGB_8888`
- Draw rounded-square mask
- Draw `LinearGradient` with the zone-appropriate stop positions (table above)
- Draw the top sheen (radial gradient white → transparent)
- Draw the bottom shadow (radial gradient black → transparent)
- Draw noise overlay (optional)
- Draw the HR number text (Geist or system sans-serif fallback, tabular figures via `Paint.TextAlign.CENTER` + fixed advance)
- Draw "BPM" label below the number
- Draw the heart glyph SVG path (inline hard-coded `Path`)
- Draw the rim accent (for BELOW / ABOVE only)
- If `paused`: apply a desaturation ColorMatrix + dark overlay + pause glyph
- Return the bitmap

**Font handling:** Attempt to load Geist from an asset file; fall back to `Typeface.SANS_SERIF` with bold weight if not present. Ship the Geist TTFs in `app/src/main/assets/fonts/` (approx 130 kb for both weights).

**Text measurement:** Use `Paint.getTextBounds` for centring, ensure `Paint.isSubpixelText = true` and `Paint.isAntiAlias = true` for smooth rendering at any density.

### Cache cleanup

The renderer holds a reference to the cache. When the service calls `notificationHelper.stop()`, the helper clears the cache.

## Action handling

### PendingIntent factory

```kotlin
private fun pausePendingIntent(): PendingIntent {
    val intent = Intent(context, WorkoutForegroundService::class.java).apply {
        action = WorkoutForegroundService.ACTION_PAUSE
    }
    return PendingIntent.getService(
        context,
        REQUEST_PAUSE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

Same for `ACTION_RESUME`.

Both `ACTION_PAUSE` and `ACTION_RESUME` already exist in `WorkoutForegroundService.kt` (lines 68-69) and are already handled in `onStartCommand` — **no service-side changes required** beyond firing the helper with new data. This is the main reason to pick MediaStyle + `getService` PendingIntents over custom action broadcasts.

### Content intent (tap to unlock + open)

Tapping the notification body opens `MainActivity` via an activity `PendingIntent` (unchanged from today's helper).

## MediaSession handling

`MediaStyle` requires a `MediaSessionCompat` token to render correctly on the lockscreen.

### Lifecycle

- **`onCreate`** — create `MediaSessionCompat(context, "CardeaWorkout")`, set `isActive = true`, attach a no-op callback (we don't respond to media transport controls directly — pause/resume flow through the notification action PendingIntents)
- **`onDestroy`** — `mediaSession.isActive = false`, `mediaSession.release()`

### PlaybackState updates

Each tick, the service updates the session's `PlaybackState`:

```kotlin
val state = PlaybackStateCompat.Builder()
    .setState(
        if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
        elapsedSeconds * 1000L,        // position in ms
        if (paused) 0f else 1f         // playback speed
    )
    .setActions(PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY)
    .build()
mediaSession.setPlaybackState(state)
```

Metadata (title / subtitle / duration) is set via `MediaMetadataCompat`:

```kotlin
val metadata = MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitleText)
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalDurationSec * 1000L)
    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, badgeBitmap)
    .build()
mediaSession.setMetadata(metadata)
```

Note: the `NotificationCompat.Builder` also sets its own title / text / largeIcon / progress. Having the same data in both places is intentional — the session drives the lockscreen media control, the builder drives the shade notification. Keeping them in sync is the helper's job.

## Data class — `NotifPayload`

New data class in `service/workout/notification/NotifPayload.kt`:

```kotlin
data class NotifPayload(
    val titleText: String,           // "Aerobic Tempo · Target 145"
    val subtitleText: String,        // "18:30 / 45:00 · +13 BPM"
    val currentHr: Int,              // 158 (or 0 for NO_DATA)
    val zoneStatus: ZoneStatus,      // drives badge gradient
    val elapsedSeconds: Long,        // for progress bar + session position
    val totalSeconds: Long,          // 0 for free run / indeterminate
    val isPaused: Boolean,           // drives action button + badge dimming
    val isIndeterminate: Boolean,    // true for free run / unknown total
)
```

`WorkoutForegroundService` builds this from `WorkoutSnapshot` + `WorkoutConfig` each tick. `WorkoutNotificationHelper.update(payload: NotifPayload)` consumes it.

## Files to create / modify

### New files

| Path                                                                                   | Purpose                                      |
|----------------------------------------------------------------------------------------|----------------------------------------------|
| `app/src/main/java/com/hrcoach/service/workout/notification/NotifPayload.kt`           | Data class passed from service → helper      |
| `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapRenderer.kt`    | Canvas-based gradient badge renderer + cache |
| `app/src/main/java/com/hrcoach/service/workout/notification/NotifContentFormatter.kt`  | Pure functions: format title / subtitle / delta |
| `app/src/main/assets/fonts/Geist-Bold.ttf`                                             | Display font for HR number                   |
| `app/src/main/assets/fonts/GeistMono-SemiBold.ttf`                                     | Mono font for "BPM" label                    |
| `app/src/test/java/com/hrcoach/service/workout/notification/NotifContentFormatterTest.kt` | Unit tests for title / subtitle formatting |

### Modified files

| Path                                                                          | Change                                                                 |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `app/src/main/java/com/hrcoach/service/workout/WorkoutNotificationHelper.kt`  | Major rewrite: MediaStyle, MediaSession, bitmap rendering, actions     |
| `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`           | Build `NotifPayload` each tick, pass to helper; own MediaSessionCompat |
| `app/build.gradle.kts`                                                        | Add `androidx.media:media:1.7.0` dependency if not already present     |

### Files NOT modified

- `WorkoutState.kt` — existing snapshot is sufficient
- `AlertPolicy`, `CoachingEventRouter`, `CoachingAudioManager` — unrelated
- UI / Compose code — notification is purely system-level

## Testing

### Unit tests (`./gradlew test`)

- **`NotifContentFormatterTest`**
  - Title fallback matrix (bootcamp label, no label, free run, paused, auto-paused, countdown, NO_DATA)
  - Subtitle formatting (elapsed / total, delta signs, indeterminate `∞`, NO_DATA `—`)
  - Edge cases: elapsed > total (overtime), elapsed = 0, negative deltas
- **`BadgeBitmapRendererTest`** (JUnit + Robolectric or pure Canvas test)
  - Renders without crashing for each `ZoneStatus` + paused state
  - Returns a non-null, non-empty Bitmap of the expected dimensions
  - Cache hits return the same bitmap instance for identical `(hr, zone, paused)` inputs
  - Cache miss on changed HR renders a new bitmap

### Integration / manual checks

- **Build & install** debug APK, start a bootcamp session, lock the phone, verify:
  - Badge shows correct gradient per zone
  - Subtitle shows correct elapsed / total / delta
  - Progress bar animates
  - Tapping pause from the lockscreen actually pauses the workout
  - Tapping resume actually resumes
  - Notification survives service restart (if the service is restarted with `START_REDELIVER_INTENT`)
- **Free run** — verify `∞` in subtitle and indeterminate progress bar
- **Countdown** — verify "Starting in 3…" title during the startup sequence
- **Paused / auto-paused** — verify dimmed badge + "Resume" action
- **Screenshot comparison** against the v5 mockup for visual fidelity

### What we're NOT testing

- Visual pixel-perfect match against the mockup — Android rendering has platform-level chrome we don't control. Close enough is close enough.
- Cross-OEM lockscreen behaviour (Samsung vs Pixel vs OnePlus) — we test on Graham's device. OEM variation is accepted as expected.

## Risks & open questions

- **Font loading** — if Geist fonts fail to load from assets (file missing, corrupt), the renderer must fall back gracefully to `Typeface.SANS_SERIF` bold. Covered in the renderer's try/catch.
- **Bitmap memory** — 144×144 ARGB_8888 bitmaps are ~83 kb each; LRU of 16 entries = ~1.3 MB peak. Acceptable for a foreground service.
- **`MediaSessionCompat` deprecation** — Android is pushing apps toward `MediaSession` (media3). For now `MediaSessionCompat` via `androidx.media:media` is still the supported path for notification-only usage. If we later migrate the app to media3, this notification code is the one thing that changes.
- **OEM lockscreen cropping** — some OEMs (Samsung) clip large icons more aggressively than stock Android. If the rim accent gets cropped, the gradient slide alone still carries the signal. Acceptable degradation.
- **Pause action latency** — the pause PendingIntent fires through `startService()` + `onStartCommand()`. Typical latency < 200 ms; acceptable for a UI control.

## Visual verification

After implementation, the assistant will:
1. Build the debug APK
2. Install it on the emulator or connected device
3. Start a simulated bootcamp workout via `SimulationController` (no real BLE/GPS needed)
4. Lock the device / open the notification shade
5. Take a screenshot via `adb shell screencap`
6. Compare against `approaches-v5.html` qualitatively
7. Verify pause/resume from the notification actually toggles workout state

If the visual does not match the mockup intent, iterate on `BadgeBitmapRenderer` parameters until it does.
