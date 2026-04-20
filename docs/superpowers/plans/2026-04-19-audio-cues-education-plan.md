# Audio Cues — Education & Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Cardea's audio cue system intuitive and self-teaching through a Sound Library settings screen, in-workout cue banners, TTS rephrasing, a first-run primer, direction-coded vibration, and a first-three-runs post-run recap.

**Architecture:** One new Composable screen (`SoundLibraryScreen`), one new overlay component (cue banner on `ActiveWorkoutScreen`), one new dialog (`AudioPrimerDialog`), one new post-run section, plus targeted edits to `CoachingAudioManager`, `VibrationManager`, `WorkoutState`, `AudioSettings`, `WorkoutMetricsEntity` (+ Room migration 18→19). Strings and cue metadata centralize in a new `CueCopy` table so screen/banner/recap all read the same source of truth.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (version 18 → 19), Gson-backed SharedPreferences for `AudioSettings`, Firebase RTDB for cloud backup sync.

**Spec:** [`docs/superpowers/specs/2026-04-19-audio-cues-education-design.md`](../specs/2026-04-19-audio-cues-education-design.md)

---

## File Map

**Create:**
- `app/src/main/java/com/hrcoach/service/audio/CueCopy.kt` — central table of cue display strings, icons, and kind
- `app/src/main/java/com/hrcoach/service/audio/CueBanner.kt` — data class + enum for the overlay payload
- `app/src/main/java/com/hrcoach/ui/account/SoundLibraryScreen.kt` — the settings library screen
- `app/src/main/java/com/hrcoach/ui/workout/CueBannerOverlay.kt` — transient in-workout banner composable
- `app/src/main/java/com/hrcoach/ui/workout/AudioPrimerDialog.kt` — 3-slide one-time primer
- `app/src/main/java/com/hrcoach/ui/postrun/SoundsHeardSection.kt` — post-run recap section
- `app/src/test/java/com/hrcoach/service/audio/CueCopyTest.kt`
- `app/src/test/java/com/hrcoach/service/audio/VibrationManagerDispatchTest.kt` (pure logic portion only)
- `app/src/test/java/com/hrcoach/data/repository/AudioSettingsPrimerTest.kt`

**Modify:**
- `app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt` — add `audioPrimerShown: Boolean = false`
- `app/src/main/java/com/hrcoach/data/repository/AudioSettingsRepository.kt` — add `setAudioPrimerShown(Boolean)`
- `app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt` — include `audioPrimerShown`
- `app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt` — restore `audioPrimerShown`
- `app/src/main/java/com/hrcoach/data/db/WorkoutMetricsEntity.kt` — add `cueCountsJson: String? = null`
- `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt` — bump version to 19 + migration
- `app/src/main/java/com/hrcoach/service/WorkoutState.kt` — add `lastCueBanner` + `flashCueBanner()`
- `app/src/main/java/com/hrcoach/service/audio/VibrationManager.kt` — add `pulseForEvent()`, `pulseSpeedUp()`, `pulseSlowDown()`
- `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt` — flash banner on every event, increment cue count, use `pulseForEvent`
- `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt` — rephrased strings, direction-aware predictive fallback
- `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt` — write `cueCountsJson` to `WorkoutMetrics` at stop
- `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt` — render `CueBannerOverlay`
- `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` — add "Sound library" row
- `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` — add `SOUND_LIBRARY` route
- `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt` — primer gating + one-shot event
- `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` — render primer
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` — primer gating
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` — render primer
- `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt` — expose cue counts + workout index
- `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — render `SoundsHeardSection` for first 3 runs

---

## Task 1: CueCopy table + CueBanner data types

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/audio/CueBanner.kt`
- Create: `app/src/main/java/com/hrcoach/service/audio/CueCopy.kt`
- Create: `app/src/test/java/com/hrcoach/service/audio/CueCopyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/hrcoach/service/audio/CueCopyTest.kt`:

```kotlin
package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CueCopyTest {

    @Test
    fun every_coaching_event_has_a_copy_entry() {
        CoachingEvent.values().forEach { event ->
            val entry = CueCopy.forEvent(event)
            assertNotNull(entry, "No CueCopy entry for $event")
            assertTrue(entry.title.isNotBlank(), "Blank title for $event")
            assertTrue(entry.subtitle.isNotBlank(), "Blank subtitle for $event")
        }
    }

    @Test
    fun all_entries_exposed_in_displayOrder() {
        val ordered = CueCopy.displayOrder.map { it }.toSet()
        val all = CoachingEvent.values().toSet()
        assertTrue(ordered.containsAll(all), "displayOrder missing events: ${all - ordered}")
    }

    @Test
    fun sections_cover_all_events() {
        val covered = CueCopy.sections.flatMap { it.events }.toSet()
        val all = CoachingEvent.values().toSet()
        assertTrue(covered == all, "Section coverage mismatch. Missing: ${all - covered}")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.CueCopyTest"`
Expected: FAIL with "Unresolved reference: CueCopy"

- [ ] **Step 3: Create CueBanner.kt**

```kotlin
package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent

enum class CueBannerKind { ALERT, GUIDANCE, MILESTONE, INFO }

data class CueBanner(
    val event: CoachingEvent,
    val title: String,
    val subtitle: String,
    val kind: CueBannerKind,
    val firedAtMs: Long
)
```

- [ ] **Step 4: Create CueCopy.kt**

