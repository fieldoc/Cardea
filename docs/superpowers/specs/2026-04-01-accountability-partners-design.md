# Accountability Partners Design

**Date:** 2026-04-01
**Status:** Approved

## Summary

Lightweight social feature for mutual encouragement between 1–3 running partners. Privacy-first: partners see only weekly run count, streak, and whether you ran today — no HR, pace, or distance. Backed by Firebase Realtime Database with FCM push notifications via Cloud Functions.

## Goals

1. Pair with 1–3 accountability partners via a 6-character invite code
2. See partner's weekly run count, current streak, and today's status
3. Receive push notification when a partner completes a run ("Sarah just finished a run — your turn?")
4. Show contextual nudge banner on HomeScreen when partner has recent activity
5. No leaderboards, no public sharing, no competition — just mutual encouragement

## Non-Goals

- Buddy Bootcamp (shared schedule merging, Pace Pal algorithm)
- QR code pairing (text-based invite code is simpler, QR can be added later)
- Photo-based avatars or rich profiles
- Chat or messaging between partners
- Sharing workout details (duration, phase shown in session summaries only — not HR, pace, distance)

## Architecture

### Firebase Realtime Database + Cloud Functions (Approach A)

- **Firebase RTDB** stores user activity data and partner relationships
- **Firebase Cloud Functions** trigger on activity updates to send FCM push notifications to partners
- **Firebase Auth (Anonymous)** — auto-sign-in on first launch. The Firebase UID becomes the canonical user identifier for RTDB paths. On first auth, `FirebaseAuthManager` writes the Firebase UID back to `UserProfileRepository` (replacing the local UUID) so all references stay consistent. No email/password, no login screen.
- **Blaze plan** (pay-as-you-go) required for Cloud Functions. At this scale (1–3 partners per user) the cost is effectively zero.

### Why RTDB over Firestore

RTDB is simpler for this use case: flat key-value data, real-time listeners, built-in offline persistence, lower latency for small reads. Firestore's querying power isn't needed since we only read known paths (`/users/{id}`).

## Data Model

### RTDB Structure

```
users/
  {userId}/
    displayName: "Sarah"
    emblemId: "pulse"
    fcmToken: "dK9x..."
    activity/
      currentStreak: 12
      weeklyRunCount: 3
      lastRunDate: "2026-04-01"
      lastRunDurationMin: 30
      lastRunPhase: "Base building"
    partners/
      {partnerUserId}: true

invites/
  {code}/
    userId: "abc-123-def"
    displayName: "Sarah"
    emblemId: "pulse"
    createdAt: 1775105615000
    expiresAt: 1775192015000
```

