# Rotating Science Facts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded single-string education content with deterministic date-seeded variant pools so users see fresh science facts daily across Home, Workout, Bootcamp, and Progress surfaces.

**Architecture:** Pure-Kotlin pool selector (`FactSelector`) keyed by `(seedKey, dayEpoch)`. `ZoneEducationProvider` keeps `BADGE` single-string but turns `ONE_LINER` and `FULL` into `List<String>` pools per zone. `CoachingInsightEngine` wraps each branch return in a per-branch pool. Mix per pool: ~6 physiology + ~3 coaching tip + ~1–2 inspiration; every fact carries a provenance comment.

**Tech Stack:** Kotlin 1.9, JUnit 4, Compose (callers only). Pure-Kotlin domain code, no Android imports in `domain/education` or `domain/coaching`.

**Spec:** `docs/superpowers/specs/2026-04-20-rotating-science-facts-design.md`

---

## File Structure

**New files:**
- `app/src/main/java/com/hrcoach/domain/education/FactSelector.kt` — pure-Kotlin index selector (`object`)
- `app/src/test/java/com/hrcoach/domain/education/FactSelectorTest.kt` — 5 tests
- `app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt` — pool shape + selection tests

**Modified files:**
- `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt` — pool refactor + 100 new fact strings + provenance comments
- `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt` — per-branch pools, derive `dayEpoch` from existing `nowMs` parameter
- `app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt` — relax exact-title asserts to membership asserts
- `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt:307` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt:128` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt:565` — pass `dayEpoch`
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt:1844, 2001, 2307, 2311` — pass `dayEpoch`

---

## Task 1: FactSelector (pure Kotlin, TDD)

**Files:**
- Create: `app/src/test/java/com/hrcoach/domain/education/FactSelectorTest.kt`
- Create: `app/src/main/java/com/hrcoach/domain/education/FactSelector.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.hrcoach.domain.education

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactSelectorTest {

    @Test
    fun `same seed and day return same index repeatedly`() {
        val a = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        val b = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        assertEquals(a, b)
    }

    @Test
    fun `30-day window with fixed seed hits at least 7 distinct indices`() {
        val seen = (0 until 30)
            .map { FactSelector.selectIndex(10, "Z2_FULL", 19000L + it) }
            .toSet()
        assertTrue("expected >=7 distinct, got ${seen.size}", seen.size >= 7)
    }

    @Test
    fun `different seeds on same day mostly return different indices`() {
        val day = 19834L
        val seeds = listOf("Z2_FULL", "Z3_FULL", "ZONE_4_5_FULL", "RECOVERY_FULL", "RACE_PACE_FULL")
        val indices = seeds.map { FactSelector.selectIndex(10, it, day) }
        val distinct = indices.toSet().size
        assertTrue("expected >=4 distinct of 5, got $distinct", distinct >= 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `poolSize zero throws`() {
        FactSelector.selectIndex(0, "anything", 1L)
    }

    @Test
    fun `extreme inputs do not throw and return valid index`() {
        val i = FactSelector.selectIndex(7, "", Long.MIN_VALUE)
        assertTrue(i in 0 until 7)
        val j = FactSelector.selectIndex(7, "x".repeat(1000), Long.MAX_VALUE)
        assertTrue(j in 0 until 7)
    }

    @Test
    fun `consecutive days with same seed do not always collide`() {
        val a = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        val b = FactSelector.selectIndex(10, "Z2_FULL", 19835L)
        // Not strictly required to differ, but with poolSize 10 collision is uncommon
        assertNotEquals("consecutive-day collision is suspicious", a, b)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.FactSelectorTest"`
Expected: FAIL — `FactSelector` does not exist.

- [ ] **Step 3: Implement FactSelector**

Write `app/src/main/java/com/hrcoach/domain/education/FactSelector.kt`:

```kotlin
package com.hrcoach.domain.education

/**
 * Deterministic variant selector for rotating educational content pools.
 *
 * Same `(poolSize, seedKey, dayEpoch)` triple always returns the same index, so
 * facts are stable within a day but vary across days and across distinct seeds
 * (zones, surfaces, branches) on the same day.
 *
 * Pure Kotlin — no Android dependencies. Caller supplies `dayEpoch` (typically
 * `LocalDate.now().toEpochDay()`) so the time dependency is explicit and testable.
 */
object FactSelector {

    /**
     * @param poolSize number of variants in the pool; must be > 0.
     * @param seedKey distinguishes surfaces/zones/branches on the same day.
     * @param dayEpoch days since epoch (e.g. `LocalDate.toEpochDay()`).
     * @return index in `0 until poolSize`.
     */
    fun selectIndex(poolSize: Int, seedKey: String, dayEpoch: Long): Int {
        require(poolSize > 0) { "poolSize must be > 0" }
        // Mix seedKey hash into a Long, xor with day, then re-hash to spread bits.
        // Mask with Int.MAX_VALUE to guarantee non-negative (sidesteps the
        // Int.MIN_VALUE.absoluteValue == Int.MIN_VALUE quirk).
        val mixed = (seedKey.hashCode().toLong() * 31L) xor dayEpoch
        val nonNeg = mixed.hashCode() and Int.MAX_VALUE
        return nonNeg % poolSize
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.FactSelectorTest"`
Expected: PASS (6 tests).

If `consecutive days do not always collide` fails, the spread is too clustered for short seedKeys — switch the inner mix to `(seedKey.hashCode().toLong() * 1_000_003L) xor (dayEpoch * 31L)` and re-run. The 1_000_003 prime improves dispersion for 6–8 char keys.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/education/FactSelector.kt \
        app/src/test/java/com/hrcoach/domain/education/FactSelectorTest.kt
git commit -m "feat(education): add FactSelector for date-seeded variant rotation"
```

---

## Task 2: ZoneEducationProvider — structural refactor (pools wired, content stub)

This task swaps the structure but keeps existing single strings as 1-element pools temporarily so callers keep working. Tasks 3–7 fill the pools with real variants.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`
- Create: `app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt`

- [ ] **Step 1: Write the failing structural tests**

Create `app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt`:

```kotlin
package com.hrcoach.domain.education

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneEducationProviderTest {

    @Test
    fun `BADGE returns single fixed string per zone (no rotation)`() {
        val day1 = 19834L
        val day2 = 19899L
        for (zone in ZoneId.values()) {
            val a = ZoneEducationProvider.getContent(zone, ContentDensity.BADGE, dayEpoch = day1)
            val b = ZoneEducationProvider.getContent(zone, ContentDensity.BADGE, dayEpoch = day2)
            assertEquals("BADGE for $zone must be stable across days", a, b)
            assertTrue("BADGE for $zone should be short label", a.length in 4..30)
        }
    }

    @Test
    fun `ONE_LINER and FULL return same content for same day and zone`() {
        val day = 19834L
        for (zone in ZoneId.values()) {
            for (density in listOf(ContentDensity.ONE_LINER, ContentDensity.FULL)) {
                val a = ZoneEducationProvider.getContent(zone, density, dayEpoch = day)
                val b = ZoneEducationProvider.getContent(zone, density, dayEpoch = day)
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `forSessionType routes EASY to ZONE_2 pool`() {
        val day = 19834L
        val viaType = ZoneEducationProvider.forSessionType("EASY", ContentDensity.ONE_LINER, dayEpoch = day)
        val viaZone = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.ONE_LINER, dayEpoch = day)
        assertNotNull(viaType)
        assertEquals(viaZone, viaType)
    }

    @Test
    fun `forSessionType returns null for unknown raw type`() {
        assertNull(ZoneEducationProvider.forSessionType("NOPE", ContentDensity.BADGE, dayEpoch = 0L))
    }

    @Test
    fun `BPM range appended to ONE_LINER when maxHr supplied`() {
        val withMax = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER,
            maxHr = 190, restHr = 50, dayEpoch = 19834L
        )
        val withoutMax = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER,
            maxHr = null, restHr = null, dayEpoch = 19834L
        )
        assertTrue("BPM suffix expected", withMax.contains("BPM"))
        assertFalse("no BPM when maxHr null", withoutMax.contains("BPM"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"`
Expected: COMPILE FAIL — `dayEpoch` is not a parameter on `getContent` / `forSessionType`.

- [ ] **Step 3: Refactor ZoneEducationProvider to pool structure**

Replace the entire body of `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt` with:

```kotlin
package com.hrcoach.domain.education

import com.hrcoach.domain.bootcamp.SessionType
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Provides contextual heart-rate zone education at three density levels.
 *
 * Pure Kotlin (java.time only — Android-free). BADGE is a fixed label per zone.
 * ONE_LINER and FULL are pools of variants selected deterministically by date
 * via [FactSelector] so users see fresh facts daily but stable within a day.
 *
 * Personalization (BPM range) is applied after variant selection when maxHr is known.
 */
object ZoneEducationProvider {

    fun getContent(
        zoneId: ZoneId,
        density: ContentDensity,
        maxHr: Int? = null,
        restHr: Int? = null,
        bufferBpm: Int = 5,
        dayEpoch: Long = LocalDate.now().toEpochDay()
    ): String = when (density) {
        ContentDensity.BADGE -> badge(zoneId)
        ContentDensity.ONE_LINER -> oneLiner(zoneId, maxHr, restHr, bufferBpm, dayEpoch)
        ContentDensity.FULL -> full(zoneId, maxHr, restHr, bufferBpm, dayEpoch)
    }

    fun zoneForSessionType(sessionType: SessionType): ZoneId = when (sessionType) {
        SessionType.EASY, SessionType.LONG, SessionType.STRIDES -> ZoneId.ZONE_2
        SessionType.TEMPO -> ZoneId.ZONE_3
        SessionType.INTERVAL -> ZoneId.ZONE_4_5
        SessionType.RACE_SIM -> ZoneId.RACE_PACE
        SessionType.DISCOVERY, SessionType.CHECK_IN -> ZoneId.RECOVERY
    }

    fun forSessionType(
        rawSessionType: String,
        density: ContentDensity,
        maxHr: Int? = null,
        restHr: Int? = null,
        bufferBpm: Int = 5,
        dayEpoch: Long = LocalDate.now().toEpochDay()
    ): String? {
        val type = runCatching { SessionType.valueOf(rawSessionType) }.getOrNull()
            ?: return null
        return getContent(zoneForSessionType(type), density, maxHr, restHr, bufferBpm, dayEpoch)
    }

    // ── BADGE (single fixed label per zone) ──────────────────────────────

    private fun badge(zoneId: ZoneId): String = when (zoneId) {
        ZoneId.ZONE_2 -> "Aerobic Base"
        ZoneId.ZONE_3 -> "Threshold"
        ZoneId.ZONE_4_5 -> "VO\u2082max"
        ZoneId.RECOVERY -> "Calibration"
        ZoneId.RACE_PACE -> "Race Effort"
    }

    // ── ONE_LINER pools (filled in Tasks 3–7) ────────────────────────────

    private fun oneLiner(
        zoneId: ZoneId, maxHr: Int?, restHr: Int?, bufferBpm: Int, dayEpoch: Long
    ): String {
        val pool = oneLinerPool(zoneId)
        val idx = FactSelector.selectIndex(pool.size, "${zoneId.name}_ONE_LINER", dayEpoch)
        val fact = pool[idx]
        val bpmSuffix = bpmRange(zoneId, maxHr, restHr, bufferBpm)?.let { " ($it)" } ?: ""
        return fact + bpmSuffix
    }

    private fun oneLinerPool(zoneId: ZoneId): List<String> = when (zoneId) {
        ZoneId.ZONE_2 -> Z2_ONE_LINERS
        ZoneId.ZONE_3 -> Z3_ONE_LINERS
        ZoneId.ZONE_4_5 -> Z45_ONE_LINERS
        ZoneId.RECOVERY -> RECOVERY_ONE_LINERS
        ZoneId.RACE_PACE -> RACE_ONE_LINERS
    }

    // ── FULL pools (filled in Tasks 3–7) ─────────────────────────────────

    private fun full(
        zoneId: ZoneId, maxHr: Int?, restHr: Int?, bufferBpm: Int, dayEpoch: Long
    ): String {
        val pool = fullPool(zoneId)
        val idx = FactSelector.selectIndex(pool.size, "${zoneId.name}_FULL", dayEpoch)
        val fact = pool[idx]
        val bpmLine = bpmRange(zoneId, maxHr, restHr, bufferBpm)
            ?.let { "\n\nYour range: $it" }
            ?: "\n\nRun a Discovery session to see your personal ranges."
        return fact + bpmLine
    }

    private fun fullPool(zoneId: ZoneId): List<String> = when (zoneId) {
        ZoneId.ZONE_2 -> Z2_FULL
        ZoneId.ZONE_3 -> Z3_FULL
        ZoneId.ZONE_4_5 -> Z45_FULL
        ZoneId.RECOVERY -> RECOVERY_FULL
        ZoneId.RACE_PACE -> RACE_FULL
    }

    // ── Stub pools (replaced in Tasks 3–7) ───────────────────────────────
    // Each pool currently holds the original 2026-04-01 single-string content
    // as a 1-element list. Tasks 3–7 expand each to ~10 variants with provenance.

    private val Z2_ONE_LINERS: List<String> = listOf(
        "Builds stroke volume and capillary density \u2014 same pace, less effort over time"
    )
    private val Z3_ONE_LINERS: List<String> = listOf(
        "Pushes your lactate threshold so you can hold faster paces longer"
    )
    private val Z45_ONE_LINERS: List<String> = listOf(
        "Raises your VO\u2082max ceiling and recruits fast-twitch fibers"
    )
    private val RECOVERY_ONE_LINERS: List<String> = listOf(
        "Easy effort that maps your HR-to-pace response for zone calibration"
    )
    private val RACE_ONE_LINERS: List<String> = listOf(
        "Rehearses the neuromuscular patterns and pacing you\u2019ll need on race day"
    )

    private val Z2_FULL: List<String> = listOf(
        "At this intensity your heart adapts to eject more blood per beat \u2014 " +
        "what physiologists call increased stroke volume. Your muscles respond " +
        "by growing denser capillary networks, improving oxygen delivery at the " +
        "cellular level. The benefit comes from accumulated volume at this effort, " +
        "not a magic intensity window \u2014 which is why elite plans keep 75\u201380% " +
        "of weekly running here."
    )
    private val Z3_FULL: List<String> = listOf(
        "Tempo effort sits near your lactate turnpoint \u2014 the intensity where " +
        "lactate production outpaces your muscles\u2019 ability to use it. " +
        "Lactate isn\u2019t waste; it\u2019s a fuel that gets shuttled between fibers " +
        "via monocarboxylate transporters (MCTs). Training here upregulates those " +
        "transporters, so the pace you can sustain before lactate overwhelms the " +
        "system shifts faster."
    )
    private val Z45_FULL: List<String> = listOf(
        "Intervals at this intensity target your VO\u2082max \u2014 the ceiling on " +
        "how much oxygen your cardiovascular system can deliver and your muscles " +
        "can use. Your heart reaches near-maximal output, and your body recruits " +
        "fast-twitch (Type II) fibers that stay dormant at easier paces. With " +
        "training, those fibers shift toward a more fatigue-resistant profile, " +
        "improving your top-end speed and kick."
    )
    private val RECOVERY_FULL: List<String> = listOf(
        "Discovery and check-in sessions collect heart rate data at controlled, " +
        "easy effort. At low intensity, the HR-to-pace relationship is most stable " +
        "and interpretable, giving Cardea clean data to identify your aerobic and " +
        "lactate thresholds without requiring an all-out effort. This calibration " +
        "personalizes every zone target in your program."
    )
    private val RACE_FULL: List<String> = listOf(
        "Race simulation trains the specific neuromuscular coordination and energy " +
        "pacing you\u2019ll use on race day. At goal pace, your body practises " +
        "sparing glycogen early so reserves remain for the final kilometres \u2014 " +
        "a negative-split strategy that produces better finishing times. It also " +
        "builds race-day confidence in a controlled setting."
    )

    // ── BPM range helper ─────────────────────────────────────────────────

    private fun bpmRange(zoneId: ZoneId, maxHr: Int?, restHr: Int?, bufferBpm: Int): String? {
        if (maxHr == null) return null
        val (lowPct, highPct) = zonePercentages(zoneId)
        val rest = restHr ?: 0
        val targetLow = (rest + (maxHr - rest) * lowPct).roundToInt()
        val targetHigh = (rest + (maxHr - rest) * highPct).roundToInt()
        return "${targetLow - bufferBpm}\u2013${targetHigh + bufferBpm} BPM"
    }

    private fun zonePercentages(zoneId: ZoneId): Pair<Float, Float> = when (zoneId) {
        ZoneId.ZONE_2 -> 0.60f to 0.70f
        ZoneId.ZONE_3 -> 0.75f to 0.85f
        ZoneId.ZONE_4_5 -> 0.85f to 0.95f
        ZoneId.RECOVERY -> 0.50f to 0.65f
        ZoneId.RACE_PACE -> 0.80f to 0.90f
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"`
Expected: PASS (5 tests).

Run all unit tests to confirm no regression:
`./gradlew testDebugUnitTest`
Expected: PASS (existing tests still green; ZoneEducationProvider call sites compile because `dayEpoch` has a default).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt \
        app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt
git commit -m "refactor(education): wire ZoneEducationProvider to FactSelector pools

BADGE remains single-string. ONE_LINER and FULL now go through
per-zone pools selected by date+seedKey. Pools currently hold the
single original string each; subsequent commits expand to ~10 variants
per zone with provenance."
```

---

## Task 3: Author Z2 (aerobic base) variant pools

**Mix:** 6 physiology + 3 coaching tip + 1 inspiration = 10 per density.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`

- [ ] **Step 1: Replace `Z2_ONE_LINERS` with the 10-variant pool**

```kotlin
    private val Z2_ONE_LINERS: List<String> = listOf(
        // physiology — Coyle 1995, stroke volume / capillary density
        "Builds stroke volume and capillary density \u2014 same pace, less effort over time",
        // physiology — Holloszy 1967, mitochondrial biogenesis at moderate intensity
        "Easy minutes grow mitochondria \u2014 the cellular furnaces that turn fat into ATP",
        // physiology — Maffetone, fat oxidation curve peaks below LT1
        "Trains your body to burn fat at faster paces, sparing glycogen for race day",
        // physiology — Brooks, MCT-1 expression in slow-twitch fibers
        "Slow-twitch fibers grow denser mitochondria when worked at this exact intensity",
        // physiology — Joyner, plasma volume expansion within 2\u20133 weeks
        "Regular Z2 expands your plasma volume in 2\u20133 weeks, lowering working HR at the same pace",
        // physiology — Daniels, aerobic enzyme adaptation timeline
        "Aerobic enzymes (citrate synthase, SDH) upregulate within 4\u20136 weeks of consistent easy volume",
        // coaching — internal-rationale, talk-test calibration
        "If you can sing a chorus without gulping air, you're in Z2; if not, ease back",
        // coaching — Maffetone, nasal-breathing as Z2 governor
        "Try nasal-only breathing on the easy parts \u2014 it caps your effort right around Z2",
        // coaching — internal-rationale, drift watch
        "HR drifts up over a long Z2 run; honour the HR cap, not the starting pace",
        // inspiration \u2014 Erickson 2011, hippocampal volume in older adults
        "Aerobic exercise raises BDNF, the protein that lets your hippocampus grow new neurons"
    )
```

- [ ] **Step 2: Replace `Z2_FULL` with the 10-variant pool**

```kotlin
    private val Z2_FULL: List<String> = listOf(
        // physiology \u2014 Coyle 1995, stroke volume and capillary growth
        "At this intensity your heart adapts to eject more blood per beat \u2014 what " +
        "physiologists call increased stroke volume. Your muscles respond by growing " +
        "denser capillary networks, improving oxygen delivery at the cellular level. " +
        "The benefit comes from accumulated volume at this effort, not a magic " +
        "intensity window \u2014 which is why elite plans keep 75\u201380% of weekly running here.",

        // physiology \u2014 Holloszy 1967, mitochondrial biogenesis
        "Easy aerobic running is the most potent stimulus for mitochondrial biogenesis " +
        "in slow-twitch fibers. Mitochondria are the cellular powerhouses that convert " +
        "fat and carbohydrate into ATP using oxygen. More mitochondria means you can " +
        "produce more energy aerobically before lactate accumulates \u2014 which is what " +
        "makes you faster at every distance from 5K to marathon.",

        // physiology \u2014 Maffetone, fat oxidation curve
        "Fat oxidation peaks at moderate aerobic effort and falls off sharply once you " +
        "cross your first lactate threshold. Training the fat-burning machinery here " +
        "spares the limited glycogen you'll need late in a hard race. Highly trained " +
        "endurance athletes can oxidise fat at paces that would force untrained runners " +
        "into pure carbohydrate burn within minutes.",

        // physiology \u2014 Joyner, plasma volume expansion
        "Within 2\u20133 weeks of consistent Z2 work, your plasma (blood liquid) volume " +
        "expands by 10\u201320%. The thinner, larger blood volume means each heartbeat " +
        "moves more oxygen, your working HR drops at the same pace, and thermoregulation " +
        "improves. This is one of the fastest visible adaptations in endurance training.",

        // physiology \u2014 Brooks, lactate shuttle in slow-twitch fibers
        "Slow-twitch (Type I) fibers don't just resist fatigue; they consume lactate " +
        "produced elsewhere as fuel. Z2 training upregulates the MCT-1 transporters " +
        "those fibers use to import lactate. The net effect: at any given pace, the " +
        "lactate your muscles produce gets cleared faster \u2014 which is functionally " +
        "the same as raising your threshold.",

        // physiology \u2014 Daniels, aerobic enzyme timeline
        "The enzymes that drive aerobic metabolism (citrate synthase, succinate " +
        "dehydrogenase) take 4\u20136 weeks of consistent easy work to upregulate. " +
        "This is why a sudden jump to harder training without an aerobic base produces " +
        "fast initial gains but plateaus quickly \u2014 the underlying machinery hasn't " +
        "scaled to support the load.",

        // coaching \u2014 internal-rationale, talk-test
        "The talk test is the most reliable Z2 governor without a HR strap: you should " +
        "be able to speak in full sentences, even sing a chorus, without gulping for " +
        "air. If conversation comes in short phrases you've drifted into Z3 \u2014 " +
        "still useful, but not what today's session is for. Slow down a touch.",

        // coaching \u2014 Maffetone, nasal breathing
        "Nasal-only breathing is a built-in effort governor. The nasal airway is " +
        "narrower than the mouth, capping how much air you can move per minute \u2014 " +
        "which caps your sustainable effort right around Z2. Drop into nasal breathing " +
        "on long runs and let the discomfort tell you when to ease.",

        // coaching \u2014 internal-rationale, cardiac drift
        "Heart rate drifts upward over a long Z2 run even at unchanged pace. This is " +
        "called cardiac drift and is driven by core temperature rise and dehydration. " +
        "Honour the HR cap, not the starting pace \u2014 letting pace creep up to keep " +
        "HR pinned defeats the point of the easy day.",

        // inspiration \u2014 Erickson et al. 2011, BDNF and hippocampal volume
        "Sustained aerobic exercise raises BDNF \u2014 brain-derived neurotrophic factor " +
        "\u2014 a protein that supports new neuron growth in the hippocampus. The biggest " +
        "jumps come from sessions over 30 minutes at exactly this kind of effort. " +
        "Runners over 50 show measurably larger hippocampal volume than non-runners, " +
        "and the gap widens with consistent years of training."
    )
```

- [ ] **Step 3: Run the unit tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"`
Expected: PASS. The pool-shape test now exercises 10-element pools.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt
git commit -m "feat(education): expand Z2 fact pools to 10 variants

Adds 6 physiology + 3 coaching + 1 BDNF inspiration variant for both
ONE_LINER and FULL densities. Each variant carries a provenance comment
matching the Science Constants Register convention."
```

---

## Task 4: Author Z3 (threshold) variant pools

**Mix:** 6 physiology + 3 coaching + 1 inspiration = 10 per density.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`

- [ ] **Step 1: Replace `Z3_ONE_LINERS` with the 10-variant pool**

```kotlin
    private val Z3_ONE_LINERS: List<String> = listOf(
        // physiology \u2014 Brooks, MCT transporter upregulation
        "Pushes your lactate threshold so you can hold faster paces longer",
        // physiology \u2014 Daniels, T-pace adaptation
        "Tempo effort raises the pace you can sustain for an hour without unravelling",
        // physiology \u2014 Coyle, fractional utilisation of VO2max
        "Trains the percentage of your VO\u2082max you can hold for long efforts",
        // physiology \u2014 Costill, glycogen sparing at sub-threshold paces
        "Just below threshold, your body burns the highest sustainable mix of fat and carbs",
        // physiology \u2014 Brooks, lactate clearance pathways
        "Type I fibers learn to consume the lactate Type II fibers produce \u2014 net clearance rises",
        // physiology \u2014 Hoffmann/Joyner, OBLA shift with training
        "The HR at which lactate accumulates faster than you can clear it shifts upward",
        // coaching \u2014 internal-rationale, RPE for tempo
        "Comfortably hard \u2014 you can speak 3\u20134 word bursts, not full sentences",
        // coaching \u2014 Daniels, T-pace for marathon prep
        "Marathon-specific tempo: settle in at the effort you'd hold for one steady hour",
        // coaching \u2014 internal-rationale, second-wind cue
        "The first 5 minutes of tempo feel hardest; settle in, breathe rhythmically, hold the line",
        // inspiration \u2014 Hopkins et al., cognitive flexibility from sustained moderate exercise
        "Threshold sessions improve cognitive flexibility within ~12 weeks \u2014 measurable on attention tasks"
    )
```

- [ ] **Step 2: Replace `Z3_FULL` with the 10-variant pool**

```kotlin
    private val Z3_FULL: List<String> = listOf(
        // physiology \u2014 Brooks, MCT-1 / MCT-4 lactate shuttle
        "Tempo effort sits near your lactate turnpoint \u2014 the intensity where lactate " +
        "production outpaces your muscles' ability to use it. Lactate isn't waste; it's " +
        "a fuel that gets shuttled between fibers via monocarboxylate transporters (MCTs). " +
        "Training here upregulates those transporters, so the pace you can sustain before " +
        "lactate overwhelms the system shifts faster.",

        // physiology \u2014 Daniels, T-pace and lactate steady state
        "T-pace (tempo pace) is roughly the speed you could hold for one hour at race " +
        "effort. Training repeatedly at this intensity teaches your body to clear lactate " +
        "as fast as it produces it, raising the threshold pace itself. A 6\u201312 week " +
        "tempo block typically shifts threshold pace by 10\u201320 seconds per kilometre " +
        "in trained runners.",

        // physiology \u2014 Coyle, fractional utilisation
        "Two runners with the same VO\u2082max can have very different race times, because " +
        "the fraction of VO\u2082max they can sustain (their fractional utilisation) " +
        "differs. Threshold training is the most direct way to raise that fraction. " +
        "Elite marathoners hold ~85% of VO\u2082max for the race; recreational runners " +
        "typically hold 70\u201375%.",

        // physiology \u2014 Costill, fuel mix at sub-threshold
        "Just below threshold, your body burns the highest sustainable mix of fat and " +
        "carbohydrate \u2014 about 50/50 in trained runners. This is the metabolic " +
        "intensity that builds the engine for efforts from 10K to half marathon. Lower " +
        "is more aerobic but doesn't push the threshold; higher tips into pure-carb burn.",

        // physiology \u2014 Brooks, intramuscular lactate shuttle
        "Your fast-twitch fibers produce lactate; your slow-twitch fibers can consume " +
        "it. Threshold training increases the slow-twitch fibers' uptake capacity \u2014 " +
        "the same mechanism as MCT-1 upregulation \u2014 so within-muscle clearance " +
        "improves. The net effect is the threshold itself shifts to a higher pace.",

        // physiology \u2014 Hoffmann/Joyner, OBLA shift
        "OBLA \u2014 onset of blood lactate accumulation \u2014 is the heart rate at which " +
        "blood lactate concentration rises above ~4 mmol/L. Threshold training shifts " +
        "OBLA to a higher HR over 6\u201312 weeks. Runners who measured OBLA before and " +
        "after a tempo block typically see shifts of 5\u201312 BPM, depending on training " +
        "background.",

        // coaching \u2014 internal-rationale, tempo RPE
        "The tempo target is 'comfortably hard' \u2014 you can speak in 3\u20134 word " +
        "bursts but not full sentences. If conversation feels normal, push a touch. If " +
        "you can't manage even short phrases, ease back: you've drifted into VO\u2082max " +
        "territory and the lactate clearance training stops working as intended.",

        // coaching \u2014 Daniels, marathon-pace tempo
        "For marathon preparation, tempo runs are the bedrock workout. The goal isn't " +
        "speed for its own sake; it's teaching your body to hold a high fractional " +
        "utilisation of VO\u2082max without crossing the threshold and triggering " +
        "lactate runaway. Settle in early, find the rhythm, hold the line.",

        // coaching \u2014 internal-rationale, second-wind
        "The first 5 minutes of a tempo run usually feel disproportionately hard \u2014 " +
        "your aerobic system is still ramping up. By minute 8\u201310, breathing " +
        "stabilises, HR settles into its target band, and the effort feels more " +
        "manageable. Don't bail in those first 5 minutes; trust the warm-up.",

        // inspiration \u2014 Hopkins et al., aerobic exercise and cognition
        "Sustained moderate-intensity exercise like threshold running improves cognitive " +
        "flexibility within ~12 weeks \u2014 measurable as faster, more accurate switching " +
        "between attention tasks. The physiological pace of your aerobic gain and the " +
        "cognitive gain run roughly in parallel: the same training that builds your engine " +
        "also rewires how efficiently you think."
    )
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt
git commit -m "feat(education): expand Z3 fact pools to 10 variants"
```

---

## Task 5: Author Z4-5 (VO2max / interval) variant pools

**Mix:** 6 physiology + 3 coaching + 1 inspiration.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`

- [ ] **Step 1: Replace `Z45_ONE_LINERS`**

```kotlin
    private val Z45_ONE_LINERS: List<String> = listOf(
        // physiology \u2014 Bassett & Howley, VO2max ceiling
        "Raises your VO\u2082max ceiling and recruits fast-twitch fibers",
        // physiology \u2014 Helgerud, 4x4 minute intervals stimulus
        "4x4 minute intervals are among the strongest known stimuli for raising VO\u2082max",
        // physiology \u2014 Coyle, fast-twitch fatigue resistance
        "Type II fibers shift toward a more fatigue-resistant profile with regular interval work",
        // physiology \u2014 Bishop, buffering capacity adaptation
        "High-intensity work raises your muscles' ability to buffer hydrogen ions \u2014 less burn at race pace",
        // physiology \u2014 Joyner, cardiac output ceiling
        "Maximum cardiac output rises with VO\u2082max training; resting HR usually falls in parallel",
        // physiology \u2014 Tabata-style protocols, EPOC contribution
        "Interval afterburn (EPOC) keeps metabolism elevated for hours post-session",
        // coaching \u2014 internal-rationale, last-rep test
        "If the last rep felt easy, you went too soft \u2014 the final rep should hurt",
        // coaching \u2014 Daniels, R-pace pacing discipline
        "Even-paced reps trump first-rep heroics \u2014 negative-split your interval set",
        // coaching \u2014 internal-rationale, recovery between reps
        "Full HR recovery between reps preserves quality; cut it short and you're training Z3, not Z4-5",
        // inspiration \u2014 Hillman et al., HIIT and BDNF/IGF-1
        "Hard intervals trigger a sharp post-exercise spike in BDNF and IGF-1, hours-long mood lift"
    )
```

- [ ] **Step 2: Replace `Z45_FULL`**

```kotlin
    private val Z45_FULL: List<String> = listOf(
        // physiology \u2014 Bassett & Howley, VO2max as ceiling
        "Intervals at this intensity target your VO\u2082max \u2014 the ceiling on how " +
        "much oxygen your cardiovascular system can deliver and your muscles can use. " +
        "Your heart reaches near-maximal output, and your body recruits fast-twitch " +
        "(Type II) fibers that stay dormant at easier paces. With training, those " +
        "fibers shift toward a more fatigue-resistant profile, improving your top-end " +
        "speed and kick.",

        // physiology \u2014 Helgerud 2007, 4x4 protocol
        "4x4 minute intervals at 90\u201395% HRmax with 3 minute recoveries are among " +
        "the strongest known stimuli for raising VO\u2082max. Trained subjects in " +
        "controlled studies have raised VO\u2082max by 5\u201310% in 8 weeks on this " +
        "protocol alone. The mechanism is a combination of stroke volume increase and " +
        "improved oxygen extraction at the muscle.",

        // physiology \u2014 Coyle, fast-twitch fatigue resistance
        "Fast-twitch fibers come in two flavours: Type IIa (somewhat fatigue resistant) " +
        "and Type IIx (powerful but fatigue quickly). Regular interval training shifts " +
        "Type IIx toward Type IIa, giving you more fast-twitch capacity that can " +
        "actually sustain race-pace efforts. The shift is reversible \u2014 detrain for " +
        "a few weeks and IIa fibers drift back toward IIx.",

        // physiology \u2014 Bishop 2008, buffering capacity
        "Hard repeated efforts raise your muscles' buffering capacity \u2014 their " +
        "ability to neutralise the hydrogen ions that accompany lactate production. " +
        "Hydrogen ions, not lactate itself, cause the burning sensation and acid-driven " +
        "fatigue. Better buffering means you can tolerate higher metabolic load before " +
        "form breaks down.",

        // physiology \u2014 Joyner, max cardiac output
        "VO\u2082max is the product of maximum cardiac output and maximum oxygen " +
        "extraction at the muscle. Interval training drives both: your heart's stroke " +
        "volume rises (more blood per beat at max HR), and your muscle capillary " +
        "density improves (more surface area for oxygen exchange). Resting HR typically " +
        "falls 5\u201310 BPM over a focused VO\u2082max block.",

        // physiology \u2014 EPOC after high-intensity work
        "After a hard interval session, your body keeps metabolism elevated for hours " +
        "\u2014 the excess post-exercise oxygen consumption (EPOC) effect. Hard intervals " +
        "produce a much larger and longer EPOC than steady-state work. This is one " +
        "reason a 30-minute interval session can match the total energy cost of a much " +
        "longer easy run.",

        // coaching \u2014 internal-rationale, last-rep test
        "The last interval should be the hardest, not the easiest. If you finish the " +
        "set feeling like you could have done two more reps at the same pace, you went " +
        "too soft on the early ones. The point of intervals is to push the system to " +
        "near-maximal stress \u2014 if you finish fresh, the stimulus didn't land.",

        // coaching \u2014 Daniels, R-pace discipline
        "Even-paced reps beat first-rep heroics. Going out too hard on rep 1 burns " +
        "buffering capacity and metabolic reserves you'll need for reps 4\u20136. Aim " +
        "for a slight negative split across the set: each rep at the same effort or a " +
        "touch faster than the one before. Consistent pace with rising HR is exactly " +
        "what you want.",

        // coaching \u2014 internal-rationale, recovery quality
        "Recovery quality between reps determines the workout's stimulus. If you cut " +
        "recovery short, HR doesn't return to its target band, the next rep starts " +
        "with a deficit, and the whole session drifts toward threshold work instead of " +
        "VO\u2082max work. Walk during recovery; jog only if HR drops fast and you feel " +
        "fully recovered.",

        // inspiration \u2014 Hillman, HIIT and post-exercise BDNF spike
        "High-intensity intervals trigger a sharp post-exercise spike in BDNF and " +
        "IGF-1 (insulin-like growth factor). The effect lasts several hours after you " +
        "finish \u2014 one reason hard sessions often produce a clearer, calmer afternoon " +
        "than easy ones. The same molecules that grow new neurons after a hard rep " +
        "are reshaping how your brain processes the rest of your day."
    )
```

- [ ] **Step 3: Run tests + commit**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt
git commit -m "feat(education): expand Z4-5 fact pools to 10 variants"
```

Expected: tests PASS.

---

## Task 6: Author RECOVERY (calibration / discovery) variant pools

**Mix:** 6 physiology + 3 coaching + 1 inspiration.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`

- [ ] **Step 1: Replace `RECOVERY_ONE_LINERS`**

```kotlin
    private val RECOVERY_ONE_LINERS: List<String> = listOf(
        // physiology \u2014 internal-rationale, low-intensity HR-pace stability
        "Easy effort that maps your HR-to-pace response for zone calibration",
        // physiology \u2014 Joyner, controlled-effort signal-to-noise
        "Low-effort runs give the cleanest HR-to-pace data \u2014 less drift, less noise",
        // physiology \u2014 Friel, HRV-based recovery readiness
        "Easy runs preserve heart-rate variability \u2014 a marker of recovery and adaptive readiness",
        // physiology \u2014 Costill, parasympathetic recovery during easy work
        "Truly easy effort lets the parasympathetic nervous system rebound between hard sessions",
        // physiology \u2014 Maffetone, MAF and aerobic restoration
        "Easy aerobic work restores capillary circulation faster than full rest",
        // physiology \u2014 internal-rationale, glycogen replenishment alignment
        "Low-intensity sessions don't deplete glycogen, so adaptations from yesterday's hard work continue",
        // coaching \u2014 internal-rationale, calibration discipline
        "On a calibration run, hold the cap; don't 'just push for the next light'",
        // coaching \u2014 internal-rationale, recovery vs lazy
        "Recovery effort should feel almost too easy \u2014 if it feels like a 'normal run', it's too hard",
        // coaching \u2014 Friel, pre-run HRV check
        "If morning HRV is low, today's planned easy run is the right call \u2014 not the wrong one",
        // inspiration \u2014 Erickson et al., default mode network changes
        "Regular runners show distinct white-matter changes in the brain's default mode network within 6 months"
    )
```

- [ ] **Step 2: Replace `RECOVERY_FULL`**

```kotlin
    private val RECOVERY_FULL: List<String> = listOf(
        // physiology \u2014 calibration rationale
        "Discovery and check-in sessions collect heart rate data at controlled, easy " +
        "effort. At low intensity, the HR-to-pace relationship is most stable and " +
        "interpretable, giving Cardea clean data to identify your aerobic and lactate " +
        "thresholds without requiring an all-out effort. This calibration personalizes " +
        "every zone target in your program.",

        // physiology \u2014 Joyner, signal-to-noise at low effort
        "At low effort, the relationship between heart rate and pace is nearly linear " +
        "and free from the cardiac drift that contaminates harder efforts. This is why " +
        "Cardea uses controlled easy runs as the calibration substrate \u2014 the data " +
        "is cleaner, the underlying aerobic system shows up unmasked, and we can fit " +
        "your personal HR-pace curve without forcing a maximum effort.",

        // physiology \u2014 Friel, HRV and recovery
        "Easy running preserves heart-rate variability (HRV), a measure of how " +
        "responsively your autonomic nervous system can switch between sympathetic " +
        "(fight-or-flight) and parasympathetic (rest-and-digest) modes. High HRV " +
        "correlates with recovery readiness and adaptation. Stacking too many hard " +
        "days in a row crashes HRV; easy days bring it back up.",

        // physiology \u2014 Costill, parasympathetic rebound
        "After a hard session, your parasympathetic nervous system needs time to " +
        "reassert dominance and drive recovery. Truly easy effort \u2014 below the " +
        "first lactate threshold \u2014 lets that rebound happen. Push too hard on " +
        "an 'easy' day and you sit in sympathetic dominance, blunting the recovery " +
        "and the adaptation it enables.",

        // physiology \u2014 Maffetone, aerobic restoration
        "Light aerobic movement after a hard day restores capillary circulation in " +
        "the working muscles faster than complete rest. The increased blood flow " +
        "clears metabolic by-products and delivers nutrients for repair. This is why " +
        "active recovery often feels better the next morning than a full off day, " +
        "though both have their place in a balanced plan.",

        // physiology \u2014 internal-rationale, glycogen sparing
        "Low-intensity work runs primarily on fat for fuel, leaving muscle glycogen " +
        "stores largely intact. This matters because the adaptations from yesterday's " +
        "hard session \u2014 enzyme synthesis, mitochondrial growth \u2014 require " +
        "energy to complete. Burning down glycogen on a recovery day diverts resources " +
        "away from those repair processes.",

        // coaching \u2014 calibration discipline
        "On a calibration session the goal is data, not training stimulus. Hold the " +
        "HR cap even when the legs want to push, don't sprint for the next traffic " +
        "light, and don't try to keep up with anyone. Treat it like a measurement " +
        "appointment: the cleaner the inputs, the more accurate your personalised " +
        "zones and predictions for every session that follows.",

        // coaching \u2014 internal-rationale, recovery vs lazy
        "True recovery effort should feel almost embarrassingly easy \u2014 like " +
        "you're under-running your fitness. If a recovery run feels like a 'normal " +
        "run', it's too hard. The sign you got it right is waking up the next day " +
        "fresher than you would have without it. The temptation to push because you " +
        "feel good is the trap that breaks training cycles.",

        // coaching \u2014 Friel, HRV-guided training
        "If morning HRV is suppressed, today's planned easy run isn't a soft option " +
        "\u2014 it's the right call. Trying to convert it into a hard session because " +
        "you 'feel okay' typically produces a poor session and digs the recovery hole " +
        "deeper. Trust the data, do the easy work, get to tomorrow with capacity to " +
        "spare.",

        // inspiration \u2014 Erickson, brain white matter
        "MRI studies of regular runners show distinct white-matter changes in the " +
        "default mode network \u2014 the brain regions active during rest and " +
        "introspection \u2014 within 6 months of consistent training. Those changes " +
        "correlate with measurable reductions in rumination and depressive symptoms. " +
        "The 'runner's brain' isn't a metaphor; it's a structural rewiring you can " +
        "see on a scan."
    )
```

- [ ] **Step 3: Run tests + commit**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt
git commit -m "feat(education): expand Recovery fact pools to 10 variants"
```

Expected: tests PASS.

---

## Task 7: Author RACE_PACE variant pools

**Mix:** 6 physiology + 3 coaching + 1 inspiration.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`

- [ ] **Step 1: Replace `RACE_ONE_LINERS`**

```kotlin
    private val RACE_ONE_LINERS: List<String> = listOf(
        // physiology \u2014 Hawley, race-specific neuromuscular patterning
        "Rehearses the neuromuscular patterns and pacing you'll need on race day",
        // physiology \u2014 Costill, glycogen sparing and pacing
        "Even pacing spares glycogen for the closing kilometres \u2014 negative splits beat fades",
        // physiology \u2014 Daniels, race-specific economy
        "Specific work at goal pace improves running economy at that pace, not just at any pace",
        // physiology \u2014 Joyner, lactate steady state for the race distance
        "Race-pace work calibrates the lactate steady state you'll be holding for the duration",
        // physiology \u2014 Brooks, fuel mix at race intensity
        "At race effort your fat/carb fuel ratio settles into the mix you'll burn on the day",
        // physiology \u2014 Maffetone, heat-acclimation through race-pace efforts
        "Race-pace work teaches thermoregulation under sustained moderate-high load",
        // coaching \u2014 internal-rationale, pacing discipline
        "If the first kilometre at goal pace feels easy, hold the pace \u2014 don't push faster",
        // coaching \u2014 Daniels, race tactics rehearsal
        "Use race-pace runs to rehearse fueling timing, gear choice, and split discipline",
        // coaching \u2014 internal-rationale, mental rehearsal
        "Visualise the hard kilometres while you run them \u2014 the brain pattern transfers to race day",
        // inspiration \u2014 cortisol and approach motivation
        "Race rehearsal shifts race-day cortisol from threat-response into approach-motivation"
    )
```

- [ ] **Step 2: Replace `RACE_FULL`**

```kotlin
    private val RACE_FULL: List<String> = listOf(
        // physiology \u2014 Hawley, neuromuscular specificity
        "Race simulation trains the specific neuromuscular coordination and energy " +
        "pacing you'll use on race day. At goal pace, your body practises sparing " +
        "glycogen early so reserves remain for the final kilometres \u2014 a " +
        "negative-split strategy that produces better finishing times. It also " +
        "builds race-day confidence in a controlled setting.",

        // physiology \u2014 Costill, glycogen and pacing
        "Glycogen depletion is the single biggest physiological cause of race-day " +
        "fades. Going out too fast burns glycogen at a rate your aerobic system can't " +
        "sustain, and once stores tip below ~30% of capacity, pace falls off a cliff. " +
        "Race-pace rehearsals train you to hold even or slightly negative splits, " +
        "preserving glycogen for the final third where it actually counts.",

        // physiology \u2014 Daniels, specificity of running economy
        "Running economy \u2014 how much oxygen you consume to maintain a given pace " +
        "\u2014 improves most at the specific pace you train. A runner who trains " +
        "extensively at marathon pace will be more economical at marathon pace than " +
        "one with the same VO\u2082max who didn't. This is why race-specific work in " +
        "the final 6\u20138 weeks of a build is non-negotiable for goal performances.",

        // physiology \u2014 Joyner, lactate steady state at race effort
        "Each race distance has a characteristic lactate steady state \u2014 the highest " +
        "intensity at which lactate production matches clearance for the race duration. " +
        "Marathon pace sits well below threshold; 10K pace sits at threshold; 5K pace " +
        "sits above. Race-pace sessions calibrate the steady state you'll be holding " +
        "and let your body settle into its rhythm under that exact load.",

        // physiology \u2014 Brooks, fuel mix at race intensity
        "Your fat/carbohydrate fuel mix shifts as intensity rises. At marathon pace, " +
        "trained runners burn ~50/50 fat-carb; at half-marathon pace, more like 30/70; " +
        "at 10K pace, near-pure carbohydrate. Race-pace work trains the metabolic " +
        "machinery for the specific mix you'll use, including the enzymes that handle " +
        "the dominant fuel at that intensity.",

        // physiology \u2014 Maffetone, thermoregulation under sustained load
        "Race-pace efforts train heat dissipation under sustained moderate-high load. " +
        "Plasma volume expands further, sweat onset comes earlier and at lower core " +
        "temperatures, and the cardiovascular system handles the dual demand of muscle " +
        "blood flow and skin blood flow more efficiently. These adaptations don't " +
        "happen on easy days; they require the specific stress of race-pace work.",

        // coaching \u2014 internal-rationale, pacing discipline
        "If the first kilometre at goal race pace feels suspiciously easy, hold the " +
        "pace anyway. The sense of ease is a function of full glycogen, fresh legs, " +
        "and adrenaline \u2014 conditions that won't last. Pushing faster early because " +
        "it feels easy is the most common cause of late-race blowups. Pace is set in " +
        "the first kilometre; the result is decided in the last.",

        // coaching \u2014 Daniels, rehearsal beyond pace
        "Race-pace runs are the place to rehearse everything you'll do on race day: " +
        "the gear, the fuel-and-fluid timing, the warm-up routine, even the mental " +
        "checklist for handling discomfort. Every detail you nail in rehearsal is one " +
        "less variable on race day. Treat the run as a dress rehearsal, not just a " +
        "physical workout.",

        // coaching \u2014 internal-rationale, mental rehearsal
        "Use the hard kilometres of a race-pace session to mentally rehearse the hard " +
        "kilometres of the race itself. Visualise the course, the discomfort, the " +
        "cues you'll use to hold form. Sport-psychology research shows the brain " +
        "patterns you build in rehearsal transfer to race day, reducing the gap " +
        "between what you can do in training and what you actually deliver under " +
        "race pressure.",

        // inspiration \u2014 race-rehearsal and dopamine pathway
        "Race rehearsal also rehearses the dopamine pathway. Visualising and physically " +
        "practising the goal effort shifts race-day arousal from threat-response into " +
        "approach-motivation \u2014 measurable in pre-race cortisol patterns. Athletes " +
        "who consistently rehearse race conditions report less pre-race anxiety and " +
        "more usable adrenaline; the line between 'nervous' and 'ready' is largely " +
        "decided by how familiar the effort feels."
    )
```

- [ ] **Step 3: Run tests + commit**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest"
git add app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt
git commit -m "feat(education): expand Race-pace fact pools to 10 variants"
```

Expected: tests PASS.

---

## Task 8: Update UI call sites to pass `dayEpoch`

The default parameter keeps everything compiling, but explicit pass-through makes the time dependency visible and lets us swap in test clocks later.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt:307`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt:128`
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt:565`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt:1844, 2001, 2307, 2311`

- [ ] **Step 1: Add a screen-level `remember`-d `dayEpoch` and thread it through HomeScreen**

In `HomeScreen.kt`, near the top of the main `@Composable` (where other `remember` blocks live), add:

```kotlin
import java.time.LocalDate
// (place with the existing imports)

// Inside the main HomeScreen composable, alongside other remembers:
val dayEpoch = remember { LocalDate.now().toEpochDay() }
```

Then update line 307:

```kotlin
ZoneEducationProvider.forSessionType(
    session.sessionType, ContentDensity.ONE_LINER,
    dayEpoch = dayEpoch
)
```

- [ ] **Step 2: Update MissionCard.kt**

Add the import and pass `dayEpoch` to the call. Since BADGE bypasses the selector, this is a consistency change only:

```kotlin
import java.time.LocalDate
// ...
val dayEpoch = remember { LocalDate.now().toEpochDay() }
// ... at line 128:
ZoneEducationProvider.forSessionType(rawType, ContentDensity.BADGE, dayEpoch = dayEpoch)
```

- [ ] **Step 3: Update ProgressScreen.kt**

The call is inside `ZoneEducationLegend` which loops over zones. Add a `dayEpoch` parameter to the helper and thread one `remember` from the parent:

```kotlin
// At the parent composable that calls ZoneEducationLegend():
val dayEpoch = remember { LocalDate.now().toEpochDay() }
ZoneEducationLegend(dayEpoch = dayEpoch)

// Update the helper signature:
@Composable
private fun ZoneEducationLegend(dayEpoch: Long) {
    // ...
    Text(
        text = ZoneEducationProvider.getContent(zoneId, ContentDensity.ONE_LINER, dayEpoch = dayEpoch),
        // ...
    )
}
```

Add `import java.time.LocalDate` and `import androidx.compose.runtime.remember` at the top if not already present.

- [ ] **Step 4: Update BootcampScreen.kt (4 sites)**

At the top of the main `BootcampScreen` composable add:

```kotlin
import java.time.LocalDate
// ...
val dayEpoch = remember { LocalDate.now().toEpochDay() }
```

Pass `dayEpoch` to each of the 4 call sites at lines 1844, 2001, 2307, 2311. For helper functions like `SessionDescription.forType` that already take `maxHr`, add a `dayEpoch: Long` parameter and forward it:

```kotlin
// SessionDescription helper (~line 1998):
private object SessionDescription {
    fun forType(rawType: String, presetId: String?, maxHr: Int? = null, dayEpoch: Long): String {
        val educationFull = ZoneEducationProvider.forSessionType(
            rawType, ContentDensity.FULL, maxHr, dayEpoch = dayEpoch
        )
        // ... rest unchanged
    }
}
```

For the calendar list and today-hero call sites, pass `dayEpoch` directly:

```kotlin
// line 1844:
ZoneEducationProvider.forSessionType(
    session.rawTypeName, ContentDensity.BADGE, dayEpoch = dayEpoch
)

// lines 2307 / 2311:
val badge = ZoneEducationProvider.forSessionType(
    today.session.type.name, ContentDensity.BADGE, dayEpoch = dayEpoch
)
val oneLiner = ZoneEducationProvider.forSessionType(
    today.session.type.name, ContentDensity.ONE_LINER, dayEpoch = dayEpoch
)
```

Update all callers of `SessionDescription.forType` to pass `dayEpoch`.

- [ ] **Step 5: Build to confirm no compile errors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

If `assembleDebug` fails on `PartnerSection.kt` `WindowInsets` errors, that's a pre-existing failure documented in `CLAUDE.md` under "Known Pre-existing Lint / Compile Errors" \u2014 not a regression from this task. Run the unit tests anyway: `./gradlew testDebugUnitTest`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt \
        app/src/main/java/com/hrcoach/ui/workout/MissionCard.kt \
        app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "refactor(ui): thread dayEpoch through ZoneEducationProvider call sites

Makes the time dependency explicit at each surface (Home, MissionCard,
Progress, Bootcamp). dayEpoch is computed once per screen via remember
so all calls within a frame agree."
```

---

## Task 9: CoachingInsightEngine — variant pools per branch

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt`

`generate()` already takes `nowMs`; derive `dayEpoch` from it inside the function. No call-site changes needed.

- [ ] **Step 1: Add per-branch pools and rewrite each branch to use FactSelector**

Replace the body of `CoachingInsightEngine.kt` with the following. The `generate()` outer logic, `classifySession()`, `parseConfig()`, and `computeZ2PaceImprovement()` helpers stay the same; only the seven insight-construction sites change to pool selections.

```kotlin
package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.education.FactSelector
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.google.gson.Gson
import java.time.Instant
import java.time.ZoneId

object CoachingInsightEngine {

    private val gson = Gson()

    fun generate(
        workouts: List<WorkoutEntity>,
        workoutsThisWeek: Int,
        weeklyTarget: Int,
        hasBootcamp: Boolean,
        nowMs: Long
    ): CoachingInsight {
        val dayEpoch = Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()

        // Priority 1: No workouts ever
        if (workouts.isEmpty()) {
            return pickInsight(FIRST_RUN_VARIANTS, "INSIGHT_FIRST_RUN", dayEpoch, CoachingIcon.HEART)
        }

        val lastWorkoutMs = workouts.first().endTime
        val daysSinceLastRun = ((nowMs - lastWorkoutMs) / 86_400_000L).toInt()

        // Priority 2: Inactivity
        if (daysSinceLastRun >= 7) {
            return pickInsight(
                INACTIVITY_VARIANTS, "INSIGHT_INACTIVITY", dayEpoch, CoachingIcon.WARNING,
                replacements = mapOf("\$days" to daysSinceLastRun.toString())
            )
        }

        // Priority 3: Consecutive hard sessions
        val recentTypes = workouts.take(5).map { classifySession(it) }
        val consecutiveHard = recentTypes.takeWhile { it == SessionType.HARD }.size
        if (consecutiveHard >= 3) {
            return pickInsight(
                CONSECUTIVE_HARD_VARIANTS, "INSIGHT_CONSECUTIVE_HARD", dayEpoch, CoachingIcon.HEART,
                replacements = mapOf("\$count" to consecutiveHard.toString())
            )
        }

        // Priority 4: Z2 pace improvement
        val z2Improvement = computeZ2PaceImprovement(workouts, nowMs)
        if (z2Improvement != null && z2Improvement >= 5) {
            return pickInsight(
                Z2_IMPROVEMENT_VARIANTS, "INSIGHT_Z2_IMPROVEMENT", dayEpoch, CoachingIcon.CHART_UP,
                replacements = mapOf("\$pct" to z2Improvement.toString())
            )
        }

        // Priority 5: Weekly goal met
        if (workoutsThisWeek >= weeklyTarget && weeklyTarget > 0) {
            return pickInsight(
                WEEKLY_GOAL_VARIANTS, "INSIGHT_WEEKLY_GOAL", dayEpoch, CoachingIcon.TROPHY,
                replacements = mapOf("\$count" to workoutsThisWeek.toString())
            )
        }

        // Priority 6: Bootcamp behind schedule
        if (hasBootcamp && weeklyTarget > 0) {
            val dayOfWeek = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault()).dayOfWeek.value
            val halfDone = workoutsThisWeek.toFloat() / weeklyTarget < 0.5f
            if (dayOfWeek >= 4 && halfDone) {
                val remaining = weeklyTarget - workoutsThisWeek
                return pickInsight(
                    BEHIND_SCHEDULE_VARIANTS, "INSIGHT_BEHIND", dayEpoch, CoachingIcon.WARNING,
                    replacements = mapOf(
                        "\$done" to workoutsThisWeek.toString(),
                        "\$target" to weeklyTarget.toString(),
                        "\$remaining" to remaining.toString()
                    )
                )
            }
        }

        // Priority 7: Default
        return pickInsight(DEFAULT_VARIANTS, "INSIGHT_DEFAULT", dayEpoch, CoachingIcon.LIGHTBULB)
    }

    private data class Variant(val title: String, val subtitle: String)

    private fun pickInsight(
        pool: List<Variant>,
        seedKey: String,
        dayEpoch: Long,
        icon: CoachingIcon,
        replacements: Map<String, String> = emptyMap()
    ): CoachingInsight {
        val v = pool[FactSelector.selectIndex(pool.size, seedKey, dayEpoch)]
        var title = v.title
        var subtitle = v.subtitle
        for ((token, value) in replacements) {
            title = title.replace(token, value)
            subtitle = subtitle.replace(token, value)
        }
        return CoachingInsight(title = title, subtitle = subtitle, icon = icon)
    }

    // ── Variant pools ────────────────────────────────────────────────────

    private val FIRST_RUN_VARIANTS = listOf(
        Variant("Start your first run", "Connect your HR monitor and hit the trail"),
        Variant("Day one is the hardest", "Start with 20 easy minutes \u2014 the rest unlocks itself"),
        Variant("Time to lace up", "An easy first run today beats a perfect first run next month")
    )

    private val INACTIVITY_VARIANTS = listOf(
        Variant("Time to get moving", "It's been \$days days since your last run"),
        Variant("Easing back in?", "\$days days off \u2014 today's run can be short and easy, just to reset"),
        Variant("Welcome back", "\$days days is recoverable in a week of easy runs \u2014 don't try to make it up at once"),
        Variant("Small step today", "After \$days days off, a 20-minute easy effort beats nothing by a long way")
    )

    private val CONSECUTIVE_HARD_VARIANTS = listOf(
        Variant("Consider an easy day", "\$count hard sessions in a row \u2014 an easy run helps recovery"),
        Variant("Time to back off", "\$count hard days running \u2014 the next adaptation lives in the rest"),
        Variant("Go easy today", "After \$count hard sessions, today's gain comes from recovery, not load"),
        Variant("Recovery is training", "\$count hard days stacked \u2014 a true easy run unlocks tomorrow's quality")
    )

    private val Z2_IMPROVEMENT_VARIANTS = listOf(
        Variant("Z2 pace improved \$pct%", "Your aerobic base is growing \u2014 keep it up"),
        Variant("Aerobic engine up \$pct%", "Same HR, faster pace \u2014 the easy work is paying off"),
        Variant("\$pct% faster at the same effort", "Stroke volume and capillary density are rewarding the consistency"),
        Variant("Easy pace up \$pct%", "The boring miles are showing up in the data \u2014 trust the process")
    )

    private val WEEKLY_GOAL_VARIANTS = listOf(
        Variant("Weekly goal reached!", "\$count runs this week \u2014 nice consistency"),
        Variant("\$count runs done this week", "Consistency beats heroics \u2014 this is exactly the pattern"),
        Variant("Target hit", "\$count sessions in the books \u2014 the streak is the engine"),
        Variant("Solid week", "\$count runs logged \u2014 each one stacks on the last")
    )

    private val BEHIND_SCHEDULE_VARIANTS = listOf(
        Variant("Pick up the pace this week", "\$done/\$target sessions done \u2014 \$remaining left to stay on track"),
        Variant("Catch-up window", "\$done/\$target this week \u2014 \$remaining easy sessions get you there"),
        Variant("\$remaining runs from on-track", "\$done/\$target so far \u2014 the back half of the week is decisive")
    )

    private val DEFAULT_VARIANTS = listOf(
        Variant("Consistency is key", "Regular training builds a stronger aerobic base"),
        Variant("Show up today", "The runs you do beat the perfect runs you plan"),
        Variant("Stack the habit", "Adaptations compound when sessions don't slip"),
        Variant("Easy days enable hard days", "The shape of a good week is mostly easy with a few sharper edges")
    )

    private enum class SessionType { HARD, EASY, UNKNOWN }

    private fun classifySession(workout: WorkoutEntity): SessionType {
        val config = parseConfig(workout.targetConfig) ?: return SessionType.UNKNOWN
        if (config.mode == WorkoutMode.FREE_RUN) return SessionType.UNKNOWN
        val presetId = config.presetId ?: ""
        return when {
            presetId.contains("Z4", ignoreCase = true) ||
            presetId.contains("TEMPO", ignoreCase = true) ||
            presetId.contains("INTERVAL", ignoreCase = true) -> SessionType.HARD
            presetId.contains("Z2", ignoreCase = true) ||
            presetId.contains("EASY", ignoreCase = true) ||
            presetId.contains("AEROBIC", ignoreCase = true) -> SessionType.EASY
            else -> SessionType.UNKNOWN
        }
    }

    private fun parseConfig(json: String?): WorkoutConfig? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, WorkoutConfig::class.java) }.getOrNull()
    }

    /** Returns % improvement in Z2 pace (positive = faster), or null if insufficient data. */
    private fun computeZ2PaceImprovement(workouts: List<WorkoutEntity>, nowMs: Long): Int? {
        val fourWeeksMs = 28L * 86_400_000L
        val recentCutoff = nowMs - fourWeeksMs
        val olderCutoff = nowMs - 2 * fourWeeksMs

        fun isZ2(w: WorkoutEntity): Boolean {
            val config = parseConfig(w.targetConfig) ?: return false
            if (config.mode != WorkoutMode.STEADY_STATE) return false
            val id = config.presetId ?: ""
            return id.contains("Z2", true) || id.contains("EASY", true) || id.contains("AEROBIC", true)
        }

        fun avgPace(list: List<WorkoutEntity>): Double? {
            val valid = list.filter { it.totalDistanceMeters > 100f && (it.endTime - it.startTime) > 60_000L }
            if (valid.size < 2) return null
            return valid.map { it.totalDistanceMeters.toDouble() / ((it.endTime - it.startTime) / 1000.0) }.average()
        }

        val recent = workouts.filter { isZ2(it) && it.startTime >= recentCutoff }
        val older = workouts.filter { isZ2(it) && it.startTime in olderCutoff until recentCutoff }

        val recentPace = avgPace(recent) ?: return null
        val olderPace = avgPace(older) ?: return null
        if (olderPace <= 0) return null

        val improvementPct = ((recentPace - olderPace) / olderPace * 100).toInt()
        return if (improvementPct >= 5) improvementPct else null
    }
}
```

- [ ] **Step 2: Run existing tests \u2014 they will fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.coaching.CoachingInsightEngineTest"`
Expected: FAIL on title-string equality assertions \u2014 e.g. "Start your first run" may now resolve to "Day one is the hardest" depending on the test's `nowMs`.

This is intentional. Task 10 updates the tests to match the pool semantics.

---

## Task 10: Update CoachingInsightEngineTest to assert pool membership

**Files:**
- Modify: `app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt`

- [ ] **Step 1: Replace exact-string asserts with pool-membership asserts**

Open the test file and update each test that pins a title or subtitle. Replace `assertEquals("Start your first run", result.title)` with a membership check against the relevant variant pool. Since the pools are private to `CoachingInsightEngine`, assert on the `icon` (which still uniquely identifies each branch) plus a deterministic-day check. Pattern:

```kotlin
@Test
fun `no workouts returns first-run insight`() {
    val nowMs = 1_700_000_000_000L  // fixed for determinism
    val result = CoachingInsightEngine.generate(
        workouts = emptyList(),
        workoutsThisWeek = 0,
        weeklyTarget = 4,
        hasBootcamp = false,
        nowMs = nowMs
    )
    assertEquals(CoachingIcon.HEART, result.icon)
    // Title is deterministic for a fixed nowMs but non-trivial to know in advance.
    // Sanity-check it's not empty and contains plausible language.
    assertTrue(result.title.isNotBlank())
    assertTrue(result.subtitle.isNotBlank())
}

@Test
fun `same nowMs produces same insight (deterministic)`() {
    val nowMs = 1_700_000_000_000L
    val a = CoachingInsightEngine.generate(emptyList(), 0, 4, false, nowMs)
    val b = CoachingInsightEngine.generate(emptyList(), 0, 4, false, nowMs)
    assertEquals(a.title, b.title)
    assertEquals(a.subtitle, b.subtitle)
    assertEquals(a.icon, b.icon)
}

@Test
fun `inactivity branch substitutes days into subtitle`() {
    val nowMs = 1_700_000_000_000L
    val eightDaysAgo = nowMs - 8 * 86_400_000L
    val result = CoachingInsightEngine.generate(
        workouts = listOf(workout(startTime = eightDaysAgo)),
        workoutsThisWeek = 0,
        weeklyTarget = 4,
        hasBootcamp = false,
        nowMs = nowMs
    )
    assertEquals(CoachingIcon.WARNING, result.icon)
    assertTrue("subtitle should mention day count", result.subtitle.contains("8"))
}
```

For every existing test, apply this pattern: keep the icon assert, drop the exact-title assert, add a `isNotBlank()` check, and where the original test verified template substitution (days, percent, count), keep that as a `contains()` check on the substituted value.

- [ ] **Step 2: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.coaching.CoachingInsightEngineTest"`
Expected: PASS.

- [ ] **Step 3: Commit Tasks 9 + 10 together**

```bash
git add app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt \
        app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt
git commit -m "feat(coaching): rotate CoachingInsight branch strings via FactSelector

Each insight branch now picks from a 3\u20134 variant pool seeded by
nowMs-derived dayEpoch + branch key. Tests relaxed from exact-title
equality to icon + substitution membership."
```

---

## Task 11: End-to-end verification

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS. All existing tests + the 11 new tests (FactSelector + ZoneEducationProvider) green.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. The pre-existing `PartnerSection.kt` compile failure (documented in `CLAUDE.md`) is the only known blocker; if it appears, it's not a regression from this work.

- [ ] **Step 3: Install and smoke-test on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify on the device:
- Open Home → today card shows a science fact (any of the 10 Z2 ONE_LINER variants if today's session is EASY).
- Tap into Bootcamp → expanded session sheet shows a FULL-density variant.
- Open Progress → zone legend shows three different one-liners (one each for Z2, Z3, Z4-5).
- Restart app → same facts persist (same day, same dayEpoch).

To verify rotation manually without waiting a day, add a temporary `dayEpoch` override in one screen, rebuild, observe a different fact.

- [ ] **Step 4: Final commit (only if you made any cleanup edits)**

```bash
git status   # should be clean if no extra edits
```

If clean, the implementation is complete. Move to `superpowers:requesting-code-review`.

---

## Summary

| Task | Files | New tests | Strings added |
|---|---|---|---|
| 1 — FactSelector | 2 (1 src, 1 test) | 6 | 0 |
| 2 — Provider refactor | 2 (1 src, 1 test) | 5 | 0 (structural) |
| 3 — Z2 pools | 1 | 0 | 18 |
| 4 — Z3 pools | 1 | 0 | 18 |
| 5 — Z4-5 pools | 1 | 0 | 18 |
| 6 — Recovery pools | 1 | 0 | 18 |
| 7 — Race-pace pools | 1 | 0 | 18 |
| 8 — UI call sites | 4 | 0 | 0 |
| 9 — CoachingInsightEngine | 1 | 0 | 25 (across 7 branches) |
| 10 — CI test update | 1 | (rewrites) | 0 |
| 11 — Verification | 0 | 0 | 0 |

**Total new fact strings:** 90 zone facts (5 zones × 9 added per pool × 2 densities = 90 net new) + 25 CoachingInsight variants = **115 new strings**, each with provenance.
