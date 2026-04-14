package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert
    suspend fun insert(achievement: AchievementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE shown = 0 ORDER BY earnedAtMs DESC")
    suspend fun getUnshownAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements ORDER BY earnedAtMs DESC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE type = :type ORDER BY earnedAtMs DESC")
    fun getAchievementsByType(type: String): Flow<List<AchievementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE type = :type AND milestone = :milestone)")
    suspend fun hasAchievement(type: String, milestone: String): Boolean

    @Query("UPDATE achievements SET shown = 1 WHERE id IN (:ids)")
    suspend fun markShown(ids: List<Long>)

    @Query("SELECT * FROM achievements ORDER BY earnedAtMs DESC")
    suspend fun getAllAchievementsOnce(): List<AchievementEntity>
}
