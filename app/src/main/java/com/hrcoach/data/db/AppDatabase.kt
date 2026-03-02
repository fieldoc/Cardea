package com.hrcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class, WorkoutMetricsEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN heartbeatsPerKm REAL")
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN paceAtRefHrMinPerKm REAL")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_metrics` (
                        `workoutId` INTEGER NOT NULL,
                        `recordedAtMs` INTEGER NOT NULL,
                        `avgPaceMinPerKm` REAL,
                        `avgHr` REAL,
                        `hrAtSixMinPerKm` REAL,
                        `settleDownSec` REAL,
                        `settleUpSec` REAL,
                        `longTermHrTrimBpm` REAL NOT NULL,
                        `responseLagSec` REAL NOT NULL,
                        `efficiencyFactor` REAL,
                        `aerobicDecoupling` REAL,
                        `efFirstHalf` REAL,
                        `efSecondHalf` REAL,
                        PRIMARY KEY(`workoutId`),
                        FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_metrics_workoutId`
                    ON `workout_metrics` (`workoutId`)
                    """.trimIndent()
                )
            }
        }
    }

    abstract fun workoutDao(): WorkoutDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun workoutMetricsDao(): WorkoutMetricsDao
}
