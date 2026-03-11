package com.hrcoach.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BootcampDao {
    @Insert
    suspend fun insertEnrollment(enrollment: BootcampEnrollmentEntity): Long

    @Update
    suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity)

    @Delete
    suspend fun deleteEnrollment(enrollment: BootcampEnrollmentEntity)

    // Changed: was status = 'ACTIVE', now includes PAUSED so paused dashboards stay visible
    @Query("SELECT * FROM bootcamp_enrollments WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY id DESC LIMIT 1")
    fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?>

    @Query("SELECT * FROM bootcamp_enrollments WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY id DESC LIMIT 1")
    suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity?

    @Query("SELECT * FROM bootcamp_enrollments WHERE id = :id")
    suspend fun getEnrollment(id: Long): BootcampEnrollmentEntity?

    @Insert
    suspend fun insertSession(session: BootcampSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<BootcampSessionEntity>)

    @Update
    suspend fun updateSession(session: BootcampSessionEntity)

    @Query("SELECT * FROM bootcamp_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): BootcampSessionEntity?

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId ORDER BY weekNumber, dayOfWeek")
    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber = :week ORDER BY dayOfWeek")
    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'SCHEDULED' ORDER BY weekNumber, dayOfWeek LIMIT 1")
    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("""
        SELECT * FROM bootcamp_sessions
        WHERE enrollmentId = :enrollmentId
          AND status IN ('SCHEDULED', 'DEFERRED')
        ORDER BY weekNumber, dayOfWeek
        LIMIT 1
    """)
    suspend fun getNextSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId ORDER BY weekNumber, dayOfWeek")
    suspend fun getSessionsForEnrollmentOnce(enrollmentId: Long): List<BootcampSessionEntity>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'COMPLETED' ORDER BY weekNumber DESC, dayOfWeek DESC LIMIT 1")
    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("DELETE FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber > :weekNumber")
    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int)

    /**
     * Atomically marks a session as completed, advances the enrollment phase/week pointer,
     * and inserts the pre-built sessions for the next week.
     * [newSessions] may be empty when the week advance does not yet require seeding.
     */
    @Transaction
    suspend fun completeSessionAndAdvanceWeek(
        completedSession: BootcampSessionEntity,
        updatedEnrollment: BootcampEnrollmentEntity,
        newSessions: List<BootcampSessionEntity>
    ) {
        updateSession(completedSession)
        updateEnrollment(updatedEnrollment)
        if (newSessions.isNotEmpty()) {
            insertSessions(newSessions)
        }
    }

    /**
     * Atomically marks a session as completed without advancing the enrollment.
     * Used when the week is not yet fully finished.
     */
    @Transaction
    suspend fun completeSessionOnly(completedSession: BootcampSessionEntity) {
        updateSession(completedSession)
    }
}
