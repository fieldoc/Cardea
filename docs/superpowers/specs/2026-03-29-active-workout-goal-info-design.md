# Active Workout Goal Info Redesign

**Date:** 2026-03-29
**Status:** Approved

## Problem

During bootcamp runs (and runs generally), the user has no way to know:
- What the goal is (how long? how far? what HR target?)
- How far through the run they are
- When to turn around on an out-and-back route

The active workout screen shows HR, distance, pace, and guidance — but never answers "what am I supposed to be doing and for how long?" The progress strip code exists in the codebase but bootcamp sessions don't wire their goal data through, so it never appears.

## Design (Approved v3)

### Information Hierarchy

1. **Mission Card** (Tier 1) — single unified card at top, replaces the old header row
2. **HR Ring** (Hero) — unchanged, central visual anchor
3. **Stat Cards** (Tier 2) — Distance + Pace
4. **Guidance Card** — coaching text
5. **Tertiary Row** (Tier 3) — Avg HR + Auto-pause toggle

### Mission Card

A single glass card with a Cardea gradient left-edge accent (3px). Contains all goal context in one glanceable unit:

**Top row:**
- Left: session name (e.g. "Long Run") in 17px bold white, "Week 4" subtitle below in 11px tertiary
- Right: zone status badge pill (e.g. "BELOW ZONE") with glowing zone-color dot

**Center — the hero timer:**
- Elapsed time in 36px black-weight white (e.g. "19:02")
- Separator "/" in 22px at 15% white opacity
- Total goal in 22px at 28% white opacity (e.g. "45:00")
- Uses `tabular-nums` for stable digit widths

**Bottom row:**
- Left: target HR pill — heart icon + "Target 129 bpm" in zone-color, with zone-color background tint and border
- Right: "25:58 remaining" in 12px tertiary text

### Progress Bar with Turn Marker

Sits below the mission card. Thin (5px) gradient-filled bar with:
- Cardea 4-color gradient fill
- Halfway tick mark (2px wide, 20px tall, 20% white)
- "Turn 22:30" label below the tick (10px, 40% white)
- **Important:** Sufficient bottom margin (min 16px) between the turn label and the HR ring to prevent overlap

Applies to both time-based and distance-based runs. For time-based: marker at totalDuration/2. For distance-based: marker at totalDistance/2.

### Header Changes

The old header row (zone badge left + elapsed time right) is replaced entirely by the mission card. No separate header element needed.

### Stat Cards

Unchanged from current: Distance (left) + Pace (right) in the existing hero card style.

### What Gets Removed from Header

- Elapsed time (now in mission card)
- Target bpm annotation (now in mission card's target pill)
- Zone status text (now in mission card's badge)

### Adaptive Behavior by Workout Type

| Workout Type | Mission Card Shows | Progress Bar | Turn Marker |
|---|---|---|---|
| Bootcamp timed session | Session label + week + timer + target HR | Time-based fill | At halfway time |
| Steady-state (segments) | "Steady-state" + timer + target HR | Time-based fill | At halfway time |
| Distance profile | "Distance profile" + distance counter + target HR | Distance-based fill | At halfway distance |
| Free run (timed) | Session label + timer, no target HR | Time-based fill | At halfway time |
| Free run (open-ended) | Session label only, no timer/progress | Hidden | Hidden |

### Data Flow Fix

The progress strip already exists in the codebase but bootcamp sessions don't populate `WorkoutConfig.plannedDurationMinutes` or `sessionLabel` when creating the workout entity. This must be fixed so the config JSON stored in `WorkoutEntity.targetConfig` contains the bootcamp session's duration and label.

**Root cause:** When the foreground service starts a bootcamp session as a FREE_RUN, the `WorkoutConfig` is created with mode=FREE_RUN but `plannedDurationMinutes` and `sessionLabel` are not set from the `BootcampSessionEntity`. The `WorkoutProgressStrip` composable checks for these fields and hides itself when they're null.

**Fix:** When starting a workout from a bootcamp session, populate `plannedDurationMinutes` from `BootcampSessionEntity.targetMinutes` and `sessionLabel` from the session type/label. Also carry the bootcamp week number so the mission card can display it.

### New Fields Needed

`WorkoutConfig` additions:
- `bootcampWeekNumber: Int?` — for "Week N" display in the mission card

`ActiveWorkoutUiState` additions:
- `bootcampWeekNumber: Int?` — derived from config
- `remainingSeconds: Long?` — computed as totalDurationSeconds - elapsedSeconds

`WorkoutSnapshot` — no changes needed. Zone status and target HR already flow through.

## Out of Scope

- Route suggestions / map-based turn-around guidance
- Phase display (BUILD/BASE/etc.) on the active screen — user confirmed not needed mid-run
- Auto-pause toggle relocation — stays in tertiary row
- Segment timeline strip (interval workouts) — separate feature, already has its own card

## Mockup Reference

Visual companion mockup saved at `C:/tmp/brainstorm/workout-v3.html` (approved with overlap fix noted).
