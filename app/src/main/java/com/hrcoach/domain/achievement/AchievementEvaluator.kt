package com.hrcoach.domain.achievement

import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import javax.inject.Inject

class AchievementEvaluator @Inject constructor(
    private val achievementDao: AchievementDao
) {

    suspend fun evaluateDistance(totalKm: Double, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.DISTANCE_THRESHOLDS) {
            if (totalKm < threshold) break
            val milestone = "${threshold.toInt()}km"
            if (!achievementDao.hasAchievement(AchievementType.DISTANCE_MILESTONE.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.DISTANCE_MILESTONE.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateStreak(currentStreak: Int, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.STREAK_THRESHOLDS) {
            if (currentStreak < threshold) break
            val milestone = "${threshold}_sessions"
            if (!achievementDao.hasAchievement(AchievementType.STREAK_MILESTONE.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.STREAK_MILESTONE.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateWeeklyGoalStreak(consecutiveWeeks: Int, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.WEEKLY_GOAL_THRESHOLDS) {
            if (consecutiveWeeks < threshold) break
            val milestone = "${threshold}_weeks"
            if (!achievementDao.hasAchievement(AchievementType.WEEKLY_GOAL_STREAK.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.WEEKLY_GOAL_STREAK.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateTierGraduation(newTierIndex: Int, goal: String) {
        val milestone = "tier_${newTierIndex}_$goal"
        if (!achievementDao.hasAchievement(AchievementType.TIER_GRADUATION.name, milestone)) {
            achievementDao.insert(
                AchievementEntity(
                    type = AchievementType.TIER_GRADUATION.name,
                    milestone = milestone,
                    goal = goal,
                    tier = newTierIndex,
                    prestigeLevel = MilestoneDefinitions.tierGraduationPrestige(newTierIndex),
                    earnedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun evaluateBootcampGraduation(enrollmentId: Long, goal: String, tierIndex: Int) {
        val milestone = "graduated_${enrollmentId}"
        if (!achievementDao.hasAchievement(AchievementType.BOOTCAMP_GRADUATION.name, milestone)) {
            achievementDao.insert(
                AchievementEntity(
                    type = AchievementType.BOOTCAMP_GRADUATION.name,
                    milestone = milestone,
                    goal = goal,
                    tier = tierIndex,
                    prestigeLevel = MilestoneDefinitions.BOOTCAMP_GRADUATION_PRESTIGE,
                    earnedAtMs = System.currentTimeMillis()
                )
            )
        }
    }
}
