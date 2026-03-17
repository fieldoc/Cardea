package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal

object FinishingTimeTierMapper {

    data class GoalTimeBrackets(
        val easyAboveMinutes: Int,
        val hardBelowMinutes: Int,
        val uiMin: Int,
        val uiMax: Int,
        val defaultMinutes: Int,
        val recommendedRunMinutes: Map<Int, Int>
    )

    data class TimeValidation(
        val canProceed: Boolean,
        val warningMessage: String?,
        val recommendedMinutes: Int,
        val absoluteMinimum: Int
    )

    private val brackets = mapOf(
        BootcampGoal.RACE_5K to GoalTimeBrackets(
            easyAboveMinutes = 30, hardBelowMinutes = 22,
            uiMin = 15, uiMax = 45, defaultMinutes = 28,
            recommendedRunMinutes = mapOf(0 to 25, 1 to 30, 2 to 35)
        ),
        BootcampGoal.RACE_10K to GoalTimeBrackets(
            easyAboveMinutes = 65, hardBelowMinutes = 50,
            uiMin = 35, uiMax = 90, defaultMinutes = 55,
            recommendedRunMinutes = mapOf(0 to 30, 1 to 35, 2 to 40)
        ),
        BootcampGoal.HALF_MARATHON to GoalTimeBrackets(
            easyAboveMinutes = 140, hardBelowMinutes = 110,
            uiMin = 80, uiMax = 180, defaultMinutes = 130,
            recommendedRunMinutes = mapOf(0 to 30, 1 to 40, 2 to 50)
        ),
        BootcampGoal.MARATHON to GoalTimeBrackets(
            easyAboveMinutes = 280, hardBelowMinutes = 220,
            uiMin = 160, uiMax = 360, defaultMinutes = 260,
            recommendedRunMinutes = mapOf(0 to 40, 1 to 50, 2 to 60)
        )
    )

    fun isRaceGoal(goal: BootcampGoal): Boolean = goal != BootcampGoal.CARDIO_HEALTH

    fun bracketsFor(goal: BootcampGoal): GoalTimeBrackets? = brackets[goal]

    fun tierFromFinishingTime(goal: BootcampGoal, minutes: Int): Int {
        val b = brackets[goal] ?: return 0
        return when {
            minutes > b.easyAboveMinutes -> 0
            minutes < b.hardBelowMinutes -> 2
            else -> 1
        }
    }

    fun recommendedRunMinutes(goal: BootcampGoal, tierIndex: Int): Int {
        return brackets[goal]?.recommendedRunMinutes?.get(tierIndex)
            ?: goal.suggestedMinMinutes
    }

    fun validateTimeCommitment(
        goal: BootcampGoal,
        tierIndex: Int,
        userMinutes: Int
    ): TimeValidation {
        val recommended = recommendedRunMinutes(goal, tierIndex)
        val absoluteMin = goal.neverPrescribeBelowMinutes
        return when {
            userMinutes < absoluteMin -> TimeValidation(
                canProceed = false,
                warningMessage = "$userMinutes min sessions aren't enough for ${goalDisplayName(goal)} training. Please increase to at least $absoluteMin min.",
                recommendedMinutes = recommended,
                absoluteMinimum = absoluteMin
            )
            userMinutes < recommended -> TimeValidation(
                canProceed = true,
                warningMessage = "Your target suggests $recommended min sessions. We'll make $userMinutes min work, but expect slower progress.",
                recommendedMinutes = recommended,
                absoluteMinimum = absoluteMin
            )
            else -> TimeValidation(
                canProceed = true,
                warningMessage = null,
                recommendedMinutes = recommended,
                absoluteMinimum = absoluteMin
            )
        }
    }

    fun formatTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun goalDisplayName(goal: BootcampGoal): String = when (goal) {
        BootcampGoal.RACE_5K -> "5K"
        BootcampGoal.RACE_10K -> "10K"
        BootcampGoal.HALF_MARATHON -> "Half Marathon"
        BootcampGoal.MARATHON -> "Marathon"
        BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    }
}
