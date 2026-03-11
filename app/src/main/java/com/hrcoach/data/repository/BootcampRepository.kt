package com.hrcoach.data.repository

import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.model.BootcampGoal
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootcampRepository @Inject constructor(
    private val bootcampDao: BootcampDao
) {
    fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?> =
        bootcampDao.getActiveEnrollment()

    suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity? =
        bootcampDao.getActiveEnrollmentOnce()

    suspend fun createEnrollment(
        goal: BootcampGoal,
        targetMinutesPerRun: Int,
        runsPerWeek: Int,
        preferredDays: List<Int>,
        startDate: Long
    ): Long {
        val entity = buildEnrollmentEntity(goal, targetMinutesPerRun, runsPerWeek, preferredDays, startDate)
        return bootcampDao.insertEnrollment(entity)
    }

    suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity) =
        bootcampDao.updateEnrollment(enrollment)

    suspend fun pauseEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(enrollment.copy(status = BootcampEnrollmentEntity.STATUS_PAUSED))
    }

    suspend fun resumeEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(enrollment.copy(status = BootcampEnrollmentEntity.STATUS_ACTIVE))
    }

    suspend fun deleteEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        // CASCADE foreign key on bootcamp_sessions deletes all sessions automatically
        bootcampDao.deleteEnrollment(enrollment)
    }

    suspend fun insertSessions(sessions: List<BootcampSessionEntity>) =
        bootcampDao.insertSessions(sessions)

    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>> =
        bootcampDao.getSessionsForEnrollment(enrollmentId)

    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity> =
        bootcampDao.getSessionsForWeek(enrollmentId, week)

    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getNextScheduledSession(enrollmentId)

    suspend fun getNextSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getNextSession(enrollmentId)

    suspend fun getSessionsForEnrollmentOnce(enrollmentId: Long): List<BootcampSessionEntity> =
        bootcampDao.getSessionsForEnrollmentOnce(enrollmentId)

    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getLastCompletedSession(enrollmentId)

    suspend fun updateSession(session: BootcampSessionEntity) =
        bootcampDao.updateSession(session)

    /**
     * Atomically marks [completedSession] as completed, updates [updatedEnrollment] to reflect
     * the new phase/week, and inserts [newSessions] for the upcoming week.
     * All three writes happen inside a single SQLite transaction.
     */
    suspend fun completeSessionAndAdvanceWeek(
        completedSession: BootcampSessionEntity,
        updatedEnrollment: BootcampEnrollmentEntity,
        newSessions: List<BootcampSessionEntity>
    ) = bootcampDao.completeSessionAndAdvanceWeek(completedSession, updatedEnrollment, newSessions)

    /**
     * Atomically marks [completedSession] as completed without advancing enrollment.
     * Used when the current week still has uncompleted sessions.
     */
    suspend fun completeSessionOnly(completedSession: BootcampSessionEntity) =
        bootcampDao.completeSessionOnly(completedSession)

    suspend fun swapSessionToRestDay(sessionId: Long) {
        val session = bootcampDao.getSessionById(sessionId) ?: return
        val updated = session.copy(
            status = BootcampSessionEntity.STATUS_SKIPPED,
            completedAtMs = System.currentTimeMillis()
        )
        bootcampDao.updateSession(updated)
    }

    suspend fun rescheduleSession(sessionId: Long, newDayOfWeek: Int) {
        val session = bootcampDao.getSessionById(sessionId) ?: return
        bootcampDao.updateSession(session.copy(dayOfWeek = newDayOfWeek))
    }

    suspend fun deferSession(sessionId: Long) {
        val session = bootcampDao.getSessionById(sessionId) ?: return
        bootcampDao.updateSession(session.copy(status = BootcampSessionEntity.STATUS_DEFERRED))
    }

    suspend fun dropSession(sessionId: Long) {
        val session = bootcampDao.getSessionById(sessionId) ?: return
        bootcampDao.updateSession(
            session.copy(
                status = BootcampSessionEntity.STATUS_SKIPPED,
                completedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun graduateEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(
            enrollment.copy(
                status = BootcampEnrollmentEntity.STATUS_GRADUATED
            )
        )
    }

    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int) =
        bootcampDao.deleteSessionsAfterWeek(enrollmentId, weekNumber)

    suspend fun updatePreferredDays(
        enrollmentId: Long,
        newDays: List<com.hrcoach.domain.bootcamp.DayPreference>,
        currentWeekNumber: Int
    ) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(
            enrollment.copy(
                preferredDays = newDays
            )
        )
    }

    suspend fun deleteScheduledSessionsFromWeek(enrollmentId: Long, weekNumber: Int) {
        // deleteSessionsAfterWeek deletes WHERE weekNumber > N, so pass weekNumber - 1
        bootcampDao.deleteSessionsAfterWeek(enrollmentId, weekNumber - 1)
    }

    companion object {
        fun buildEnrollmentEntity(
            goal: BootcampGoal,
            targetMinutesPerRun: Int,
            runsPerWeek: Int,
            preferredDays: List<Int>,
            startDate: Long
        ) = BootcampEnrollmentEntity(
            goalType = goal.name,
            targetMinutesPerRun = targetMinutesPerRun,
            runsPerWeek = runsPerWeek,
            preferredDays = preferredDays.map { com.hrcoach.domain.bootcamp.DayPreference(it, com.hrcoach.domain.bootcamp.DaySelectionLevel.AVAILABLE) },
            startDate = startDate
        )

        fun buildSessionEntity(
            enrollmentId: Long,
            weekNumber: Int,
            dayOfWeek: Int,
            sessionType: String,
            targetMinutes: Int,
            presetId: String? = null
        ) = BootcampSessionEntity(
            enrollmentId = enrollmentId,
            weekNumber = weekNumber,
            dayOfWeek = dayOfWeek,
            sessionType = sessionType,
            targetMinutes = targetMinutes,
            presetId = presetId
        )

        /**
         * Computes reslotted day assignments for SCHEDULED sessions given a new ordered list of
         * preferred days.  Completed sessions are left in place; their days are excluded from
         * the available pool so scheduled sessions don't collide with them.
         *
         * Returns a list of (session, newDayOfWeek) pairs — one entry per SCHEDULED session.
         * When [newDays] has fewer entries than the number of scheduled sessions, the original
         * day is preserved as a fallback.
         */
        fun computeReslottedDays(
            sessions: List<BootcampSessionEntity>,
            newDays: List<com.hrcoach.domain.bootcamp.DayPreference>
        ): List<Pair<BootcampSessionEntity, Int>> {
            val completedDays = sessions
                .filter { it.status == BootcampSessionEntity.STATUS_COMPLETED }
                .map { it.dayOfWeek }
                .toSet()

            val availableNewDays = newDays
                .map { it.day }
                .filter { it !in completedDays }

            val scheduledSessions = sessions.filter {
                it.status == BootcampSessionEntity.STATUS_SCHEDULED ||
                it.status == BootcampSessionEntity.STATUS_DEFERRED
            }

            return scheduledSessions.mapIndexed { index, session ->
                val newDay = availableNewDays.getOrElse(index) { session.dayOfWeek }
                session to newDay
            }
        }
    }
}
