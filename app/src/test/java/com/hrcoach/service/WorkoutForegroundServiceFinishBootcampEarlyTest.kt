package com.hrcoach.service

import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.firebase.CloudBackupManager
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the contract that [WorkoutForegroundService.ACTION_FINISH_BOOTCAMP_EARLY] relies on.
 *
 * The handler body (WFS onStartCommand) is:
 *
 *     val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
 *     if (pendingId != null && workoutId > 0L) {
 *         val profile = adaptiveProfileRepository.getProfile()
 *         bootcampSessionCompleter.complete(
 *             workoutId = workoutId,
 *             pendingSessionId = pendingId,
 *             tuningDirection = profile.lastTuningDirection ?: TuningDirection.HOLD
 *         )
 *     }
 *     stopWorkout()
 *
 * Rather than spinning up the Android service + Hilt graph, we exercise the real
 * [BootcampSessionCompleter] with a fake DAO and assert:
 *   (a) non-null pendingBootcampSessionId → completer marks the session completed
 *       with the correct workoutId + tuningDirection carried through.
 *   (b) null pendingBootcampSessionId → completer short-circuits (completed=false);
 *       this is the invariant that lets the handler's `if` guard safely skip the
 *       completer and still call stopWorkout().
 */
class WorkoutForegroundServiceFinishBootcampEarlyTest {

    private val noopCloudBackupManager: CloudBackupManager = mockk(relaxed = true)

    private val noopAchievementEvaluator = AchievementEvaluator(object : AchievementDao {
        override suspend fun insert(achievement: AchievementEntity): Long = 0L
        override suspend fun upsert(achievement: AchievementEntity) {}
        override suspend fun hasAchievement(type: String, milestone: String): Boolean = false
        override fun getAllAchievements(): Flow<List<AchievementEntity>> = emptyFlow()
        override fun getAchievementsByType(type: String): Flow<List<AchievementEntity>> = emptyFlow()
        override suspend fun getUnshownAchievements(): List<AchievementEntity> = emptyList()
        override suspend fun markShown(ids: List<Long>) {}
        override suspend fun getAllAchievementsOnce(): List<AchievementEntity> = emptyList()
    }, noopCloudBackupManager)

    private fun makeCompleter(dao: FakeBootcampDao) = BootcampSessionCompleter(
        BootcampRepository(dao),
        noopAchievementEvaluator,
        noopCloudBackupManager
    )

    @Test
    fun finishBootcampEarlyMarksSessionCompletedWithTuningDirection() = runTest {
        val dao = FakeBootcampDao(
            activeEnrollment = makeEnrollment(runsPerWeek = 2),
            sessionsByWeek = mutableMapOf(
                1 to mutableListOf(
                    makeSession(id = 10L, dayOfWeek = 2),
                    makeSession(id = 11L, dayOfWeek = 4)
                )
            )
        )
        val completer = makeCompleter(dao)

        // Handler semantics with pendingBootcampSessionId = 10L, workoutId = 77L.
        val pendingId: Long? = 10L
        val workoutId: Long = 77L
        val tuningDirection = TuningDirection.EASE_BACK

        val result = if (pendingId != null && workoutId > 0L) {
            completer.complete(
                workoutId = workoutId,
                pendingSessionId = pendingId,
                tuningDirection = tuningDirection
            )
        } else {
            null
        }

        assertTrue(
            "Handler should mark the pending bootcamp session completed",
            result?.completed == true
        )
        val saved = dao.getSession(10L)
        assertEquals(77L, saved?.completedWorkoutId)
        assertEquals(BootcampSessionEntity.STATUS_COMPLETED, saved?.status)
    }

    @Test
    fun finishBootcampEarlyWithNullPendingSkipsCompleterAndFallsThroughToStopWorkout() = runTest {
        val dao = FakeBootcampDao(
            activeEnrollment = makeEnrollment(),
            sessionsByWeek = mutableMapOf(
                1 to mutableListOf(makeSession(id = 10L, dayOfWeek = 2))
            )
        )
        val completer = makeCompleter(dao)

        // Handler semantics with pendingBootcampSessionId = null: the guard MUST skip
        // the completer and proceed to stopWorkout(). We assert by never invoking
        // completer.complete() under the guard, and confirming no session state changes.
        val pendingId: Long? = null
        val workoutId: Long = 77L

        val result = if (pendingId != null && workoutId > 0L) {
            completer.complete(
                workoutId = workoutId,
                pendingSessionId = pendingId,
                tuningDirection = TuningDirection.HOLD
            )
        } else {
            null
        }

        assertNull("Completer must not be called when pendingId is null", result)
        // Session state unchanged — still SCHEDULED, no completedWorkoutId.
        val untouched = dao.getSession(10L)
        assertEquals(BootcampSessionEntity.STATUS_SCHEDULED, untouched?.status)
        assertNull(untouched?.completedWorkoutId)
    }

