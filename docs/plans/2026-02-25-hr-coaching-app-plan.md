# HR Coaching App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android app that connects to a BLE heart rate monitor and coaches runners to stay in their target HR zone via non-intrusive audio tones that don't interrupt music.

**Architecture:** Foreground Service with four internal components (BleHrManager, GpsDistanceTracker, ZoneEngine, AlertManager) exposes state via StateFlow to a Jetpack Compose UI. Room database stores workout history. SoundPool plays alert tones mixed on top of existing audio.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Google Maps SDK, Google Play Services Location, Android BLE APIs, SoundPool

**Design Doc:** `docs/plans/2026-02-25-hr-coaching-app-design.md`

---

## Package Structure

```
com.hrcoach/
├── data/
│   ├── db/          (Room entities, DAOs, database)
│   └── repository/  (WorkoutRepository)
├── domain/
│   ├── model/       (WorkoutMode, ZoneStatus, HrSegment, WorkoutConfig)
│   └── engine/      (ZoneEngine)
├── service/         (ForegroundService, BleHrManager, GpsDistanceTracker, AlertManager)
├── ui/
│   ├── theme/       (Color, Theme, Type)
│   ├── setup/       (SetupScreen, SetupViewModel)
│   ├── workout/     (ActiveWorkoutScreen, WorkoutViewModel)
│   ├── history/     (HistoryListScreen, HistoryDetailScreen, HistoryViewModel)
│   └── navigation/  (NavGraph)
├── di/              (Hilt modules)
├── HrCoachApp.kt    (Application class)
└── MainActivity.kt
```

All source files live under: `app/src/main/java/com/hrcoach/`
All test files live under: `app/src/test/java/com/hrcoach/`
All androidTest files live under: `app/src/androidTest/java/com/hrcoach/`
Resources live under: `app/src/main/res/`

---

## Task 1: Project Scaffolding

**Goal:** Create the Android project structure with all dependencies configured.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/hrcoach/HrCoachApp.kt`
- Create: `app/src/main/java/com/hrcoach/MainActivity.kt`
- Create: `local.properties` (gitignored — holds Google Maps API key)

**Step 1: Create root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "HRCoach"
include(":app")
```

**Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.54" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}
```

**Step 3: Create `gradle.properties`**

```properties
android.useAndroidX=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m
```

**Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.hrcoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hrcoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Google Maps
    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Gson (for JSON serialization of workout config)
    implementation("com.google.code.gson:gson:2.11.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
```

**Step 5: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- BLE permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- GPS -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Foreground service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
    <uses-feature android:name="android.hardware.location.gps" android:required="true" />

    <application
        android:name=".HrCoachApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="HR Coach"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${MAPS_API_KEY}" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Material3.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.WorkoutForegroundService"
            android:foregroundServiceType="location|connectedDevice"
            android:exported="false" />

    </application>
</manifest>
```

**Step 6: Create `local.defaults.properties`**

```properties
MAPS_API_KEY=YOUR_API_KEY_HERE
```

**Step 7: Create `HrCoachApp.kt`**

```kotlin
package com.hrcoach

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HrCoachApp : Application()
```

**Step 8: Create `MainActivity.kt`** (minimal placeholder — will be filled in navigation task)

```kotlin
package com.hrcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("HR Coach — placeholder")
        }
    }
}
```

**Step 9: Create `.gitignore`**

```
*.iml
.gradle
/local.properties
/.idea
/build
/app/build
/captures
.externalNativeBuild
.cxx
```

**Step 10: Verify the project builds**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/HRapp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 11: Commit**

```bash
git init
git add -A
git commit -m "chore: scaffold Android project with all dependencies"
```

---

## Task 2: Domain Models

**Goal:** Define the pure Kotlin data classes that represent workout configuration, zone status, and HR segments. No Android dependencies — just data.

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/model/WorkoutMode.kt`
- Create: `app/src/main/java/com/hrcoach/domain/model/HrSegment.kt`
- Create: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Create: `app/src/main/java/com/hrcoach/domain/model/ZoneStatus.kt`

**Step 1: Create `WorkoutMode.kt`**

```kotlin
package com.hrcoach.domain.model

enum class WorkoutMode {
    STEADY_STATE,
    DISTANCE_PROFILE
}
```

**Step 2: Create `HrSegment.kt`**

```kotlin
package com.hrcoach.domain.model

/**
 * One segment of a distance-profile workout.
 * @param distanceMeters cumulative end distance for this segment (e.g. 5000m means "up to 5km")
 * @param targetHr target heart rate in BPM for this segment
 */
data class HrSegment(
    val distanceMeters: Float,
    val targetHr: Int
)
```

**Step 3: Create `WorkoutConfig.kt`**

```kotlin
package com.hrcoach.domain.model

data class WorkoutConfig(
    val mode: WorkoutMode,
    val steadyStateTargetHr: Int? = null,         // used in STEADY_STATE mode
    val segments: List<HrSegment> = emptyList(),   // used in DISTANCE_PROFILE mode
    val bufferBpm: Int = 5,                        // +/- tolerance around target
    val alertDelaySec: Int = 15,                   // seconds out of zone before alert
    val alertCooldownSec: Int = 30                 // seconds between repeated alerts
) {
    fun targetHrAtDistance(distanceMeters: Float): Int? {
        return when (mode) {
            WorkoutMode.STEADY_STATE -> steadyStateTargetHr
            WorkoutMode.DISTANCE_PROFILE -> {
                segments.firstOrNull { distanceMeters <= it.distanceMeters }?.targetHr
                    ?: segments.lastOrNull()?.targetHr
            }
        }
    }
}
```

**Step 4: Create `ZoneStatus.kt`**

```kotlin
package com.hrcoach.domain.model

enum class ZoneStatus {
    IN_ZONE,    // HR is within target +/- buffer
    ABOVE_ZONE, // HR is too high — slow down
    BELOW_ZONE, // HR is too low — speed up
    NO_DATA     // no HR reading yet or no target defined
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/
git commit -m "feat: add domain models for workout config and zone status"
```

---

## Task 3: ZoneEngine (TDD)

**Goal:** The core logic that decides whether the runner is in-zone, above, or below. Pure Kotlin, no Android dependencies, fully testable.

