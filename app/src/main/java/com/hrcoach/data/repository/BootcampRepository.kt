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
        bootcampDao.updateEnrollment(enrollment.copy(status = "PAUSED"))
    }

    suspend fun insertSessions(sessions: List<BootcampSessionEntity>) =
        bootcampDao.insertSessions(sessions)

    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>> =
        bootcampDao.getSessionsForEnrollment(enrollmentId)

    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity> =
        bootcampDao.getSessionsForWeek(enrollmentId, week)

    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getNextScheduledSession(enrollmentId)

    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getLastCompletedSession(enrollmentId)

    suspend fun updateSession(session: BootcampSessionEntity) =
        bootcampDao.updateSession(session)

    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int) =
        bootcampDao.deleteSessionsAfterWeek(enrollmentId, weekNumber)

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
            preferredDays = preferredDays.joinToString(",", "[", "]"),
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
    }
}
