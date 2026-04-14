package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: WorkoutEntity)

    @Update
    suspend fun update(workout: WorkoutEntity)

    // History and UI — excludes simulated rows
    @Query("SELECT * FROM workouts WHERE isSimulated = 0 ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    // Used by service (CTL/ATL prev-workout lookup) and general callers — excludes simulated
    @Query("SELECT * FROM workouts WHERE isSimulated = 0 ORDER BY startTime DESC")
    suspend fun getAllWorkoutsOnce(): List<WorkoutEntity>

    // Used by AdaptiveProfileRebuilder — real runs only, ascending for replay
    @Query("SELECT * FROM workouts WHERE isSimulated = 0 AND endTime > 0 ORDER BY startTime ASC")
    suspend fun getAllRealWorkoutsAsc(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(totalDistanceMeters), 0) / 1000.0 FROM workouts WHERE isSimulated = 0")
    suspend fun sumAllDistanceKm(): Double

    @Query("SELECT * FROM workouts WHERE endTime = 0")
    suspend fun getOrphanedWorkouts(): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts WHERE isSimulated = 0")
    suspend fun getWorkoutCount(): Int
}