**Files:**
- Create: `app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt`
- Create: `app/src/main/java/com/hrcoach/domain/engine/ZoneEngine.kt`

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZoneEngineTest {

    private lateinit var engine: ZoneEngine

    // --- Steady State tests ---

    @Test
    fun `steady state - in zone returns IN_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 135, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 145, distanceMeters = 0f))
    }

    @Test
    fun `steady state - above zone returns ABOVE_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 146, distanceMeters = 0f))
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 180, distanceMeters = 0f))
    }

    @Test
    fun `steady state - below zone returns BELOW_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 134, distanceMeters = 0f))
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 100, distanceMeters = 0f))
    }

    // --- Distance Profile tests ---

    @Test
    fun `distance profile - selects correct segment based on distance`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 5000f, targetHr = 140),
                HrSegment(distanceMeters = 7000f, targetHr = 160),
                HrSegment(distanceMeters = 8000f, targetHr = 180)
            ),
            bufferBpm = 5
        )
        engine = ZoneEngine(config)

        // At 3km, target is 140 → 140 is in zone
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 3000f))

        // At 6km, target is 160 → 140 is below zone
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 140, distanceMeters = 6000f))

        // At 7.5km, target is 180 → 180 is in zone
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 180, distanceMeters = 7500f))
    }

    @Test
    fun `distance profile - past last segment uses last target`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 5000f, targetHr = 140)
            ),
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 10000f))
    }

    // --- Custom buffer ---

    @Test
    fun `custom buffer width is respected`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 150,
            bufferBpm = 10
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 160, distanceMeters = 0f))
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 139, distanceMeters = 0f))
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 161, distanceMeters = 0f))
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest" --info`
Expected: FAIL — class `ZoneEngine` does not exist

**Step 3: Implement ZoneEngine**

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus

class ZoneEngine(private val config: WorkoutConfig) {

    fun evaluate(hr: Int, distanceMeters: Float): ZoneStatus {
        val target = config.targetHrAtDistance(distanceMeters) ?: return ZoneStatus.NO_DATA
        val low = target - config.bufferBpm
        val high = target + config.bufferBpm
        return when {
            hr < low -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else -> ZoneStatus.IN_ZONE
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest" --info`
Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/ app/src/test/java/com/hrcoach/domain/engine/
git commit -m "feat: add ZoneEngine with full test coverage"
```

---

## Task 4: Room Database

**Goal:** Create Room entities, DAOs, and database class for persisting workout history.

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/db/WorkoutEntity.kt`
- Create: `app/src/main/java/com/hrcoach/data/db/TrackPointEntity.kt`
- Create: `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt`
- Create: `app/src/main/java/com/hrcoach/data/db/TrackPointDao.kt`
- Create: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`

**Step 1: Create `WorkoutEntity.kt`**

```kotlin
package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,          // epoch millis
    val endTime: Long = 0,        // epoch millis, updated when workout stops
    val totalDistanceMeters: Float = 0f,
    val mode: String,             // "STEADY_STATE" or "DISTANCE_PROFILE"
    val targetConfig: String      // JSON string of WorkoutConfig
)
```

**Step 2: Create `TrackPointEntity.kt`**

```kotlin
package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val timestamp: Long,          // epoch millis
    val latitude: Double,
    val longitude: Double,
    val heartRate: Int,           // BPM
    val distanceMeters: Float     // cumulative
)
```

**Step 3: Create `WorkoutDao.kt`**

```kotlin
package com.hrcoach.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Delete
    suspend fun delete(workout: WorkoutEntity)
}
```

**Step 4: Create `TrackPointDao.kt`**

```kotlin
package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getPointsForWorkout(workoutId: Long): List<TrackPointEntity>
}
```

**Step 5: Create `AppDatabase.kt`**

```kotlin
package com.hrcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun trackPointDao(): TrackPointDao
}
```

**Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/
git commit -m "feat: add Room database with Workout and TrackPoint entities"
```

---

## Task 5: Hilt Dependency Injection Module

**Goal:** Wire up Room database and repository as Hilt singletons.

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt`
- Create: `app/src/main/java/com/hrcoach/di/AppModule.kt`

**Step 1: Create `WorkoutRepository.kt`**

```kotlin
package com.hrcoach.data.repository

import com.hrcoach.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val trackPointDao: TrackPointDao
) {
    fun getAllWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.getAllWorkouts()

    suspend fun getWorkoutById(id: Long): WorkoutEntity? = workoutDao.getById(id)

    suspend fun createWorkout(workout: WorkoutEntity): Long = workoutDao.insert(workout)

    suspend fun updateWorkout(workout: WorkoutEntity) = workoutDao.update(workout)

    suspend fun deleteWorkout(workout: WorkoutEntity) = workoutDao.delete(workout)

    suspend fun addTrackPoint(point: TrackPointEntity) = trackPointDao.insert(point)

    suspend fun getTrackPoints(workoutId: Long): List<TrackPointEntity> =
        trackPointDao.getPointsForWorkout(workoutId)
}
```

**Step 2: Create `AppModule.kt`**

```kotlin
package com.hrcoach.di

import android.content.Context
import androidx.room.Room
import com.hrcoach.data.db.AppDatabase
import com.hrcoach.data.db.TrackPointDao
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
        ).build()
    }

    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideTrackPointDao(db: AppDatabase): TrackPointDao = db.trackPointDao()
}
```

**Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/ app/src/main/java/com/hrcoach/di/
git commit -m "feat: add WorkoutRepository and Hilt DI module"
```

---

## Task 6: BLE Heart Rate Manager

**Goal:** Scan for BLE HR monitors, connect, subscribe to heart rate notifications, emit values as a Flow. Auto-reconnect on disconnect.

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/BleHrManager.kt`

**Step 1: Create `BleHrManager.kt`**

```kotlin
package com.hrcoach.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages BLE connection to a heart rate monitor.
 *
 * Usage:
 * 1. Call startScan() to discover HR devices. Observe discoveredDevices.
 * 2. Call connectToDevice(device) to connect and start receiving HR data.
 * 3. Observe heartRate for live BPM values.
 * 4. Observe isConnected for connection status.
 * 5. Call disconnect() when done.
 */
class BleHrManager(private val context: Context) {