```kotlin
package com.hrcoach.service.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.hrcoach.domain.model.CoachingEvent

/**
 * Single source of truth for how each [CoachingEvent] is presented to the user.
 * Read by:
 *   - [CueBannerOverlay] during the active workout
 *   - SoundLibraryScreen in settings
 *   - SoundsHeardSection on the post-run summary
 *
 * When adding a new [CoachingEvent] enum value, you MUST add it here AND to
 * [displayOrder] AND to a [Section]. The CueCopyTest will fail otherwise.
 */
object CueCopy {

    data class Entry(
        val title: String,
        val subtitle: String,
        val kind: CueBannerKind,
        val icon: ImageVector
    )

    data class Section(
        val heading: String,
        val caption: String,
        val events: List<CoachingEvent>
    )

    private val entries: Map<CoachingEvent, Entry> = mapOf(
        CoachingEvent.SPEED_UP to Entry(
            title = "Speed up",
            subtitle = "You're slower than your target — pick up the pace.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.ArrowUpward
        ),
        CoachingEvent.SLOW_DOWN to Entry(
            title = "Slow down",
            subtitle = "You're working harder than your target — ease off.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.ArrowDownward
        ),
        CoachingEvent.SIGNAL_LOST to Entry(
            title = "Signal lost",
            subtitle = "Heart-rate sensor disconnected. Check your strap.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.Favorite
        ),
        CoachingEvent.SIGNAL_REGAINED to Entry(
            title = "Signal back",
            subtitle = "Heart-rate sensor reconnected.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Favorite
        ),
        CoachingEvent.RETURN_TO_ZONE to Entry(
            title = "Back in zone",
            subtitle = "You just came back into your target.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Check
        ),
        CoachingEvent.PREDICTIVE_WARNING to Entry(
            title = "Heads up",
            subtitle = "You're in zone now, but trending toward the edge. Adjust early.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Notifications
        ),
        CoachingEvent.SEGMENT_CHANGE to Entry(
            title = "Next interval",
            subtitle = "New interval starting now.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.ChevronRight
        ),
        CoachingEvent.KM_SPLIT to Entry(
            title = "Split",
            subtitle = "You just passed a distance marker.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Timer
        ),
        CoachingEvent.HALFWAY to Entry(
            title = "Halfway",
            subtitle = "You've reached 50% of your target.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Timer
        ),
        CoachingEvent.WORKOUT_COMPLETE to Entry(
            title = "Workout complete",
            subtitle = "You've reached your target distance or time.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Check
        ),
        CoachingEvent.IN_ZONE_CONFIRM to Entry(
            title = "Holding zone",
            subtitle = "Cruising nicely — a periodic check-in while you're steady.",
            kind = CueBannerKind.INFO,
            icon = Icons.Default.FavoriteBorder
        ),
    )

    fun forEvent(event: CoachingEvent): Entry =
        entries[event] ?: error("No CueCopy entry for $event. Add it in CueCopy.entries.")

    val displayOrder: List<CoachingEvent> = listOf(
        CoachingEvent.SPEED_UP,
        CoachingEvent.SLOW_DOWN,
        CoachingEvent.SIGNAL_LOST,
        CoachingEvent.SIGNAL_REGAINED,
        CoachingEvent.RETURN_TO_ZONE,
        CoachingEvent.PREDICTIVE_WARNING,
        CoachingEvent.SEGMENT_CHANGE,
        CoachingEvent.KM_SPLIT,
        CoachingEvent.HALFWAY,
        CoachingEvent.WORKOUT_COMPLETE,
        CoachingEvent.IN_ZONE_CONFIRM
    )

    val sections: List<Section> = listOf(
        Section(
            heading = "Zone alerts",
            caption = "Fires when you're outside your heart-rate target for more than 30 seconds.",
            events = listOf(
                CoachingEvent.SPEED_UP,
                CoachingEvent.SLOW_DOWN,
                CoachingEvent.SIGNAL_LOST,
                CoachingEvent.SIGNAL_REGAINED
            )
        ),
        Section(
            heading = "Pace guidance",
            caption = "Coaching that runs even when you're in zone.",
            events = listOf(
                CoachingEvent.RETURN_TO_ZONE,
                CoachingEvent.PREDICTIVE_WARNING,
                CoachingEvent.SEGMENT_CHANGE
            )
        ),
        Section(
            heading = "Milestones",
            caption = "Distance and completion markers.",
            events = listOf(
                CoachingEvent.KM_SPLIT,
                CoachingEvent.HALFWAY,
                CoachingEvent.WORKOUT_COMPLETE
            )
        ),
        Section(
            heading = "Reassurance",
            caption = "A periodic check-in while you're cruising in zone.",
            events = listOf(
                CoachingEvent.IN_ZONE_CONFIRM
            )
        )
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.CueCopyTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/CueBanner.kt \
        app/src/main/java/com/hrcoach/service/audio/CueCopy.kt \
        app/src/test/java/com/hrcoach/service/audio/CueCopyTest.kt
git commit -m "feat(audio): add CueCopy table and CueBanner types"
```

---

## Task 2: WorkoutState — add lastCueBanner + flashCueBanner()

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

- [ ] **Step 1: Add field to WorkoutSnapshot**

Insert as the final field of `WorkoutSnapshot`:

```kotlin
val hrMaxUpdatedDelta: Pair<Int, Int>? = null,
// Transient banner shown over the active workout screen for ~3.5 s whenever
// a coaching event fires. Auto-cleared by flashCueBanner's own delay coroutine.
val lastCueBanner: com.hrcoach.service.audio.CueBanner? = null,
```

- [ ] **Step 2: Add flashCueBanner helper to WorkoutState**

Replace the `object WorkoutState { ... }` block with:

```kotlin
object WorkoutState {
    private val _snapshot = MutableStateFlow(WorkoutSnapshot())
    val snapshot: StateFlow<WorkoutSnapshot> = _snapshot.asStateFlow()

    // Supervisor scope so a cancellation of one banner-clear coroutine doesn't
    // take down sibling workout-level coroutines. IO is fine — all we do is delay.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bannerClearJob: Job? = null

    private const val BANNER_VISIBLE_MS = 3500L

    fun setPendingBootcampSessionId(id: Long?) {
        _snapshot.update { it.copy(pendingBootcampSessionId = id) }
    }

    fun update(transform: (WorkoutSnapshot) -> WorkoutSnapshot) {
        _snapshot.update(transform)
    }

    fun set(snapshot: WorkoutSnapshot) {
        _snapshot.value = snapshot
    }

    fun reset() {
        _snapshot.update { current ->
            WorkoutSnapshot(
                completedWorkoutId = current.completedWorkoutId,
                pendingBootcampSessionId = current.pendingBootcampSessionId,
                hrMaxUpdatedDelta = current.hrMaxUpdatedDelta
            )
        }
        bannerClearJob?.cancel()
        bannerClearJob = null
    }

    fun clearCompletedWorkoutId() {
        _snapshot.update { it.copy(completedWorkoutId = null) }
    }

    fun clearHrMaxUpdatedDelta() {
        _snapshot.update { it.copy(hrMaxUpdatedDelta = null) }
    }

    /**
     * Sets [WorkoutSnapshot.lastCueBanner] and schedules a clear after [BANNER_VISIBLE_MS].
     * Cancels any previous pending clear so the new banner always gets its full visibility
     * window regardless of what fired before it.
     */
    fun flashCueBanner(banner: com.hrcoach.service.audio.CueBanner) {
        _snapshot.update { it.copy(lastCueBanner = banner) }
        bannerClearJob?.cancel()
        bannerClearJob = scope.launch {
            delay(BANNER_VISIBLE_MS)
            // Only clear if this is still the banner we set (another may have replaced it).
            _snapshot.update { current ->
                if (current.lastCueBanner?.firedAtMs == banner.firedAtMs) current.copy(lastCueBanner = null)
                else current
            }
        }
    }
}
```

