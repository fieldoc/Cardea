# Cardea Product Direction

Date: 2026-03-10
Status: Current top-level product direction

This document is the primary planning reference for the app's overall direction. It replaces the need to infer strategy from scattered implementation plans.

## Product thesis

Cardea is a heart-rate-led running coach. The app should feel like a calm, credible training companion that can support both:

- simple self-directed runs
- structured guided workouts
- longer-lived adaptive Bootcamp programs

The product should keep the same core promise across all three: translate heart-rate data into training decisions that are understandable and actionable during and after the run.

## Current app shape

### Navigation

The app currently uses four bottom-nav tabs:

- Home
- Training
- Activity
- Profile

Behavior:

- Training routes to `bootcamp` when the user has an active Bootcamp enrollment, otherwise to `setup`
- Activity is a shared shell for both history/log and trends/progress
- active workout, workout detail, and post-run summary live outside the bottom-nav shell

### Training modes

There are three workout configuration modes in the app:

- `STEADY_STATE`
- `DISTANCE_PROFILE`
- `FREE_RUN`

And there are two product-level training surfaces:

- Setup for one-off or manually configured runs
- Bootcamp for structured multi-week progression

Guided presets sit between those two layers: they are still launched from Setup, but they are more opinionated than raw manual configuration.

## Product hierarchy

The intended hierarchy going forward is:

1. Bootcamp is the primary structured-training experience.
2. Setup remains the flexible manual-training escape hatch.
3. Free Run remains the zero-friction mode for data capture and casual use.

That means the app should keep making Bootcamp more coherent and visible without making manual training impossible or hidden.

## Experience principles

### 1. Coaching over dashboarding

Metrics matter, but the product should interpret them whenever possible. Raw signals are acceptable in Activity. Home, Bootcamp, and post-run surfaces should bias toward coaching language, next actions, and confidence-building explanations.

### 2. Structured depth, simple entry

A new user should be able to:

- pair a sensor
- understand the difference between Bootcamp, Guided, Steady, and Free Run
- start a run quickly

without reading a wall of training jargon.

### 3. Bootcamp earns the center

Bootcamp should become the most compelling path because it is measurably smarter and more useful, not because the app artificially hides the alternatives.

### 4. Calm premium UI

Cardea's visual system stays dark, glassy, and restrained. Accent color is meaningful, not decorative. The UI should feel high-trust, not gamified.

## What the app should look like in the future

### Home

Home should answer three questions immediately:

- What is my current training state?
- What should I do next?
- Is anything important blocked, running, or changing?

Home is not just a stats landing page. It is the decision surface.

### Training

Training should clearly separate:

- Bootcamp
- guided preset sessions
- manual custom runs
- free runs

without turning the screen into a maze. Bootcamp remains the lead option when enrolled. Outside enrollment, Setup should still make the other three paths legible in plain language.

### Activity

Activity should remain the archive and analysis surface:

- Log/history for concrete past runs
- Trends/progress for pattern recognition

The current shared Activity tab plus in-screen toggle is the accepted architecture.

### Profile

Profile should own durable preferences and identity-like settings:

- sensor/device preferences
- coaching defaults
- account/about/settings

Session-specific overrides belong in the run setup flow only when they truly differ from defaults.

## Strategic bets

### 1. Adaptive Bootcamp is the flagship feature

The strongest long-term differentiation is not generic run tracking. It is the adaptive Bootcamp system that uses CTL, ATL, TRIMP, HR recovery, and related signals to tune training while keeping the user in control.

The authoritative design for that system is:

- `docs/plans/2026-03-04-adaptive-bootcamp-design.md`

### 2. Guided workouts remain important

Not every runner wants a full multi-week program. Guided presets are still important as:

- a bridge into structured training
- a manual override path
- a standalone value surface for users who do not want Bootcamp

### 3. Post-run insight quality matters

Cardea should increasingly turn completed sessions into useful explanations:

- how the run went
- what changed in the user's training state
- what to do next

This is a product differentiator, not just polish.

## Near-term priorities

The current high-value open work is:

1. Finish stabilizing adaptive-engine wiring and the metrics pipeline.
2. Continue reducing ambiguity between global defaults and per-run setup controls.
3. Improve Home so it reflects training state, sensor readiness, and active-session status more clearly.
4. Keep Bootcamp scheduling flexible enough for real life: preferred days, blackout days, rescheduling, and recovery logic.

Detailed follow-up items live in:

- `docs/plans/2026-03-10-deferred-ux-refinements.md`
- `docs/plans/2026-03-09-rescheduling-design.md`
- `docs/plans/2026-03-10-engine-wiring.md`

## Documentation rules for future updates

- Update this file when the app's top-level navigation, product hierarchy, or primary strategic bets change.
- Do not use feature implementation plans as a substitute for product direction.
- If another doc supersedes part of this one, add a note here and in that doc.