    companion object {
        // Standard BLE Heart Rate Service and Characteristic UUIDs
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var shouldReconnect = false
    private var lastDeviceAddress: String? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    // ---- Scanning ----

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val current = _discoveredDevices.value.toMutableList()
            if (current.none { it.address == device.address }) {
                current.add(device)
                _discoveredDevices.value = current
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return
        _discoveredDevices.value = emptyList()
        scanner = bluetoothAdapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        isScanning = true

        // Auto-stop scan after 15 seconds
        scope.launch {
            delay(15_000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    // ---- Connection ----

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        shouldReconnect = true
        lastDeviceAddress = device.address
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        _heartRate.value = 0
    }

    @SuppressLint("MissingPermission")
    private fun attemptReconnect() {
        if (!shouldReconnect) return
        val address = lastDeviceAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    // ---- GATT Callback ----

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isConnected.value = true
                    reconnectJob?.cancel()
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    _heartRate.value = 0
                    attemptReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val hrService = gatt.getService(HR_SERVICE_UUID) ?: return
            val hrChar = hrService.getCharacteristic(HR_MEASUREMENT_UUID) ?: return

            gatt.setCharacteristicNotification(hrChar, true)
            val descriptor = hrChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                val flag = characteristic.value[0].toInt()
                val hrValue = if (flag and 0x01 != 0) {
                    // HR is UINT16
                    (characteristic.value[1].toInt() and 0xFF) or
                        ((characteristic.value[2].toInt() and 0xFF) shl 8)
                } else {
                    // HR is UINT8
                    characteristic.value[1].toInt() and 0xFF
                }
                _heartRate.value = hrValue
            }
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/BleHrManager.kt
git commit -m "feat: add BLE heart rate manager with scan, connect, and auto-reconnect"
```

---

## Task 7: GPS Distance Tracker

**Goal:** Track cumulative distance using FusedLocationProviderClient. Emit distance and current location as StateFlows.

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt`

**Step 1: Create `GpsDistanceTracker.kt`**

```kotlin
package com.hrcoach.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GpsDistanceTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 2000L  // update every 2 seconds
    ).setMinUpdateDistanceMeters(3f).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _currentLocation.value = location

            lastLocation?.let { prev ->
                _distanceMeters.value += prev.distanceTo(location)
            }
            lastLocation = location
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        lastLocation = null
        _distanceMeters.value = 0f
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun stop() {
        fusedClient.removeLocationUpdates(locationCallback)
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt
git commit -m "feat: add GPS distance tracker using FusedLocationProviderClient"
```

---

## Task 8: Alert Manager (SoundPool)

**Goal:** Play short, non-intrusive tones that layer on top of music. Different tones for "speed up" and "slow down". Uses SoundPool on STREAM_NOTIFICATION so it never pauses or ducks the user's music.

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/AlertManager.kt`
- Create: `app/src/main/res/raw/tone_speed_up.ogg` (placeholder — generate programmatically)
- Create: `app/src/main/res/raw/tone_slow_down.ogg` (placeholder — generate programmatically)

**Important context for tone files:** We need two short (~0.5s) OGG audio files. Since we can't bundle real audio files via code generation, we'll use Android's `ToneGenerator` as a fallback to generate tones at runtime. No external audio files needed.

**Step 1: Create `AlertManager.kt`**

```kotlin
package com.hrcoach.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays short alert tones that mix on top of existing audio (music, podcasts, etc).
 *
 * Uses ToneGenerator on STREAM_NOTIFICATION so it does NOT request AudioFocus.
 * This means the user's music is never paused, ducked, or interrupted.
 *
 * Two distinct tones:
 * - "Speed up" (HR too low): a short double-beep (TONE_PROP_BEEP2)
 * - "Slow down" (HR too high): a single lower tone (TONE_PROP_BEEP)
 */
class AlertManager(context: Context) {

    // ToneGenerator on STREAM_NOTIFICATION mixes with music without interrupting it
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) // 80% volume
    } catch (e: Exception) {
        null
    }

    /**
     * Play the "speed up" alert — HR is below target zone.
     * Uses a higher-pitched double beep to suggest increasing effort.
     */
    fun playSpeedUp() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400) // 400ms
    }

    /**
     * Play the "slow down" alert — HR is above target zone.
     * Uses a single lower tone to suggest easing off.
     */
    fun playSlowDown() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 400) // 400ms
    }

    fun destroy() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/AlertManager.kt
git commit -m "feat: add AlertManager with non-intrusive tones via ToneGenerator"
```

---

## Task 9: Workout Foreground Service

**Goal:** Ties together BleHrManager, GpsDistanceTracker, ZoneEngine, and AlertManager. Runs as a foreground service with a persistent notification. Exposes workout state as a singleton StateFlow that the UI observes.

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Create: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

**Step 1: Create `WorkoutState.kt`** — the shared state object between service and UI

```kotlin
package com.hrcoach.service

import com.hrcoach.domain.model.ZoneStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton state holder for the active workout. The ForegroundService writes to it,
 * the Compose UI reads from it. This avoids complex service binding — both just
 * reference the same object.
 */
object WorkoutState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _currentHr = MutableStateFlow(0)
    val currentHr: StateFlow<Int> = _currentHr

    private val _targetHr = MutableStateFlow(0)
    val targetHr: StateFlow<Int> = _targetHr

    private val _zoneStatus = MutableStateFlow(ZoneStatus.NO_DATA)
    val zoneStatus: StateFlow<ZoneStatus> = _zoneStatus

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _hrConnected = MutableStateFlow(false)
    val hrConnected: StateFlow<Boolean> = _hrConnected

    fun update(
        isRunning: Boolean = _isRunning.value,
        currentHr: Int = _currentHr.value,
        targetHr: Int = _targetHr.value,
        zoneStatus: ZoneStatus = _zoneStatus.value,
        distanceMeters: Float = _distanceMeters.value,
        hrConnected: Boolean = _hrConnected.value
    ) {
        _isRunning.value = isRunning
        _currentHr.value = currentHr
        _targetHr.value = targetHr
        _zoneStatus.value = zoneStatus
        _distanceMeters.value = distanceMeters
        _hrConnected.value = hrConnected
    }

    fun reset() {
        _isRunning.value = false
        _currentHr.value = 0
        _targetHr.value = 0
        _zoneStatus.value = ZoneStatus.NO_DATA
        _distanceMeters.value = 0f
        _hrConnected.value = false
    }
}
```

**Step 2: Create `WorkoutForegroundService.kt`**

```kotlin
package com.hrcoach.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.hrcoach.MainActivity
import com.hrcoach.R
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.hrcoach.ACTION_START"
        const val ACTION_STOP = "com.hrcoach.ACTION_STOP"
        const val EXTRA_CONFIG_JSON = "config_json"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_channel"
    }

    @Inject lateinit var repository: WorkoutRepository

    private var bleHrManager: BleHrManager? = null
    private var gpsTracker: GpsDistanceTracker? = null
    private var alertManager: AlertManager? = null
    private var zoneEngine: ZoneEngine? = null
    private var config: WorkoutConfig? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workoutId: Long = 0
    private var outOfZoneSince: Long = 0       // epoch ms when we first went out of zone, 0 = in zone
    private var lastAlertTime: Long = 0         // epoch ms of last alert, for cooldown

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: return START_NOT_STICKY
                config = Gson().fromJson(json, WorkoutConfig::class.java)
                startWorkout()
            }
            ACTION_STOP -> stopWorkout()
        }
        return START_STICKY
    }

    private fun startWorkout() {
        val cfg = config ?: return

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting workout..."))

        bleHrManager = BleHrManager(this)
        gpsTracker = GpsDistanceTracker(this)
        alertManager = AlertManager(this)
        zoneEngine = ZoneEngine(cfg)

        gpsTracker?.start()

        WorkoutState.update(isRunning = true)

        // Create workout record in DB
        scope.launch {
            workoutId = repository.createWorkout(
                WorkoutEntity(
                    startTime = System.currentTimeMillis(),
                    mode = cfg.mode.name,
                    targetConfig = Gson().toJson(cfg)
                )
            )
        }

        // Main observation loop: combine HR + distance, evaluate zone, trigger alerts
        scope.launch {
            combine(
                bleHrManager!!.heartRate,
                bleHrManager!!.isConnected,
                gpsTracker!!.distanceMeters,
                gpsTracker!!.currentLocation
            ) { hr, connected, distance, location ->
                WorkoutTick(hr, connected, distance, location)
            }.collect { tick ->
                val engine = zoneEngine ?: return@collect
                val target = cfg.targetHrAtDistance(tick.distance) ?: 0
                val status = if (tick.hr == 0) ZoneStatus.NO_DATA else engine.evaluate(tick.hr, tick.distance)

                WorkoutState.update(
                    currentHr = tick.hr,
                    targetHr = target,
                    zoneStatus = status,
                    distanceMeters = tick.distance,
                    hrConnected = tick.connected
                )

                // Alert logic with delay and cooldown
                handleAlerts(status, cfg)

                // Record track point every ~5 seconds (GPS updates every 2s, so skip some)
                tick.location?.let { loc ->
                    if (tick.hr > 0) {
                        repository.addTrackPoint(
                            TrackPointEntity(
                                workoutId = workoutId,
                                timestamp = System.currentTimeMillis(),
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                heartRate = tick.hr,
                                distanceMeters = tick.distance
                            )
                        )
                    }
                }

                // Update notification
                val notifText = if (tick.connected) "HR: ${tick.hr} bpm | Target: $target" else "HR Monitor disconnected"
                updateNotification(notifText)
            }
        }
    }

    private fun handleAlerts(status: ZoneStatus, cfg: WorkoutConfig) {
        val now = System.currentTimeMillis()

        if (status == ZoneStatus.IN_ZONE || status == ZoneStatus.NO_DATA) {
            outOfZoneSince = 0
            return
        }

        // Started being out of zone
        if (outOfZoneSince == 0L) {
            outOfZoneSince = now
            return
        }

        // Check if we've been out of zone long enough
        val outOfZoneDuration = now - outOfZoneSince
        if (outOfZoneDuration < cfg.alertDelaySec * 1000L) return

        // Check cooldown
        if (now - lastAlertTime < cfg.alertCooldownSec * 1000L) return

        // Fire alert
        when (status) {
            ZoneStatus.ABOVE_ZONE -> alertManager?.playSlowDown()
            ZoneStatus.BELOW_ZONE -> alertManager?.playSpeedUp()
            else -> {}
        }
        lastAlertTime = now
    }

    private fun stopWorkout() {
        gpsTracker?.stop()
        bleHrManager?.disconnect()
        alertManager?.destroy()

        // Update workout end time
        scope.launch {
            repository.getWorkoutById(workoutId)?.let { workout ->
                repository.updateWorkout(
                    workout.copy(
                        endTime = System.currentTimeMillis(),
                        totalDistanceMeters = WorkoutState.distanceMeters.value
                    )
                )
            }
        }

        WorkoutState.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Workout", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active workout tracking" }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HR Coach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleHrManager?.destroy()
        alertManager?.destroy()
        gpsTracker?.stop()
        scope.cancel()
    }

    private data class WorkoutTick(
        val hr: Int,
        val connected: Boolean,
        val distance: Float,
        val location: android.location.Location?
    )
}
```

**Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/
git commit -m "feat: add WorkoutForegroundService tying BLE, GPS, ZoneEngine, and alerts together"
```

