# HR Coaching App — Design Document

**Date:** 2026-02-25
**Platform:** Android (Kotlin, Jetpack Compose)
**HR Monitor:** Coospo H808S (BLE + ANT+; app uses BLE)

## Purpose

A real-time heart rate coaching app that alerts the runner to speed up or slow down to stay within a target HR zone. Alerts are short tones that layer on top of music without pausing or ducking it.

## Architecture

Foreground Service + Jetpack Compose UI + SoundPool alerts.

```
┌─────────────────────────────────────┐
│            Compose UI               │
│  Setup | Active Workout | History   │
│         │              ▲            │
│         │    StateFlow │            │
└─────────┼──────────────┼────────────┘
          ▼              │
┌─────────────────────────────────────┐
│      WorkoutForegroundService       │
│  BleHrManager | GpsDistanceTracker  │
│  ZoneEngine   | AlertManager        │
└─────────────────────────────────────┘
          │
┌─────────────────────────────────────┐
│     Room Database (local only)      │
│  Workout | TrackPoint               │
└─────────────────────────────────────┘
```

### Core Components (inside ForegroundService)

- **BleHrManager** — Connects to HR monitor via BLE Heart Rate Service (UUID `0x180D`), subscribes to HR Measurement characteristic (`0x2A37`). Emits `Flow<Int>` at ~1Hz. Auto-reconnects on disconnect (5s retry). Remembers last device MAC.
- **GpsDistanceTracker** — Uses `FusedLocationProviderClient` to accumulate distance traveled in meters.
- **ZoneEngine** — Compares current HR against target zone for current distance segment. Determines in-zone / above / below status.
- **AlertManager** — Plays short tones via `SoundPool` when out of zone. Ascending tone = speed up, descending tone = slow down.

## Workout Modes

### Steady State
User sets a single target HR. Zone = target +/- buffer (default ±5 bpm, user-configurable).

### Distance Profile
User defines ordered segments, each with a distance and target HR:
- Example: "0-5km @ 140bpm, 5-7km @ 160bpm, 7-8km @ 180bpm"
- Each segment uses the same configurable buffer.
- App tracks cumulative distance via GPS to determine which segment is active.

## Alert System

1. ZoneEngine checks each HR update (~1/sec).
2. If HR is outside zone, a timer starts.
3. If HR returns to zone before the delay threshold (default 15s, configurable), no alert.
4. If still out of zone after delay, AlertManager plays a short tone (~0.5s):
   - **Too high:** descending tone (slow down)
   - **Too low:** ascending tone (speed up)
5. 30-second cooldown between alerts to prevent nagging.
6. Alerts repeat after cooldown if still out of zone.

### Why SoundPool (not AudioFocus)
- `SoundPool` plays on `STREAM_NOTIFICATION`, mixing with existing audio.
- Does NOT request AudioFocus — music is never paused, ducked, or interrupted.
- Tones are short OGG files bundled in `res/raw/`.

## UI Screens

### 1. Setup Screen
- Mode toggle: Steady State / Distance Profile
- Steady State: single HR input + buffer size
- Distance Profile: list of segments (distance + target HR), add/remove rows
- Alert delay setting (default 15s)
- "Connect HR Monitor" button (BLE scan → device list → select)
- "Start Workout" button

### 2. Active Workout Screen
- Large current HR display (center)
- Current zone target below it
- Color indicator: green (in zone), orange (slightly out), red (way out)
- Current distance traveled
- Persistent notification showing HR + zone status
- "Stop" button

### 3. History Screen
- List of past workouts (date, distance, duration)
- Tap to view detail: Google Map with GPS route as a polyline
- Polyline color = HR heatmap: green (~100bpm) → yellow (~150bpm) → red (~200bpm)
- Sampled at ~5-second intervals

## Data Model (Room)

### Workout
| Column | Type | Notes |
|--------|------|-------|
| id | Long (PK) | Auto-generated |
| startTime | Long | Epoch millis |
| endTime | Long | Epoch millis |
| totalDistanceMeters | Float | |
| mode | String | "STEADY_STATE" or "DISTANCE_PROFILE" |
| targetConfig | String | JSON of zone config |

### TrackPoint
| Column | Type | Notes |
|--------|------|-------|
| id | Long (PK) | Auto-generated |
| workoutId | Long (FK) | References Workout.id |
| timestamp | Long | Epoch millis |
| latitude | Double | |
| longitude | Double | |
| heartRate | Int | BPM |
| distanceMeters | Float | Cumulative |

~720 points per hour at 5s intervals.

## Dependencies

- **Jetpack Compose** — UI
- **Room** — Local database
- **Google Maps SDK for Android** — History route display
- **FusedLocationProviderClient** (Google Play Services) — GPS tracking
- **Android BLE APIs** — HR monitor connection
- **SoundPool** — Alert tones (built-in Android API)
- **Hilt** — Dependency injection

## Permissions

- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (GPS + BLE on older Android)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+)

## Out of Scope

- Cloud sync / accounts
- Export to Strava/GPX
- Map-based route planning
- ANT+ support (BLE only)
- iOS
