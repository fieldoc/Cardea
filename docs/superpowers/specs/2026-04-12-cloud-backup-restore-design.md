# Cloud Backup & Restore — Design Spec

**Date:** 2026-04-12
**Status:** Draft
**Scope:** Google Sign-In account linking + full data backup/restore via Firebase RTDB

## Problem

Profile data (name, emblem), partner connections, bootcamp progress, adaptive training model, workout history, and achievements are stored locally. Losing or replacing a phone means losing everything. Partner connections already live in Firebase RTDB keyed by anonymous UID, but if the anonymous account is lost, so are the partner links.

## Solution

Allow users to optionally link a Google account to their anonymous Firebase UID. Once linked, all essential data syncs incrementally to RTDB. On a new device, signing in with Google restores the same UID and triggers a one-time data restore.

## Design Decisions

### Include GPS routes

TrackPointEntity (GPS route data) is 96% of all data by volume (~1.4 MB per user over 6 months). Firebase RTDB free tier is 1 GB. At 5 users, a full year of routes is ~14 MB — well under the limit. Routes are included in backup.

### Anonymous-first, link optional

The app continues to work fully without Google Sign-In. Linking is optional but surfaced prominently. Users who never link lose data on phone change (same as today).

### RTDB wins on restore

Cloud is the source of truth during restore. Local-only data on a new device (empty stores) is overwritten. No merge logic — restore only happens when local stores are empty or user explicitly triggers it.

### No real-time multi-device sync

This is backup/restore, not live sync. Changes write to RTDB incrementally, but there's no conflict resolution for concurrent edits from multiple devices.

## Identity Flow

### Linking (existing user)

1. User taps "Link Google Account" in Account screen
2. Firebase `GoogleAuthProvider` credential created via Google Sign-In
3. `auth.currentUser.linkWithCredential(credential)` — attaches Google identity to existing anonymous UID
4. UID does not change — all partner connections remain intact
5. Full backup triggered immediately after successful link

### Restore (new device)

1. User installs app on new device
2. During onboarding (or Account screen), taps "Sign in with Google"
3. Firebase returns the linked UID
4. App detects local stores are empty (no workouts in Room, onboarding not completed)
5. One-time restore pulls all data from `/users/{uid}/backup/` into Room + SharedPrefs
6. Onboarding is skipped (marked complete from restored flag)

### Unlinking

Not implemented in v1. User can sign out, which reverts to anonymous-only behavior. Data remains in RTDB but won't sync further until re-linked.

## RTDB Structure

Extends the existing `/users/{uid}/` node. All backup data lives under a `/backup/` subtree to keep it separate from the existing partner/activity sync.

