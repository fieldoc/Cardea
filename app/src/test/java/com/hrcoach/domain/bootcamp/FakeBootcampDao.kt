package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Shared in-memory [BootcampDao] for unit tests in this package
 * ([BootcampSessionCompleterTest], [CalendarDriftRecovererTest]).
 *
 * Per `docs/claude-rules/test-fakes.md`: adding DAO methods requires updating
 * this fake. Previously this class was duplicated as a `private` declaration
 * inside [BootcampSessionCompleterTest]; consolidated 2026-05-04 to remove the
 * redeclaration and let new test files in this package reuse it.
 */
internal class FakeBootcampDao(
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

    override suspend fun getCompletedSessionCount(enrollmentId: Long): Int =
        sessionsByWeek.values.flatten()
            .count { it.enrollmentId == enrollmentId && it.status == BootcampSessionEntity.STATUS_COMPLETED }

    override suspend fun getTotalSessionCount(enrollmentId: Long): Int =
        sessionsByWeek.values.flatten().count { it.enrollmentId == enrollmentId }

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

    override suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int): Int {
        var deleted = 0
        val weeksToDelete = sessionsByWeek.keys.filter { it > weekNumber }
        weeksToDelete.forEach { week ->
            val current = sessionsByWeek.getValue(week)
            val (toDelete, toKeep) = current.partition { it.enrollmentId == enrollmentId }
            deleted += toDelete.size
            sessionsByWeek[week] = toKeep.toMutableList()
        }
        return deleted
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
