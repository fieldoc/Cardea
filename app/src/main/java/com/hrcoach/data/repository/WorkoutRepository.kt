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

    suspend fun getWorkoutById(id: Long): WorkoutEntity? = workoutDao.getById(id)

    suspend fun createWorkout(workout: WorkoutEntity): Long = workoutDao.insert(workout)

    suspend fun updateWorkout(workout: WorkoutEntity) = workoutDao.update(workout)

    suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteById(workoutId)

    suspend fun addTrackPoint(point: TrackPointEntity) = trackPointDao.insert(point)

    suspend fun getTrackPoints(workoutId: Long): List<TrackPointEntity> =
        trackPointDao.getPointsForWorkout(workoutId)
}