Add these imports at the top:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
```

- [ ] **Step 3: Build check**

Run: `./gradlew assembleDebug`
Expected: SUCCESS (or the ambient `PartnerSection.kt` pre-existing failure per CLAUDE.md — ignore that one, but nothing new)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutState.kt
git commit -m "feat(audio): add lastCueBanner to WorkoutState with auto-clear"
```

---

## Task 3: AudioSettings.audioPrimerShown + cloud sync

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/AudioSettingsRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt`
- Modify: `app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt`
- Create: `app/src/test/java/com/hrcoach/data/repository/AudioSettingsPrimerTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/hrcoach/data/repository/AudioSettingsPrimerTest.kt`:

```kotlin
package com.hrcoach.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AudioSettingsPrimerTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun default_audioPrimerShown_is_false() {
        val repo = AudioSettingsRepository(context)
        assertFalse(repo.getAudioSettings().audioPrimerShown)
    }

    @Test
    fun setAudioPrimerShown_persists() {
        val repo = AudioSettingsRepository(context)
        repo.setAudioPrimerShown(true)
        assertTrue(repo.getAudioSettings().audioPrimerShown)
        repo.setAudioPrimerShown(false)
        assertFalse(repo.getAudioSettings().audioPrimerShown)
    }
}
```

This test is `@RunWith(AndroidJUnit4::class)` because `AudioSettingsRepository` uses real `Context.getSharedPreferences`. If the repo tests are already in `androidTest/`, colocate there; otherwise Robolectric dependency is already on the classpath (typical for this project).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.AudioSettingsPrimerTest"`
Expected: FAIL — `audioPrimerShown` is unresolved on `AudioSettings`.

- [ ] **Step 3: Add field to AudioSettings**

In `AudioSettings.kt`, add as the final constructor param:

```kotlin
data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean? = null,
    val enableKmSplits: Boolean? = null,
    val enableWorkoutComplete: Boolean? = null,
    val enableInZoneConfirm: Boolean? = null,
    val inZoneConfirmCadence: ConfirmCadence = ConfirmCadence.STANDARD,
    val minimalTierOneVoice: Boolean = true,
    // True after the user sees the first-workout audio primer. Default false for
    // fresh installs. See AudioPrimerDialog / SetupViewModel for gating logic.
    val audioPrimerShown: Boolean = false
)
```

- [ ] **Step 4: Add setter method to AudioSettingsRepository**

Open `AudioSettingsRepository.kt`. Find the existing `saveAudioSettings(...)` method. Immediately below it add:

```kotlin
fun setAudioPrimerShown(shown: Boolean) {
    val current = getAudioSettings()
    saveAudioSettings(current.copy(audioPrimerShown = shown))
}
```

Local Gson serialization already handles new nullable/defaulted fields per the CLAUDE.md note — no other local-path change needed.

- [ ] **Step 5: Update cloud backup**

In `CloudBackupManager.kt` `syncSettings()`, extend the `data` map. Insert before the closing `)`:

```kotlin
val data = mapOf(
    "earconVolume"          to audio.earconVolume,
    "voiceVolume"           to audio.voiceVolume,
    "voiceVerbosity"        to audio.voiceVerbosity.name,
    "enableVibration"       to audio.enableVibration,
    "enableHalfwayReminder" to audio.enableHalfwayReminder,
    "enableKmSplits"        to audio.enableKmSplits,
    "enableWorkoutComplete" to audio.enableWorkoutComplete,
    "enableInZoneConfirm"   to audio.enableInZoneConfirm,
    "inZoneConfirmCadence"  to audio.inZoneConfirmCadence.name,
    "minimalTierOneVoice"   to audio.minimalTierOneVoice,
    "audioPrimerShown"      to audio.audioPrimerShown,
    "autoPauseEnabled"      to autoPauseRepo.isAutoPauseEnabled(),
    "themeMode"             to themePrefsRepo.getThemeMode().name,
)
```

- [ ] **Step 6: Update cloud restore**

In `CloudRestoreManager.kt` `restoreSettings(...)`, extend the `AudioSettings(...)` constructor call. Insert before the closing `)`:

```kotlin
val audio = AudioSettings(
    earconVolume          = snap.child("earconVolume").getValue(Int::class.java) ?: 80,
    voiceVolume           = snap.child("voiceVolume").getValue(Int::class.java) ?: 80,
    voiceVerbosity        = snap.child("voiceVerbosity").getValue(String::class.java)?.let {
        runCatching { VoiceVerbosity.valueOf(it) }.getOrNull()
    } ?: VoiceVerbosity.MINIMAL,
    enableVibration       = snap.child("enableVibration").getValue(Boolean::class.java) ?: true,
    enableHalfwayReminder = snap.child("enableHalfwayReminder").getValue(Boolean::class.java),
    enableKmSplits        = snap.child("enableKmSplits").getValue(Boolean::class.java),
    enableWorkoutComplete = snap.child("enableWorkoutComplete").getValue(Boolean::class.java),
    enableInZoneConfirm   = snap.child("enableInZoneConfirm").getValue(Boolean::class.java),
    inZoneConfirmCadence  = snap.child("inZoneConfirmCadence").getValue(String::class.java)?.let {
        runCatching { ConfirmCadence.valueOf(it) }.getOrNull()
    } ?: ConfirmCadence.STANDARD,
    minimalTierOneVoice   = snap.child("minimalTierOneVoice").getValue(Boolean::class.java) ?: true,
    // Added 2026-04-19 for audio primer. Missing field on older backups defaults to false,
    // which will re-show the primer on next workout — acceptable, non-destructive.
    audioPrimerShown      = snap.child("audioPrimerShown").getValue(Boolean::class.java) ?: false,
)
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.AudioSettingsPrimerTest"`
Expected: PASS (2 tests)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt \
        app/src/main/java/com/hrcoach/data/repository/AudioSettingsRepository.kt \
        app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt \
        app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt \
        app/src/test/java/com/hrcoach/data/repository/AudioSettingsPrimerTest.kt
git commit -m "feat(audio): add audioPrimerShown flag + cloud sync"
```

---

## Task 4: WorkoutMetrics schema migration (cueCountsJson)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutMetricsEntity.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`

- [ ] **Step 1: Add field to entity**

In `WorkoutMetricsEntity.kt`, add as the final constructor param:

```kotlin
val environmentAffected: Boolean = false,
// JSON map of CoachingEvent.name -> count, populated at workout stop from
// CoachingAudioManager.cueCounts(). Read by the post-run "Sounds heard today"
// section. Null for workouts pre-migration or with no cues (OFF verbosity, etc.).
val cueCountsJson: String? = null,
```

