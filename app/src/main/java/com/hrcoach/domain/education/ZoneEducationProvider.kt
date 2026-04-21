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
