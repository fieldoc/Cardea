package com.hrcoach.domain.achievement

object MilestoneDefinitions {

    val DISTANCE_THRESHOLDS: List<Pair<Double, Int>> = listOf(
        50.0 to 1, 100.0 to 1, 250.0 to 2, 500.0 to 2, 1000.0 to 3, 2500.0 to 3
    )

    val STREAK_THRESHOLDS: List<Pair<Int, Int>> = listOf(
        5 to 1, 10 to 1, 20 to 2, 50 to 2, 100 to 3
    )

    val WEEKLY_GOAL_THRESHOLDS: List<Pair<Int, Int>> = listOf(
        4 to 1, 8 to 2, 12 to 2, 24 to 3
    )

    const val BOOTCAMP_GRADUATION_PRESTIGE = 3

    fun tierGraduationPrestige(newTierIndex: Int): Int = when (newTierIndex) {
        1 -> 2
        2 -> 3
        else -> 1
    }
}