---

## Task 10: Theme & Design System

**Goal:** Define the app's visual identity. All UI tasks reference these values. This is the single source of truth for colors, typography, and spacing.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/theme/Color.kt`
- Create: `app/src/main/java/com/hrcoach/ui/theme/Type.kt`
- Create: `app/src/main/java/com/hrcoach/ui/theme/Theme.kt`

**Step 1: Create `Color.kt`**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Color

// --- Brand colors ---
val Primary = Color(0xFF1B6EF3)         // Vibrant blue — buttons, active elements
val PrimaryVariant = Color(0xFF1455C0)  // Darker blue — pressed states
val OnPrimary = Color.White

// --- Background ---
val Background = Color(0xFF121212)      // Near-black dark background
val Surface = Color(0xFF1E1E2E)         // Slightly lighter card surfaces
val OnBackground = Color(0xFFE0E0E0)    // Light gray text on dark bg
val OnSurface = Color(0xFFE0E0E0)

// --- Zone status colors ---
val ZoneGreen = Color(0xFF4CAF50)       // In zone — confident green
val ZoneOrange = Color(0xFFFF9800)      // Slightly out — warning orange
val ZoneRed = Color(0xFFF44336)         // Way out of zone — alert red

// --- HR heatmap gradient (for history map polyline) ---
// Interpolate between these based on BPM:
//   100 bpm → HeatmapGreen
//   150 bpm → HeatmapYellow
//   200 bpm → HeatmapRed
val HeatmapGreen = Color(0xFF4CAF50)
val HeatmapYellow = Color(0xFFFFEB3B)
val HeatmapRed = Color(0xFFF44336)

// --- Misc ---
val DisabledGray = Color(0xFF666666)
val DividerColor = Color(0xFF2A2A3A)
```

**Step 2: Create `Type.kt`**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HrCoachTypography = Typography(
    // Giant HR display on active workout screen
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 96.sp,
        letterSpacing = (-1.5).sp
    ),
    // Section headers
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    // Card titles, target HR display
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.15.sp
    ),
    // Body text, input labels
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // Secondary info, units, small labels
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    // Button text
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 1.25.sp
    )
)
```

**Step 3: Create `Theme.kt`**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnSurface
)

@Composable
fun HrCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = HrCoachTypography,
        content = content
    )
}
```

**Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/
git commit -m "feat: add dark theme with zone status colors and HR heatmap palette"
```

---

## Task 11: Setup Screen

**Goal:** The first screen the user sees. Lets them configure a workout and connect their HR monitor before starting.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

### DETAILED UI SPECIFICATION

The setup screen is a single scrollable column on a dark background (`Background = #121212`). All content is padded 16dp from screen edges. Here is the exact layout from top to bottom:

