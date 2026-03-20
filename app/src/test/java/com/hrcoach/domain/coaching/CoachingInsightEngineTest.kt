package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachingInsightEngineTest {

    private fun workout(
        startTime: Long,
        endTime: Long = startTime + 30 * 60_000L,
        distanceMeters: Float = 3000f,
        targetConfig: String = """{"mode":"STEADY_STATE","steadyStateTargetHr":140}"""
    ) = WorkoutEntity(
        startTime = startTime,
        endTime = endTime,
        totalDistanceMeters = distanceMeters,
        mode = "STEADY_STATE",
        targetConfig = targetConfig
    )

    @Test
    fun `no workouts returns start-your-first-run insight`() {
        val result = CoachingInsightEngine.generate(
            workouts = emptyList(),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = System.currentTimeMillis()
        )
        assertEquals("Start your first run", result.title)
        assertEquals(CoachingIcon.HEART, result.icon)
    }

    @Test
    fun `7+ days since last run returns inactivity warning`() {
        val eightDaysAgo = System.currentTimeMillis() - 8 * 86_400_000L
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = eightDaysAgo)),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = System.currentTimeMillis()
        )
        assertEquals(CoachingIcon.WARNING, result.icon)
        assert(result.title.contains("get moving", ignoreCase = true))
    }

    @Test
    fun `weekly goal met returns trophy insight`() {
        val now = System.currentTimeMillis()
        val workouts = (0..3).map { workout(startTime = now - it * 86_400_000L) }
        val result = CoachingInsightEngine.generate(
            workouts = workouts,
            workoutsThisWeek = 4,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals(CoachingIcon.TROPHY, result.icon)
        assert(result.title.contains("goal", ignoreCase = true))
    }

    @Test
    fun `default fallback when no rules match`() {
        val now = System.currentTimeMillis()
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = now - 86_400_000L)),
            workoutsThisWeek = 1,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals("Consistency is key", result.title)
        assertEquals(CoachingIcon.LIGHTBULB, result.icon)
    }
}
