package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert
    suspend fun insert(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE shown = 0 AND userId = :userId ORDER BY earnedAtMs DESC")
    fun getUnshownAchievements(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY earnedAtMs DESC")
    fun getAllAchievements(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE type = :type AND userId = :userId ORDER BY earnedAtMs DESC")
    fun getAchievementsByType(type: String, userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE type = :type AND milestone = :milestone AND userId = :userId)")
    suspend fun hasAchievement(type: String, milestone: String, userId: String): Boolean

    @Query("UPDATE achievements SET shown = 1 WHERE id IN (:ids)")
    suspend fun markShown(ids: List<Long>)

    @Query("UPDATE achievements SET userId = :userId WHERE userId = ''")
    suspend fun claimOrphanedAchievements(userId: String)
}
