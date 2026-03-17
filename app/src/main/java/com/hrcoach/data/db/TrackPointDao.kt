package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getPointsForWorkout(workoutId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE workoutId IN (:workoutIds) ORDER BY timestamp ASC")
    suspend fun getTrackPointsForWorkouts(workoutIds: List<Long>): List<TrackPointEntity>
}
