package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `no workouts returns first-run insight`() {
        val nowMs = 1_700_000_000_000L  // fixed for determinism
        val result = CoachingInsightEngine.generate(
            workouts = emptyList(),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = nowMs
        )
        assertEquals(CoachingIcon.HEART, result.icon)
        // Title is deterministic for a fixed nowMs but non-trivial to know in advance.
        // Sanity-check it's not empty and contains plausible language.
        assertTrue(result.title.isNotBlank())
        assertTrue(result.subtitle.isNotBlank())
    }

    @Test
    fun `same nowMs produces same insight (deterministic)`() {
        val nowMs = 1_700_000_000_000L
        val a = CoachingInsightEngine.generate(emptyList(), 0, 4, false, nowMs)
        val b = CoachingInsightEngine.generate(emptyList(), 0, 4, false, nowMs)
        assertEquals(a.title, b.title)
        assertEquals(a.subtitle, b.subtitle)
        assertEquals(a.icon, b.icon)
    }

    @Test
    fun `7+ days since last run returns inactivity warning`() {
        val nowMs = 1_700_000_000_000L
        // Workout endTime is startTime + 30min, so 9 days back yields integer
        // daysSinceLastRun = 8 (subtracting the 30min workout duration).
        val nineDaysAgo = nowMs - 9 * 86_400_000L
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = nineDaysAgo)),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = nowMs
        )
        assertEquals(CoachingIcon.WARNING, result.icon)
        assertTrue(result.title.isNotBlank())
        assertTrue("subtitle should mention day count", result.subtitle.contains("8"))
    }

    @Test
    fun `weekly goal met returns trophy insight`() {
        val now = 1_700_000_000_000L
        val workouts = (0..3).map { workout(startTime = now - it * 86_400_000L) }
        val result = CoachingInsightEngine.generate(
            workouts = workouts,
            workoutsThisWeek = 4,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals(CoachingIcon.TROPHY, result.icon)
        assertTrue(result.title.isNotBlank())
        // count substitution: "4" should appear somewhere in title or subtitle
        assertTrue(
            "title or subtitle should mention run count",
            result.title.contains("4") || result.subtitle.contains("4")
        )
    }

    @Test
    fun `default fallback when no rules match`() {
        val now = 1_700_000_000_000L
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = now - 86_400_000L)),
            workoutsThisWeek = 1,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals(CoachingIcon.LIGHTBULB, result.icon)
        assertTrue(result.title.isNotBlank())
        assertTrue(result.subtitle.isNotBlank())
    }
}
