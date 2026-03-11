package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.model.BootcampGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootcampSessionCompleterTest {

    @Test
    fun completeReturnsFalseWhenNoPendingSessionId() = runTest {
        val completer = BootcampSessionCompleter(BootcampRepository(FakeBootcampDao()))

        val result = completer.complete(workoutId = 5L, pendingSessionId = null)

        assertFalse(result.completed)
    }

    @Test
    fun completeReturnsFalseWhenEnrollmentMissing() = runTest {
        val completer = BootcampSessionCompleter(BootcampRepository(FakeBootcampDao()))

        val result = completer.complete(workoutId = 5L, pendingSessionId = 10L)

        assertFalse(result.completed)
    }

    @Test
    fun completeMarksSessionAndReturnsTrue() = runTest {
        val dao = FakeBootcampDao(
            activeEnrollment = makeEnrollment(),
            sessionsByWeek = mutableMapOf(
                1 to mutableListOf(
                    makeSession(id = 10L, dayOfWeek = 2),
                    makeSession(id = 11L, dayOfWeek = 4)
                )
            )
        )
        val completer = BootcampSessionCompleter(BootcampRepository(dao))

        val result = completer.complete(workoutId = 99L, pendingSessionId = 10L)

        assertTrue(result.completed)
        assertFalse(result.weekComplete)
        assertEquals("1 of 2 sessions this week", result.progressLabel)
        assertEquals(99L, dao.getSession(10L)?.completedWorkoutId)
        assertEquals(BootcampSessionEntity.STATUS_COMPLETED, dao.getSession(10L)?.status)
    }

    @Test
    fun completeAdvancesWeekWhenFinalSessionFinishesWeek() = runTest {
        val dao = FakeBootcampDao(
            activeEnrollment = makeEnrollment(runsPerWeek = 1),
            sessionsByWeek = mutableMapOf(
                1 to mutableListOf(makeSession(id = 10L, dayOfWeek = 2))
            )
        )
        val completer = BootcampSessionCompleter(BootcampRepository(dao))

        val result = completer.complete(workoutId = 100L, pendingSessionId = 10L)

        assertTrue(result.completed)
        assertTrue(result.weekComplete)
        assertEquals("Week 1 complete - Base", result.progressLabel)
        assertNotNull(dao.activeEnrollment)
        assertEquals(1, dao.activeEnrollment?.currentWeekInPhase)
        assertTrue(dao.getSessionsForWeek(1L, 2).isNotEmpty())
    }

    private fun makeEnrollment(
        id: Long = 1L,
        runsPerWeek: Int = 2,
        currentPhaseIndex: Int = 0,
        currentWeekInPhase: Int = 0
    ) = BootcampEnrollmentEntity(
        id = id,
        goalType = BootcampGoal.RACE_5K_10K.name,
        targetMinutesPerRun = 30,
        runsPerWeek = runsPerWeek,
        preferredDays = listOf(
            DayPreference(2, DaySelectionLevel.AVAILABLE),
            DayPreference(4, DaySelectionLevel.AVAILABLE),
            DayPreference(6, DaySelectionLevel.AVAILABLE)
        ),
        startDate = System.currentTimeMillis(),
        currentPhaseIndex = currentPhaseIndex,
        currentWeekInPhase = currentWeekInPhase,
        status = BootcampEnrollmentEntity.STATUS_ACTIVE,
        tierIndex = 1,
        tierPromptDismissCount = 0,
        tierPromptSnoozedUntilMs = 0L
    )

    private fun makeSession(
        id: Long = 10L,
        enrollmentId: Long = 1L,
        weekNumber: Int = 1,
        dayOfWeek: Int = 2
    ) = BootcampSessionEntity(
        id = id,
        enrollmentId = enrollmentId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        sessionType = "EASY",
        targetMinutes = 30,
        status = BootcampSessionEntity.STATUS_SCHEDULED,
        completedWorkoutId = null,
        presetId = null
    )
}

private class FakeBootcampDao(
    var activeEnrollment: BootcampEnrollmentEntity? = null,
    private val sessionsByWeek: MutableMap<Int, MutableList<BootcampSessionEntity>> = mutableMapOf()
) : BootcampDao {

    override suspend fun insertEnrollment(enrollment: BootcampEnrollmentEntity): Long {
        activeEnrollment = enrollment
        return enrollment.id
    }

    override suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity) {
        activeEnrollment = enrollment
    }

    override suspend fun deleteEnrollment(enrollment: BootcampEnrollmentEntity) {
        if (activeEnrollment?.id == enrollment.id) {
            activeEnrollment = null
        }
    }

    override fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?> = emptyFlow()

    override suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity? = activeEnrollment

    override suspend fun getEnrollment(id: Long): BootcampEnrollmentEntity? =
        activeEnrollment?.takeIf { it.id == id }

    override suspend fun insertSession(session: BootcampSessionEntity): Long {
        sessionsByWeek.getOrPut(session.weekNumber) { mutableListOf() }.add(session)
        return session.id
    }

    override suspend fun insertSessions(sessions: List<BootcampSessionEntity>) {
        sessions.forEach { insertSession(it) }
    }

    override suspend fun updateSession(session: BootcampSessionEntity) {
        val existingWeek = sessionsByWeek.entries.firstOrNull { entry ->
            entry.value.any { it.id == session.id }
        }?.key
        if (existingWeek != null) {
            sessionsByWeek[existingWeek] = sessionsByWeek.getValue(existingWeek)
                .map { if (it.id == session.id) session else it }
                .toMutableList()
        } else {
            sessionsByWeek.getOrPut(session.weekNumber) { mutableListOf() }.add(session)
        }
    }

    override suspend fun getSessionById(sessionId: Long): BootcampSessionEntity? = getSession(sessionId)

    override fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>> = emptyFlow()

    override suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity> =
        sessionsByWeek[week].orEmpty().filter { it.enrollmentId == enrollmentId }

    override suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity? =
        sessionsByWeek.values.flatten()
            .filter { it.enrollmentId == enrollmentId && it.status == BootcampSessionEntity.STATUS_SCHEDULED }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }))
            .firstOrNull()

    override suspend fun getNextSession(enrollmentId: Long): BootcampSessionEntity? =
        sessionsByWeek.values.flatten()
            .filter {
                it.enrollmentId == enrollmentId &&
                (it.status == BootcampSessionEntity.STATUS_SCHEDULED || it.status == BootcampSessionEntity.STATUS_DEFERRED)
            }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }))
            .firstOrNull()

    override suspend fun getSessionsForEnrollmentOnce(enrollmentId: Long): List<BootcampSessionEntity> =
        sessionsByWeek.values.flatten()
            .filter { it.enrollmentId == enrollmentId }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }))

    override suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity? =
        sessionsByWeek.values.flatten()
            .filter { it.enrollmentId == enrollmentId && it.status == BootcampSessionEntity.STATUS_COMPLETED }
            .sortedWith(compareByDescending<BootcampSessionEntity> { it.weekNumber }.thenByDescending { it.dayOfWeek })
            .firstOrNull()

    override suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int) {
        val weeksToDelete = sessionsByWeek.keys.filter { it > weekNumber }
        weeksToDelete.forEach { week ->
            sessionsByWeek[week] = sessionsByWeek.getValue(week)
                .filterNot { it.enrollmentId == enrollmentId }
                .toMutableList()
        }
    }

    fun getSession(sessionId: Long): BootcampSessionEntity? =
        sessionsByWeek.values.flatten().firstOrNull { it.id == sessionId }
}
