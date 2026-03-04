package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics

enum class FitnessLevel {
    UNKNOWN, BEGINNER, INTERMEDIATE, ADVANCED
}

object FitnessEvaluator {

    private const val MIN_SESSIONS = 3

    fun hasEnoughData(profile: AdaptiveProfile): Boolean =
        profile.totalSessions >= MIN_SESSIONS

    fun assess(
        profile: AdaptiveProfile,
        recentMetrics: List<WorkoutAdaptiveMetrics>
    ): FitnessLevel {
        if (profile.totalSessions < MIN_SESSIONS) return FitnessLevel.UNKNOWN

        val avgEf = recentMetrics.mapNotNull { it.efficiencyFactor }.average().toFloat()
        val avgDecoupling = recentMetrics.mapNotNull { it.aerobicDecoupling }.average().toFloat()

        if (avgEf.isNaN() || avgDecoupling.isNaN()) return FitnessLevel.UNKNOWN

        return when {
            avgEf >= 1.1f && avgDecoupling <= 5f -> FitnessLevel.ADVANCED
            avgEf >= 0.9f && avgDecoupling <= 8f -> FitnessLevel.INTERMEDIATE
            else -> FitnessLevel.BEGINNER
        }
    }
}
