package com.hrcoach.domain.education

import com.hrcoach.domain.bootcamp.SessionType
import kotlin.math.roundToInt

/**
 * Provides contextual heart-rate zone education at three density levels.
 *
 * Pure Kotlin — no Android dependencies. Content is hardcoded; personalization
 * is applied when the user's maxHR is known.
 */
object ZoneEducationProvider {

    /**
     * Returns educational content for a zone at the requested density.
     *
     * @param zoneId which training zone
     * @param density how much text the caller has room for
     * @param maxHr user's max heart rate (null if unknown — omits BPM ranges)
     * @param bufferBpm zone buffer in BPM (default 5, matches ZoneEngine)
     */
    fun getContent(
        zoneId: ZoneId,
        density: ContentDensity,
        maxHr: Int? = null,
        bufferBpm: Int = 5
    ): String = when (density) {
        ContentDensity.BADGE -> badge(zoneId)
        ContentDensity.ONE_LINER -> oneLiner(zoneId, maxHr, bufferBpm)
        ContentDensity.FULL -> full(zoneId, maxHr, bufferBpm)
    }

    /**
     * Maps a [SessionType] to its corresponding [ZoneId].
     */
    fun zoneForSessionType(sessionType: SessionType): ZoneId = when (sessionType) {
        SessionType.EASY, SessionType.LONG, SessionType.STRIDES -> ZoneId.ZONE_2
        SessionType.TEMPO -> ZoneId.ZONE_3
        SessionType.INTERVAL -> ZoneId.ZONE_4_5
        SessionType.RACE_SIM -> ZoneId.RACE_PACE
        SessionType.DISCOVERY, SessionType.CHECK_IN -> ZoneId.RECOVERY
    }

    /**
     * Convenience: get content directly from a session type string.
     */
    fun forSessionType(
        rawSessionType: String,
        density: ContentDensity,
        maxHr: Int? = null,
        bufferBpm: Int = 5
    ): String? {
        val type = runCatching { SessionType.valueOf(rawSessionType) }.getOrNull()
            ?: return null
        return getContent(zoneForSessionType(type), density, maxHr, bufferBpm)
    }

    // ── Badge (1-2 words) ────────────────────────────────────────────────

    private fun badge(zoneId: ZoneId): String = when (zoneId) {
        ZoneId.ZONE_2 -> "Aerobic Base"
        ZoneId.ZONE_3 -> "Threshold"
        ZoneId.ZONE_4_5 -> "VO\u2082max"
        ZoneId.RECOVERY -> "Calibration"
        ZoneId.RACE_PACE -> "Race Effort"
    }

    // ── One-liner (1 sentence) ───────────────────────────────────────────

    private fun oneLiner(zoneId: ZoneId, maxHr: Int?, bufferBpm: Int): String {
        val bpmSuffix = bpmRange(zoneId, maxHr, bufferBpm)?.let { " ($it)" } ?: ""
        return when (zoneId) {
            ZoneId.ZONE_2 ->
                "Increases your heart\u2019s stroke volume and builds capillary networks around muscle fibers, so the same pace costs less cardiac effort$bpmSuffix"
            ZoneId.ZONE_3 ->
                "Trains at the intensity where lactate production outpaces your muscles\u2019 ability to use it as fuel \u2014 pushing that ceiling higher lets you hold faster paces$bpmSuffix"
            ZoneId.ZONE_4_5 ->
                "Works your heart at near-peak cardiac output to raise your VO\u2082max ceiling, while recruiting fast-twitch fibers that stay dormant at easier paces$bpmSuffix"
            ZoneId.RECOVERY ->
                "Low-effort data collection that maps how your heart rate responds to pace \u2014 used to calibrate your personal zones"
            ZoneId.RACE_PACE ->
                "Rehearses the exact neuromuscular patterns and energy pacing you\u2019ll need on race day$bpmSuffix"
        }
    }

    // ── Full (2-4 sentences) ─────────────────────────────────────────────

    private fun full(zoneId: ZoneId, maxHr: Int?, bufferBpm: Int): String {
        val bpmLine = bpmRange(zoneId, maxHr, bufferBpm)
            ?.let { "\n\nYour range: $it" }
            ?: "\n\nRun a Discovery session to see your personal ranges."
        return when (zoneId) {
            ZoneId.ZONE_2 ->
                "At this intensity your heart adapts to eject more blood per beat \u2014 " +
                "what physiologists call increased stroke volume. Your muscles respond " +
                "by growing denser capillary networks, improving oxygen delivery at the " +
                "cellular level. The benefit comes from accumulated volume at this effort, " +
                "not a magic intensity window \u2014 which is why elite plans keep 75\u201380% " +
                "of weekly running here." + bpmLine

            ZoneId.ZONE_3 ->
                "Tempo effort sits near your lactate turnpoint \u2014 the intensity where " +
                "lactate production outpaces your muscles\u2019 ability to use it. " +
                "Lactate isn\u2019t waste; it\u2019s a fuel that gets shuttled between fibers " +
                "via monocarboxylate transporters (MCTs). Training here upregulates those " +
                "transporters, so the pace you can sustain before lactate overwhelms the " +
                "system shifts faster." + bpmLine

            ZoneId.ZONE_4_5 ->
                "Intervals at this intensity target your VO\u2082max \u2014 the ceiling on " +
                "how much oxygen your cardiovascular system can deliver and your muscles " +
                "can use. Your heart reaches near-maximal output, and your body recruits " +
                "fast-twitch (Type II) fibers that stay dormant at easier paces. With " +
                "training, those fibers shift toward a more fatigue-resistant profile, " +
                "improving your top-end speed and kick." + bpmLine

            ZoneId.RECOVERY ->
                "Discovery and check-in sessions collect heart rate data at controlled, " +
                "easy effort. At low intensity, the HR-to-pace relationship is most stable " +
                "and interpretable, giving Cardea clean data to identify your aerobic and " +
                "lactate thresholds without requiring an all-out effort. This calibration " +
                "personalizes every zone target in your program." + bpmLine

            ZoneId.RACE_PACE ->
                "Race simulation trains the specific neuromuscular coordination and energy " +
                "pacing you\u2019ll use on race day. At goal pace, your body practises " +
                "sparing glycogen early so reserves remain for the final kilometres \u2014 " +
                "a negative-split strategy that produces better finishing times. It also " +
                "builds race-day confidence in a controlled setting." + bpmLine
        }
    }

    // ── BPM range helper ─────────────────────────────────────────────────

    private fun bpmRange(zoneId: ZoneId, maxHr: Int?, bufferBpm: Int): String? {
        if (maxHr == null) return null
        val (lowPct, highPct) = zonePercentages(zoneId)
        val targetLow = (maxHr * lowPct).roundToInt()
        val targetHigh = (maxHr * highPct).roundToInt()
        return "${targetLow - bufferBpm}\u2013${targetHigh + bufferBpm} BPM"
    }

    /**
     * Approximate HRmax percentage ranges per zone.
     * These are for educational display only — actual workout targets
     * come from the bootcamp preset configuration.
     */
    private fun zonePercentages(zoneId: ZoneId): Pair<Float, Float> = when (zoneId) {
        ZoneId.ZONE_2 -> 0.60f to 0.70f
        ZoneId.ZONE_3 -> 0.75f to 0.85f
        ZoneId.ZONE_4_5 -> 0.85f to 0.95f
        ZoneId.RECOVERY -> 0.50f to 0.65f
        ZoneId.RACE_PACE -> 0.80f to 0.90f
    }
}
