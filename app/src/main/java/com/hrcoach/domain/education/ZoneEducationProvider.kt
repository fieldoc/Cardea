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
        // physiology — Bassett & Howley, VO2max ceiling
        "Raises your VO\u2082max ceiling and recruits fast-twitch fibers",
        // physiology — Helgerud, 4x4 minute intervals stimulus
        "4x4 minute intervals are among the strongest known stimuli for raising VO\u2082max",
        // physiology — Coyle, fast-twitch fatigue resistance
        "Type II fibers shift toward a more fatigue-resistant profile with regular interval work",
        // physiology — Bishop, buffering capacity adaptation
        "High-intensity work raises your muscles' ability to buffer hydrogen ions — less burn at race pace",
        // physiology — Joyner, cardiac output ceiling
        "Maximum cardiac output rises with VO\u2082max training; resting HR usually falls in parallel",
        // physiology — Tabata-style protocols, EPOC contribution
        "Interval afterburn (EPOC) keeps metabolism elevated for hours post-session",
        // coaching — internal-rationale, last-rep test
        "If the last rep felt easy, you went too soft — the final rep should hurt",
        // coaching — Daniels, R-pace pacing discipline
        "Even-paced reps trump first-rep heroics — negative-split your interval set",
        // coaching — internal-rationale, recovery between reps
        "Full HR recovery between reps preserves quality; cut it short and you're training Z3, not Z4-5",
        // inspiration — Hillman et al., HIIT and BDNF/IGF-1
        "Hard intervals trigger a sharp post-exercise spike in BDNF and IGF-1, hours-long mood lift"
    )
    private val RECOVERY_ONE_LINERS: List<String> = listOf(
        // physiology — internal-rationale, low-intensity HR-pace stability
        "Easy effort that maps your HR-to-pace response for zone calibration",
        // physiology — Joyner, controlled-effort signal-to-noise
        "Low-effort runs give the cleanest HR-to-pace data — less drift, less noise",
        // physiology — Friel, HRV-based recovery readiness
        "Easy runs preserve heart-rate variability — a marker of recovery and adaptive readiness",
        // physiology — Costill, parasympathetic recovery during easy work
        "Truly easy effort lets the parasympathetic nervous system rebound between hard sessions",
        // physiology — Maffetone, MAF and aerobic restoration
        "Easy aerobic work restores capillary circulation faster than full rest",
        // physiology — internal-rationale, glycogen replenishment alignment
        "Low-intensity sessions don't deplete glycogen, so adaptations from yesterday's hard work continue",
        // coaching — internal-rationale, calibration discipline
        "On a calibration run, hold the cap; don't 'just push for the next light'",
        // coaching — internal-rationale, recovery vs lazy
        "Recovery effort should feel almost too easy — if it feels like a 'normal run', it's too hard",
        // coaching — Friel, pre-run HRV check
        "If morning HRV is low, today's planned easy run is the right call — not the wrong one",
        // inspiration — Erickson et al., default mode network changes
        "Regular runners show distinct white-matter changes in the brain's default mode network within 6 months"
    )
    private val RACE_ONE_LINERS: List<String> = listOf(
        // physiology — Hawley, race-specific neuromuscular patterning
        "Rehearses the neuromuscular patterns and pacing you'll need on race day",
        // physiology — Costill, glycogen sparing and pacing
        "Even pacing spares glycogen for the closing kilometres — negative splits beat fades",
        // physiology — Daniels, race-specific economy
        "Specific work at goal pace improves running economy at that pace, not just at any pace",
        // physiology — Joyner, lactate steady state for the race distance
        "Race-pace work calibrates the lactate steady state you'll be holding for the duration",
        // physiology — Brooks, fuel mix at race intensity
        "At race effort your fat/carb fuel ratio settles into the mix you'll burn on the day",
        // physiology — Maffetone, heat-acclimation through race-pace efforts
        "Race-pace work teaches thermoregulation under sustained moderate-high load",
        // coaching — internal-rationale, pacing discipline
        "If the first kilometre at goal pace feels easy, hold the pace — don't push faster",
        // coaching — Daniels, race tactics rehearsal
        "Use race-pace runs to rehearse fueling timing, gear choice, and split discipline",
        // coaching — internal-rationale, mental rehearsal
        "Visualise the hard kilometres while you run them — the brain pattern transfers to race day",
        // inspiration — cortisol and approach motivation
        "Race rehearsal shifts race-day cortisol from threat-response into approach-motivation"
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
        // physiology — Bassett & Howley, VO2max as ceiling
        "Intervals at this intensity target your VO\u2082max — the ceiling on how " +
        "much oxygen your cardiovascular system can deliver and your muscles can use. " +
        "Your heart reaches near-maximal output, and your body recruits fast-twitch " +
        "(Type II) fibers that stay dormant at easier paces. With training, those " +
        "fibers shift toward a more fatigue-resistant profile, improving your top-end " +
        "speed and kick.",

        // physiology — Helgerud 2007, 4x4 protocol
        "4x4 minute intervals at 90–95% HRmax with 3 minute recoveries are among " +
        "the strongest known stimuli for raising VO\u2082max. Trained subjects in " +
        "controlled studies have raised VO\u2082max by 5–10% in 8 weeks on this " +
        "protocol alone. The mechanism is a combination of stroke volume increase and " +
        "improved oxygen extraction at the muscle.",

        // physiology — Coyle, fast-twitch fatigue resistance
        "Fast-twitch fibers come in two flavours: Type IIa (somewhat fatigue resistant) " +
        "and Type IIx (powerful but fatigue quickly). Regular interval training shifts " +
        "Type IIx toward Type IIa, giving you more fast-twitch capacity that can " +
        "actually sustain race-pace efforts. The shift is reversible — detrain for " +
        "a few weeks and IIa fibers drift back toward IIx.",

        // physiology — Bishop 2008, buffering capacity
        "Hard repeated efforts raise your muscles' buffering capacity — their " +
        "ability to neutralise the hydrogen ions that accompany lactate production. " +
        "Hydrogen ions, not lactate itself, cause the burning sensation and acid-driven " +
        "fatigue. Better buffering means you can tolerate higher metabolic load before " +
        "form breaks down.",

        // physiology — Joyner, max cardiac output
        "VO\u2082max is the product of maximum cardiac output and maximum oxygen " +
        "extraction at the muscle. Interval training drives both: your heart's stroke " +
        "volume rises (more blood per beat at max HR), and your muscle capillary " +
        "density improves (more surface area for oxygen exchange). Resting HR typically " +
        "falls 5–10 BPM over a focused VO\u2082max block.",

        // physiology — EPOC after high-intensity work
        "After a hard interval session, your body keeps metabolism elevated for hours " +
        "— the excess post-exercise oxygen consumption (EPOC) effect. Hard intervals " +
        "produce a much larger and longer EPOC than steady-state work. This is one " +
        "reason a 30-minute interval session can match the total energy cost of a much " +
        "longer easy run.",

        // coaching — internal-rationale, last-rep test
        "The last interval should be the hardest, not the easiest. If you finish the " +
        "set feeling like you could have done two more reps at the same pace, you went " +
        "too soft on the early ones. The point of intervals is to push the system to " +
        "near-maximal stress — if you finish fresh, the stimulus didn't land.",

        // coaching — Daniels, R-pace discipline
        "Even-paced reps beat first-rep heroics. Going out too hard on rep 1 burns " +
        "buffering capacity and metabolic reserves you'll need for reps 4–6. Aim " +
        "for a slight negative split across the set: each rep at the same effort or a " +
        "touch faster than the one before. Consistent pace with rising HR is exactly " +
        "what you want.",

        // coaching — internal-rationale, recovery quality
        "Recovery quality between reps determines the workout's stimulus. If you cut " +
        "recovery short, HR doesn't return to its target band, the next rep starts " +
        "with a deficit, and the whole session drifts toward threshold work instead of " +
        "VO\u2082max work. Walk during recovery; jog only if HR drops fast and you feel " +
        "fully recovered.",

        // inspiration — Hillman, HIIT and post-exercise BDNF spike
        "High-intensity intervals trigger a sharp post-exercise spike in BDNF and " +
        "IGF-1 (insulin-like growth factor). The effect lasts several hours after you " +
        "finish — one reason hard sessions often produce a clearer, calmer afternoon " +
        "than easy ones. The same molecules that grow new neurons after a hard rep " +
        "are reshaping how your brain processes the rest of your day."
    )
    private val RECOVERY_FULL: List<String> = listOf(
        // physiology — calibration rationale
        "Discovery and check-in sessions collect heart rate data at controlled, easy " +
        "effort. At low intensity, the HR-to-pace relationship is most stable and " +
        "interpretable, giving Cardea clean data to identify your aerobic and lactate " +
        "thresholds without requiring an all-out effort. This calibration personalizes " +
        "every zone target in your program.",

        // physiology — Joyner, signal-to-noise at low effort
        "At low effort, the relationship between heart rate and pace is nearly linear " +
        "and free from the cardiac drift that contaminates harder efforts. This is why " +
        "Cardea uses controlled easy runs as the calibration substrate — the data " +
        "is cleaner, the underlying aerobic system shows up unmasked, and we can fit " +
        "your personal HR-pace curve without forcing a maximum effort.",

        // physiology — Friel, HRV and recovery
        "Easy running preserves heart-rate variability (HRV), a measure of how " +
        "responsively your autonomic nervous system can switch between sympathetic " +
        "(fight-or-flight) and parasympathetic (rest-and-digest) modes. High HRV " +
        "correlates with recovery readiness and adaptation. Stacking too many hard " +
        "days in a row crashes HRV; easy days bring it back up.",

        // physiology — Costill, parasympathetic rebound
        "After a hard session, your parasympathetic nervous system needs time to " +
        "reassert dominance and drive recovery. Truly easy effort — below the " +
        "first lactate threshold — lets that rebound happen. Push too hard on " +
        "an 'easy' day and you sit in sympathetic dominance, blunting the recovery " +
        "and the adaptation it enables.",

        // physiology — Maffetone, aerobic restoration
        "Light aerobic movement after a hard day restores capillary circulation in " +
        "the working muscles faster than complete rest. The increased blood flow " +
        "clears metabolic by-products and delivers nutrients for repair. This is why " +
        "active recovery often feels better the next morning than a full off day, " +
        "though both have their place in a balanced plan.",

        // physiology — internal-rationale, glycogen sparing
        "Low-intensity work runs primarily on fat for fuel, leaving muscle glycogen " +
        "stores largely intact. This matters because the adaptations from yesterday's " +
        "hard session — enzyme synthesis, mitochondrial growth — require " +
        "energy to complete. Burning down glycogen on a recovery day diverts resources " +
        "away from those repair processes.",

        // coaching — calibration discipline
        "On a calibration session the goal is data, not training stimulus. Hold the " +
        "HR cap even when the legs want to push, don't sprint for the next traffic " +
        "light, and don't try to keep up with anyone. Treat it like a measurement " +
        "appointment: the cleaner the inputs, the more accurate your personalised " +
        "zones and predictions for every session that follows.",

        // coaching — internal-rationale, recovery vs lazy
        "True recovery effort should feel almost embarrassingly easy — like " +
        "you're under-running your fitness. If a recovery run feels like a 'normal " +
        "run', it's too hard. The sign you got it right is waking up the next day " +
        "fresher than you would have without it. The temptation to push because you " +
        "feel good is the trap that breaks training cycles.",

        // coaching — Friel, HRV-guided training
        "If morning HRV is suppressed, today's planned easy run isn't a soft option " +
        "— it's the right call. Trying to convert it into a hard session because " +
        "you 'feel okay' typically produces a poor session and digs the recovery hole " +
        "deeper. Trust the data, do the easy work, get to tomorrow with capacity to " +
        "spare.",

        // inspiration — Erickson, brain white matter
        "MRI studies of regular runners show distinct white-matter changes in the " +
        "default mode network — the brain regions active during rest and " +
        "introspection — within 6 months of consistent training. Those changes " +
        "correlate with measurable reductions in rumination and depressive symptoms. " +
        "The 'runner's brain' isn't a metaphor; it's a structural rewiring you can " +
        "see on a scan."
    )
    private val RACE_FULL: List<String> = listOf(
        // physiology — Hawley, neuromuscular specificity
        "Race simulation trains the specific neuromuscular coordination and energy " +
        "pacing you'll use on race day. At goal pace, your body practises sparing " +
        "glycogen early so reserves remain for the final kilometres — a " +
        "negative-split strategy that produces better finishing times. It also " +
        "builds race-day confidence in a controlled setting.",

        // physiology — Costill, glycogen and pacing
        "Glycogen depletion is the single biggest physiological cause of race-day " +
        "fades. Going out too fast burns glycogen at a rate your aerobic system can't " +
        "sustain, and once stores tip below ~30% of capacity, pace falls off a cliff. " +
        "Race-pace rehearsals train you to hold even or slightly negative splits, " +
        "preserving glycogen for the final third where it actually counts.",

        // physiology — Daniels, specificity of running economy
        "Running economy — how much oxygen you consume to maintain a given pace " +
        "— improves most at the specific pace you train. A runner who trains " +
        "extensively at marathon pace will be more economical at marathon pace than " +
        "one with the same VO\u2082max who didn't. This is why race-specific work in " +
        "the final 6–8 weeks of a build is non-negotiable for goal performances.",

        // physiology — Joyner, lactate steady state at race effort
        "Each race distance has a characteristic lactate steady state — the highest " +
        "intensity at which lactate production matches clearance for the race duration. " +
        "Marathon pace sits well below threshold; 10K pace sits at threshold; 5K pace " +
        "sits above. Race-pace sessions calibrate the steady state you'll be holding " +
        "and let your body settle into its rhythm under that exact load.",

        // physiology — Brooks, fuel mix at race intensity
        "Your fat/carbohydrate fuel mix shifts as intensity rises. At marathon pace, " +
        "trained runners burn ~50/50 fat-carb; at half-marathon pace, more like 30/70; " +
        "at 10K pace, near-pure carbohydrate. Race-pace work trains the metabolic " +
        "machinery for the specific mix you'll use, including the enzymes that handle " +
        "the dominant fuel at that intensity.",

        // physiology — Maffetone, thermoregulation under sustained load
        "Race-pace efforts train heat dissipation under sustained moderate-high load. " +
        "Plasma volume expands further, sweat onset comes earlier and at lower core " +
        "temperatures, and the cardiovascular system handles the dual demand of muscle " +
        "blood flow and skin blood flow more efficiently. These adaptations don't " +
        "happen on easy days; they require the specific stress of race-pace work.",

        // coaching — internal-rationale, pacing discipline
        "If the first kilometre at goal race pace feels suspiciously easy, hold the " +
        "pace anyway. The sense of ease is a function of full glycogen, fresh legs, " +
        "and adrenaline — conditions that won't last. Pushing faster early because " +
        "it feels easy is the most common cause of late-race blowups. Pace is set in " +
        "the first kilometre; the result is decided in the last.",

        // coaching — Daniels, rehearsal beyond pace
        "Race-pace runs are the place to rehearse everything you'll do on race day: " +
        "the gear, the fuel-and-fluid timing, the warm-up routine, even the mental " +
        "checklist for handling discomfort. Every detail you nail in rehearsal is one " +
        "less variable on race day. Treat the run as a dress rehearsal, not just a " +
        "physical workout.",

        // coaching — internal-rationale, mental rehearsal
        "Use the hard kilometres of a race-pace session to mentally rehearse the hard " +
        "kilometres of the race itself. Visualise the course, the discomfort, the " +
        "cues you'll use to hold form. Sport-psychology research shows the brain " +
        "patterns you build in rehearsal transfer to race day, reducing the gap " +
        "between what you can do in training and what you actually deliver under " +
        "race pressure.",

        // inspiration — race-rehearsal and dopamine pathway
        "Race rehearsal also rehearses the dopamine pathway. Visualising and physically " +
        "practising the goal effort shifts race-day arousal from threat-response into " +
        "approach-motivation — measurable in pre-race cortisol patterns. Athletes " +
        "who consistently rehearse race conditions report less pre-race anxiety and " +
        "more usable adrenaline; the line between 'nervous' and 'ready' is largely " +
        "decided by how familiar the effort feels."
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

    /**
     * Numeric BPM range for a session type (e.g. "168\u2013176"), no unit suffix.
     * Returns null when maxHr is unknown. UI surface adds the "bpm" label itself.
     */
    fun targetHrRange(
        rawSessionType: String,
        maxHr: Int?,
        restHr: Int?,
        bufferBpm: Int = 5
    ): String? {
        if (maxHr == null) return null
        val type = runCatching { SessionType.valueOf(rawSessionType) }.getOrNull() ?: return null
        val zoneId = zoneForSessionType(type)
        val (lowPct, highPct) = zonePercentages(zoneId)
        val rest = restHr ?: 0
        val targetLow = (rest + (maxHr - rest) * lowPct).roundToInt()
        val targetHigh = (rest + (maxHr - rest) * highPct).roundToInt()
        return "${targetLow - bufferBpm}\u2013${targetHigh + bufferBpm}"
    }

    private fun zonePercentages(zoneId: ZoneId): Pair<Float, Float> = when (zoneId) {
        ZoneId.ZONE_2 -> 0.60f to 0.70f
        ZoneId.ZONE_3 -> 0.75f to 0.85f
        ZoneId.ZONE_4_5 -> 0.85f to 0.95f
        ZoneId.RECOVERY -> 0.50f to 0.65f
        ZoneId.RACE_PACE -> 0.80f to 0.90f
    }
}
