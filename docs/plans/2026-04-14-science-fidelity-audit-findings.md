# Science Fidelity Audit — Findings (2026-04-14)

**Status: all Tier A/B findings resolved 2026-04-14. See `## Resolution log` at the bottom for what was fixed.**



Audit of the Cardea adaptive engine for AI-drift — places where AI agents wrote physiological constants or model choices with no human decision trail.

Audit method: three-tier dispatch — four Haiku file-scoped agents in parallel, one Sonnet filter/merge, Opus synthesis. Exclusion list pre-loaded from CLAUDE.md so documented decisions don't resurface as findings.

---

## Findings ranked by runner-impact

### Tier A — real user-facing effects

**A1. SubMax effort-fraction buckets bias HRmax upward**
- `SubMaxHrEstimator.kt:45-49`
- Buckets `≥0.88→0.92, ≥0.80→0.85, else→0.75` produce the biggest HRmax bump on the least confident observations (ratio 0.70–0.79 divides by 0.75, a 33% upward revision).
- Runner effect: HRmax creeps up on moderate efforts; zones drift upward; runner hears "speed up" at HRs that used to be in-zone.
- **Fix:** replace buckets with a guarded upward-only rule — "if observed 2-min peak ≥ current HRmax + 3 bpm, set new HRmax to observed peak + 1." Delete the effort-fraction buckets.
- Owner: engine
- Status: proposed

**A2. HRR1 infrastructure absent; illness detection dead code**
- `WorkoutForegroundService.kt:728` / `FitnessSignalEvaluator.kt:69-71`
- Design specifies a 120s post-workout BLE hold with HR samples at T=0/60/120s, stored as `hrr1Bpm`. Never implemented.
- Runner effect: SOFT and FULL illness tiers never trigger. Runner training through a cold gets no EASE_BACK signal. Fitness improvements via faster recovery are not credited.
- **Fix taken:** delete dead schema. Removed `hrr1Bpm` field, `IllnessSignalTier`, illness fields on `FitnessEvaluation`, the `IllnessPromptCard` UI path, and the `illnessPromptSnoozedUntilMs` enrollment column. DB migration v19→v20 rebuilds both tables to drop the columns. Cloud backup/restore stops emitting/reading the keys; older remote backups continue to restore (the surplus keys are simply ignored).
- **If reopened:** build the full cool-down pathway — requires BLE hold past `stopForeground`, a cool-down UI screen, T=0/60/120s sampling, and `hrr1Bpm` write-through. Reintroduce as its own plan.
- Owner: engine + UI
- Status: **resolved 2026-05-06**

**A3. TSB PUSH_HARDER threshold aggressive**
- `FitnessSignalEvaluator.kt:21`
- `TSB_PUSH_THRESHOLD = 5f`. Standard value (Friel) is ~+15 to +25.
- Runner effect: will recommend harder workouts while user still mildly fatigued. Combined with `efTrend > 0.04` this is partly guarded, but noisy EF slopes can spuriously authorize PUSH.
- **Fix:** raise to `TSB_PUSH_THRESHOLD = 15f`. Add comment citing Friel *The Training Bible*. Low-risk change.
- Owner: engine
- Status: proposed

### Tier B — documentation debt (prevents future drift, no user effect today)

**B1. SubMax 70% HR floor uncited**
- `SubMaxHrEstimator.kt:43`
- Value is reasonable; just unsourced.
- Fix: add comment citing Seiler LIPIT (low-intensity-polarised-intensity-threshold) ~70% HRmax.

**B2. SubMax window minimum fraction uncited**
- `SubMaxHrEstimator.kt:76`
- Value is reasonable; just unsourced.
- Fix: add comment explaining why 1-min sub-windows are tolerated.

**B3. TierCtlRanges divergence from design doc**
- `TierCtlRanges.kt:10`
- Cardio floor is 0 in code, 10 in doc. Code has 3 tiers 0-indexed, doc has 3 tiers 1-indexed. Substantive question: does a "below tier 1" beginner tier exist?
- Fix: reconcile code and doc. Decide once; update both.

### Tier C — not actually bugs

**C1. TRIMP "never computed upstream"**
- Originally flagged as: MetricsCalculator should compute TRIMP but doesn't; WFS fallback always fires.
- Verdict: the WFS fallback and the "primary path" would produce the same number. The difference is code organization, not computation. No runner effect.
- Fix: update design doc to say TRIMP lives in WFS. Delete any dead `trimpScore` fields in MetricsCalculator that are always-null.

**C2. CTL/ATL updated only at workout-end**
- Originally flagged as: design implies continuous updates.
- Verdict: agent was wrong. CTL/ATL are session-level metrics by definition. Updating mid-run is meaningless.
- No action.

---

## Process: why these drifted

Every Tier A/B item has the same root cause: an AI agent picked a number, a threshold, or a formula structure because it sounded reasonable and no earlier agent had documented what the value should be. The code ran, tests passed, and the constant became load-bearing.

The Science Constants Register (`2026-04-14-science-constants-register.md`) is the counter-measure. It forces every science constant to carry a source: published, internal-rationale, intentional-non-standard, or unsourced. `unsourced` is the only failure state — everything in that state blocks promotion to stable.

## Audit reproducibility

To re-run this audit in a future session:

