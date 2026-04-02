package com.hrcoach.domain.preset

import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import kotlin.math.roundToInt

/** Karvonen / Heart Rate Reserve formula. */
internal fun karvonen(maxHr: Int, restHr: Int, pct: Float): Int =
    (restHr + (maxHr - restHr) * pct).roundToInt()

object PresetLibrary {

    val ALL: List<WorkoutPreset> = listOf(
        zone2Base(), zone2WithStrides(), aeroTempo(), lactateThreshold(),
        norwegian4x4(), hiit3030(), hillRepeats(),
        halfMarathonPrep(), marathonPrep()
    )

    private fun zone2Base() = WorkoutPreset(
        id = "zone2_base",
        name = "Zone 2 Base",
        subtitle = "Aerobic foundation",
        description = "45\u201375 min at 68% HR reserve. Builds mitochondrial density and fat oxidation.",
        category = PresetCategory.BASE_AEROBIC,
        durationLabel = "45\u201375 min",
        intensityLabel = "Easy",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.68f),
                bufferBpm = 5,
                presetId = "zone2_base"
            )
        }
    )

    private fun zone2WithStrides() = WorkoutPreset(
        id = "zone2_with_strides",
        name = "Easy + Strides",
        subtitle = "Aerobic base with pickups",
        description = "45\u201375 min easy run with 4\u20136 \u00d7 20-sec strides after 20 minutes.",
        category = PresetCategory.BASE_AEROBIC,
        durationLabel = "45\u201375 min",
        intensityLabel = "Easy",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.68f),
                bufferBpm = 5,
                presetId = "zone2_with_strides",
                sessionLabel = "Easy + Strides",
                guidanceTag = "strides"
            )
        }
    )

    private fun aeroTempo() = WorkoutPreset(
        id = "aerobic_tempo",
        name = "Aerobic Tempo",
        subtitle = "Comfortably hard",
        description = "30 min at 84% HR reserve. Raises lactate threshold and improves running economy.",
        category = PresetCategory.THRESHOLD,
        durationLabel = "30 min",
        intensityLabel = "Moderate",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.84f),
                bufferBpm = 4,
                presetId = "aerobic_tempo"
            )
        }
    )

    private fun lactateThreshold() = WorkoutPreset(
        id = "lactate_threshold",
        name = "Lactate Threshold",
        subtitle = "Threshold effort",
        description = "25 min at 90% HR reserve. Targets the lactate threshold directly.",
        category = PresetCategory.THRESHOLD,
        durationLabel = "25 min",
        intensityLabel = "Hard",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.90f),
                bufferBpm = 3,
                presetId = "lactate_threshold"
            )
        }
    )

    private fun norwegian4x4() = WorkoutPreset(
        id = "norwegian_4x4",
        name = "Norwegian 4x4",
        subtitle = "VO2max booster",
        description = "4 x 4-min intervals at 92% HR reserve with 3-min active recovery.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~35 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr, restHr ->
            val intervalHr = karvonen(maxHr, restHr, 0.92f)
            val recoveryHr = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 600, targetHr = recoveryHr, label = "Warm-up"))
                repeat(4) { i ->
                    add(HrSegment(durationSeconds = 240, targetHr = intervalHr, label = "Interval ${i+1} of 4"))
                    if (i < 3) add(HrSegment(durationSeconds = 180, targetHr = recoveryHr, label = "Recovery ${i+1}"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "norwegian_4x4")
        }
    )

    private fun hiit3030() = WorkoutPreset(
        id = "hiit_30_30",
        name = "HIIT 30/30",
        subtitle = "Sprint intervals",
        description = "10 x 30-sec sprints at 92% HR reserve with 30-sec recoveries.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~20 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr, restHr ->
            val sprintHr   = karvonen(maxHr, restHr, 0.92f)
            val recoveryHr = karvonen(maxHr, restHr, 0.62f)
            val warmupHr   = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 300, targetHr = warmupHr, label = "Warm-up"))
                repeat(10) { i ->
                    add(HrSegment(durationSeconds = 30, targetHr = sprintHr, label = "Sprint ${i+1} of 10"))
                    add(HrSegment(durationSeconds = 30, targetHr = recoveryHr, label = "Recover"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hiit_30_30")
        }
    )

    private fun hillRepeats() = WorkoutPreset(
        id = "hill_repeats",
        name = "Hill Repeats",
        subtitle = "Strength + power",
        description = "6 x 90-sec hill climbs at 87% HR reserve with 90-sec flat recovery.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~25 min",
        intensityLabel = "High",
        buildConfig = { maxHr, restHr ->
            val climbHr    = karvonen(maxHr, restHr, 0.87f)
            val recoveryHr = karvonen(maxHr, restHr, 0.63f)
            val warmupHr   = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 300, targetHr = warmupHr, label = "Warm-up"))
                repeat(6) { i ->
                    add(HrSegment(durationSeconds = 90, targetHr = climbHr, label = "Hill ${i+1} of 6"))
                    add(HrSegment(durationSeconds = 90, targetHr = recoveryHr, label = "Recover"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hill_repeats")
        }
    )

    private fun halfMarathonPrep() = WorkoutPreset(
        id = "half_marathon_prep",
        name = "Half Marathon Prep",
        subtitle = "Race simulation",
        description = "3 progressive HR zones across 21.1 km.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "21.1 km",
        intensityLabel = "Moderate\u2013Hard",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 3000f,  targetHr = karvonen(maxHr, restHr, 0.72f), label = "Easy Start"),
                    HrSegment(distanceMeters = 14000f, targetHr = karvonen(maxHr, restHr, 0.80f), label = "Race Pace"),
                    HrSegment(distanceMeters = 21100f, targetHr = karvonen(maxHr, restHr, 0.85f), label = "Strong Finish")
                ),
                bufferBpm = 4,
                presetId = "half_marathon_prep"
            )
        }
    )

    private fun marathonPrep() = WorkoutPreset(
        id = "marathon_prep",
        name = "Marathon Prep",
        subtitle = "Negative-split strategy",
        description = "Negative-split strategy across 42.2 km.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "42.2 km",
        intensityLabel = "Moderate",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 10000f, targetHr = karvonen(maxHr, restHr, 0.70f), label = "Easy Start"),
                    HrSegment(distanceMeters = 32000f, targetHr = karvonen(maxHr, restHr, 0.75f), label = "Marathon Pace"),
                    HrSegment(distanceMeters = 42200f, targetHr = karvonen(maxHr, restHr, 0.78f), label = "Final Push")
                ),
                bufferBpm = 4,
                presetId = "marathon_prep"
            )
        }
    )
}
