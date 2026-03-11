# Home Tab Redesign — Design Spec

## Goal

Redesign the Home tab to establish clear visual hierarchy: one dominant hero card for the next Bootcamp session with compact stat chips below, replacing the current three equal-weight cards.

---

## Decisions Made

| Topic | Decision |
|-------|----------|
| Primary purpose | Launch pad — "start my next session" is the hero action |
| Hero card style | Ambient Banner — radial glow, Start Session + Details buttons |
| Stat chips (with Bootcamp) | Weekly Goal (X/Y), Last Run km, No-Misses Streak |
| Stat chips (no Bootcamp) | Sessions This Week, Last Run km, Last Run duration |
| Header | Remove "CARDEA" text label; keep greeting + Bluetooth icon + logo avatar |
| No-Bootcamp state | Dashed card with "Set Up Bootcamp" CTA; chips show current-week stats |

---

## Layout

### With Active Bootcamp

```
┌─────────────────────────────┐
│  Good morning         🔵 ♥  │  ← greeting + BT icon + logo avatar
│                             │
│  ┌─────────────────────────┐│  ← Hero: Ambient Banner
│  │ TODAY'S SESSION · WEEK 3 ││
│  │ Zone 2 Easy Run          ││
│  │ 30–40 min · Aerobic base ││
│  │ [Z2 AEROBIC]             ││
│  │ [Start Session] [Details]││
│  └─────────────────────────┘│
│                             │
│  ┌───────┐┌───────┐┌───────┐│  ← Stat chips
│  │  3/4  ││ 5.2km ││  6    ││
│  │ GOAL  ││ LAST  ││NO MISS││
│  └───────┘└───────┘└───────┘│
└─────────────────────────────┘
```

### Without Active Bootcamp

```
┌─────────────────────────────┐
│  Good morning         🔵 ♥  │
│                             │
│  ┌ - - - - - - - - - - - ┐ │  ← Dashed card
│  │ STRUCTURED TRAINING     │ │
│  │ Start Bootcamp          │ │
│  │ Adaptive program...     │ │
│  │ [Set Up Bootcamp]       │ │
│  └ - - - - - - - - - - - ┘ │
│                             │
│  ┌───────┐┌───────┐┌───────┐│  ← Stat chips (current-week data)
│  │   3   ││ 5.2km ││ 38min ││
│  │ THIS  ││ LAST  ││ LAST  ││
│  │ WEEK  ││  RUN  ││  RUN  ││
│  └───────┘└───────┘└───────┘│
└─────────────────────────────┘
```

---

## Hero Card — Ambient Banner Style

- Background: `rgba(255,45,166,0.20) → rgba(0,229,255,0.08)` linear gradient at 135°
- Border: `1px solid rgba(255,255,255,0.15)`
- Corner radius: `20.dp`
- Radial glow: `rgba(255,45,166,0.25)` circle, 120×120dp, positioned top-right behind card content (`-30dp` offset)
- Eyebrow: `"TODAY'S SESSION · WEEK {n}"` — 9sp, letter-spacing 2, weight 700, opacity 0.5
- Title: session name — 22sp, weight 900
- Subtitle: `"{min}–{max} min · {description}"` — 12sp, opacity 0.5
- Zone pill: gradient background chip, 10sp weight 700
- Actions row: `[Start Session]` (full gradient button) + `[Details]` (outline button)

**Next session query:** Use a new DAO method that includes both `SCHEDULED` and `DEFERRED` sessions: `WHERE status IN ('SCHEDULED', 'DEFERRED') ORDER BY weekNumber, dayOfWeek LIMIT 1`. The existing `getNextScheduledSession` only returns `SCHEDULED` and would return null when today's session has been deferred.

**No-session fallback** (enrollment active but no upcoming session found): show `"Rest Day"` as title with no action buttons. This state is out of scope for this redesign — use existing Bootcamp card as-is until Bootcamp dashboard is redesigned.

**Paused enrollment:** When `enrollment.status == PAUSED`, treat as no active Bootcamp — show the dashed "Start Bootcamp" card and current-week stat chips (Option B). Do not show the Ambient Banner hero for paused enrollments.

**Graduated enrollment:** When `enrollment.status == GRADUATED`, also treat as no active Bootcamp — show the dashed "Start Bootcamp" card. `getActiveEnrollment()` already excludes graduated enrollments (it queries `status IN ('ACTIVE', 'PAUSED')`), so no special handling is needed in the ViewModel — the graduated case is indistinguishable from no enrollment at all.

