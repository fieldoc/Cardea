package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.google.gson.Gson

object CoachingInsightEngine {

    private val gson = Gson()

    fun generate(
        workouts: List<WorkoutEntity>,
        workoutsThisWeek: Int,
        weeklyTarget: Int,
        hasBootcamp: Boolean,
        nowMs: Long
    ): CoachingInsight {
        // Priority 1: No workouts ever
        if (workouts.isEmpty()) {
            return CoachingInsight(
                title = "Start your first run",
                subtitle = "Connect your HR monitor and hit the trail",
                icon = CoachingIcon.HEART
            )
        }

        val lastWorkoutMs = workouts.first().endTime
        val daysSinceLastRun = ((nowMs - lastWorkoutMs) / 86_400_000L).toInt()

        // Priority 2: Inactivity
        if (daysSinceLastRun >= 7) {
            return CoachingInsight(
                title = "Time to get moving",
                subtitle = "It's been $daysSinceLastRun days since your last run",
                icon = CoachingIcon.WARNING
            )
        }

        // Priority 3: Consecutive hard sessions
        val recentTypes = workouts.take(5).map { classifySession(it) }
        val consecutiveHard = recentTypes.takeWhile { it == SessionType.HARD }.size
        if (consecutiveHard >= 3) {
            return CoachingInsight(
                title = "Consider an easy day",
                subtitle = "$consecutiveHard hard sessions in a row — an easy run helps recovery",
                icon = CoachingIcon.HEART
            )
        }

        // Priority 4: Z2 pace improvement
        val z2Improvement = computeZ2PaceImprovement(workouts, nowMs)
        if (z2Improvement != null && z2Improvement >= 5) {
            return CoachingInsight(
                title = "Z2 pace improved ${z2Improvement}%",
                subtitle = "Your aerobic base is growing — keep it up",
                icon = CoachingIcon.CHART_UP
            )
        }

        // Priority 5: Weekly goal met
        if (workoutsThisWeek >= weeklyTarget && weeklyTarget > 0) {
            return CoachingInsight(
                title = "Weekly goal reached!",
                subtitle = "$workoutsThisWeek runs this week — nice consistency",
                icon = CoachingIcon.TROPHY
            )
        }

        // Priority 6: Bootcamp behind schedule (past Thursday = day 4+)
        if (hasBootcamp && weeklyTarget > 0) {
            val dayOfWeek = java.time.Instant.ofEpochMilli(nowMs)
                .atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value
            val halfDone = workoutsThisWeek.toFloat() / weeklyTarget < 0.5f
            if (dayOfWeek >= 4 && halfDone) {
                val remaining = weeklyTarget - workoutsThisWeek
                return CoachingInsight(
                    title = "Pick up the pace this week",
                    subtitle = "$workoutsThisWeek/$weeklyTarget sessions done — $remaining left to stay on track",
                    icon = CoachingIcon.WARNING
                )
            }
        }

        // Priority 7: Default
        return CoachingInsight(
            title = "Consistency is key",
            subtitle = "Regular training builds a stronger aerobic base",
            icon = CoachingIcon.LIGHTBULB
        )
    }

    private enum class SessionType { HARD, EASY, UNKNOWN }

    private fun classifySession(workout: WorkoutEntity): SessionType {
        val config = parseConfig(workout.targetConfig) ?: return SessionType.UNKNOWN
        if (config.mode == WorkoutMode.FREE_RUN) return SessionType.UNKNOWN
        val presetId = config.presetId ?: ""
        return when {
            presetId.contains("Z4", ignoreCase = true) ||
            presetId.contains("TEMPO", ignoreCase = true) ||
            presetId.contains("INTERVAL", ignoreCase = true) -> SessionType.HARD
            presetId.contains("Z2", ignoreCase = true) ||
            presetId.contains("EASY", ignoreCase = true) ||
            presetId.contains("AEROBIC", ignoreCase = true) -> SessionType.EASY
            else -> SessionType.UNKNOWN
        }
    }

    private fun parseConfig(json: String?): WorkoutConfig? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, WorkoutConfig::class.java) }.getOrNull()
    }

    /** Returns % improvement in Z2 pace (positive = faster), or null if insufficient data. */
    private fun computeZ2PaceImprovement(workouts: List<WorkoutEntity>, nowMs: Long): Int? {
        val fourWeeksMs = 28L * 86_400_000L
        val recentCutoff = nowMs - fourWeeksMs
        val olderCutoff = nowMs - 2 * fourWeeksMs

        fun isZ2(w: WorkoutEntity): Boolean {
            val config = parseConfig(w.targetConfig) ?: return false
            if (config.mode != WorkoutMode.STEADY_STATE) return false
            val id = config.presetId ?: ""
            return id.contains("Z2", true) || id.contains("EASY", true) || id.contains("AEROBIC", true)
        }

        fun avgPace(list: List<WorkoutEntity>): Double? {
            val valid = list.filter { it.totalDistanceMeters > 100f && (it.endTime - it.startTime) > 60_000L }
            if (valid.size < 2) return null
            return valid.map { it.totalDistanceMeters.toDouble() / ((it.endTime - it.startTime) / 1000.0) }.average()
        }

        val recent = workouts.filter { isZ2(it) && it.startTime >= recentCutoff }
        val older = workouts.filter { isZ2(it) && it.startTime in olderCutoff until recentCutoff }

        val recentPace = avgPace(recent) ?: return null
        val olderPace = avgPace(older) ?: return null
        if (olderPace <= 0) return null

        val improvementPct = ((recentPace - olderPace) / olderPace * 100).toInt()
        return if (improvementPct >= 5) improvementPct else null
    }
}
