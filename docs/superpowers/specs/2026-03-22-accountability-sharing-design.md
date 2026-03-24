# Accountability Sharing — Design Spec

**Date:** 2026-03-22
**Status:** Approved

## Overview

A mutual accountability feature where paired partners automatically see when the other completes a run. The core mechanic is visibility, not competition — "I see my partner ran today, I should do mine" — with no pace, time, or HR data shared.

## Principles

- **Accountability through visibility**, not competition. No leaderboards, no pace comparisons.
- **Celebrate showing up.** The notification says "they ran," not "they ran faster than you."
- **Forgive and shift.** Missed a scheduled day but made it up later? Show the effort, not the miss.
- **Privacy by default.** Only distance and route are shared. No time, pace, or HR data.
- **Minimal server.** The server is a dumb relay — all business logic lives on-device.

## Pairing

### Invite Code Flow

1. **Generate:** User taps "Invite Partner" in Account screen → `createInvite` Cloud Function returns a 6-character alphanumeric code (e.g. `K7X2M9`). Code expires after 24 hours.
2. **Share:** Android share sheet opens with text: "Join me on Cardea! Use code K7X2M9 or tap: https://cardea.app/pair/K7X2M9"
3. **Accept (deep link — primary):** Partner taps the link → app opens → auto-fills code → calls `acceptInvite` → both devices paired and notified via FCM.
4. **Accept (manual — fallback):** Partner taps "Join Partner" in Account screen → enters 6-char code. Input field detects clipboard content matching `[A-Z0-9]{6}` pattern and offers "Paste K7X2M9?" suggestion.
5. **Confirmation:** Both devices receive FCM: "You're now connected with Sarah!" Home screen immediately shows the partner hero card.

### Deep Link Implementation

Android App Links with a verified HTTPS domain (`cardea.app/pair/{code}`). A static Firebase Hosting page handles the redirect:
- If app installed: `intent://` URI opens the app with the code as an intent extra (`EXTRA_PAIR_CODE`)
- If not installed: redirects to Play Store listing

**Intent routing:** `MainActivity` receives the deep link intent. `onCreate`/`onNewIntent` extracts `EXTRA_PAIR_CODE` and passes it to the nav graph as a start destination argument. The nav graph routes to `account` screen with the code pre-filled in the "Join Partner" field. If the app is cold-starting, the splash screen completes first, then the code is processed.

### Clipboard Detection

When the "Join Partner" code input field gains focus, it reads the clipboard once. If the clipboard content matches `[A-Z0-9]{6}`, a chip appears below the field: "Paste K7X2M9?" Tapping the chip fills the field. The clipboard is only read on focus — not continuously. On Android 13+ this uses `ClipboardManager` which shows a system toast when clipboard is accessed, which is acceptable UX.

### Unpairing

