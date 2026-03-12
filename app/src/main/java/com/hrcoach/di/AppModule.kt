package com.hrcoach.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hrcoach.data.db.AppDatabase
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hr_coach_db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    @Singleton
    fun provideTrackPointDao(db: AppDatabase): TrackPointDao = db.trackPointDao()

    @Provides
    @Singleton
    fun provideWorkoutMetricsDao(db: AppDatabase): WorkoutMetricsDao = db.workoutMetricsDao()

    @Provides
    @Singleton
    fun provideBootcampDao(db: AppDatabase): BootcampDao = db.bootcampDao()

    @Provides
    @Singleton
    fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
}
