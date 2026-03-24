package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllWorkouts(userId: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
    suspend fun getAllWorkoutsOnce(userId: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(totalDistanceMeters), 0) / 1000.0 FROM workouts WHERE userId = :userId")
    suspend fun sumAllDistanceKm(userId: String): Double

    @Query("SELECT * FROM workouts WHERE endTime = 0")
    suspend fun getOrphanedWorkouts(): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts WHERE userId = :userId")
    suspend fun countWorkouts(userId: String): Int

    @Query("UPDATE workouts SET userId = :userId WHERE userId = ''")
    suspend fun claimOrphanedWorkouts(userId: String)
}