### Security Rules

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth.uid === $uid || root.child('users').child(auth.uid).child('partners').child($uid).exists()",
        ".write": "auth.uid === $uid",
        "partners": {
          ".validate": "newData.numChildren() <= 3",
          "$partnerId": {
            ".write": "auth.uid === $uid || auth.uid === $partnerId",
            ".validate": "$partnerId !== auth.uid"
          }
        }
      }
    },
    "invites": {
      "$code": {
        ".read": true,
        ".write": "!data.exists() || data.child('userId').val() === auth.uid"
      }
    }
  }
}
```

Key rules:
- You can only write to your own `/users/{uid}` node
- You can read a partner's node if they're in your `partners/` map
- Partner writes are bidirectional: either user can add/remove the link
- Invite codes are readable by anyone, writable only by creator (or if unclaimed)

### Local Data Classes

```kotlin
data class PartnerActivity(
    val userId: String,
    val displayName: String,
    val emblemId: String,
    val currentStreak: Int,
    val weeklyRunCount: Int,
    val lastRunDate: String?,       // ISO date "2026-04-01"
    val lastRunDurationMin: Int?,
    val lastRunPhase: String?,
)
```

### Activity Sync (Write Path)

After each workout completes, `WorkoutForegroundService` writes an activity summary to RTDB:
- `currentStreak` — computed from consecutive days with completed workouts (existing logic in `computeSessionStreak`)
- `weeklyRunCount` — count of workouts completed in the current ISO week
- `lastRunDate` — today's date
- `lastRunDurationMin` — workout duration in minutes
- `lastRunPhase` — bootcamp phase name (e.g., "Base building") or "Free run"

Also sync `displayName` and `emblemId` on profile save, and refresh `fcmToken` on each app launch.

### Activity Sync (Read Path)

RTDB `ValueEventListener` on each partner's `/users/{id}/activity` node. Firebase RTDB has built-in offline persistence — partner data renders from cache even without connectivity. Listeners are attached when the partner list loads and detached on ViewModel clear.

## Pairing Flow

### Step 1: Generate Invite Code

1. User taps "+ Add" in AccountScreen Partners section
2. Bottom sheet opens with two tabs: "Share my code" / "Enter a code"
3. "Share my code" (default tab):
   - App generates a 6-character alphanumeric code (uppercase letters + digits, excluding ambiguous chars: 0/O, 1/I/L)
   - Writes to `/invites/{code}` with `expiresAt` set to now + 24 hours
   - Displays code in large monospace gradient text with shimmer animation
   - "Share invite code" button triggers Android share sheet with text: "Join me on Cardea! Enter my partner code: X7K2M9"
   - "Expires in 24 hours" label below code

### Step 2: Enter Partner's Code

1. "Enter a code" tab:
   - Six individual character input boxes with gradient cascade effect as each is filled
   - Each box lights up in brand gradient sequence (red → pink → purple → blue → cyan)
   - "Connect" button disabled until all 6 characters entered
2. On submit: app reads `/invites/{code}`
   - If not found or expired → show error "Code not found or expired"
   - If found → show partner preview card with name + emblem

### Step 3: Mutual Connection

1. User taps "Connect" on the preview
2. App performs bidirectional write:
   - Adds partner's userId to own `/users/{myId}/partners/{partnerId}: true`
   - Adds own userId to partner's `/users/{partnerId}/partners/{myId}: true`
3. Deletes the consumed invite code
4. Success screen: celebration emoji with confetti particles, partner card with pulsing green dot, "What you'll see" privacy summary (weekly run count, streak, gentle nudge), "Done" button

### Disconnect

- Long-press partner row in AccountScreen → confirmation dialog → removes both sides of the partner link
- Alternatively: swipe-to-reveal delete action on the partner row

### Limits

- Maximum 3 partners enforced client-side (disable "+ Add" when at 3) and via RTDB security rules (`.validate`: `newData.parent().numChildren() <= 3` on the `partners/` node)
- Invite codes are single-use and expire after 24 hours
- A user cannot partner with themselves (validated client-side and in security rules)

## UI: AccountScreen Partners Section

### Layout (Compact List)

New section in AccountScreen, positioned below the profile hero card.

**Section header:**
- Left: 👥 icon + "Partners" title + "2 of 3" count in tertiary text
- Right: Gradient "+ Add" pill button (disabled at 3/3)

**Glass card** with partner rows, each containing:
- Gradient-ring emblem avatar (40dp circle)
- Partner name (600 weight, 14sp)
- Stats line: "3 runs · 12-day streak 🔥" (secondary text, 12sp)
- Right-aligned status: "Ran today" (green, pulsing dot) or "Yesterday" / "2 days ago" (tertiary text)
- Divider between rows (subtle, starting after avatar)

**Empty state** (no partners): glass card with centered text "Add a partner to stay motivated together" + gradient "+ Add Partner" button.

### Notifications Toggle

Below partners section, in a separate glass card:
- "Partner nudges" toggle (defaults on)
- Controls whether FCM notifications are shown for partner activity

## UI: HomeScreen Nudge Banner

### Layout

Positioned below the bootcamp hero card, above stat chips.

- Green accent left border (3px, `#4ADE80`)
- Green-tinted glass background (`rgba(74,222,128,0.06)` → `rgba(0,209,255,0.04)` gradient)
- Stacked partner avatars (32dp, overlapping by 8dp)
- Text: "**Sarah** just finished a run" (name in green) / "Mike ran yesterday · Both keeping it up!" (secondary)
- "Go →" gradient CTA text (navigates to workout setup)

