package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutMetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metrics: WorkoutMetricsEntity)

    @Query("SELECT * FROM workout_metrics WHERE workoutId = :workoutId")
    suspend fun getByWorkoutId(workoutId: Long): WorkoutMetricsEntity?

    @Query("SELECT * FROM workout_metrics WHERE workoutId IN (:workoutIds)")
    suspend fun getByWorkoutIds(workoutIds: List<Long>): List<WorkoutMetricsEntity>

    @Query("DELETE FROM workout_metrics WHERE workoutId = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: Long)

    @Query("DELETE FROM workout_metrics WHERE workoutId NOT IN (:validWorkoutIds)")
    suspend fun deleteAllExcept(validWorkoutIds: List<Long>)

    @Query("DELETE FROM workout_metrics")
    suspend fun deleteAll()
}
