# Firebase RTDB

Load when touching `CloudBackupManager`, Firebase rules, partner/invite flows, App Distribution, or `/users/{uid}/backup` paths.

Project: `cardea-1c8fc`.

## CLI

- Read: `MSYS_NO_PATHCONV=1 firebase database:get /path --project cardea-1c8fc --pretty` (add `--shallow` for child keys only — useful to survey large nodes).
- Write: `firebase database:set -d "value" -f /path`.
- Delete: `firebase database:remove -f /path` — use `-f` (force), not `--confirm` (doesn't exist).
- Deploy rules: `firebase deploy --only database --project cardea-1c8fc` — no `.firebaserc`, always pass `--project`.

## Schema

- `/users/{uid}` — `displayName`, `emblemId`, `fcmToken`, `partners: {uid: true}`, `activity: {lastRunDate, lastRunDurationMin, lastRunPhase, weeklyRunCount, currentStreak}`.
- `/users/{uid}/backup` — cloud backup root. `backupComplete: true` written last (absence = partial or pre-fix). Subtrees: `profile/`, `settings/`, `adaptive/` (key `paceHrBuckets`, not `buckets`), `workouts/`, `trackPoints/`, `metrics/`, `bootcamp/enrollment`, `bootcamp/sessions/`, `achievements/`. Track point keys abbreviated: `id`, `ts`, `lat`, `lng`, `hr`, `dist`, `alt` (see `CloudBackupManager.TpKeys`).
- `/invites/{code}` — `userId`, `displayName`, `createdAt`, `expiresAt`.
- Partner connections bidirectional: both users must have each other in `partners`.

## Security rules — scope

Use `$wildcardVar !== $uid` (not `auth.uid`) when constraining by node identity. `auth.uid` breaks bidirectional writes where caller writes their own UID as a key under another user's node.

## App Distribution & versioning

- `./gradlew assembleDebug appDistributionUploadDebug` — builds + uploads debug APK. Testers via `testers` group in Firebase console (not hardcoded). `firebase login --reauth` needs interactive terminal.
- `versionCode` +1 per release, `versionName` semver (`0.x.0` pre-release). Both in `app/build.gradle.kts` `defaultConfig`. Release notes: `debug { firebaseAppDistribution { releaseNotes = "..." } }`.

## Coroutine trap (applies here heavily)

`runCatching { withTimeout {} }` and inner `catch (e: Exception)` inside `withTimeout` silently swallow `TimeoutCancellationException`. Before every `catch (e: Exception)` in Firebase coroutine code, add `catch (e: CancellationException) { throw e }`.
