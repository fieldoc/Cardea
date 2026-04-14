package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import com.hrcoach.domain.preset.SessionPresetArray

object SessionSelector {

    fun weekSessions(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        targetMinutes: Int,
        tierIndex: Int = 1,
        tuningDirection: TuningDirection = TuningDirection.HOLD,
        weekInPhase: Int = 0,
        absoluteWeek: Int = 0,
        isRecoveryWeek: Boolean = false,
        lastTierChangeWeek: Int? = null
    ): List<PlannedSession> {
        val tuningFactor = when (tuningDirection) {
            TuningDirection.PUSH_HARDER -> 1.05f
            TuningDirection.EASE_BACK -> 0.90f
            TuningDirection.HOLD -> 1.0f
        }
        val effectiveMinutes = if (phase == TrainingPhase.TAPER) {
            (targetMinutes * 0.7f * tuningFactor).toInt()
        } else {
            (targetMinutes * tuningFactor).toInt()
        }
        val durations = DurationScaler.compute(runsPerWeek, effectiveMinutes)

        return when {
            phase == TrainingPhase.EVERGREEN -> evergreenWeek(
                goal, runsPerWeek, effectiveMinutes, durations, tierIndex, weekInPhase, absoluteWeek
            )
            isRecoveryWeek && tierIndex <= 1 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
            isRecoveryWeek && tierIndex >= 2 -> recoveryPeriodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
            tierIndex <= 0 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
            else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex, absoluteWeek, lastTierChangeWeek)
        }
    }

    private fun baseAerobicWeek(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        minutes: Int,
        durations: DurationScaler.WeekDurations
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.BASE
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)
        val easyCount = if (hasLong) runsPerWeek - 1 else runsPerWeek
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
        }
        if (hasLong) {
            sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
        }
        return sessions
    }

    private fun periodizedWeek(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        minutes: Int,
        durations: DurationScaler.WeekDurations,
        tierIndex: Int,
        absoluteWeek: Int = 0,
        lastTierChangeWeek: Int? = null
    ): List<PlannedSession> {
        // Transition window: the promotion week itself and the following week (2-week window),
        // use the intro tempo preset to soften the 68%→84% HR jump.
        val inTransitionWindow = lastTierChangeWeek != null &&
            (absoluteWeek - lastTierChangeWeek) <= 1
        val sessions = mutableListOf<PlannedSession>()
        val includeStrides = (phase == TrainingPhase.BUILD && tierIndex >= 2 && runsPerWeek >= 3) ||
            (phase == TrainingPhase.BASE && tierIndex >= 1 && goal != BootcampGoal.CARDIO_HEALTH)

        val qualitySessions = when (phase) {
            TrainingPhase.BASE -> 0
            TrainingPhase.BUILD -> 1
            TrainingPhase.PEAK -> if (tierIndex >= 2) 2 else 1
            TrainingPhase.TAPER -> 1
            TrainingPhase.EVERGREEN -> 1 // handled by evergreenWeek, but needed for exhaustiveness
        }

        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)
        // When strides are included, they provide easy-effort recovery between efforts,
        // so they can substitute for a dedicated easy run at low run counts (e.g., 3 runs/week).
        val easyFloor = if (includeStrides) 0 else 1
        val easyCount = (
            runsPerWeek -
                qualitySessions -
                (if (hasLong) 1 else 0) -
                (if (includeStrides) 1 else 0)
            ).coerceAtLeast(easyFloor)

        // Easy runs
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
        }

        // Quality sessions based on phase
        when (phase) {
            TrainingPhase.BASE -> {
                if (includeStrides) {
                    sessions.add(
                        PlannedSession(
                            type = SessionType.STRIDES,
                            minutes = durations.easyMinutes,
                            presetId = "zone2_with_strides"
                        )
                    )
                }
            }
            TrainingPhase.BUILD -> {
                val tempoPreset = if (inTransitionWindow) "aerobic_tempo_intro" else "aerobic_tempo"
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, tempoPreset))
                if (includeStrides) {
                    val stridesPreset = if (tierIndex >= 3) {
                        SessionPresetArray.stridesTier3().presetAt(0)
                    } else {
                        SessionPresetArray.stridesTier2().presetAt(0)
                    }
                    sessions.add(
                        PlannedSession(
                            type = SessionType.STRIDES,
                            minutes = stridesPreset.durationMinutes,
                            presetId = stridesPreset.presetId
                        )
                    )
                }
            }
            TrainingPhase.PEAK -> {
                when {
                    tierIndex >= 2 -> {
                        // Goal determines primary quality type
                        val intervalPreset = if (absoluteWeek % 2 == 0) "norwegian_4x4" else "hill_repeats"
                        when (goal) {
                            BootcampGoal.RACE_5K -> {
                                sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, intervalPreset))
                                if (qualitySessions >= 2) {
                                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                                }
                            }
                            BootcampGoal.RACE_10K -> {
                                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                                if (qualitySessions >= 2) {
                                    sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, intervalPreset))
                                }
                            }
                            BootcampGoal.HALF_MARATHON, BootcampGoal.MARATHON -> {
                                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                                if (qualitySessions >= 2) {
                                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                                }
                            }
                            BootcampGoal.CARDIO_HEALTH -> {
                                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                            }
                        }
                    }
                    else -> {
                        // tierIndex 1: aerobic tempo for all goals
                        sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                    }
                }
            }
            TrainingPhase.TAPER -> {
                val taperPreset = if (inTransitionWindow) "aerobic_tempo_intro" else "aerobic_tempo"
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, taperPreset))
            }
            TrainingPhase.EVERGREEN -> {
                // periodizedWeek is not called for EVERGREEN (handled by evergreenWeek above)
                sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
            }
        }

        // Long run
        if (hasLong) {
            val isRaceSim = phase == TrainingPhase.PEAK && tierIndex >= 2 &&
                goal != BootcampGoal.CARDIO_HEALTH
            val longType = if (isRaceSim) SessionType.RACE_SIM else SessionType.LONG
            val longPreset = if (isRaceSim) raceSimPresetFor(goal) else "zone2_base"
            sessions.add(PlannedSession(longType, longMinutes, longPreset))
        }

        return sessions
    }

    private fun evergreenWeek(
        goal: BootcampGoal,
        runsPerWeek: Int,
        minutes: Int,
        durations: DurationScaler.WeekDurations,
        tierIndex: Int,
        weekInPhase: Int,
        absoluteWeek: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val microWeek = weekInPhase % 4 // 0=Tempo, 1=Strides, 2=Interval/Tempo, 3=Recovery

        // Tier 0 always gets base aerobic only
        if (tierIndex <= 0 || microWeek == 3) {
            // Recovery week (D) or tier 0: all easy + optional long
            val hasLong = runsPerWeek >= 3
            val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)
            val easyCount = if (hasLong) runsPerWeek - 1 else runsPerWeek
            repeat(easyCount) {
                sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
            }
            if (hasLong) {
                sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
            }
            return sessions
        }

        val hasLong = runsPerWeek >= 3
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)

        // Quality session for this micro-week
        when (microWeek) {
            0 -> {
                // Week A: Tempo
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
            }
            1 -> {
                // Week B: Strides
                sessions.add(PlannedSession(SessionType.STRIDES, durations.easyMinutes, "strides_20s"))
            }
            2 -> {
                // Week C: Interval (tier 2+) or Tempo (tier 1)
                if (tierIndex >= 2) {
                    val intervalPreset = if (absoluteWeek % 2 == 0) "hill_repeats" else "hiit_30_30"
                    sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, intervalPreset))
                } else {
                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                }
            }
        }

        // Long run
        if (hasLong) {
            sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
        }

        // Fill remaining with easy
        val easyCount = (runsPerWeek - sessions.size).coerceAtLeast(0)
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
        }

        return sessions
    }

    private fun recoveryPeriodizedWeek(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        minutes: Int,
        durations: DurationScaler.WeekDurations,
        tierIndex: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)

        // Downgraded quality: interval→aerobic tempo, tempo→strides
        when (phase) {
            TrainingPhase.BUILD, TrainingPhase.TAPER -> {
                sessions.add(PlannedSession(SessionType.STRIDES, durations.easyMinutes, "strides_20s"))
            }
            TrainingPhase.PEAK -> {
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
            }
            else -> { /* BASE has no quality — nothing to downgrade */ }
        }

        // Long run
        if (hasLong) {
            sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
        }

        // Fill remaining with easy
        val easyCount = (runsPerWeek - sessions.size).coerceAtLeast(1)
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
        }

        return sessions
    }

    private fun raceSimPresetFor(goal: BootcampGoal): String = when (goal) {
        BootcampGoal.RACE_5K -> "race_sim_5k"
        BootcampGoal.RACE_10K -> "race_sim_10k"
        BootcampGoal.HALF_MARATHON -> "half_marathon_prep"
        BootcampGoal.MARATHON -> "marathon_prep"
        BootcampGoal.CARDIO_HEALTH -> "zone2_base"
    }
}