- [ ] **Step 2: Bump DB version and add migration**

Open `AppDatabase.kt`. Find `version = 18` and change to:

```kotlin
version = 19,
```

Find the companion object with migrations. Add a new migration constant:

```kotlin
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_metrics ADD COLUMN cueCountsJson TEXT")
    }
}
```

Register the migration in whichever builder config the app uses (search for `MIGRATION_17_18` and add `MIGRATION_18_19` alongside it in the same `addMigrations(...)` call).

- [ ] **Step 3: Build + verify migration tests (if they exist)**

Run: `./gradlew assembleDebug`
Expected: SUCCESS. If the project has migration tests (look under `app/src/androidTest/`), run them:

Run: `./gradlew connectedAndroidTest --tests "*Migration*"` (only if the device is attached; skip if not).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/WorkoutMetricsEntity.kt \
        app/src/main/java/com/hrcoach/data/db/AppDatabase.kt
git commit -m "feat(db): workout_metrics.cueCountsJson column (v18 -> v19)"
```

---

## Task 5: VibrationManager.pulseForEvent dispatcher

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/VibrationManager.kt`
- Create: `app/src/test/java/com/hrcoach/service/audio/VibrationManagerDispatchTest.kt`

- [ ] **Step 1: Write failing test**

We can't unit-test actual vibration output without an Android runtime. Instead we test the dispatcher's routing decision via an enum indirection. Create `app/src/test/java/com/hrcoach/service/audio/VibrationManagerDispatchTest.kt`:

```kotlin
package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import org.junit.Test
import kotlin.test.assertEquals

class VibrationManagerDispatchTest {

    @Test
    fun speed_up_routes_to_speedUp_pattern() {
        assertEquals(VibrationPattern.SPEED_UP, VibrationManager.patternFor(CoachingEvent.SPEED_UP))
    }

    @Test
    fun slow_down_routes_to_slowDown_pattern() {
        assertEquals(VibrationPattern.SLOW_DOWN, VibrationManager.patternFor(CoachingEvent.SLOW_DOWN))
    }

    @Test
    fun signal_lost_routes_to_generic_alert() {
        assertEquals(VibrationPattern.GENERIC_ALERT, VibrationManager.patternFor(CoachingEvent.SIGNAL_LOST))
    }

    @Test
    fun informational_events_route_to_none() {
        listOf(
            CoachingEvent.IN_ZONE_CONFIRM,
            CoachingEvent.KM_SPLIT,
            CoachingEvent.HALFWAY,
            CoachingEvent.WORKOUT_COMPLETE,
            CoachingEvent.RETURN_TO_ZONE,
            CoachingEvent.PREDICTIVE_WARNING,
            CoachingEvent.SEGMENT_CHANGE,
            CoachingEvent.SIGNAL_REGAINED,
        ).forEach {
            assertEquals(VibrationPattern.NONE, VibrationManager.patternFor(it), "for $it")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VibrationManagerDispatchTest"`
Expected: FAIL — `VibrationPattern` and `VibrationManager.patternFor` unresolved.

- [ ] **Step 3: Extend VibrationManager**

Replace the file contents with:

```kotlin
package com.hrcoach.service.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hrcoach.domain.model.CoachingEvent

enum class VibrationPattern { SPEED_UP, SLOW_DOWN, GENERIC_ALERT, NONE }

class VibrationManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled: Boolean = true

    /**
     * Dispatches to a direction-coded pattern. At escalation tier 3, CoachingAudioManager
     * calls this instead of [pulseAlert] so the vibration *itself* encodes whether the
     * runner should speed up or slow down — learnable tactile signal.
     */
    fun pulseForEvent(event: CoachingEvent) {
        when (patternFor(event)) {
            VibrationPattern.SPEED_UP -> pulseSpeedUp()
            VibrationPattern.SLOW_DOWN -> pulseSlowDown()
            VibrationPattern.GENERIC_ALERT -> pulseAlert()
            VibrationPattern.NONE -> { /* no-op */ }
        }
    }

    /** "Hurry" — three short taps. */
    fun pulseSpeedUp() {
        vibrate(longArrayOf(0, 100, 80, 100, 80, 100), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    /** "Settle" — two slow heavy pulses. */
    fun pulseSlowDown() {
        vibrate(longArrayOf(0, 280, 150, 280), intArrayOf(0, 200, 0, 200))
    }

    /** Original generic alert pattern. Preserved for SIGNAL_LOST and any caller without an event. */
    fun pulseAlert() {
        vibrate(longArrayOf(0, 150, 100, 150), intArrayOf(0, 200, 0, 200))
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        deviceVibrator.vibrate(effect)
    }

    fun destroy() {
        vibrator?.cancel()
    }

    companion object {
        fun patternFor(event: CoachingEvent): VibrationPattern = when (event) {
            CoachingEvent.SPEED_UP -> VibrationPattern.SPEED_UP
            CoachingEvent.SLOW_DOWN -> VibrationPattern.SLOW_DOWN
            CoachingEvent.SIGNAL_LOST -> VibrationPattern.GENERIC_ALERT
            else -> VibrationPattern.NONE
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.audio.VibrationManagerDispatchTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VibrationManager.kt \
        app/src/test/java/com/hrcoach/service/audio/VibrationManagerDispatchTest.kt
git commit -m "feat(audio): direction-coded vibration patterns via pulseForEvent"
```

---

## Task 6: CoachingAudioManager — flash banner, count cues, use pulseForEvent

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Add cue count tracking + banner trigger to CoachingAudioManager**

In `CoachingAudioManager.kt`, add these imports at the top:

```kotlin
import com.hrcoach.service.WorkoutState
```

Just below `private var currentWorkoutMode: WorkoutMode = WorkoutMode.STEADY_STATE`, add:

```kotlin
private val cueCounts = mutableMapOf<CoachingEvent, Int>()

/** Called by WFS at stop time to persist into WorkoutMetrics. Returns defensive copy. */
fun consumeCueCounts(): Map<CoachingEvent, Int> {
    val snapshot = cueCounts.toMap()
    cueCounts.clear()
    return snapshot
}
```

Inside `fireEvent(...)`, immediately after the initial `when (event)` toggle filter block (around line 73, right after `else -> { /* coaching alerts always pass through */ }`), add:

