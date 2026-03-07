package com.hrcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class, WorkoutMetricsEntity::class,
                BootcampEnrollmentEntity::class, BootcampSessionEntity::class],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between 7 and 8 (identity hash matches)
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN pausedAtMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN altitudeMeters REAL")
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN illnessPromptSnoozedUntilMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bootcamp_sessions ADD COLUMN presetIndex INTEGER")
                db.execSQL("ALTER TABLE bootcamp_sessions ADD COLUMN completedAtMs INTEGER")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN hrr1Bpm REAL")
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN trimpScore REAL")
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN trimpReliable INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN environmentAffected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierPromptSnoozedUntilMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierPromptDismissCount INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bootcamp_enrollments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalType TEXT NOT NULL,
                        targetMinutesPerRun INTEGER NOT NULL,
                        runsPerWeek INTEGER NOT NULL,
                        preferredDays TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        currentPhaseIndex INTEGER NOT NULL DEFAULT 0,
                        currentWeekInPhase INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE'
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bootcamp_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        enrollmentId INTEGER NOT NULL,
                        weekNumber INTEGER NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        sessionType TEXT NOT NULL,
                        targetMinutes INTEGER NOT NULL,
                        presetId TEXT,
                        status TEXT NOT NULL DEFAULT 'SCHEDULED',
                        completedWorkoutId INTEGER,
                        FOREIGN KEY (enrollmentId) REFERENCES bootcamp_enrollments(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bootcamp_sessions_enrollmentId ON bootcamp_sessions(enrollmentId)")
            }
        }
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
    abstract fun bootcampDao(): BootcampDao
}
