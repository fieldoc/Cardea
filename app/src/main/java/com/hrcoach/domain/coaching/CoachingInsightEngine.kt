package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.education.FactSelector
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.google.gson.Gson
import java.time.Instant
import java.time.ZoneId

object CoachingInsightEngine {

    private val gson = Gson()

    fun generate(
        workouts: List<WorkoutEntity>,
        workoutsThisWeek: Int,
        weeklyTarget: Int,
        hasBootcamp: Boolean,
        nowMs: Long
    ): CoachingInsight {
        val dayEpoch = Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()

        // Priority 1: No workouts ever
        if (workouts.isEmpty()) {
            return pickInsight(FIRST_RUN_VARIANTS, "INSIGHT_FIRST_RUN", dayEpoch, CoachingIcon.HEART)
        }

        val lastWorkoutMs = workouts.first().endTime
        val daysSinceLastRun = ((nowMs - lastWorkoutMs) / 86_400_000L).toInt()

        // Priority 2: Inactivity
        if (daysSinceLastRun >= 7) {
            return pickInsight(
                INACTIVITY_VARIANTS, "INSIGHT_INACTIVITY", dayEpoch, CoachingIcon.WARNING,
                replacements = mapOf("\$days" to daysSinceLastRun.toString())
            )
        }

        // Priority 3: Consecutive hard sessions
        val recentTypes = workouts.take(5).map { classifySession(it) }
        val consecutiveHard = recentTypes.takeWhile { it == SessionType.HARD }.size
        if (consecutiveHard >= 3) {
            return pickInsight(
                CONSECUTIVE_HARD_VARIANTS, "INSIGHT_CONSECUTIVE_HARD", dayEpoch, CoachingIcon.HEART,
                replacements = mapOf("\$count" to consecutiveHard.toString())
            )
        }

        // Priority 4: Z2 pace improvement
        val z2Improvement = computeZ2PaceImprovement(workouts, nowMs)
        if (z2Improvement != null && z2Improvement >= 5) {
            return pickInsight(
                Z2_IMPROVEMENT_VARIANTS, "INSIGHT_Z2_IMPROVEMENT", dayEpoch, CoachingIcon.CHART_UP,
                replacements = mapOf("\$pct" to z2Improvement.toString())
            )
        }

        // Priority 5: Weekly goal met
        if (workoutsThisWeek >= weeklyTarget && weeklyTarget > 0) {
            return pickInsight(
                WEEKLY_GOAL_VARIANTS, "INSIGHT_WEEKLY_GOAL", dayEpoch, CoachingIcon.TROPHY,
                replacements = mapOf("\$count" to workoutsThisWeek.toString())
            )
        }

        // Priority 6: Bootcamp behind schedule
        if (hasBootcamp && weeklyTarget > 0) {
            val dayOfWeek = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault()).dayOfWeek.value
            val halfDone = workoutsThisWeek.toFloat() / weeklyTarget < 0.5f
            if (dayOfWeek >= 4 && halfDone) {
                val remaining = weeklyTarget - workoutsThisWeek
                return pickInsight(
                    BEHIND_SCHEDULE_VARIANTS, "INSIGHT_BEHIND", dayEpoch, CoachingIcon.WARNING,
                    replacements = mapOf(
                        "\$done" to workoutsThisWeek.toString(),
                        "\$target" to weeklyTarget.toString(),
                        "\$remaining" to remaining.toString()
                    )
                )
            }
        }

        // Priority 7: Default
        return pickInsight(DEFAULT_VARIANTS, "INSIGHT_DEFAULT", dayEpoch, CoachingIcon.LIGHTBULB)
    }

    private data class Variant(val title: String, val subtitle: String)

    private fun pickInsight(
        pool: List<Variant>,
        seedKey: String,
        dayEpoch: Long,
        icon: CoachingIcon,
        replacements: Map<String, String> = emptyMap()
    ): CoachingInsight {
        val v = pool[FactSelector.selectIndex(pool.size, seedKey, dayEpoch)]
        var title = v.title
        var subtitle = v.subtitle
        for ((token, value) in replacements) {
            title = title.replace(token, value)
            subtitle = subtitle.replace(token, value)
        }
        return CoachingInsight(title = title, subtitle = subtitle, icon = icon)
    }

    // ── Variant pools ────────────────────────────────────────────────────

    private val FIRST_RUN_VARIANTS = listOf(
        Variant("Start your first run", "Connect your HR monitor and hit the trail"),
        Variant("Day one is the hardest", "Start with 20 easy minutes \u2014 the rest unlocks itself"),
        Variant("Time to lace up", "An easy first run today beats a perfect first run next month")
    )

    private val INACTIVITY_VARIANTS = listOf(
        Variant("Time to get moving", "It's been \$days days since your last run"),
        Variant("Easing back in?", "\$days days off \u2014 today's run can be short and easy, just to reset"),
        Variant("Welcome back", "\$days days is recoverable in a week of easy runs \u2014 don't try to make it up at once"),
        Variant("Small step today", "After \$days days off, a 20-minute easy effort beats nothing by a long way")
    )

    private val CONSECUTIVE_HARD_VARIANTS = listOf(
        Variant("Consider an easy day", "\$count hard sessions in a row \u2014 an easy run helps recovery"),
        Variant("Time to back off", "\$count hard days running \u2014 the next adaptation lives in the rest"),
        Variant("Go easy today", "After \$count hard sessions, today's gain comes from recovery, not load"),
        Variant("Recovery is training", "\$count hard days stacked \u2014 a true easy run unlocks tomorrow's quality")
    )

    private val Z2_IMPROVEMENT_VARIANTS = listOf(
        Variant("Z2 pace improved \$pct%", "Your aerobic base is growing \u2014 keep it up"),
        Variant("Aerobic engine up \$pct%", "Same HR, faster pace \u2014 the easy work is paying off"),
        Variant("\$pct% faster at the same effort", "Stroke volume and capillary density are rewarding the consistency"),
        Variant("Easy pace up \$pct%", "The boring miles are showing up in the data \u2014 trust the process")
    )

    private val WEEKLY_GOAL_VARIANTS = listOf(
        Variant("Weekly goal reached!", "\$count runs this week \u2014 nice consistency"),
        Variant("\$count runs done this week", "Consistency beats heroics \u2014 this is exactly the pattern"),
        Variant("Target hit", "\$count sessions in the books \u2014 the streak is the engine"),
        Variant("Solid week", "\$count runs logged \u2014 each one stacks on the last")
    )

    private val BEHIND_SCHEDULE_VARIANTS = listOf(
        Variant("Pick up the pace this week", "\$done/\$target sessions done \u2014 \$remaining left to stay on track"),
        Variant("Catch-up window", "\$done/\$target this week \u2014 \$remaining easy sessions get you there"),
        Variant("\$remaining runs from on-track", "\$done/\$target so far \u2014 the back half of the week is decisive")
    )

    private val DEFAULT_VARIANTS = listOf(
        Variant("Consistency is key", "Regular training builds a stronger aerobic base"),
        Variant("Show up today", "The runs you do beat the perfect runs you plan"),
        Variant("Stack the habit", "Adaptations compound when sessions don't slip"),
        Variant("Easy days enable hard days", "The shape of a good week is mostly easy with a few sharper edges")
    )

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