```kotlin
// Count every cue that *passes* the toggle filter. This is what SoundsHeardSection reads.
cueCounts.merge(event, 1) { a, b -> a + b }

// Flash the visual banner for every cue. Banner respects no verbosity gating —
// it's a transparency feature. Users who silenced voice still want to know what
// just fired (e.g. SIGNAL_LOST vibration with banner saying "signal lost").
val copy = CueCopy.forEvent(event)
WorkoutState.flashCueBanner(
    CueBanner(
        event = event,
        title = copy.title,
        subtitle = copy.subtitle,
        kind = copy.kind,
        firedAtMs = System.currentTimeMillis()
    )
)
```

Then find the two `vibrationManager.pulseAlert()` call sites inside `fireEvent`:

1. In the `SPEED_UP, SLOW_DOWN` branch under `if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION)`, replace `vibrationManager.pulseAlert()` with:

```kotlin
vibrationManager.pulseForEvent(event)
```

2. In the `SIGNAL_LOST` branch, leave `vibrationManager.pulseAlert()` as-is — `pulseForEvent(SIGNAL_LOST)` would produce the same pattern but the explicit call reads clearer for the safety-critical path.

- [ ] **Step 2: Also flash banner for pause/resume tones**

At the top of `playPauseFeedback(paused: Boolean)`, add:

```kotlin
fun playPauseFeedback(paused: Boolean) {
    val volume = currentSettings.earconVolume.coerceIn(0, 100)
    // Banner even for pause/resume — users asked "what was that chime?" during pauses too.
    WorkoutState.flashCueBanner(
        CueBanner(
            event = CoachingEvent.IN_ZONE_CONFIRM, // reuse INFO kind; event is nominal
            title = if (paused) "Paused" else "Resumed",
            subtitle = if (paused) "Workout paused — tap resume when ready." else "Workout resumed.",
            kind = CueBannerKind.INFO,
            firedAtMs = System.currentTimeMillis()
        )
    )
    // ... rest of method unchanged
```

Note: we reuse `CoachingEvent.IN_ZONE_CONFIRM` only to satisfy the `event` field — it never reaches `cueCounts` because this code path doesn't route through `fireEvent`.

- [ ] **Step 3: Wire cue-count persistence in WorkoutForegroundService**

Open `WorkoutForegroundService.kt`. Search for where `workoutMetricsRepository.insertMetrics(...)` (or similar — named like `persistMetrics`, `saveMetrics`, `writeMetrics`) is called during stop.

Just before that call, build the JSON and include it:

```kotlin
val cueCountsMap = coachingAudioManager.consumeCueCounts()
val cueCountsJson = if (cueCountsMap.isEmpty()) null
    else com.google.gson.Gson().toJson(cueCountsMap.mapKeys { it.key.name })
```

Add `cueCountsJson = cueCountsJson` to the `WorkoutMetricsEntity(...)` construction used by the insert.

If the metrics are built through a helper (`MetricsCalculator.build(...)`), pass `cueCountsJson` through as an additional parameter to that helper and have it include in the returned entity.

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt \
        app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(audio): flash cue banner + count cues + direction vibration"
```

---

## Task 7: TTS rephrase + direction-aware predictive fallback

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt`

- [ ] **Step 1: Locate the current per-event phrasing**

Search for `speakEvent` in `VoicePlayer.kt`. It likely has a `when (event)` block with string literals. Replace the relevant branches with:

```kotlin
CoachingEvent.SIGNAL_LOST -> "Heart-rate signal lost."
CoachingEvent.SIGNAL_REGAINED -> "Heart-rate signal back."
CoachingEvent.SEGMENT_CHANGE -> "Next interval."
```

- [ ] **Step 2: Direction-aware predictive warning fallback**

Still in `speakEvent`, locate the `PREDICTIVE_WARNING` branch. The current fallback when `guidanceText` is null/blank is "Watch your pace." Replace that fallback with a direction lookup.

First, add a parameter to `speakEvent` for slope:

```kotlin
fun speakEvent(
    event: CoachingEvent,
    guidanceText: String? = null,
    workoutMode: WorkoutMode,
    paceMinPerKm: Float? = null,
    distanceUnit: DistanceUnit = DistanceUnit.KM,
    hrSlopeBpmPerMin: Float = 0f
) {
```

In the PREDICTIVE_WARNING branch:

```kotlin
CoachingEvent.PREDICTIVE_WARNING -> {
    val text = if (!guidanceText.isNullOrBlank()) {
        guidanceText
    } else if (hrSlopeBpmPerMin > 0f) {
        "Pace climbing. Ease off to hold zone."
    } else {
        "Pace dropping. Pick it up to hold zone."
    }
    text
}
```

- [ ] **Step 3: Pass slope through CoachingAudioManager**

In `CoachingAudioManager.kt`, change `fireEvent` to accept slope and forward:

```kotlin
fun fireEvent(
    event: CoachingEvent,
    guidanceText: String? = null,
    paceMinPerKm: Float? = null,
    hrSlopeBpmPerMin: Float = 0f
) {
    // ... existing body unchanged until voice calls ...
    voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm, distanceUnit, hrSlopeBpmPerMin)
}
```

Forward the slope everywhere `voicePlayer.speakEvent(...)` is called inside `fireEvent`.

- [ ] **Step 4: Pass slope at every fireEvent call site**

Grep the codebase:

Run: `grep -rn "coachingAudioManager.fireEvent\|audioManager.fireEvent" app/src/main/`

