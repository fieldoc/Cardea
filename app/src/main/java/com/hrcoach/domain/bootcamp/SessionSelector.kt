package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import com.hrcoach.domain.preset.SessionPresetArray

object SessionSelector {

    fun weekSessions(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        targetMinutes: Int,
        tierIndex: Int = 1
    ): List<PlannedSession> {
        val effectiveTier = (goal.tier + tierIndex - 1).coerceIn(1, 4)
        val effectiveMinutes = if (phase == TrainingPhase.TAPER) {
            (targetMinutes * 0.7f).toInt()
        } else {
            targetMinutes
        }
        val durations = DurationScaler.compute(runsPerWeek, effectiveMinutes)
        val longMinutes = durations.longMinutes

        return when {
            effectiveTier <= 1 -> baseAerobicWeek(phase, runsPerWeek, effectiveMinutes, longMinutes, durations)
            else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, longMinutes, durations, effectiveTier)
        }
    }

    private fun baseAerobicWeek(
        phase: TrainingPhase,
        runsPerWeek: Int,
        minutes: Int,
        longMinutes: Int,
        durations: DurationScaler.WeekDurations
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.BASE
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
        longMinutes: Int,
        durations: DurationScaler.WeekDurations,
        effectiveTier: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val includeStrides = phase == TrainingPhase.BUILD && effectiveTier >= 2 && runsPerWeek >= 4

        val qualitySessions = when (phase) {
            TrainingPhase.BASE -> 0
            TrainingPhase.BUILD -> 1
            TrainingPhase.PEAK -> if (effectiveTier >= 3) 2 else 1
            TrainingPhase.TAPER -> 1
        }

        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
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
            TrainingPhase.BASE -> {} // No quality sessions
            TrainingPhase.BUILD -> {
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                if (includeStrides) {
                    val stridesPreset = if (effectiveTier >= 3) {
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
                if (effectiveTier >= 4) {
                    sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                    if (qualitySessions >= 2) {
                        sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                    }
                } else if (effectiveTier >= 3) {
                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                    if (qualitySessions >= 2) {
                        sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                    }
                } else if (goal == BootcampGoal.RACE_5K) {
                    sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                } else if (goal == BootcampGoal.RACE_10K) {
                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                } else {
                    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                }
            }
            TrainingPhase.TAPER -> {
                sessions.add(PlannedSession(SessionType.TEMPO, (durations.tempoMinutes * 0.8f).toInt(), "aerobic_tempo"))
            }
        }

        // Long run
        if (hasLong) {
            val longPreset = if (phase == TrainingPhase.PEAK && effectiveTier >= 3) null else "zone2_base"
            val longType = if (phase == TrainingPhase.PEAK && effectiveTier >= 3) SessionType.RACE_SIM else SessionType.LONG
            sessions.add(PlannedSession(longType, longMinutes, longPreset))
        }

        return sessions
    }
}