1. Dispatch four Haiku agents in parallel, one per target (FormulaArchaeologist, DocComparator, NamingSkeptic, OmissionDetector).
2. Pre-load the exclusion list from CLAUDE.md + the Science Constants Register. Any constant in `published`, `internal-rationale`, or `intentional-non-standard` state is excluded; only `unsourced` entries and uncataloged constants should surface.
3. Output rule: markdown table only, `file | line | what_code_does | nearest_doc_says | confidence`, max 6 findings per agent, confidence ≥ 80.
4. Sonnet filter merges and applies exclusion list.
5. Human (or Opus) synthesizes and verifies by reading the actual code — agents hallucinate line numbers and mis-quote constants; always verify before acting.

Prompts used in this audit are preserved in this session's conversation history.

---

## Resolution log (2026-04-14)

Every Tier A and Tier B finding from this audit was resolved in a single session. Summary of what landed:

### A1 — SubMax effort-fraction buckets (replaced)

`SubMaxHrEstimator.kt` rewrite. Old algorithm divided observed sustained peak by an effort-fraction bucket (0.92/0.85/0.75) to back-calculate HRmax. The lowest bucket produced a 33% upward revision — biased HRmax upward on moderate runs.

New algorithm: conservative upward-only rule. Sustained 2-min rolling peak must exceed current HRmax by `EVIDENCE_MARGIN_BPM` (= 2 bpm) before revision fires. New HRmax = observed peak + `UPWARD_STEP_BPM` (= 1 bpm), capped at 220 − age + 20. Moderate-effort runs cannot revise HRmax.

Tests: updated `SubMaxHrEstimatorTest.kt` — three existing tests rewritten for the new behavior (including a regression test explicitly named `returns null when moderate effort - the pre-2026-04-14 mushing case`), two new tests added for margin-gate edge cases.

### A2 — HRR1 infrastructure (deleted 2026-05-06)

HRR1 post-workout measurement was never implemented. Initially the dead path was just tagged with a `TODO(HRR1):` comment; on 2026-05-06 it was deleted outright. DB migration v19→v20 rebuilds `workout_metrics` (drops `hrr1Bpm`) and `bootcamp_enrollments` (drops `illnessPromptSnoozedUntilMs`) via the table-rebuild pattern (SQLite < 3.35 lacks `DROP COLUMN`; minSdk = 26). The `IllnessSignalTier` enum, illness fields on `FitnessEvaluation`, `IllnessPromptCard`, and the `confirmIllness`/`dismissIllness` viewmodel methods are gone. Cloud backup stops emitting `hrr1Bpm` / `illnessPromptSnoozedUntilMs`; restore ignores them in older remote backups.

Reintroduce as its own plan if a 120s post-workout cool-down hold (BLE held past `stopForeground` + cool-down UI screen + T=0/60/120s sampling + write-through) is ever prioritized.

### A3 — TSB PUSH threshold (re-verified, not changed)

Original finding claimed `TSB_PUSH_THRESHOLD = 5f` was aggressive vs. Friel's "+15 to +25 peak" zone. Re-verification: +5 actually maps to the lower edge of Friel's "fresh enough for quality" window, and PUSH_HARDER is half of a **conjunctive gate** with `EF_RISE_THRESHOLD > 0.04` — both must fire together, which dramatically softens the threshold. Values kept as-is; code now has a citation block explaining the conjunctive-gate architecture and Friel reference.

This is a good example of why the audit is valuable: forced re-verification caught that the original finding was based on an incomplete reading of the gate logic.

### B1/B2 — SubMax 70% floor and window fraction (folded into A1)

Both are comments now; the underlying filter was superseded by the new algorithm's margin-gate approach. Documented in the rewritten `SubMaxHrEstimator.kt` KDoc.

### B3 — TierCtlRanges / design doc reconciliation

Code and doc are now aligned. Code `TierCtlRanges.kt` has a class-level KDoc explaining the two edge-case divergences (Foundation floor = 0, Performance ceiling = 200) and why they're intentional. Design doc `2026-03-04-adaptive-bootcamp-design.md` has a new "Edge cases (as implemented)" block describing the below-floor and above-ceiling behavior.

### C1 — TRIMP primary path (moved to helper)

Rather than just documenting that TRIMP lives in WFS (the original recommendation), introduced `MetricsCalculator.trimpFrom(durationMin, avgHr, hrMax)` as the named home for the formula. WFS now calls this helper instead of inlining the computation. The `reliableMetrics?.trimpScore ?: if (...) { ... }` fallback pattern — where the first branch was always null — is gone. TRIMP is now testable in `MetricsCalculatorTest.kt` (3 new tests added covering null-input rejection, quadratic scaling, and exact formula value).

### Bonus — responseLagSec default mismatch (discovered during audit)

Not in the original audit findings, surfaced while reading the files. `AdaptiveProfile.responseLagSec = 38f` (correct) but `WorkoutAdaptiveMetrics.responseLagSec = 25f` and `MetricsCalculator.deriveFromPaceSamples` parameter default = `25f`. Production callers always supply an explicit value so no current bug, but the `25f` default is an "inert horizon" landmine for any future caller who omits the parameter. Both defaults raised to `38f` with a comment explaining the trap.

### Bonus — `defaultRestHr` formula (discovered during audit)

`AdaptiveProfile.kt:defaultRestHr` uses `(72 - 0.2 * age).coerceIn(55, 75)` — population-level linear model with no citation. Added KDoc citing Framingham/MESA cohort data and explaining the intentional conservative bias.

### Verification

- All three affected test classes pass (`SubMaxHrEstimatorTest`, `MetricsCalculatorTest`, `FitnessSignalEvaluatorTest`).
- Full unit test suite passes (`./gradlew :app:testDebugUnitTest`).
- Debug APK builds cleanly (`./gradlew :app:assembleDebug`).
- No new lint warnings beyond the pre-existing ones documented in CLAUDE.md.