For each call site, pass `hrSlopeBpmPerMin = <available slope>`. The primary callers are:
- `CoachingEventRouter.kt` — has access to `AdaptivePaceController.hrSlopeBpmPerMin` via the controller reference already held or passed in.
- `AlertPolicy.kt` — pass `0f` (zone alerts don't need direction inflection since SPEED_UP / SLOW_DOWN carry it already).
- `WorkoutForegroundService.kt` — any direct invocations pass the slope from `AdaptivePaceController.snapshot()`.

If threading the slope through all the way is intrusive, an acceptable alternative is: `CoachingAudioManager` exposes `fun setHrSlope(slope: Float)` that the service calls from the main tick loop (it already ticks every second with slope available), and `fireEvent` reads from `private var lastHrSlope = 0f`. Prefer this simpler approach:

Actually, use the simpler approach. In `CoachingAudioManager.kt`:

```kotlin
private var lastHrSlopeBpmPerMin: Float = 0f

fun setHrSlope(slope: Float) {
    lastHrSlopeBpmPerMin = slope
}
```

Remove the `hrSlopeBpmPerMin` parameter from `fireEvent` — keep its signature unchanged to minimize blast radius. Inside `fireEvent`, use `lastHrSlopeBpmPerMin` when calling `voicePlayer.speakEvent`:

```kotlin
voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm, distanceUnit, lastHrSlopeBpmPerMin)
```

Now only one call site needs to change: wherever the main workout tick runs in `WorkoutForegroundService.kt`, call `coachingAudioManager.setHrSlope(adaptiveController.hrSlopeBpmPerMin)` once per tick before any cue evaluation.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt \
        app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt \
        app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(audio): rephrase cues + direction-aware predictive fallback"
```

---

## Task 8: Sound Library screen + nav route + Account entry

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/SoundLibraryScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Create SoundLibraryScreen.kt**

```kotlin
package com.hrcoach.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.service.audio.CueBannerKind
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.service.audio.EarconPlayer
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

@Composable
fun SoundLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { EarconPlayer(context) }
    DisposableEffect(player) { onDispose { player.destroy() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CardeaTheme.colors.textPrimary)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Sound Library",
                style = CardeaTheme.typography.headlineSmall,
                color = CardeaTheme.colors.textPrimary
            )
        }

        Text(
            "Cardea coaches you with chimes, voice, and vibration. Tap any cue to hear what it sounds like.",
            style = CardeaTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CueCopy.sections.forEach { section ->
                item(key = "h-${section.heading}") {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            section.heading.uppercase(),
                            style = CardeaTheme.typography.labelMedium,
                            color = CardeaTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            section.caption,
                            style = CardeaTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
                items(section.events, key = { it.name }) { event ->
                    SoundRow(event = event, onPreview = { player.play(event) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SoundRow(event: CoachingEvent, onPreview: () -> Unit) {
    val entry = CueCopy.forEvent(event)
    var playingTick by remember { mutableStateOf(0L) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        borderColor = borderFor(entry.kind)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = CardeaTheme.colors.textPrimary
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = CardeaTheme.typography.titleSmall,
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    entry.subtitle,
                    style = CardeaTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
            IconButton(
                onClick = {
                    playingTick = System.currentTimeMillis()
                    onPreview()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview ${entry.title}",
                    tint = CardeaTheme.colors.textPrimary
                )
            }
        }
    }
}

private fun borderFor(kind: CueBannerKind): Color = when (kind) {
    CueBannerKind.ALERT -> ZoneRed.copy(alpha = 0.4f)
    CueBannerKind.GUIDANCE -> Color(0x66FF4D5A) // pink-tinted
    CueBannerKind.MILESTONE -> ZoneGreen.copy(alpha = 0.4f)
    CueBannerKind.INFO -> Color(0x1AFFFFFF)
}
```

Note: if `Icons.Default.PlayArrow` is not confirmed in the CLAUDE.md icon list, substitute `Icons.Default.ChevronRight` or a custom drawable. Per CLAUDE.md grep-before-use rule: check with `grep -rn "Icons.Default.PlayArrow" app/src/main/` — if it's already used in the codebase, it's safe; otherwise use `ChevronRight`.

- [ ] **Step 2: Add route to NavGraph**

In `NavGraph.kt`, find the `Routes` object (around line 96). Add:

```kotlin
const val SOUND_LIBRARY = "sound_library"
```

In the NavHost Composable, add:

```kotlin
composable(Routes.SOUND_LIBRARY) {
    SoundLibraryScreen(onBack = { navController.popBackStack() })
}
```

Add the import: `import com.hrcoach.ui.account.SoundLibraryScreen`.

- [ ] **Step 3: Wire Account entry row**

Open `AccountScreen.kt`. Add a `NavController` parameter if not already present (follow the pattern used by other screens in the file — likely already threaded).

Find the "Audio & Coaching" section. After the existing earcon/voice volume controls, add:

```kotlin
GlassCard(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { navController.navigate(Routes.SOUND_LIBRARY) },
    contentPadding = PaddingValues(16.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Sound library",
                style = CardeaTheme.typography.titleSmall,
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                "Preview every coaching cue.",
                style = CardeaTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CardeaTheme.colors.textSecondary
        )
    }
}
```

Add missing imports as needed.

- [ ] **Step 4: Build and manually verify**

Run: `./gradlew assembleDebug`
Expected: SUCCESS. Install via `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Open app → Account → Audio & Coaching → tap Sound library row → verify screen opens, shows all cues grouped, preview button plays earcon.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/SoundLibraryScreen.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
        app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(audio): Sound Library settings screen"
```

---

## Task 9: In-workout cue banner overlay

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/CueBannerOverlay.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

- [ ] **Step 1: Create CueBannerOverlay.kt**

```kotlin
package com.hrcoach.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.service.audio.CueBanner
import com.hrcoach.service.audio.CueBannerKind
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.DarkGlassFillBrush
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

@Composable
fun CueBannerOverlay(banner: CueBanner?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        banner?.let { BannerPill(it) }
    }
}

@Composable
private fun BannerPill(banner: CueBanner) {
    val borderColor = when (banner.kind) {
        CueBannerKind.ALERT -> ZoneRed.copy(alpha = 0.5f)
        CueBannerKind.GUIDANCE -> Color(0x80FF4D5A)
        CueBannerKind.MILESTONE -> ZoneGreen.copy(alpha = 0.5f)
        CueBannerKind.INFO -> Color(0x1AFFFFFF)
    }
    val icon = CueCopy.forEvent(banner.event).icon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(brush = DarkGlassFillBrush)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp))
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CardeaTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                banner.title.uppercase(),
                style = CardeaTheme.typography.labelMedium,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                banner.subtitle,
                style = CardeaTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
```

- [ ] **Step 2: Render in ActiveWorkoutScreen**

Open `ActiveWorkoutScreen.kt`. Find where the MissionCard / primary content lives. Above or below it (design: below the mission card, above the primary stats), insert:

```kotlin
val cueBanner = uiState.snapshot.lastCueBanner  // adapt to however snapshot is exposed
CueBannerOverlay(
    banner = cueBanner,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
)
```

If the ViewModel exposes only primitive fields rather than the full snapshot, add a `lastCueBanner: CueBanner?` field to the ViewModel's UiState that observes `WorkoutState.snapshot` directly. Pattern:

```kotlin
val cueBanner: StateFlow<CueBanner?> = WorkoutState.snapshot
    .map { it.lastCueBanner }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

- [ ] **Step 3: Build + install + manually verify**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

Launch Simulation mode to avoid needing BLE/GPS. Start a workout. Trigger HR out-of-zone to see SPEED_UP / SLOW_DOWN banners. Verify:
- Banner appears within ~100 ms of audio.
- Auto-dismisses after ~3.5 s.
- A second cue within the 3.5 s window replaces the first.
- Title + subtitle match CueCopy.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/CueBannerOverlay.kt \
        app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat(audio): in-workout cue banner overlay"
```

---

## Task 10: AudioPrimerDialog + primer gating in Setup + Bootcamp

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/AudioPrimerDialog.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 1: Create AudioPrimerDialog.kt**

```kotlin
package com.hrcoach.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun AudioPrimerDialog(
    onFinish: () -> Unit,
    onSeeLibrary: () -> Unit,
) {
    var slideIndex by remember { mutableStateOf(0) }
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${slideIndex + 1} of 3",
                        style = CardeaTheme.typography.labelMedium,
                        color = CardeaTheme.colors.textSecondary
                    )
                    TextButton(onClick = onFinish) {
                        Text("Skip", color = CardeaTheme.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (slideIndex) {
                    0 -> Slide1(onSeeLibrary)
                    1 -> Slide2()
                    else -> Slide3()
                }
                Spacer(Modifier.height(20.dp))
                CardeaButton(
                    onClick = {
                        if (slideIndex < 2) slideIndex += 1 else onFinish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(if (slideIndex < 2) "Next" else "Got it — start my run")
                }
            }
        }
    }
}

@Composable
private fun Slide1(onSeeLibrary: () -> Unit) {
    Column {
        Text(
            "How Cardea coaches you",
            style = CardeaTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You'll hear three kinds of sound during your run:",
            style = CardeaTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Bullet("Chime", "A quick status ping.")
        Bullet("Voice", "What to do about it.")
        Bullet("Vibration", "Something urgent.")
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSeeLibrary) {
            Text("See all sounds →", color = CardeaTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun Slide2() {
    Column {
        Text(
            "When you drift",
            style = CardeaTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "If you drift out of your target zone, Cardea gives you a chime and a voice cue.\n\n" +
                "If you're still out of zone 30 seconds later, you'll hear it again — with vibration. " +
                "That's escalation — it means you haven't adjusted yet.",
            style = CardeaTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun Slide3() {
    Column {
        Text(
            "A heads-up, before you drift",
            style = CardeaTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Sometimes Cardea coaches you while you're still in zone. If your heart rate is trending " +
                "toward the edge, you'll hear a heads-up like \"Pace climbing — ease off.\"\n\n" +
                "Adjust early and you'll stay in. That's a feature, not a bug.",
            style = CardeaTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun Bullet(title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(6.dp).padding(top = 8.dp)) {}
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, style = CardeaTheme.typography.titleSmall, color = CardeaTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(body, style = CardeaTheme.typography.bodySmall, color = CardeaTheme.colors.textSecondary)
        }
    }
}
```

- [ ] **Step 2: Gate in SetupViewModel**

Open `SetupViewModel.kt`. Add to its UiState (or create a one-shot event via `Channel<PrimerEvent>`):

```kotlin
data class SetupUiState(
    // ... existing fields ...
    val showAudioPrimer: Boolean = false
)
```

Inject `AudioSettingsRepository` if not already. Add a method:

```kotlin
fun onStartWorkoutTapped() {
    val audio = audioSettingsRepository.getAudioSettings()
    if (!audio.audioPrimerShown && !SimulationController.isActive) {
        _uiState.update { it.copy(showAudioPrimer = true) }
    } else {
        startWorkoutInternal()
    }
}

fun onPrimerFinished() {
    audioSettingsRepository.setAudioPrimerShown(true)
    _uiState.update { it.copy(showAudioPrimer = false) }
    startWorkoutInternal()
}

fun onPrimerSkipped() {
    onPrimerFinished()  // same behavior: mark shown, proceed
}

fun onPrimerDismissedForLibrary() {
    audioSettingsRepository.setAudioPrimerShown(true)
    _uiState.update { it.copy(showAudioPrimer = false) }
    // Do NOT auto-start; user navigated to library.
}

private fun startWorkoutInternal() {
    // existing startWorkout body, renamed
}
```

- [ ] **Step 3: Render in SetupScreen**

In `SetupScreen.kt`, wherever the primary Start button's `onClick` currently calls `viewModel.startWorkout(...)`, change to `viewModel.onStartWorkoutTapped()`.

At the top of the screen's Composable, after `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`:

```kotlin
if (uiState.showAudioPrimer) {
    AudioPrimerDialog(
        onFinish = { viewModel.onPrimerFinished() },
        onSeeLibrary = {
            viewModel.onPrimerDismissedForLibrary()
            navController.navigate(Routes.SOUND_LIBRARY)
        }
    )
}
```

- [ ] **Step 4: Mirror the change in BootcampViewModel + BootcampScreen**

Same pattern. `BootcampViewModel.kt` gets the same three methods and `showAudioPrimer` state. `BootcampScreen.kt` renders `AudioPrimerDialog` identically where its start-session button lives. Simulation bypass follows the existing pattern in the file (per CLAUDE.md, both screens already have a `SimulationController.isActive` check).

- [ ] **Step 5: Build + manually verify**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

1. Clear app data: `adb shell pm clear com.hrcoach`.
2. Complete onboarding (fast-skip if possible).
3. Tap Start Workout on Setup → primer dialog appears → tap Next → Next → Got it — workout starts.
4. Tap Start Workout again → primer does NOT re-appear.
5. Re-clear data. This time tap Start → "See all sounds" link on slide 1 → verify navigates to Sound Library. Back-press returns to the Setup screen (no primer re-shown because `audioPrimerShown` is now true).
6. Verify simulation mode (toggle via dev menu) bypasses primer.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/AudioPrimerDialog.kt \
        app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt \
        app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(audio): one-time primer dialog on first workout start"
```

---

## Task 11: Post-run "Sounds heard today" recap (first 3 runs)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/postrun/SoundsHeardSection.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1: Extend PostRunSummaryUiState**

Open `PostRunSummaryViewModel.kt`. Add to UiState:

```kotlin
data class PostRunSummaryUiState(
    // ... existing fields ...
    val cueCounts: Map<com.hrcoach.domain.model.CoachingEvent, Int> = emptyMap(),
    val showSoundsRecap: Boolean = false
)
```

In the loader (the function that populates state from the workout ID), read:

```kotlin
val metrics = workoutMetricsRepository.getMetrics(workoutId)
val lifetimeCount = workoutRepository.countWorkouts()  // add this method if absent

val counts: Map<CoachingEvent, Int> = metrics?.cueCountsJson
    ?.let { parseCounts(it) }
    ?: emptyMap()

_uiState.update {
    it.copy(
        cueCounts = counts,
        showSoundsRecap = lifetimeCount <= 3 && counts.isNotEmpty()
    )
}
```

```kotlin
private fun parseCounts(json: String): Map<CoachingEvent, Int> = runCatching {
    com.google.gson.Gson()
        .fromJson(json, com.google.gson.reflect.TypeToken.get(Map::class.java).type) as Map<String, Double>
}.getOrDefault(emptyMap())
    .mapNotNull { (k, v) ->
        runCatching { CoachingEvent.valueOf(k) to v.toInt() }.getOrNull()
    }.toMap()
```

(Gson deserializes numeric fields as Double by default when targeting `Map`; hence the `.toInt()`.)

If `workoutRepository.countWorkouts()` doesn't exist, add:

```kotlin
// In WorkoutRepository
suspend fun countWorkouts(): Int = workoutDao.countAll()

// In WorkoutDao
@Query("SELECT COUNT(*) FROM workouts")
suspend fun countAll(): Int
```

- [ ] **Step 2: Create SoundsHeardSection.kt**

```kotlin
package com.hrcoach.ui.postrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun SoundsHeardSection(
    counts: Map<CoachingEvent, Int>,
    onSeeLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (counts.isEmpty()) return

    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                "SOUNDS YOU HEARD TODAY",
                style = CardeaTheme.typography.labelMedium,
                color = CardeaTheme.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "We fired a few coaching cues during your run. Here's what each meant:",
                style = CardeaTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(12.dp))

            CueCopy.displayOrder.forEach { event ->
                val count = counts[event] ?: return@forEach
                if (count <= 0) return@forEach
                val entry = CueCopy.forEvent(event)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "• $count × ${entry.title.lowercase()}",
                        style = CardeaTheme.typography.titleSmall,
                        color = CardeaTheme.colors.textPrimary,
                        modifier = Modifier.weight(0.35f)
                    )
                    Text(
                        entry.subtitle,
                        style = CardeaTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary,
                        modifier = Modifier.weight(0.65f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSeeLibrary, modifier = Modifier.align(Alignment.End)) {
                Text("See all sounds →", color = CardeaTheme.colors.textPrimary)
            }
        }
    }
}
```

Note: `Column.align(Alignment.End)` requires the button be in a `Column` scope that supports it. If the linter complains, wrap in `Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { TextButton(...) }`.

- [ ] **Step 3: Render in PostRunSummaryScreen**

Open `PostRunSummaryScreen.kt`. Find where the main summary cards are laid out. Add after the primary stats, only when `showSoundsRecap`:

```kotlin
if (uiState.showSoundsRecap) {
    SoundsHeardSection(
        counts = uiState.cueCounts,
        onSeeLibrary = { navController.navigate(Routes.SOUND_LIBRARY) },
        modifier = Modifier.padding(vertical = 12.dp)
    )
}
```

- [ ] **Step 4: Build + manually verify**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

1. Clear app data.
2. Complete 1st simulated workout, trigger some cues.
3. Reach post-run summary → verify "Sounds you heard today" section appears with correct counts.
4. Complete workouts 2 and 3 → section still appears.
5. Complete workout 4 → section does NOT appear.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/SoundsHeardSection.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt \
        app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt \
        app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt
git commit -m "feat(audio): post-run 'Sounds you heard today' recap (first 3 runs)"
```

---

## Task 12: Full-build verification + smoke checks

- [ ] **Step 1: Full build + lint + unit tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Pre-existing lint/compile errors from CLAUDE.md (`PartnerSection.kt`, `BleHrManager.kt` MissingPermission, etc.) are not regressions; ignore. Any NEW failure is a regression — fix before proceeding.

- [ ] **Step 2: End-to-end smoke on device**

Install via `adb install -r`. Walkthrough:

1. **Primer** — clear app data, complete onboarding, tap Start Workout → primer appears → complete.
2. **Sound Library** — Account → Audio & Coaching → Sound library → tap every preview button, hear each earcon, verify title+subtitle text matches.
3. **Cue banner** — start a simulated workout with HR going out of zone → verify:
   - SPEED_UP banner + earcon + (at tier 3) fast-tap vibration.
   - SLOW_DOWN banner + earcon + (at tier 3) slow-heavy vibration.
   - RETURN_TO_ZONE banner + earcon.
   - KM_SPLIT banner + earcon every km.
   - In-zone confirm banner + voice after 3 min steady.
4. **Predictive warning phrasing** — induce a predictive event (sim controller); verify voice says "Pace climbing. Ease off to hold zone." not "Watch your pace."
5. **Signal lost** — simulate BLE drop → banner "Signal lost" + vibration (even at OFF verbosity).
6. **Post-run recap** — finish workout → summary shows "Sounds you heard today" with counts; "See all sounds →" navigates to library.
7. **Verbosity OFF** — set verbosity OFF → start workout → verify no voice, no earcons, but banners STILL appear (by design), and SIGNAL_LOST vibration STILL fires.
8. **Cloud backup round-trip** — trigger a cloud sync (Account → Backup), wipe data, restore → verify `audioPrimerShown = true` survives (primer does not re-appear).

- [ ] **Step 3: Commit any final smoke fixes; tag release-ready**

```bash
# If any final tweaks were needed during smoke:
git add -A
git commit -m "chore(audio): smoke-test fixes"

# Log a final summary commit (empty allowed if nothing changed):
git log --oneline -20
```

Report the smoke-test summary to the user.

---

## Self-Review

Running through the spec section by section:

- **Sound Library screen** → Task 8 ✓
- **In-workout cue banner** → Task 9 ✓
- **TTS rephrase pass** → Task 7 ✓
- **First-workout audio primer** → Task 10 ✓
- **Patterned vibration at tier 3** → Task 5 (impl) + Task 6 (wiring in CoachingAudioManager) ✓
- **Post-run "Sounds heard today" recap** → Task 11 ✓
- **Data model — WorkoutSnapshot.lastCueBanner + flashCueBanner** → Task 2 ✓
- **AudioSettings.audioPrimerShown + cloud sync** → Task 3 ✓
- **WorkoutMetrics.cueCountsJson + migration 18→19** → Task 4 ✓
- **Navigation wiring** → Tasks 8, 10, 11 ✓
- **Testing coverage** (CueCopy, vibration dispatch, AudioSettings round-trip) → Tasks 1, 3, 5 ✓
- **Manual smoke coverage** (primer once-only, banner timing, post-run gating first-3-runs, cloud round-trip) → Task 12 ✓

No placeholders remain. `clearLayers()`-style type drift check: `CueCopy.forEvent` is consistent across all tasks. `flashCueBanner` signature matches between WorkoutState (Task 2) and callers (Task 6). `cueCountsJson` key name consistent between entity (Task 4) and readers (Task 11).

Plan complete.

---

## Execution Handoff

Two execution options:

1. **Subagent-Driven** (recommended) — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
