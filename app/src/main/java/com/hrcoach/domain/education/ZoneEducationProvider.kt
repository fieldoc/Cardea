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