### Visibility Rules

- **Show when:** At least one partner has a `lastRunDate` within the last 48 hours AND the user hasn't completed their own run today
- **Hide when:** User has no partners, OR no partner activity in 48 hours, OR user already ran today
- **Priority text:** Most recent partner activity shown first. If multiple partners ran today, show "Sarah and Mike both ran today"

### Interaction

- Tapping the banner navigates to workout setup screen
- Banner is not dismissible (disappears naturally when user runs or activity ages out)

## Push Notifications

### Cloud Function

```typescript
// Triggers on any write to /users/{userId}/activity/lastRunDate
exports.onPartnerRun = functions.database
  .ref('/users/{userId}/activity/lastRunDate')
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    const userData = (await admin.database().ref(`/users/${userId}`).once('value')).val();
    const partners = Object.keys(userData.partners || {});

    for (const partnerId of partners) {
      const partnerData = (await admin.database().ref(`/users/${partnerId}`).once('value')).val();
      if (!partnerData?.fcmToken) continue;

      await admin.messaging().send({
        token: partnerData.fcmToken,
        notification: {
          title: `${userData.displayName} just finished a run`,
          body: "Your turn? 💪",
        },
        android: {
          notification: {
            channelId: "partner_activity",
            icon: "ic_notification",
          },
        },
      });
    }
  });
```

### Android Notification Channel

- Channel ID: `partner_activity`
- Name: "Partner Activity"
- Importance: `IMPORTANCE_DEFAULT` (sound + heads-up, but not intrusive)
- Created on app startup

### FCM Token Management

- Token retrieved on app launch and after each Firebase Auth sign-in
- Written to `/users/{userId}/fcmToken`
- `FirebaseMessagingService` subclass handles token refresh → re-writes to RTDB

## Files

### New Files

| File | Purpose |
|------|---------|
| `data/firebase/FirebasePartnerRepository.kt` | RTDB read/write: activity sync, invite codes, partner CRUD, listeners |
| `data/firebase/FcmTokenManager.kt` | FCM token registration, refresh, RTDB sync |
| `data/firebase/FirebaseAuthManager.kt` | Anonymous auth sign-in, UID mapping |
| `domain/model/PartnerActivity.kt` | Data class for partner state |
| `ui/account/PartnerSection.kt` | Compact list composable, add partner bottom sheet, empty state |
| `ui/home/PartnerNudgeBanner.kt` | HomeScreen nudge banner composable |
| `service/PartnerActivityNotificationService.kt` | `FirebaseMessagingService` subclass |
| `functions/index.ts` | Cloud Function for FCM push on activity update |
| `functions/package.json` | Cloud Functions dependencies |
| `firebase.json` | Firebase project config |
| `database.rules.json` | RTDB security rules |

### Modified Files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add Firebase BOM, RTDB, Auth, Messaging dependencies |
| `AndroidManifest.xml` | FCM service declaration, notification channel |
| `ui/account/AccountScreen.kt` | Add `PartnerSection` below profile |
| `ui/account/AccountViewModel.kt` | Partner list state, add/remove/disconnect methods |
| `ui/home/HomeScreen.kt` | Add `PartnerNudgeBanner` below hero card |
| `ui/home/HomeViewModel.kt` | Partner nudge state from `FirebasePartnerRepository` |
| `service/WorkoutForegroundService.kt` | Sync activity to RTDB after workout completes |
| `di/AppModule.kt` | Provide `FirebasePartnerRepository`, `FcmTokenManager`, `FirebaseAuthManager` |

## Testing

- **Unit tests:** `FirebasePartnerRepository` (mocked RTDB), invite code generation (charset validation, uniqueness), `PartnerActivity` serialization, nudge banner visibility rules
- **Integration tests:** End-to-end pairing flow with Firebase emulator, activity sync triggers, disconnect cleanup
- **UI tests:** Partner section renders correctly with 0/1/2/3 partners, bottom sheet tab switching, code input cascade animation, nudge banner show/hide logic