```
/users/{uid}/
  ├── displayName              (existing — partner-visible)
  ├── emblemId                 (existing — partner-visible)
  ├── fcmToken                 (existing)
  ├── partners/                (existing)
  ├── activity/                (existing)
  │
  └── backup/
      ├── version: 1                          (schema version for future migration)
      ├── lastSyncMs: 1712937600000           (epoch ms of last write)
      │
      ├── profile/
      │   ├── maxHr: 185
      │   ├── age: 32
      │   ├── weight: 175
      │   ├── weightUnit: "lbs"
      │   ├── distanceUnit: "km"
      │   ├── partnerNudgesEnabled: true
      │   └── onboardingCompleted: true
      │
      ├── settings/
      │   ├── earconVolume: 80
      │   ├── voiceVolume: 80
      │   ├── voiceVerbosity: "MINIMAL"
      │   ├── enableVibration: true
      │   ├── enableHalfwayReminder: true
      │   ├── enableKmSplits: true
      │   ├── enableWorkoutComplete: true
      │   ├── enableInZoneConfirm: true
      │   ├── autoPauseEnabled: true
      │   └── themeMode: "DARK"
      │
      ├── adaptive/
      │   ├── longTermHrTrimBpm: 2.5
      │   ├── responseLagSec: 35.0
      │   ├── totalSessions: 42
      │   ├── ctl: 55.0
      │   ├── atl: 30.0
      │   ├── hrMax: 185
      │   ├── hrMaxIsCalibrated: true
      │   ├── hrRest: 52
      │   ├── lastTuningDirection: "HOLD"
      │   └── buckets/
      │       ├── "360": {avgHr: 145, sampleCount: 12}
      │       └── "420": {avgHr: 155, sampleCount: 8}
      │
      ├── bootcamp/
      │   ├── enrollment/
      │   │   ├── id: 1
      │   │   ├── goalType: "CARDIO_HEALTH"
      │   │   ├── targetMinutesPerRun: 30
      │   │   ├── runsPerWeek: 3
      │   │   ├── preferredDays: "1,3,5"
      │   │   ├── startDate: "2026-01-15"
      │   │   ├── currentPhaseIndex: 1
      │   │   ├── currentWeekInPhase: 3
      │   │   ├── status: "ACTIVE"
      │   │   ├── tierIndex: 2
      │   │   ├── targetFinishingTimeMinutes: null
      │   │   ├── tierPromptSnoozedUntilMs: null
      │   │   ├── tierPromptDismissCount: 0
      │   │   ├── illnessPromptSnoozedUntilMs: null
      │   │   └── pausedAtMs: null
      │   │
      │   └── sessions/
      │       ├── "1": {enrollmentId: 1, weekNumber: 1, dayOfWeek: 1, sessionType: "EASY", targetMinutes: 30, presetId: null, status: "COMPLETED", completedWorkoutId: 1, presetIndex: null, completedAtMs: 1705363200000}
      │       ├── "2": { ... }
      │       └── "N": { ... }
      │
      ├── workouts/
      │   ├── "1": {startTime: 1705363200000, endTime: 1705365000000, totalDistanceMeters: 5200.0, mode: "STEADY_STATE", targetConfig: "{...json...}"}
      │   ├── "2": { ... }
      │   └── "N": { ... }
      │
      ├── trackPoints/
      │   ├── "1"/                              (grouped by workoutId)
      │   │   ├── "0": {ts: 1705363205000, lat: 51.5074, lng: -0.1278, hr: 120, dist: 0.0, alt: 15.0}
      │   │   ├── "1": {ts: 1705363210000, lat: 51.5075, lng: -0.1277, hr: 125, dist: 12.5, alt: 15.2}
      │   │   └── ...
      │   └── "2"/ { ... }
      │
      ├── metrics/
      │   ├── "1": {recordedAtMs: ..., avgPaceMinPerKm: 6.2, avgHr: 148, ef: 1.05, trimp: 85.0, ...}
      │   └── "N": { ... }
      │
      └── achievements/
          ├── "1": {type: "STREAK", milestone: 7, goal: null, tier: 1, prestigeLevel: 0, earnedAtMs: 1705363200000, triggerWorkoutId: 7, shown: true}
          └── "N": { ... }
```

### Key naming notes

- Track points grouped by workoutId under `trackPoints/{workoutId}/{index}` for efficient per-workout read/write
- Workout/session/achievement IDs are stringified Room auto-increment IDs
- `targetConfig` stored as raw JSON string (same as Room)
- Bucket keys in `adaptive/buckets/` are stringified pace (seconds per km)

## Sync Strategy

### Incremental writes (app → RTDB)

Each write is scoped to the smallest changed subtree:

| Trigger | RTDB path written | When |
|---------|-------------------|------|
| Profile save (name, emblem, maxHr, etc.) | `backup/profile/` | On AccountViewModel.saveProfile(), saveMaxHr(), setAge(), etc. |
| Settings change | `backup/settings/{key}` | On each setting toggle/save |
| Adaptive profile update | `backup/adaptive/` | On workout complete (AdaptivePaceController save) |
| Bootcamp enrollment create/update | `backup/bootcamp/enrollment/` | On enroll, phase advance, tier change |
| Bootcamp session update | `backup/bootcamp/sessions/{id}` | On session status change |
| Workout saved | `backup/workouts/{id}` + `backup/trackPoints/{id}/` + `backup/metrics/{id}` | On workout complete (in WorkoutForegroundService.stopWorkout()) |
| Achievement earned | `backup/achievements/{id}` | On achievement insert |

All writes are fire-and-forget with 10s timeout, wrapped in `runCatching`. Backup failure must never crash the app or block the user.

### Guard: only sync when Google-linked

A helper `CloudBackupManager.isBackupEnabled()` checks `auth.currentUser?.providerData` for `GoogleAuthProvider.PROVIDER_ID`. Anonymous-only users skip all backup writes.

### One-time restore (RTDB → app)

Triggered when:
1. User signs in with Google on a device where Room DB has zero workouts AND onboarding is not marked complete
2. OR user explicitly taps "Restore from cloud" in Account screen

Restore sequence:
1. Read entire `/users/{uid}/backup/` in one call
2. Populate SharedPrefs (profile, settings, adaptive)
3. Insert Room entities (enrollment → sessions → workouts → trackPoints → metrics → achievements)
4. Mark onboarding complete
5. Show brief "Restored N workouts, N sessions" confirmation

Room inserts use explicit IDs (not auto-generate) to preserve FK relationships.

## UI Changes

### Account Screen

New section above the existing partner section:

```
┌─────────────────────────────────────┐
│  ☁  Cloud Backup                    │
│                                     │
│  [Link Google Account]     (if anonymous)
│  ── or ──                           │
│  ✓ Backed up · you@gmail.com        │
│    Last sync: 2 min ago             │
│    [Restore from cloud]  [Unlink]   │
└─────────────────────────────────────┘
```

- **Anonymous state:** "Link Google Account" button (Google-branded per their guidelines)
- **Linked state:** Shows email, last sync time, Restore button (for manual re-restore), Unlink option
- **Syncing indicator:** Brief "Syncing..." text when a backup write is in progress (optional, low priority)

### Onboarding

Add optional non-blocking step after existing onboarding:
- "Sign in with Google to back up your training data"
- "Skip" and "Sign in with Google" buttons
- Skipping continues to anonymous mode (current behavior)

### Restore Progress

When restore is active, show a simple full-screen overlay:
- CardeaLogo
- "Restoring your training data..."
- Progress text: "Workouts... Sessions... Done!"

## Security Rules

Extend existing RTDB rules so users can only read/write their own backup:

```json
{
  "users": {
    "$uid": {
      "backup": {
        ".read": "auth.uid === $uid",
        ".write": "auth.uid === $uid"
      }
    }
  }
}
```

## Error Handling

- **Link fails (account already linked to different anonymous UID):** Show error "This Google account is already linked to another profile. Use a different Google account." This happens if user created two anonymous accounts and tries to link both to the same Google account.
- **Backup write fails:** Log warning, retry on next trigger. Never block UI.
- **Restore read fails:** Show error with retry button. Don't partially apply data.
- **Restore on non-empty device:** Warn user "This will replace your local data. Continue?" Only when explicitly triggered via Account screen button (auto-restore only fires on empty device).

## Testing

- Unit tests: `CloudBackupManager` serialization/deserialization round-trip
- Unit tests: Restore populates Room entities with correct FK relationships
- Manual test: Link account → verify RTDB data → uninstall → reinstall → sign in → verify restore
- Manual test: Complete workout → verify incremental sync to RTDB

## Not in Scope

- Multi-device live sync / conflict resolution
- Cloud Functions for notifications
- Account deletion / GDPR data export
- Backup encryption (RTDB security rules are sufficient for this scale)
- Offline queue for backup writes (Firebase SDK handles temporary offline automatically)
