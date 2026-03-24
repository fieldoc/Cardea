package com.hrcoach.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate15To16_addsUserIdColumns() {
        val db: SupportSQLiteDatabase = helper.createDatabase("test_db", 15)
        db.execSQL("INSERT INTO workouts (startTime, endTime, totalDistanceMeters, mode, targetConfig) VALUES (1000, 2000, 5000.0, 'FREE_RUN', '{}')")
        db.close()
        val migratedDb = helper.runMigrationsAndValidate("test_db", 16, true, AppDatabase.MIGRATION_15_16)
        val cursor = migratedDb.query("SELECT userId FROM workouts")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0))
        cursor.close()
        migratedDb.close()
    }
}
