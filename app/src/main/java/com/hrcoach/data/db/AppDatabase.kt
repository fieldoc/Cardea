package com.hrcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@TypeConverters(Converters::class)
@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class, WorkoutMetricsEntity::class,
                BootcampEnrollmentEntity::class, BootcampSessionEntity::class,
                AchievementEntity::class],
    version = 20,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        // v20 deletes the dormant HRR1/illness-detection schema (TODO(HRR1) was never implemented;
        // see docs/plans/2026-04-14-science-fidelity-audit-findings.md A2). Drops:
        //   - workout_metrics.hrr1Bpm
        //   - bootcamp_enrollments.illnessPromptSnoozedUntilMs
        // SQLite < 3.35 (Android API < 34, our minSdk = 26) lacks ALTER TABLE DROP COLUMN, so each
        // table is rebuilt via CREATE NEW / INSERT SELECT / DROP / RENAME (same pattern as
        // MIGRATION_11_12). All other user data is preserved by explicit column-list copy.
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── workout_metrics: drop hrr1Bpm ────────────────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `workout_metrics_new` (
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
                        `heartbeatsPerKm` REAL,
                        `paceAtRefHrMinPerKm` REAL,
                        `trimpScore` REAL,
                        `trimpReliable` INTEGER NOT NULL,
                        `environmentAffected` INTEGER NOT NULL,
                        `cueCountsJson` TEXT,
                        PRIMARY KEY(`workoutId`),
                        FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `workout_metrics_new` (
                        `workoutId`, `recordedAtMs`, `avgPaceMinPerKm`, `avgHr`, `hrAtSixMinPerKm`,
                        `settleDownSec`, `settleUpSec`, `longTermHrTrimBpm`, `responseLagSec`,
                        `efficiencyFactor`, `aerobicDecoupling`, `efFirstHalf`, `efSecondHalf`,
                        `heartbeatsPerKm`, `paceAtRefHrMinPerKm`, `trimpScore`, `trimpReliable`,
                        `environmentAffected`, `cueCountsJson`
                    )
                    SELECT
                        `workoutId`, `recordedAtMs`, `avgPaceMinPerKm`, `avgHr`, `hrAtSixMinPerKm`,
                        `settleDownSec`, `settleUpSec`, `longTermHrTrimBpm`, `responseLagSec`,
                        `efficiencyFactor`, `aerobicDecoupling`, `efFirstHalf`, `efSecondHalf`,
                        `heartbeatsPerKm`, `paceAtRefHrMinPerKm`, `trimpScore`, `trimpReliable`,
                        `environmentAffected`, `cueCountsJson`
                    FROM `workout_metrics`
                """.trimIndent())
                db.execSQL("DROP TABLE `workout_metrics`")
                db.execSQL("ALTER TABLE `workout_metrics_new` RENAME TO `workout_metrics`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_metrics_workoutId` ON `workout_metrics` (`workoutId`)")

                // ── bootcamp_enrollments: drop illnessPromptSnoozedUntilMs ───────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bootcamp_enrollments_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `goalType` TEXT NOT NULL,
                        `targetMinutesPerRun` INTEGER NOT NULL,
                        `runsPerWeek` INTEGER NOT NULL,
                        `preferredDays` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `currentPhaseIndex` INTEGER NOT NULL,
                        `currentWeekInPhase` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `tierIndex` INTEGER NOT NULL,
                        `tierPromptSnoozedUntilMs` INTEGER NOT NULL,
                        `tierPromptDismissCount` INTEGER NOT NULL,
                        `pausedAtMs` INTEGER NOT NULL,
                        `targetFinishingTimeMinutes` INTEGER,
                        `lastTierChangeWeek` INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `bootcamp_enrollments_new` (
                        `id`, `goalType`, `targetMinutesPerRun`, `runsPerWeek`, `preferredDays`,
                        `startDate`, `currentPhaseIndex`, `currentWeekInPhase`, `status`,
                        `tierIndex`, `tierPromptSnoozedUntilMs`, `tierPromptDismissCount`,
                        `pausedAtMs`, `targetFinishingTimeMinutes`, `lastTierChangeWeek`
                    )
                    SELECT
                        `id`, `goalType`, `targetMinutesPerRun`, `runsPerWeek`, `preferredDays`,
                        `startDate`, `currentPhaseIndex`, `currentWeekInPhase`, `status`,
                        `tierIndex`, `tierPromptSnoozedUntilMs`, `tierPromptDismissCount`,
                        `pausedAtMs`, `targetFinishingTimeMinutes`, `lastTierChangeWeek`
                    FROM `bootcamp_enrollments`
                """.trimIndent())
                db.execSQL("DROP TABLE `bootcamp_enrollments`")
                db.execSQL("ALTER TABLE `bootcamp_enrollments_new` RENAME TO `bootcamp_enrollments`")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_metrics ADD COLUMN cueCountsJson TEXT")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN activeDurationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN lastTierChangeWeek INTEGER")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN isSimulated INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN targetFinishingTimeMinutes INTEGER")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE bootcamp_enrollments SET goalType = 'RACE_5K' WHERE goalType = 'RACE_5K_10K'")
                db.execSQL("UPDATE achievements SET goal = 'RACE_5K' WHERE goal = 'RACE_5K_10K'")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `achievements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `milestone` TEXT NOT NULL,
                        `goal` TEXT,
                        `tier` INTEGER,
                        `prestigeLevel` INTEGER NOT NULL,
                        `earnedAtMs` INTEGER NOT NULL,
                        `triggerWorkoutId` INTEGER,
                        `shown` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add FK: bootcamp_sessions.completedWorkoutId -> workouts.id ON DELETE SET NULL
                // SQLite cannot ALTER TABLE to add a FK; must rebuild the table.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bootcamp_sessions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `enrollmentId` INTEGER NOT NULL,
                        `weekNumber` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `sessionType` TEXT NOT NULL,
                        `targetMinutes` INTEGER NOT NULL,
                        `presetId` TEXT,
                        `status` TEXT NOT NULL DEFAULT 'SCHEDULED',
                        `completedWorkoutId` INTEGER,
                        `presetIndex` INTEGER,
                        `completedAtMs` INTEGER,
                        FOREIGN KEY(`enrollmentId`) REFERENCES `bootcamp_enrollments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`completedWorkoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `bootcamp_sessions_new`
                    SELECT `id`, `enrollmentId`, `weekNumber`, `dayOfWeek`, `sessionType`,
                           `targetMinutes`, `presetId`, `status`, `completedWorkoutId`,
                           `presetIndex`, `completedAtMs`
                    FROM `bootcamp_sessions`
                """.trimIndent())
                db.execSQL("DROP TABLE `bootcamp_sessions`")
                db.execSQL("ALTER TABLE `bootcamp_sessions_new` RENAME TO `bootcamp_sessions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bootcamp_sessions_enrollmentId` ON `bootcamp_sessions` (`enrollmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bootcamp_sessions_completedWorkoutId` ON `bootcamp_sessions` (`completedWorkoutId`)")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change. BLACKOUT is a new DaySelectionLevel value
                // stored in the existing preferredDays TEXT column.
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix missing columns that might have been skipped in dev versions 5-9
                runCatching { db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN illnessPromptSnoozedUntilMs INTEGER NOT NULL DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE bootcamp_sessions ADD COLUMN presetIndex INTEGER") }
                runCatching { db.execSQL("ALTER TABLE bootcamp_sessions ADD COLUMN completedAtMs INTEGER") }
                runCatching { db.execSQL("ALTER TABLE track_points ADD COLUMN altitudeMeters REAL") }
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Already partially run on some devices, move logic to 9_10
                runCatching { db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN illnessPromptSnoozedUntilMs INTEGER NOT NULL DEFAULT 0") }
            }
        }
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
    abstract fun achievementDao(): AchievementDao
}