---

## Stat Chips

Three equal-width chips in a horizontal row, each:
- Background: `rgba(255,255,255,0.04)`
- Border: `1px solid rgba(255,255,255,0.07)`
- Corner radius: `14.dp`
- Value: 20sp, weight 900
- Label: 9sp, opacity 0.4, letter-spacing 1.5, uppercase

### With Active Bootcamp

| Position | Value | Label | Source |
|----------|-------|-------|--------|
| Left | `{workoutsThisWeek}/{weeklyTarget}` | WEEKLY GOAL | `HomeUiState.workoutsThisWeek` + `weeklyTarget` |
| Center | `{lastKm} km` | LAST RUN | `HomeUiState.lastWorkout.totalDistanceMeters / 1000f` |
| Right | `{sessionStreak}` | NO MISSES | `HomeUiState.sessionStreak` (new field — see below) |

### Without Active Bootcamp

| Position | Value | Label | Source |
|----------|-------|-------|--------|
| Left | `{workoutsThisWeek}` | THIS WEEK | `HomeUiState.workoutsThisWeek` |
| Center | `{lastKm} km` | LAST RUN | `HomeUiState.lastWorkout.totalDistanceMeters / 1000f` |
| Right | `{lastDuration} min` | LAST RUN | `HomeUiState.lastWorkout` end–start time, floored to whole minutes; show `< 1` if under 60 s; always use `min` unit |

Empty state (no runs yet): chips show `—` at reduced opacity (0.35).

---

## No-Misses Streak

Definition: consecutive **planned** Bootcamp sessions completed without skipping one. Rest days do not count against it. Only explicitly skipping a planned session, or letting a past-due session go un-actioned, resets the streak.

**There is no `MISSED` status** in `BootcampSessionEntity`. The four valid statuses are: `SCHEDULED`, `COMPLETED`, `SKIPPED`, `DEFERRED`. A session that was never actioned remains `SCHEDULED` indefinitely — the streak logic must detect "effectively missed" sessions by computing whether a SCHEDULED session's calendar date is in the past.

**Calendar date computation:** `enrollment.startDate` is the epoch-ms of the first calendar day of week 1 of the program (the Monday that opens week 1, stored as midnight local time — identical to how `BootcampNotificationManager.reminderTimeForSession` uses it). Convert it to `LocalDate` and call `.plusDays(((weekNumber - 1L) * 7L) + (dayOfWeek - 1L))` to get the session's nominal `LocalDate`; compare to `LocalDate.now()` to determine if it is in the past.

**Logic (walk backward through all sessions, ordered by weekNumber DESC, dayOfWeek DESC):**
1. If `status == COMPLETED` → streak continues, increment count
2. If `status == SKIPPED` → streak broken, stop
3. If `status == SCHEDULED` and computed date < today midnight → streak broken (effectively missed), stop
4. If `status == SCHEDULED` and computed date >= today midnight → future session, skip (ignore)
5. If `status == DEFERRED` → skip (in-progress rescheduling, does not break streak)

**New ViewModel field:** `HomeUiState.sessionStreak: Int` — computed by `HomeViewModel` from `BootcampRepository.getSessionsForEnrollment(enrollmentId)` (all sessions for the enrollment, not just one).

---

## What Is Removed

| Removed | Replaced by |
|---------|-------------|
| "CARDEA" text label in header | Already removed (branding redesign) |
| Weekly Activity GlassCard (EfficiencyRing + session count) | Stat chips |
| Last Run GlassCard (date / distance / duration) | Stat chips |
| Old Bootcamp GlassCard (generic description + "Jump back in") | Ambient Banner hero |

---

## New Data Required

| Field | Type | Source |
|-------|------|--------|
| `HomeUiState.nextSession` | `BootcampSessionEntity?` | `BootcampRepository` — next scheduled session for active enrollment |
| `HomeUiState.sessionStreak` | `Int` | `HomeViewModel` — computed from completed session history |
| `HomeUiState.weeklyTarget` | `Int` | `enrollment.runsPerWeek` from `BootcampEnrollmentEntity` |

---

## Out of Scope

- Bottom nav bar changes
- Bootcamp tab redesign (separate spec)
- Quick-start free run button (no design decision made)
- "Details" navigation destination (routes to existing Bootcamp dashboard)
