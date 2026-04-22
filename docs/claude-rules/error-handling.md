# Error Handling & Crash Resilience

Load when writing coroutine code (especially Firebase), wrapping with `runCatching`/`withTimeout`, editing `stopWorkout`, or adding `collectAsState` to a composable.

Audit: `docs/superpowers/plans/2026-04-11-error-handling-audit.md`.

## Rules

- **`stopWorkout()` essential/best-effort split** — essential ops (save workout, stop GPS/BLE) in individual `runCatching`; best-effort (metrics, achievements) grouped. Both log.
- **Firebase typed exceptions** — never return `null` for multiple failure conditions. Throw subclasses (`ExpiredInviteException`, `PartnerLimitException`); catch individually. Collapsing to null = ambiguous errors.
- **`runCatching { withTimeout {} }` is unsafe** — `TimeoutCancellationException extends CancellationException`; `runCatching` swallows it, turning timeout into no-op. Use `try/catch` and rethrow: `} catch (e: CancellationException) { throw e }`. **Same trap for inner `catch (e: Exception)`** nested inside a `withTimeout` lambda. Add `catch (e: CancellationException) { throw e }` before every `catch (e: Exception)` in Firebase coroutine code.
- All `collectAsState()` → `collectAsStateWithLifecycle()`.

## Deferred (known gaps, not regressions)

Per-op BLE permission checks (8 `@SuppressLint`), mid-session permission revocation, Room migration tests, full state restoration (`START_REDELIVER_INTENT`).
