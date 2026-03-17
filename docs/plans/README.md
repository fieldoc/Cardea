# Planning Docs Map

Last updated: 2026-03-10

This folder contains a mix of active strategy docs, implementation plans, and historical build notes. To avoid multiple "source of truth" problems, use the documents below in this order.

## Current authoritative docs

1. `docs/plans/2026-03-10-product-direction.md`
   Current product direction, current app shape, training mode model, and future-state guidance.
2. `docs/plans/2026-03-04-adaptive-bootcamp-design.md`
   Strategic design for Bootcamp as the long-term primary training system.
3. `docs/plans/2026-03-10-deferred-ux-refinements.md`
   Shortlist of accepted UX follow-ups that remain open after the current UI/navigation restructuring.
4. `docs/plans/2026-02-25-hr-coaching-app-design.md`
   Baseline service/data architecture and original product constraints. UI/navigation sections are historical.

## Current implementation-reference docs

- `docs/plans/2026-03-09-rescheduling-design.md`
- `docs/plans/2026-03-09-rescheduling-plan.md`
- `docs/plans/2026-03-10-engine-wiring.md`
- `docs/plans/2026-03-06-bootcamp-settings.md`
- `docs/plans/2026-03-06-training-ux-improvements.md`

These are useful when touching the named area, but they are not the top-level product spec.

## Historical or largely completed docs

- `docs/plans/2026-02-25-hr-coaching-app-plan.md`
- `docs/plans/2026-03-02-cardea-ui-ux-plan.md`
- `docs/plans/2026-03-02-ui-unification-plan.md`
- `docs/plans/2026-03-02-workout-ui-impl-plan.md`
- `docs/plans/2026-03-03-guided-workouts-impl-plan.md`
- `docs/plans/2026-03-05-adaptive-bootcamp-plan.md`

Treat these as implementation history. Reuse details from them only if they still match the current product-direction doc and the codebase.

## Working rules

- Do not create another top-level product vision doc unless the current one is being replaced.
- If a plan is feature-scoped, mark it with its status near the top.
- If a newer doc supersedes an older one, add an explicit note in the older doc rather than silently letting both drift.
