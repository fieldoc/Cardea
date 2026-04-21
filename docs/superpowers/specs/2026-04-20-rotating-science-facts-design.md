# Rotating Science Facts — Design

**Date:** 2026-04-20
**Status:** Approved (pending spec review)
**Supersedes:** Hardcoded single-string content in `ZoneEducationProvider` (2026-04-01) for the FULL and ONE_LINER densities.

## Problem

Cardea surfaces educational "science facts" in five places (Home today card, Active workout `MissionCard`, Bootcamp hero, Bootcamp expanded session sheet, Bootcamp calendar list, Progress zone legend). All of them ultimately call `ZoneEducationProvider`, which today returns **exactly one hardcoded string per `(ZoneId, ContentDensity)` cell** — 15 strings total.

A bootcamp user running 3 EASY sessions a week sees the same Zone-2 paragraph three times that week, every week. The same is true for the secondary `CoachingInsightEngine` strip on Home: each insight branch hardcodes its title and subtitle, so e.g. "Consider an easy day" is identical every time it triggers.

The user reports "a lot of repetition" — accurate.

## Goals

1. **Variety per surface.** Same surface, viewed across different days, shows different facts.
2. **Stability per day.** Re-opening the app on the same day must not flicker the fact.
3. **Per-surface variety on the same day.** When multiple zones appear together (Progress legend), each zone shows a *different* fact, not three rolled from the same seed.
4. **Broaden tone** beyond pure physiology to include practical coaching tips and a small minority of inspirational long-term-benefit facts.
5. **Fact integrity.** Every new fact has a provenance tag matching the project's Science Constants Register conventions.

## Non-goals

- Per-tier or per-phase fact filtering (Foundation vs Performance). Architecture supports it later but not built now.
- User-pickable category preferences ("show me more coaching tips, fewer history facts"). Pool tagging makes this possible later.
- Localisation. All facts in en-US.
- Updating `SessionDescription.STRIDES` fallback string in `BootcampScreen.kt:2007`. Out of scope; one string isn't the repetition problem.

## Architecture

Two parallel changes, same selector pattern:

### 1. `ZoneEducationProvider` — pool-backed

Restructure private functions from `String` returns to `List<String>` pools, keyed by `(ZoneId, ContentDensity)`.

| Density | Treatment |
|---|---|
| `BADGE` | **Stays single-string.** 1–2 word labels ("Aerobic Base", "Threshold", "VO₂max") — rotation would be jarring and non-additive. No change. |
| `ONE_LINER` | **Pool of ~10 variants per zone** (5 zones × 10 = 50 strings). |
| `FULL` | **Pool of ~10 variants per zone** (5 zones × 10 = 50 strings). |

`getContent()` selects a variant via the shared selector before appending the existing `bpmRange` personalization (no change to BPM logic — still works with `maxHr` / `restHr` / `bufferBpm`).

### 2. `CoachingInsightEngine` — variant pools per branch

Each `CoachingInsight(title, subtitle, icon)` return becomes a selection from a 3–4 variant pool keyed by branch name (e.g. `"INACTIVITY"`, `"CONSECUTIVE_HARD"`, `"Z2_IMPROVEMENT"`). Icon stays per-branch (icon variation would be more confusing than helpful). Subtitle template-substitutes user data where needed (`$daysSinceLastRun`, `$consecutiveHard`, `$z2Improvement`) — handled by Kotlin string interpolation inside each variant.

### 3. New `domain/education/FactSelector.kt` — shared selector

Pure Kotlin, no Android imports (matches the existing `domain/education` pattern, keeps unit tests fast).

```kotlin
object FactSelector {
    /**
     * Deterministic variant selector. Same (seedKey, dayEpoch) → same index.
     *
     * @param poolSize must be > 0
     * @param seedKey distinguishes surfaces / zones on the same day so multi-zone
     *                screens don't all show the same variant
     * @param dayEpoch LocalDate.toEpochDay() — caller supplies for testability
     */
    fun selectIndex(poolSize: Int, seedKey: String, dayEpoch: Long): Int {
        require(poolSize > 0) { "poolSize must be > 0" }
        val mixed = (seedKey.hashCode().toLong() * 31L) xor dayEpoch
        val nonNeg = mixed.hashCode() and Int.MAX_VALUE
        return nonNeg % poolSize
    }
}
```