```
┌──────────────────────────────────────────┐
│  [top padding 16dp]                      │
│                                          │
│  HR Coach              (text, headlineMd)│
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  Steady State  │ Distance Profile  │  │  ← SegmentedButton, full width
│  └────────────────────────────────────┘  │    fills Primary when selected,
│  [12dp spacing]                          │    Surface when not
│                                          │
│  === IF STEADY STATE ===                 │
│  ┌────────────────────────────────────┐  │
│  │ Target HR (bpm)           [ 140 ]  │  │  ← OutlinedTextField, number input
│  └────────────────────────────────────┘  │
│  [8dp spacing]                           │
│                                          │
│  === IF DISTANCE PROFILE ===             │
│  ┌────────────────────────────────────┐  │
│  │ Segment 1                          │  │
│  │ Distance (km) [5.0]  HR (bpm)[140]│  │  ← Two OutlinedTextFields in a Row
│  │                            [✕]     │  │  ← IconButton to remove segment
│  ├────────────────────────────────────┤  │
│  │ Segment 2                          │  │
│  │ Distance (km) [7.0]  HR (bpm)[160]│  │
│  │                            [✕]     │  │
│  ├────────────────────────────────────┤  │
│  │          [ + Add Segment ]         │  │  ← TextButton with + icon
│  └────────────────────────────────────┘  │
│  [16dp spacing]                          │
│                                          │
│  === SETTINGS (always shown) ===         │
│  ┌────────────────────────────────────┐  │  ← Card with Surface background,
│  │ Settings                           │  │    8dp corner radius
│  │                                    │  │
│  │ Buffer (±bpm)              [ 5 ]   │  │  ← OutlinedTextField, number input
│  │ [8dp]                              │  │
│  │ Alert delay (sec)          [ 15 ]  │  │  ← OutlinedTextField, number input
│  └────────────────────────────────────┘  │
│  [16dp spacing]                          │
│                                          │
│  === HR MONITOR CONNECTION ===           │
│  ┌────────────────────────────────────┐  │
│  │ HR Monitor                         │  │  ← Card
│  │                                    │  │
│  │ (if not connected):                │  │
│  │ [ 🔍 Scan for Devices ]           │  │  ← FilledTonalButton, full width
│  │                                    │  │
│  │ (if scanning, show list):          │  │
│  │  ○ Coospo H808S                    │  │  ← clickable Row items
│  │  ○ Other Device                    │  │    each shows device name or
│  │  [ Scanning... ]                   │  │    "Unknown" + MAC address
│  │                                    │  │
│  │ (if connected):                    │  │
│  │  ✓ Connected: Coospo H808S        │  │  ← green checkmark icon + text
│  │    HR: 72 bpm                      │  │  ← shows live HR to confirm
│  └────────────────────────────────────┘  │
│  [24dp spacing]                          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │         START WORKOUT              │  │  ← Large Button, full width,
│  └────────────────────────────────────┘  │    height 56dp, Primary color
│  (disabled + grayed out if HR monitor    │    disabled = DisabledGray
│   not connected or no target set)        │
│                                          │
│  [bottom padding 16dp]                   │
└──────────────────────────────────────────┘
```

**Key UI behaviors:**
- The mode toggle (SegmentedButton) instantly swaps between the steady-state input and the segment list. No animation, just swap.
- In distance profile mode, segments are cumulative distances in km. The first segment's distance means "from 0 to X km".
- The "Add Segment" button appends a new row with empty fields.
- The "X" remove button on each segment row is only shown if there are 2+ segments.
- The "Start Workout" button is disabled (grayed out, not clickable) until: (a) HR monitor is connected, AND (b) at least one valid target is set.
- All number inputs use `keyboardType = KeyboardType.Decimal`.
- When BLE scan finds devices, they appear in a list below the scan button. Tapping a device connects to it and the scan stops.
- After connecting, the device list disappears and is replaced by the green "Connected" status with live HR preview.

**Step 1: Create `SetupViewModel.kt`**

```kotlin
package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.service.BleHrManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SegmentInput(
    val distanceKm: String = "",
    val targetHr: String = ""
)

data class SetupUiState(
    val mode: WorkoutMode = WorkoutMode.STEADY_STATE,
    val steadyStateHr: String = "140",
    val segments: List<SegmentInput> = listOf(SegmentInput("5.0", "140")),
    val bufferBpm: String = "5",
    val alertDelaySec: String = "15",
    val isScanning: Boolean = false,
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val isHrConnected: Boolean = false,
    val connectedDeviceName: String = "",
    val liveHr: Int = 0
)

@HiltViewModel
class SetupViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    // BleHrManager is created here so the Setup screen can scan/connect
    // before starting the workout. It will be passed to the service via config.
    var bleHrManager: BleHrManager? = null
        private set

    fun initBle(context: android.content.Context) {
        if (bleHrManager == null) {
            bleHrManager = BleHrManager(context)
        }
    }

    fun setMode(mode: WorkoutMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun setSteadyStateHr(value: String) {
        _uiState.value = _uiState.value.copy(steadyStateHr = value)
    }

    fun updateSegment(index: Int, segment: SegmentInput) {
        val segments = _uiState.value.segments.toMutableList()
        if (index < segments.size) {
            segments[index] = segment
            _uiState.value = _uiState.value.copy(segments = segments)
        }
    }

    fun addSegment() {
        val segments = _uiState.value.segments.toMutableList()
        segments.add(SegmentInput())
        _uiState.value = _uiState.value.copy(segments = segments)
    }

    fun removeSegment(index: Int) {
        val segments = _uiState.value.segments.toMutableList()
        if (segments.size > 1) {
            segments.removeAt(index)
            _uiState.value = _uiState.value.copy(segments = segments)
        }
    }

    fun setBufferBpm(value: String) {
        _uiState.value = _uiState.value.copy(bufferBpm = value)
    }

    fun setAlertDelaySec(value: String) {
        _uiState.value = _uiState.value.copy(alertDelaySec = value)
    }

    fun startScan() {
        bleHrManager?.startScan()
        _uiState.value = _uiState.value.copy(isScanning = true)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        bleHrManager?.connectToDevice(device)
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            isHrConnected = true,
            connectedDeviceName = device.name ?: device.address
        )
    }

    fun updateDiscoveredDevices(devices: List<BluetoothDevice>) {
        _uiState.value = _uiState.value.copy(discoveredDevices = devices)
    }

    fun updateConnectionState(connected: Boolean) {
        _uiState.value = _uiState.value.copy(isHrConnected = connected)
    }

    fun updateLiveHr(hr: Int) {
        _uiState.value = _uiState.value.copy(liveHr = hr)
    }

    fun buildConfig(): WorkoutConfig? {
        val state = _uiState.value
        val buffer = state.bufferBpm.toIntOrNull() ?: 5
        val delay = state.alertDelaySec.toIntOrNull() ?: 15

        return when (state.mode) {
            WorkoutMode.STEADY_STATE -> {
                val hr = state.steadyStateHr.toIntOrNull() ?: return null
                WorkoutConfig(
                    mode = WorkoutMode.STEADY_STATE,
                    steadyStateTargetHr = hr,
                    bufferBpm = buffer,
                    alertDelaySec = delay
                )
            }
            WorkoutMode.DISTANCE_PROFILE -> {
                val segments = state.segments.mapNotNull { seg ->
                    val dist = seg.distanceKm.toFloatOrNull() ?: return@mapNotNull null
                    val hr = seg.targetHr.toIntOrNull() ?: return@mapNotNull null
                    HrSegment(distanceMeters = dist * 1000f, targetHr = hr)
                }
                if (segments.isEmpty()) return null
                WorkoutConfig(
                    mode = WorkoutMode.DISTANCE_PROFILE,
                    segments = segments,
                    bufferBpm = buffer,
                    alertDelaySec = delay
                )
            }
        }
    }

    val isStartEnabled: Boolean
        get() {
            val state = _uiState.value
            if (!state.isHrConnected) return false
            return buildConfig() != null
        }

    override fun onCleared() {
        super.onCleared()
        // Don't destroy BLE here — it gets handed to the service
    }
}
```

**Step 2: Create `SetupScreen.kt`**

This is a large file. Build it exactly as specified in the UI diagram above.