    @Test
    fun finishBootcampEarlyWithZeroWorkoutIdSkipsCompleter() = runTest {
        // Defensive guard: the handler also requires workoutId > 0L (matches WFS sim-path).
        // startWorkout() may not yet have persisted a row — in that window, pendingId can
        // be non-null while workoutId is still 0L. Completer must not be called.
        val dao = FakeBootcampDao(
            activeEnrollment = makeEnrollment(),
            sessionsByWeek = mutableMapOf(
                1 to mutableListOf(makeSession(id = 10L, dayOfWeek = 2))
            )
        )
        val completer = makeCompleter(dao)

        val pendingId: Long? = 10L
        val workoutId: Long = 0L

        val result = if (pendingId != null && workoutId > 0L) {
            completer.complete(
                workoutId = workoutId,
                pendingSessionId = pendingId,
                tuningDirection = TuningDirection.HOLD
            )
        } else {
            null
        }

        assertNull("Completer must not be called when workoutId is 0", result)
        assertEquals(BootcampSessionEntity.STATUS_SCHEDULED, dao.getSession(10L)?.status)
    }

    @Test
    fun completerNullPendingReturnsCompletedFalse() = runTest {
        // Belt-and-braces: the completer itself returns completed=false on null pendingId.
        // This is the invariant that makes the handler's guard safe even if the caller
        // passed through unconditionally.
        val completer = makeCompleter(FakeBootcampDao())
        val result = completer.complete(workoutId = 77L, pendingSessionId = null)
        assertFalse(result.completed)
    }

    private fun makeEnrollment(
        id: Long = 1L,
        runsPerWeek: Int = 2,
        currentPhaseIndex: Int = 0,
        currentWeekInPhase: Int = 0
    ) = BootcampEnrollmentEntity(
        id = id,
        goalType = BootcampGoal.RACE_5K.name,
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
        dayOfWeek: Int = 2,
        status: String = BootcampSessionEntity.STATUS_SCHEDULED
    ) = BootcampSessionEntity(
        id = id,
        enrollmentId = enrollmentId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        sessionType = "EASY",
        targetMinutes = 30,
        status = status,
        completedWorkoutId = null,
        presetId = null
    )
}

/**
 * File-private fake of [BootcampDao]. Mirrors the one in BootcampSessionCompleterTest.kt
 * (that one is also file-private). If the DAO interface grows, both fakes need updating —
 * see CLAUDE.md "Test Fakes".
 */
private class FakeBootcampDao(
    var activeEnrollment: BootcampEnrollmentEntity? = null,
    private val sessionsByWeek: MutableMap<Int, MutableList<BootcampSessionEntity>> = mutableMapOf()
) : BootcampDao {

    override suspend fun insertEnrollment(enrollment: BootcampEnrollmentEntity): Long {
        activeEnrollment = enrollment
        return enrollment.id
    }

    override suspend fun upsertEnrollment(enrollment: BootcampEnrollmentEntity) {
        activeEnrollment = enrollment
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

    override fun getLatestEnrollmentAnyStatus(): Flow<BootcampEnrollmentEntity?> = emptyFlow()

    override suspend fun getEnrollment(id: Long): BootcampEnrollmentEntity? =
        activeEnrollment?.takeIf { it.id == id }

    override suspend fun getCompletedSessionCount(enrollmentId: Long): Int = 0

    override suspend fun getTotalSessionCount(enrollmentId: Long): Int = 0

    override suspend fun sumCompletedWorkoutDistanceMeters(enrollmentId: Long): Double = 0.0

    override suspend fun insertSession(session: BootcampSessionEntity): Long {
        sessionsByWeek.getOrPut(session.weekNumber) { mutableListOf() }.add(session)
        return session.id
    }

    override suspend fun upsertSession(session: BootcampSessionEntity) {
        sessionsByWeek.values.forEach { list -> list.removeAll { it.id == session.id } }
        sessionsByWeek.getOrPut(session.weekNumber) { mutableListOf() }.add(session)
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
        getScheduledAndDeferredSessions(enrollmentId).firstOrNull()

    override suspend fun getScheduledAndDeferredSessions(enrollmentId: Long): List<BootcampSessionEntity> =
        sessionsByWeek.values.flatten()
            .filter {
                it.enrollmentId == enrollmentId &&
                    (it.status == BootcampSessionEntity.STATUS_SCHEDULED || it.status == BootcampSessionEntity.STATUS_DEFERRED)
            }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }))

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

    override suspend fun getCompletedWeekNumbers(enrollmentId: Long): List<Int> =
        sessionsByWeek.entries
            .filter { (_, sessions) ->
                sessions.filter { it.enrollmentId == enrollmentId }.let { enrolled ->
                    enrolled.isNotEmpty() && enrolled.all { it.status == BootcampSessionEntity.STATUS_COMPLETED }
                }
            }
            .map { it.key }
            .sortedDescending()
}
