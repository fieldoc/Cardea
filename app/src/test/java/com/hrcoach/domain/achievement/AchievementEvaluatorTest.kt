package com.hrcoach.domain.achievement

import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import com.hrcoach.data.firebase.CloudBackupManager
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAchievementDao : AchievementDao {
    val inserted = mutableListOf<AchievementEntity>()

    override suspend fun insert(achievement: AchievementEntity) { inserted.add(achievement) }
    override suspend fun upsert(achievement: AchievementEntity) {
        inserted.removeAll { it.id == achievement.id }
        inserted.add(achievement)
    }
    override suspend fun getUnshownAchievements(): List<AchievementEntity> = inserted.filter { !it.shown }
    override fun getAllAchievements(): Flow<List<AchievementEntity>> = flowOf(inserted.toList())
    override fun getAchievementsByType(type: String): Flow<List<AchievementEntity>> = flowOf(inserted.filter { it.type == type })
    override suspend fun hasAchievement(type: String, milestone: String): Boolean = inserted.any { it.type == type && it.milestone == milestone }
    override suspend fun markShown(ids: List<Long>) { /* no-op for tests */ }
    override suspend fun getAllAchievementsOnce(): List<AchievementEntity> = inserted.toList()
}

class AchievementEvaluatorTest {

    private lateinit var dao: FakeAchievementDao
    private lateinit var evaluator: AchievementEvaluator

    @Before
    fun setup() {
        dao = FakeAchievementDao()
        evaluator = AchievementEvaluator(dao, mockk(relaxed = true))
    }

    @Test
    fun `evaluateDistance inserts achievement when threshold crossed`() = runTest {
        evaluator.evaluateDistance(totalKm = 105.0, workoutId = 42L)
        assertTrue(dao.inserted.any { it.milestone == "50km" && it.prestigeLevel == 1 })
        assertTrue(dao.inserted.any { it.milestone == "100km" && it.prestigeLevel == 1 })
        assertEquals(2, dao.inserted.size)
    }

    @Test
    fun `evaluateDistance skips already earned milestones`() = runTest {
        dao.inserted.add(AchievementEntity(type = AchievementType.DISTANCE_MILESTONE.name, milestone = "50km", prestigeLevel = 1, earnedAtMs = 0))
        evaluator.evaluateDistance(totalKm = 105.0, workoutId = 42L)
        val newInserts = dao.inserted.filter { it.triggerWorkoutId == 42L }
        assertEquals(1, newInserts.size)
        assertEquals("100km", newInserts[0].milestone)
    }

    @Test
    fun `evaluateDistance does nothing below first threshold`() = runTest {
        evaluator.evaluateDistance(totalKm = 30.0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `evaluateDistance at exactly threshold value awards it`() = runTest {
        evaluator.evaluateDistance(totalKm = 50.0, workoutId = 42L)
        assertEquals(1, dao.inserted.size)
        assertEquals("50km", dao.inserted[0].milestone)
    }

    @Test
    fun `evaluateStreak inserts when streak crosses threshold`() = runTest {
        evaluator.evaluateStreak(currentStreak = 12, workoutId = 42L)
        val milestones = dao.inserted.map { it.milestone }
        assertTrue("5_sessions" in milestones)
        assertTrue("10_sessions" in milestones)
        assertEquals(2, dao.inserted.size)
    }

    @Test
    fun `evaluateStreak does nothing when streak is 0`() = runTest {
        evaluator.evaluateStreak(currentStreak = 0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `evaluateTierGraduation inserts with correct prestige and goal`() = runTest {
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "HALF_MARATHON")
        assertEquals(1, dao.inserted.size)
        val a = dao.inserted[0]
        assertEquals(AchievementType.TIER_GRADUATION.name, a.type)
        assertEquals("tier_1_HALF_MARATHON", a.milestone)
        assertEquals("HALF_MARATHON", a.goal)
        assertEquals(1, a.tier)
        assertEquals(2, a.prestigeLevel)
    }

    @Test
    fun `evaluateTierGraduation allows same tier for different goals`() = runTest {
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "HALF_MARATHON")
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "MARATHON")
        assertEquals(2, dao.inserted.size)
        assertEquals("tier_1_HALF_MARATHON", dao.inserted[0].milestone)
        assertEquals("tier_1_MARATHON", dao.inserted[1].milestone)
    }

    @Test
    fun `evaluateBootcampGraduation inserts prestige 3 with goal`() = runTest {
        evaluator.evaluateBootcampGraduation(enrollmentId = 1L, goal = "MARATHON", tierIndex = 2)
        assertEquals(1, dao.inserted.size)
        val a = dao.inserted[0]
        assertEquals(AchievementType.BOOTCAMP_GRADUATION.name, a.type)
        assertEquals(3, a.prestigeLevel)
        assertEquals("MARATHON", a.goal)
    }

    @Test
    fun `evaluateWeeklyGoalStreak inserts when threshold crossed`() = runTest {
        evaluator.evaluateWeeklyGoalStreak(consecutiveWeeks = 5, workoutId = 42L)
        assertEquals(1, dao.inserted.size)
        assertEquals("4_weeks", dao.inserted[0].milestone)
        assertEquals(1, dao.inserted[0].prestigeLevel)
    }

    @Test
    fun `evaluateWeeklyGoalStreak does nothing when weeks is 0`() = runTest {
        evaluator.evaluateWeeklyGoalStreak(consecutiveWeeks = 0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }
}