The double-hash + xor + mask is intentional: a naïve `seedKey.hashCode() xor dayEpoch.toInt()` clusters consecutive days for short seedKeys. The mask handles `Int.MIN_VALUE.absoluteValue == Int.MIN_VALUE` (the only negative-after-`absoluteValue` case in Kotlin). Verified by test: 30-day window with `seedKey="Z2_FULL"` and `poolSize=10` hits ≥7 distinct indices.

### 4. Caller signature change

`getContent()` and `forSessionType()` gain a `dayEpoch: Long` parameter.

```kotlin
fun getContent(
    zoneId: ZoneId,
    density: ContentDensity,
    maxHr: Int? = null,
    restHr: Int? = null,
    bufferBpm: Int = 5,
    dayEpoch: Long = LocalDate.now().toEpochDay()  // default keeps callers compiling
): String
```

Callers should pass an explicit `dayEpoch` for testability and to make the time dependency visible. Updated call sites:

- `HomeScreen.kt:307` — Home today card (ONE_LINER)
- `MissionCard.kt:128` — Active workout (BADGE — passing `dayEpoch` is no-op since BADGE bypasses selector, but call site updated for consistency)
- `ProgressScreen.kt:565` — Zone legend (ONE_LINER, called inside a loop — different `seedKey` per zone gives the multi-zone variety)
- `BootcampScreen.kt:1844` — Calendar list rows (BADGE)
- `BootcampScreen.kt:2001` — Expanded session sheet (FULL)
- `BootcampScreen.kt:2307`, `:2311` — Today hero (BADGE + ONE_LINER)

### 5. Seed key convention

| Surface call | seedKey |
|---|---|
| Zone facts | `"${zoneId.name}_${density.name}"` (e.g. `"ZONE_2_FULL"`) |
| CoachingInsight | `"INSIGHT_${branch}"` (e.g. `"INSIGHT_INACTIVITY"`) |

