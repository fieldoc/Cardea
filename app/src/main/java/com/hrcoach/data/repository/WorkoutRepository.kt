package com.hrcoach.data.repository

import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutDao
import com.hrcoach.data.db.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val trackPointDao: TrackPointDao
) {
    fun getAllWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.getAllWorkouts()
    suspend fun getAllWorkoutsOnce(): List<WorkoutEntity> = workoutDao.getAllWorkoutsOnce()
    suspend fun getAllRealWorkoutsAsc(): List<WorkoutEntity> = workoutDao.getAllRealWorkoutsAsc()

    suspend fun getWorkoutById(id: Long): WorkoutEntity? = workoutDao.getById(id)

    suspend fun createWorkout(workout: WorkoutEntity): Long = workoutDao.insert(workout)

    suspend fun updateWorkout(workout: WorkoutEntity) = workoutDao.update(workout)

    suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteById(workoutId)

    suspend fun addTrackPoint(point: TrackPointEntity) = trackPointDao.insert(point)

    suspend fun getTrackPoints(workoutId: Long): List<TrackPointEntity> =
        trackPointDao.getPointsForWorkout(workoutId)

    suspend fun getTrackPointsForWorkouts(workoutIds: List<Long>): Map<Long, List<TrackPointEntity>> =
        trackPointDao.getTrackPointsForWorkouts(workoutIds).groupBy { it.workoutId }

    suspend fun sumAllDistanceKm(): Double = workoutDao.sumAllDistanceKm()

    suspend fun getWorkoutsCompletedThisWeek(): Int {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val weekStartMs = cal.timeInMillis
        return workoutDao.getAllWorkoutsOnce().count { it.startTime >= weekStartMs && it.endTime > 0 }
    }

    /** Lifetime count of non-simulated workouts. Used by post-run recap's "first 3 runs" gate. */
    suspend fun countNonSimulated(): Int = workoutDao.getWorkoutCount()

    suspend fun cleanupOrphanedWorkouts() {
        val orphans = workoutDao.getOrphanedWorkouts()
        for (orphan in orphans) {
            val trackPoints = getTrackPoints(orphan.id)
            val estimatedEnd = trackPoints.maxOfOrNull { it.timestamp } ?: orphan.startTime
            val estimatedDistance = trackPoints.maxOfOrNull { it.distanceMeters } ?: 0f
            updateWorkout(orphan.copy(endTime = estimatedEnd, totalDistanceMeters = estimatedDistance))
        }
    }
}