```kotlin
package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.ui.theme.*

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onStartWorkout: (configJson: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Init BLE manager
    LaunchedEffect(Unit) {
        viewModel.initBle(context)
    }

    // Observe BLE flows
    LaunchedEffect(viewModel.bleHrManager) {
        viewModel.bleHrManager?.let { ble ->
            kotlinx.coroutines.launch {
                ble.discoveredDevices.collect { viewModel.updateDiscoveredDevices(it) }
            }
            kotlinx.coroutines.launch {
                ble.isConnected.collect { viewModel.updateConnectionState(it) }
            }
            kotlinx.coroutines.launch {
                ble.heartRate.collect { viewModel.updateLiveHr(it) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Title
        Text(
            text = "HR Coach",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Mode toggle — SegmentedButton row
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == WorkoutMode.STEADY_STATE,
                onClick = { viewModel.setMode(WorkoutMode.STEADY_STATE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Steady State") }
            SegmentedButton(
                selected = state.mode == WorkoutMode.DISTANCE_PROFILE,
                onClick = { viewModel.setMode(WorkoutMode.DISTANCE_PROFILE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Distance Profile") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Mode-specific inputs
        when (state.mode) {
            WorkoutMode.STEADY_STATE -> {
                OutlinedTextField(
                    value = state.steadyStateHr,
                    onValueChange = { viewModel.setSteadyStateHr(it) },
                    label = { Text("Target HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            WorkoutMode.DISTANCE_PROFILE -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.segments.forEachIndexed { index, segment ->
                            Text(
                                "Segment ${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DisabledGray
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = segment.distanceKm,
                                    onValueChange = {
                                        viewModel.updateSegment(index, segment.copy(distanceKm = it))
                                    },
                                    label = { Text("km") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = segment.targetHr,
                                    onValueChange = {
                                        viewModel.updateSegment(index, segment.copy(targetHr = it))
                                    },
                                    label = { Text("bpm") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                if (state.segments.size > 1) {
                                    IconButton(onClick = { viewModel.removeSegment(index) }) {
                                        Icon(Icons.Default.Close, "Remove", tint = ZoneRed)
                                    }
                                }
                            }
                            if (index < state.segments.lastIndex) {
                                HorizontalDivider(
                                    color = DividerColor,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.addSegment() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Segment")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Settings card
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.bufferBpm,
                    onValueChange = { viewModel.setBufferBpm(it) },
                    label = { Text("Buffer (±bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.alertDelaySec,
                    onValueChange = { viewModel.setAlertDelaySec(it) },
                    label = { Text("Alert delay (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // HR Monitor connection card
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("HR Monitor", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                Spacer(modifier = Modifier.height(8.dp))

                if (state.isHrConnected) {
                    // Connected state
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = ZoneGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Connected: ${state.connectedDeviceName}",
                                color = ZoneGreen,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (state.liveHr > 0) {
                                Text(
                                    "HR: ${state.liveHr} bpm",
                                    color = OnSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    // Scan button
                    FilledTonalButton(
                        onClick = { viewModel.startScan() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isScanning
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isScanning) "Scanning..." else "Scan for Devices")
                    }

                    // Device list
                    if (state.discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        state.discoveredDevices.forEach { device ->
                            DeviceRow(device = device) {
                                viewModel.connectToDevice(device)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Start button
        Button(
            onClick = {
                val config = viewModel.buildConfig() ?: return@Button
                val json = com.google.gson.Gson().toJson(config)
                onStartWorkout(json)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = viewModel.isStartEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                disabledContainerColor = DisabledGray
            )
        ) {
            Text("START WORKOUT", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name ?: "Unknown",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodySmall,
            color = DisabledGray
        )
    }
}
```

**Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/
git commit -m "feat: add Setup screen with mode toggle, segment editor, BLE scan, and settings"
```

---

## Task 12: Active Workout Screen

**Goal:** The screen shown during a run. Large, glanceable HR display with zone color feedback.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

### DETAILED UI SPECIFICATION

This screen is designed to be glanceable while running. The layout is centered, with very large text. Dark background with a colored glow effect behind the HR number to indicate zone status.

```
┌──────────────────────────────────────────┐
│                                          │
│        [status bar area]                 │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  HR Monitor Disconnected           │  │  ← Only shown when BLE is
│  └────────────────────────────────────┘  │    disconnected. Red background
│                                          │    (#F44336 at 20% opacity),
│                                          │    white text, 8dp corner radius,
│                                          │    full width, 40dp height.
│                                          │
│                                          │
│                                          │
│              [large spacer]              │
│                                          │
│                                          │
│              1 4 2                        │  ← displayLarge (96sp), Bold
│                                          │    Color = ZoneGreen/Orange/Red
│              bpm                          │  ← bodySmall (12sp), centered
│                                          │    below the number, gray
│                                          │
│         Target: 140 ± 5                  │  ← bodyLarge (16sp), centered,
│                                          │    OnSurface color (#E0E0E0)
│                                          │
│         ● IN ZONE                        │  ← titleLarge (20sp), centered
│                                          │    The dot and text use the zone
│                                          │    color (Green/Orange/Red).
│                                          │    Text values:
│                                          │    IN_ZONE → "IN ZONE"
│                                          │    ABOVE_ZONE → "SLOW DOWN"
│                                          │    BELOW_ZONE → "SPEED UP"
│                                          │    NO_DATA → "WAITING..."
│                                          │
│              [spacer]                    │
│                                          │
│         2.34 km                          │  ← headlineMedium (24sp),
│                                          │    centered, OnBackground color
│                                          │
│              [large spacer]              │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │            STOP                    │  │  ← Full width button, 56dp
│  └────────────────────────────────────┘  │    height, ZoneRed background,
│                                          │    white text
│  [bottom padding 32dp]                   │
└──────────────────────────────────────────┘
```

**Key UI behaviors:**
- The HR number color changes in real time based on ZoneStatus: `IN_ZONE → ZoneGreen`, `ABOVE_ZONE → ZoneRed`, `BELOW_ZONE → ZoneOrange`, `NO_DATA → DisabledGray`.
- The zone status label also changes color and text to match.
- When HR is 0 (no reading yet), display "---" instead of "0".
- Distance shows 2 decimal places, always in km (divide meters by 1000).
- The "HR Monitor Disconnected" banner only appears when `hrConnected = false`. It sits at the top, above the HR display. It uses a semi-transparent red background so it's noticeable but not overwhelming.
- The STOP button is always enabled (you can always stop a workout).

**Step 1: Create `ActiveWorkoutScreen.kt`**

```kotlin
package com.hrcoach.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.theme.*

@Composable
fun ActiveWorkoutScreen(
    onStop: () -> Unit
) {
    val currentHr by WorkoutState.currentHr.collectAsState()
    val targetHr by WorkoutState.targetHr.collectAsState()
    val zoneStatus by WorkoutState.zoneStatus.collectAsState()
    val distanceMeters by WorkoutState.distanceMeters.collectAsState()
    val hrConnected by WorkoutState.hrConnected.collectAsState()
    val bufferBpm = 5 // TODO: pass from config

    val zoneColor = when (zoneStatus) {
        ZoneStatus.IN_ZONE -> ZoneGreen
        ZoneStatus.ABOVE_ZONE -> ZoneRed
        ZoneStatus.BELOW_ZONE -> ZoneOrange
        ZoneStatus.NO_DATA -> DisabledGray
    }

    val zoneLabel = when (zoneStatus) {
        ZoneStatus.IN_ZONE -> "IN ZONE"
        ZoneStatus.ABOVE_ZONE -> "SLOW DOWN"
        ZoneStatus.BELOW_ZONE -> "SPEED UP"
        ZoneStatus.NO_DATA -> "WAITING..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Disconnected banner
        if (!hrConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        ZoneRed.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "HR Monitor Disconnected",
                    color = ZoneRed,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Giant HR number
        Text(
            text = if (currentHr > 0) currentHr.toString() else "---",
            style = MaterialTheme.typography.displayLarge,
            color = zoneColor,
            textAlign = TextAlign.Center
        )

        // "bpm" label
        Text(
            text = "bpm",
            style = MaterialTheme.typography.bodySmall,
            color = DisabledGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target display
        Text(
            text = "Target: $targetHr ± $bufferBpm",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Zone status indicator
        Text(
            text = "● $zoneLabel",
            style = MaterialTheme.typography.titleLarge,
            color = zoneColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Distance
        Text(
            text = "%.2f km".format(distanceMeters / 1000f),
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Stop button
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZoneRed)
        ) {
            Text("STOP", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/
git commit -m "feat: add Active Workout screen with glanceable HR display and zone color"
```

---

## Task 13: History Screens

**Goal:** Two sub-screens: a list of past workouts, and a detail view showing the route on Google Maps with an HR heatmap polyline.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`
- Create: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

### DETAILED UI SPECIFICATION — History List

```
┌──────────────────────────────────────────┐
│  [top padding 16dp]                      │
│                                          │
│  History                (headlineMedium) │
│                                          │
│  [12dp spacing]                          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  Feb 25, 2026                      │  │  ← Card, Surface background,
│  │  3.45 km  •  32:15                 │  │    8dp corner radius. Tapping
│  │                                    │  │    navigates to detail.
│  └────────────────────────────────────┘  │    Date: bodyLarge, OnSurface
│  [8dp spacing]                           │    Distance + duration: bodySmall,
│  ┌────────────────────────────────────┐  │    DisabledGray. Separated by
│  │  Feb 23, 2026                      │  │    a "•" character.
│  │  5.12 km  •  48:03                 │  │    Padding inside card: 16dp.
│  └────────────────────────────────────┘  │
│  [8dp spacing]                           │
│  ... (LazyColumn)                        │
│                                          │
│  (if no workouts):                       │
│  ┌────────────────────────────────────┐  │
│  │                                    │  │
│  │     No workouts yet                │  │  ← Centered, bodyLarge,
│  │     Go run!                        │  │    DisabledGray color
│  │                                    │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### DETAILED UI SPECIFICATION — History Detail (Map)

```
┌──────────────────────────────────────────┐
│  [← Back]  Feb 25, 2026                 │  ← TopAppBar with back arrow
│                                          │    and date as title
│  ┌────────────────────────────────────┐  │
│  │                                    │  │
│  │                                    │  │
│  │        GOOGLE MAP                  │  │  ← GoogleMap composable fills
│  │                                    │  │    the remaining screen space.
│  │     ╭──────╮                       │  │    Camera auto-zooms to fit
│  │    ╱ (green) ╲                     │  │    the entire route polyline
│  │   ╱           ╲                    │  │    with 64dp padding.
│  │  │  (yellow)   │                   │  │
│  │   ╲           ╱                    │  │    The polyline is drawn as
│  │    ╲  (red)  ╱                     │  │    multiple segments. Each
│  │     ╰──────╯                       │  │    segment's color is based
│  │                                    │  │    on the HR at that point:
│  │                                    │  │
│  │  HR Color mapping:                 │  │    ≤100 bpm → HeatmapGreen
│  │  Linear interpolation:             │  │    150 bpm  → HeatmapYellow
│  │  100→Green, 150→Yellow, 200→Red    │  │    ≥200 bpm → HeatmapRed
│  │                                    │  │
│  │  Polyline width: 8f               │  │    Between these values,
│  │                                    │  │    interpolate linearly
│  │                                    │  │    between the two nearest
│  └────────────────────────────────────┘  │    colors using Android's
│                                          │    ArgbEvaluator.
│  ┌────────────────────────────────────┐  │
│  │  3.45 km  •  32:15  •  Avg 148bpm │  │  ← Card at bottom, Surface bg
│  └────────────────────────────────────┘  │    Stats in a single row,
│  [bottom padding 8dp]                    │    bodyLarge, separated by •
└──────────────────────────────────────────┘
```

**Step 1: Create `HistoryViewModel.kt`**

```kotlin
package com.hrcoach.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    val workouts: StateFlow<List<WorkoutEntity>> = repository.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trackPoints = MutableStateFlow<List<TrackPointEntity>>(emptyList())
    val trackPoints: StateFlow<List<TrackPointEntity>> = _trackPoints

    private val _selectedWorkout = MutableStateFlow<WorkoutEntity?>(null)
    val selectedWorkout: StateFlow<WorkoutEntity?> = _selectedWorkout

    fun loadWorkoutDetail(workoutId: Long) {
        viewModelScope.launch {
            _selectedWorkout.value = repository.getWorkoutById(workoutId)
            _trackPoints.value = repository.getTrackPoints(workoutId)
        }
    }
}
```

**Step 2: Create `HistoryListScreen.kt`**

```kotlin
package com.hrcoach.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryListScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onWorkoutClick: (Long) -> Unit
) {
    val workouts by viewModel.workouts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No workouts yet", style = MaterialTheme.typography.bodyLarge, color = DisabledGray)
                    Text("Go run!", style = MaterialTheme.typography.bodyLarge, color = DisabledGray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(workouts) { workout ->
                    WorkoutCard(workout = workout, onClick = { onWorkoutClick(workout.id) })
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val durationMs = workout.endTime - workout.startTime
    val durationMin = (durationMs / 1000 / 60).toInt()
    val durationSec = ((durationMs / 1000) % 60).toInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                dateFormat.format(Date(workout.startTime)),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "%.2f km  •  %d:%02d".format(
                    workout.totalDistanceMeters / 1000f,
                    durationMin,
                    durationSec
                ),
                style = MaterialTheme.typography.bodySmall,
                color = DisabledGray
            )
        }
    }
}
```

**Step 3: Create `HistoryDetailScreen.kt`**

```kotlin
package com.hrcoach.ui.history

import android.animation.ArgbEvaluator
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    workoutId: Long,
    viewModel: HistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutDetail(workoutId)
    }

    val workout by viewModel.selectedWorkout.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(workout?.let { dateFormat.format(Date(it.startTime)) } ?: "Workout")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map takes up most of the screen
            Box(modifier = Modifier.weight(1f)) {
                if (trackPoints.size >= 2) {
                    HrHeatmapMap(trackPoints = trackPoints)
                }
            }

            // Stats bar at bottom
            workout?.let { w ->
                val durationMs = w.endTime - w.startTime
                val durationMin = (durationMs / 1000 / 60).toInt()
                val durationSec = ((durationMs / 1000) % 60).toInt()
                val avgHr = if (trackPoints.isNotEmpty()) {
                    trackPoints.map { it.heartRate }.average().toInt()
                } else 0

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "%.2f km  •  %d:%02d  •  Avg %d bpm".format(
                            w.totalDistanceMeters / 1000f,
                            durationMin,
                            durationSec,
                            avgHr
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HrHeatmapMap(trackPoints: List<TrackPointEntity>) {
    val cameraPositionState = rememberCameraPositionState()

    // Fit camera to route bounds
    LaunchedEffect(trackPoints) {
        if (trackPoints.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                trackPoints.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 64))
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false)
    ) {
        // Draw each segment between consecutive points with HR-based color
        for (i in 0 until trackPoints.size - 1) {
            val p1 = trackPoints[i]
            val p2 = trackPoints[i + 1]
            val color = hrToColor(p2.heartRate)

            Polyline(
                points = listOf(
                    LatLng(p1.latitude, p1.longitude),
                    LatLng(p2.latitude, p2.longitude)
                ),
                color = color,
                width = 8f
            )
        }
    }
}

/**
 * Maps a heart rate (BPM) to a color on a green → yellow → red gradient.
 *
 *   ≤100 bpm → HeatmapGreen (#4CAF50)
 *    150 bpm → HeatmapYellow (#FFEB3B)
 *   ≥200 bpm → HeatmapRed   (#F44336)
 *
 * Values between these are linearly interpolated using ArgbEvaluator.
 */
private fun hrToColor(hr: Int): Color {
    val evaluator = ArgbEvaluator()
    val greenArgb = android.graphics.Color.parseColor("#4CAF50")
    val yellowArgb = android.graphics.Color.parseColor("#FFEB3B")
    val redArgb = android.graphics.Color.parseColor("#F44336")

    val argb = when {
        hr <= 100 -> greenArgb
        hr <= 150 -> evaluator.evaluate((hr - 100) / 50f, greenArgb, yellowArgb) as Int
        hr <= 200 -> evaluator.evaluate((hr - 150) / 50f, yellowArgb, redArgb) as Int
        else -> redArgb
    }
    return Color(argb)
}
```

**Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/
git commit -m "feat: add History list and detail screens with HR heatmap map"
```

---

## Task 14: Navigation & Permissions

**Goal:** Wire up the three screens with Compose Navigation. Handle runtime permissions for BLE and Location on app start.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/MainActivity.kt`

**Step 1: Create `NavGraph.kt`**

```kotlin
package com.hrcoach.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.history.HistoryDetailScreen
import com.hrcoach.ui.history.HistoryListScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.theme.*
import com.hrcoach.ui.workout.ActiveWorkoutScreen

object Routes {
    const val SETUP = "setup"
    const val WORKOUT = "workout"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{workoutId}"
}

@Composable
fun HrCoachNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isWorkoutRunning by WorkoutState.isRunning.collectAsState()

    // If a workout is running and we're not on the workout screen, navigate there
    LaunchedEffect(isWorkoutRunning) {
        if (isWorkoutRunning) {
            navController.navigate(Routes.WORKOUT) {
                popUpTo(Routes.SETUP) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (!isWorkoutRunning) {
                NavigationBar(containerColor = Surface) {
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETUP,
                        onClick = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.FavoriteBorder, "Workout") },
                        label = { Text("Workout") }
                    )
                    NavigationBarItem(
                        selected = currentRoute?.startsWith("history") == true,
                        onClick = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(Routes.SETUP)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.List, "History") },
                        label = { Text("History") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SETUP,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    onStartWorkout = { configJson ->
                        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                            action = WorkoutForegroundService.ACTION_START
                            putExtra(WorkoutForegroundService.EXTRA_CONFIG_JSON, configJson)
                        }
                        context.startForegroundService(intent)
                    }
                )
            }

            composable(Routes.WORKOUT) {
                ActiveWorkoutScreen(
                    onStop = {
                        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                            action = WorkoutForegroundService.ACTION_STOP
                        }
                        context.startService(intent)
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryListScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate("history/$workoutId")
                    }
                )
            }

            composable(
                Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                HistoryDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
```

**Step 2: Update `MainActivity.kt`** — add permissions and wire up nav graph

```kotlin
package com.hrcoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.hrcoach.ui.navigation.HrCoachNavGraph
import com.hrcoach.ui.theme.HrCoachTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() = buildList {
            // BLE (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // Location (GPS tracking)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — UI handles missing permissions gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions on first launch
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            HrCoachTheme {
                HrCoachNavGraph()
            }
        }
    }
}
```

**Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/ app/src/main/java/com/hrcoach/MainActivity.kt
git commit -m "feat: add navigation with bottom bar, permissions, and service start/stop wiring"
```

---

## Task 15: Final Integration & Build Verification

**Goal:** Make sure the full app compiles, the debug APK builds, and all unit tests pass.

**Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: All tests PASS (ZoneEngineTest)

**Step 2: Build debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`

**Step 3: Final commit**

```bash
git add -A
git commit -m "chore: verify full build and test suite passes"
```

---

## Summary

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Project scaffolding | `build.gradle.kts`, `AndroidManifest.xml` |
| 2 | Domain models | `WorkoutMode`, `HrSegment`, `WorkoutConfig`, `ZoneStatus` |
| 3 | ZoneEngine (TDD) | `ZoneEngine.kt`, `ZoneEngineTest.kt` |
| 4 | Room database | `WorkoutEntity`, `TrackPointEntity`, DAOs, `AppDatabase` |
| 5 | Hilt DI | `WorkoutRepository`, `AppModule` |
| 6 | BLE HR Manager | `BleHrManager.kt` |
| 7 | GPS Distance Tracker | `GpsDistanceTracker.kt` |
| 8 | Alert Manager | `AlertManager.kt` (ToneGenerator on STREAM_NOTIFICATION) |
| 9 | Foreground Service | `WorkoutForegroundService.kt`, `WorkoutState.kt` |
| 10 | Theme | `Color.kt`, `Type.kt`, `Theme.kt` |
| 11 | Setup Screen | `SetupScreen.kt`, `SetupViewModel.kt` |
| 12 | Active Workout Screen | `ActiveWorkoutScreen.kt` |
| 13 | History Screens | `HistoryListScreen.kt`, `HistoryDetailScreen.kt`, `HistoryViewModel.kt` |
| 14 | Navigation & Permissions | `NavGraph.kt`, `MainActivity.kt` |
| 15 | Integration & Build | Full build + test verification |