Different surfaces showing the same zone on the same day will see the **same** fact (intentional — keeps Home and Bootcamp consistent for that day's session). Different zones on the same screen (Progress) show different facts because the seedKey differs.

## Content authoring

### Mix per zone pool (ONE_LINER and FULL each)

| Category | Count per zone | Tag |
|---|---|---|
| Physiology | ~6 | `physiology` |
| Practical coaching tip | ~3 | `coaching` |
| Inspirational / long-term benefit | ~1–2 | `inspiration` |

Total per zone × density = ~10. Across 5 zones × 2 densities = ~100 new strings.

### Tone examples

**Physiology (continues current style):**
> Z2 — "Easy running grows mitochondria — the cellular furnaces that turn fat into ATP. More mitochondria means burning fat at faster paces, sparing the limited glycogen you'll need on race day."

**Practical coaching tip (new):**
> Z2 — "If you can sing a chorus without gulping air, you're in Z2. If you can only manage short phrases, you've drifted into Z3 — ease back."

> Z3 — "Tempo effort is 'comfortably hard' — you can speak in 3–4 word bursts, not full sentences. If conversation feels normal, push a touch."

> Z4-5 — "If you finish the last interval feeling like you could have done two more, you went too easy. The final rep should hurt."

**Inspirational / long-term benefit (~1–2 per zone, mostly in FULL density where there's room):**
> Z2 — "Aerobic exercise raises BDNF (brain-derived neurotrophic factor), a protein that supports new neuron growth in the hippocampus. The biggest jumps come from sustained 30+ minute sessions at exactly this kind of effort. Runners over 50 show measurably larger hippocampal volume than non-runners."

> Z2 — "Just 4 hours of moderate aerobic work per week is associated with a ~40% lower all-cause mortality risk in long-term cohort studies. The effect plateaus around 7–8 hours; you don't need to be elite to capture it."

> Z3 — "Threshold training improves cognitive flexibility within ~12 weeks — measurable gains on tasks involving rapid attention switching. The pace of adaptation is roughly the same as the cardiovascular gain."

> Z4-5 — "High-intensity intervals trigger a sharp post-exercise spike in BDNF and IGF-1. The effect lasts hours after you finish — one reason hard sessions often produce a clearer, calmer afternoon than easy ones."

> Recovery — "The 'runner's brain' isn't poetic — MRI studies show distinct white-matter changes in regular runners' default mode networks within 6 months. Those changes correlate with reductions in rumination and depressive symptoms."

> Race-pace — "Race rehearsal also rehearses the dopamine pathway. Visualising and physically practising the goal effort shifts race-day anxiety from threat-response into approach-motivation — measurable in cortisol response."

Inspirational facts deliberately spread across multiple zones rather than living in their own bucket — they apply to most aerobic effort, and a "did you know" framing keeps them feeling like asides rather than the main point of any given session.

### Provenance discipline

Every fact gets a one-line provenance comment in the source file:

```kotlin
// physiology — Coyle 1995, MCT-1 upregulation in trained muscle
"Tempo effort sits near your lactate turnpoint…"

// inspiration — Erickson et al. 2011, hippocampal volume in older adults
"Aerobic exercise raises BDNF…"

// coaching — internal-rationale, talk-test calibration to Z2/Z3 boundary
"If you can sing a chorus without gulping air…"
```

Valid provenance tags (matching the Science Constants Register convention):
- `published` — peer-reviewed paper or established physiology textbook
- `coaching-canon` — widely-cited in mainstream coaching literature (Daniels, Lydiard, Friel)
- `internal-rationale` — Cardea's own derivation; safe to use for tips that follow from the engine logic
- `intentional-non-standard` — facts where the popular framing is technically inexact but useful (e.g. "lactic acid" instead of "lactate")

**No `unsourced` facts ship.** Spec-time check: every variant in the implementation plan must carry a tag.

## Testing

New tests:

- **`FactSelectorTest`**
  - Same `(seedKey, dayEpoch)` → same index, repeatedly.
  - 30-day window with `poolSize=10`, fixed `seedKey` → at least 7 distinct indices.
  - Different `seedKey`s on same day → different indices for at least 4 out of 5 sample seeds.
  - `poolSize=0` throws `IllegalArgumentException`.
  - Edge case: `seedKey=""`, `dayEpoch=Long.MIN_VALUE` doesn't throw and returns valid index.

- **`ZoneEducationProviderTest`** (new file — none exists today)
  - Every `(ZoneId, ContentDensity.ONE_LINER)` and `(ZoneId, ContentDensity.FULL)` pool has ≥10 entries.
  - `BADGE` returns a single fixed string per zone (regression guard).
  - Calling `getContent()` with same `(zone, density, dayEpoch)` returns same string.
  - BPM personalization still appended when `maxHr` provided, omitted when null.
  - `forSessionType("EASY", FULL, dayEpoch=...)` → routes to ZONE_2 pool.

- **Update `CoachingInsightEngineTest`** (if exists; otherwise create)
  - Each insight branch returns a variant from its pool given a fixed `dayEpoch`.
  - Variant selection is deterministic for a fixed day.
  - Template substitution (`$daysSinceLastRun` etc.) works inside selected variants.

## Files touched

**New:**
- `app/src/main/java/com/hrcoach/domain/education/FactSelector.kt`
- `app/src/test/java/com/hrcoach/domain/education/FactSelectorTest.kt`
- `app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt`

**Modified:**
- `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt` — large content delta (50 new ONE_LINER + 50 new FULL strings), small structural change
- `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt` — wrap returns in `selectVariant()`; add per-branch pools
- `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt:307` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt:128` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt:565` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt:1844`, `:2001`, `:2307`, `:2311` — pass `dayEpoch`
- `app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt` — if it exists, augment

## Risks

- **Fact accuracy.** ~100 new physiology + coaching + inspiration claims. Mitigation: provenance tag on every line; reject `unsourced`; the implementation plan will list each fact with its citation.
- **Tone drift.** "Inspiration" facts can feel like marketing. Mitigation: keep them factual and quantified ("~40% lower mortality", not "running changes your life"). Cap at 1–2 per pool.
- **Same-day cross-surface consistency.** Intentional that Home and Bootcamp show the same Z2 fact on the same day — feels coherent rather than buggy. If a future user complaint says it feels redundant, we can add the surface name to the seedKey to break the tie.
- **Compose recomposition stability.** `LocalDate.now()` called inside a `@Composable` recomposes daily but is stable within a frame. Pass it as a `remember`-d value at the screen root if any composition takes long enough that midnight could fall inside it (extreme edge case — not pre-emptively guarded).

## Open question

None at design time. Implementation plan will list each fact with provenance for review before code lands.
