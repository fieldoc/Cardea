package com.hrcoach.di

import android.content.Context
import androidx.room.Room
import com.hrcoach.data.db.AppDatabase
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.WorkoutMetricsDao
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
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
}