"Disconnect Partner" in Account screen with confirmation dialog. Uses a Cloud Function (or client-side Firestore transaction) to atomically clear `partnerId` on both user documents. Historical `runCompletions/` documents are **retained** (they're anonymous snapshots — no PII beyond a display name). Hero card reverts to bootcamp shortcut (or bootcamp promo if not enrolled).

**Propagation:** The partner's device observes the `users/{uid}` document via a Firestore snapshot listener. When `partnerId` becomes null, the partner card is removed immediately — no FCM required for unpairing.

### Scope

One partner per user in v1. Data model uses a collection (not a single field) to support future expansion to multiple partners without migration.

## User Identity

Firebase Anonymous Auth provides a stable UID that persists across app sessions. On first launch, the app silently creates an anonymous Firebase Auth account — no sign-up form, no friction. The UID (not device ID) is the primary key for all Firestore documents.

**Account recovery:** If the user reinstalls the app or switches phones, the anonymous account is lost. To prevent this, the app prompts (non-blocking, deferrable) to link the anonymous account to a Google account after pairing. This makes the UID recoverable via Google Sign-In on any device. Linking is optional — the feature works without it, but partnerships are lost on reinstall.

**Why not just deviceId:** Device IDs change on reinstall, factory reset, or phone switch. Firebase Auth UID is stable and recoverable when linked to Google.

## Server Architecture

### Firebase Stack

All server-side components use Firebase services:
- **Firebase Auth** — anonymous auth with optional Google account linking
- **Firestore** — stores user records, pairings, and run completion snapshots
- **Cloud Functions** — three functions handle all server logic
- **FCM** — delivers push notifications to partner devices
- **Firebase Hosting** — serves the deep link redirect page

### Firestore Data Model

```
users/{uid}                        // Firebase Auth UID
  ├── fcmToken: string
  ├── displayName: string
  ├── avatarSymbol: string
  └── partnerId: string?           // → partner's uid

pairings/{inviteCode}
  ├── creatorId: string            // uid
  ├── createdAt: timestamp
  └── expiresAt: timestamp         // 24h TTL

runCompletions/{id}
  ├── userId: string               // uid
  ├── timestamp: long
  ├── distanceMeters: double
  ├── routePolyline: string        // encoded polyline
  ├── streakCount: int
  ├── programPhase: string?        // "Week 3 · BUILD"
  ├── sessionLabel: string?        // "Day 2 of 4"
  ├── wasScheduled: boolean
  ├── originalScheduledWeekDay: int?  // if this is a makeup run, the day it was originally scheduled (null if not a makeup)
  └── weekDay: int                 // 1=Mon .. 7=Sun (ISO 8601)
```

**Retention:** `runCompletions/` documents are retained for 90 days. A scheduled Cloud Function runs weekly to delete older documents. This bounds Firestore storage costs.

### Cloud Functions

1. **`createInvite`** — Generates a random 6-char base36 code (uppercase + digits). Checks `pairings/{code}` for collision; retries up to 3 times if exists. Stores with 24h expiry, returns code.
2. **`acceptInvite`** — Validates code exists and hasn't expired. **Uses a Firestore transaction** to atomically: (a) set `partnerId` on both user documents, (b) delete the pairing document. If the transaction fails (network error, concurrent write), the client retries. Both devices receive an FCM confirming the connection.
3. **`onRunCompleted`** — Triggered by new doc in `runCompletions/`. Looks up the user's partner via `users/{uid}.partnerId`, retrieves partner's FCM token, sends push notification.
4. **`cleanupOldCompletions`** (scheduled) — Runs every Sunday at 2 AM UTC. Queries `runCompletions/` where `timestamp < now() - 90 days` and batch-deletes. Uses a conservative bias: documents exactly 90 days old are retained. No locking needed — Firestore's eventual consistency model handles concurrent writes safely.

### FCM Token Lifecycle

The app writes the FCM token to `users/{uid}.fcmToken` in two situations:
1. **On every cold start** — during Firebase Auth initialization, after the UID is confirmed.
2. **On `onTokenRefresh` callback** — Firebase may rotate tokens at any time; the `FirebaseMessagingService.onNewToken()` override writes the new token to Firestore immediately.

This ensures the stored token is always current. If the user has not opened the app in months, their token may become stale, but this is acceptable — the fallback Firestore query on foreground resume ensures the partner card is still accurate even without push notifications.

### Offline Resilience

Firestore SDK queues writes locally when offline. When the run completion is written after a workout ends without connectivity, it syncs automatically when the device reconnects. FCM delivery is handled server-side after the doc lands in Firestore.

## Streak Definition

A streak counts consecutive days on which the user completed at least one run, evaluated against their schedule (or raw calendar days for free runners).

**Bootcamp runners:**
- A scheduled day that is completed increments the streak.
- A **deferred** day (missed but made up within the same week) does NOT break the streak — the make-up day counts.
- A missed day that is never made up breaks the streak (streak resets to 0).
- Rest days are invisible to the streak — they neither increment nor break it.
- Bonus (unscheduled) runs do NOT increment the streak (they're outside the program), but they don't break it either.

**Free runners:**
- Every day with at least one run increments the streak.
- A day with no run breaks the streak (streak resets to 0).
- There is no concept of rest days — every day counts.

**Timing:** A bootcamp day is considered missed if not completed or deferred before 11:59 PM local time on that day. The streak breaks at midnight when the missed day's deadline passes. Free runners: a day is "missed" if no run is logged before midnight local time.

**Computation:** Streaks are computed locally on-device from Room data (bootcamp session statuses or workout timestamps). The computed value is included in the `RunCompletionPayload` as a snapshot — the server never computes streaks.

**Partner-side rendering of deferred runs:** The `RunCompletionPayload` includes `originalScheduledWeekDay` (non-null for makeup runs). When the partner card renders the week strip, it uses this field to show the ↷ deferred marker on the originally scheduled day and the green checkmark on the actual run day. If `originalScheduledWeekDay` is null, the run is rendered on `weekDay` as a normal completion or bonus run.

## Run Completion Pipeline

When a workout ends in `WorkoutForegroundService`:

1. Service saves workout to Room (existing behavior, unchanged)
2. **New step:** Compose a `RunCompletionPayload` containing:
   - `distanceMeters` from the workout
   - `routePolyline` — encoded from track points
   - `streakCount` — computed locally from bootcamp session history
   - `programPhase` — current bootcamp phase label (null if free runner)
   - `sessionLabel` — e.g. "Day 2 of 4" (null if free runner)
   - `wasScheduled` — whether this run corresponded to a scheduled bootcamp session
   - `weekDay` — day of week (1-7)
3. Post payload to Firestore `runCompletions/` collection
4. Cloud Function triggers → sends FCM to partner
5. Partner device receives push → displays notification

## Push Notification

**Format:** Standard Android notification
- **Icon:** Cardea gradient heart
- **Title:** "Cardea"
- **Body:** "{DisplayName} just finished their run!"
- **Subtitle:** "Tap to see their route"
- **Tap action:** Opens partner detail screen

## Partner Detail Screen

Opened by tapping the push notification or tapping the partner hero card. A simple read-only screen showing:

- **Route map** — Google Maps with the run polyline rendered. No pace or time overlays.
- **Distance** — total distance only
- **Streak count** — current streak
- **Program phase & session label** — if partner is in a bootcamp

**Free runner variant:** Same screen, same layout. The program phase and session label rows are simply absent. The screen shows: route map, distance, streak count. No empty placeholders — the layout collapses cleanly.

This is NOT a full workout detail view — no HR chart, no splits, no zone analysis.

### FCM Delivery & Fallback

FCM is best-effort — delivery is not guaranteed if the partner's device is offline or has restricted background activity. As a fallback, the app queries `runCompletions/` on every foreground resume (filtered to partner's uid, last 7 days) to hydrate the partner card. This means the partner card is always accurate even if a push notification was missed — FCM is just the alert mechanism, not the data source.

## Home Screen — Partner Hero Card

### Progressive Hero Card States

The home screen hero card evolves as user engagement deepens:

| State | Condition | Hero Card |
|-------|-----------|-----------|
| 1 | No bootcamp enrollment | "Start your running journey" CTA → Workout tab |
| 2 | Enrolled in bootcamp, no partner | Bootcamp session shortcut (today's session) |
| 3 | Enrolled + paired with partner | Partner card |
| 4 | Paired but no bootcamp | Partner card (no phase label for self) |

**Rule:** Partner card always wins once paired. Bootcamp session is accessible via the Workout tab.

### Card Visual Design

Glass card with glowing progress track (dark glass-morphic, matching Cardea design system).

**Header row:**
- Gradient-bordered rounded square avatar (reuses existing `AVATAR_SYMBOLS`)
- Partner display name
- Status subtitle: "Ran today · 3.2 km" / "Run scheduled today" / "Rest day"
- Gradient streak badge (gradient background glow, large number, "STREAK" label)

**Week strip — connected progress track:**
Seven day nodes connected by a horizontal track line. Completed portion of the track line is green.

| Day State | Visual |
|-----------|--------|
| Scheduled + completed | Green glow circle with dark checkmark SVG, `box-shadow` glow |
| Deferred (forgive & shift) | Dashed border circle with subtle ↷ arrow |
| Bonus (unscheduled) run | Cyan border circle with ★ star, blue glow |
| Scheduled today (pending) | Yellow border circle with pulsing yellow dot |
| Rest / no run | Empty circle, dim border |
| Today (current) | Slightly larger node, brighter day label |

**Footer:** Program phase label centered below the track (e.g. "Week 3 · BUILD"). Omitted for free runners.

### Week Strip Semantics

- **Forgive and shift (Option B):** If a scheduled Tuesday run is missed but a Wednesday make-up run happens, Tuesday shows the deferred marker (↷) and Wednesday shows the green completion. No red "missed" indicators.
- **Free runners:** No schedule overlay. Days they ran show the completion marker. Days they didn't are empty. Same visual treatment, just fewer states.
- **Bonus runs:** Unscheduled runs (e.g. spontaneous Saturday run outside the bootcamp program) use the cyan star marker — distinct from scheduled completions but still celebratory.

### Card Status Subtitle

| Partner State | Subtitle Text | Color |
|---------------|---------------|-------|
| Ran today | "Ran today · {distance} km" | Green |
| Scheduled but hasn't run yet | "Run scheduled today" | Yellow |
| Rest day | "Rest day" | Dim/muted |
| Free runner, ran today | "Ran today · {distance} km" | Green |
| Free runner, hasn't run | No specific label | Dim/muted |

## Account Screen Changes

New `GlassCard` section in Account screen titled **"Accountability Partner"**, positioned below the profile card and above the settings sections.

**Unpaired state:**
- Two buttons side by side: "Invite Partner" (primary, gradient) and "Join Partner" (secondary, outline)
- "Invite Partner" → calls `createInvite`, shows code in a dialog with a "Share" button that opens the Android share sheet
- "Join Partner" → expands to show a 6-character code input field with clipboard detection chip

**Paired state:**
- Shows partner avatar + display name + streak in a compact row
- "Disconnect Partner" text button (destructive style, muted red) with confirmation dialog

## Dependencies

### New Firebase Libraries (added to existing Firebase App Distribution setup)

- `firebase-auth` — anonymous auth + optional Google account linking
- `firebase-messaging` — FCM for push notifications
- `firebase-firestore` — Firestore client SDK
- `firebase-functions` — Cloud Functions client SDK (for calling createInvite/acceptInvite)

### Firebase Project Setup Required

- Enable Firestore in Firebase Console
- Deploy 3 Cloud Functions
- Set up Firebase Hosting for the deep link redirect page
- Configure Android App Links verification for `cardea.app` domain
- Register FCM with the existing Firebase project

### New Android Permissions

- `POST_NOTIFICATIONS` (Android 13+) — for FCM push notifications (runtime permission request needed)

**Notification permission graceful degradation:** If the user denies `POST_NOTIFICATIONS`, pairing still succeeds and run completions are still posted to Firestore. Push notifications fail silently on Android 13+. The partner card still works because it hydrates from Firestore on foreground resume (not from FCM). The app requests the permission once when the user first pairs. If denied, a subtle banner on the Account screen's partner section offers "Enable Notifications" linking to the system app settings.

## Out of Scope (v1)

- Multiple partners
- Pace/time/HR sharing ("brag" feature — explicitly deferred)
- In-app user search/discovery
- Chat or messaging between partners
- Reacting to or commenting on partner's runs
- Sharing with non-Cardea-users
