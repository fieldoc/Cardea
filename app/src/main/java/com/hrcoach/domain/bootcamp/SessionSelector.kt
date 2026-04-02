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
        tuningDirection: TuningDirection = TuningDirection.HOLD
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
            tierIndex <= 0 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
            else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
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
        tierIndex: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val includeStrides = (phase == TrainingPhase.BUILD && tierIndex >= 2 && runsPerWeek >= 4) ||
            (phase == TrainingPhase.BASE && tierIndex >= 1 && goal != BootcampGoal.CARDIO_HEALTH)

        val qualitySessions = when (phase) {
            TrainingPhase.BASE -> 0
            TrainingPhase.BUILD -> 1
            TrainingPhase.PEAK -> if (tierIndex >= 2) 2 else 1
            TrainingPhase.TAPER -> 1
        }

        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)
        val easyCount = (
            runsPerWeek -
                qualitySessions -
                (if (hasLong) 1 else 0) -
                (if (includeStrides) 1 else 0)
            ).coerceAtLeast(1)

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
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
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
                        when (goal) {
                            BootcampGoal.RACE_5K -> {
                                sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                                if (qualitySessions >= 2) {
                                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                                }
                            }
                            BootcampGoal.RACE_10K -> {
                                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                                if (qualitySessions >= 2) {
                                    sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
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
                sessions.add(PlannedSession(SessionType.TEMPO, (durations.tempoMinutes * 0.8f).toInt(), "aerobic_tempo"))
            }
        }

        // Long run
        if (hasLong) {
            val isRaceSim = phase == TrainingPhase.PEAK && tierIndex >= 2 &&
                goal != BootcampGoal.CARDIO_HEALTH
            val longType = if (isRaceSim) SessionType.RACE_SIM else SessionType.LONG
            val longPreset = if (isRaceSim) null else "zone2_base"
            sessions.add(PlannedSession(longType, longMinutes, longPreset))
        }

        return sessions
    }
}
