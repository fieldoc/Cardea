# Home Screen Content & Nav Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the empty bottom half of the home screen with a Bootcamp Progress Ring, Weekly Volume card, and Coaching Insight card, and fix the navigation bug that prevents returning to the Home tab after visiting Bootcamp.

**Architecture:** Three new composables added to `HomeScreen.kt`, with data computed in `HomeViewModel` and a new pure-function `CoachingInsightEngine`. Nav fix is a small change to navigation parameters in `NavGraph.kt`.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas for progress ring), Hilt, Room, StateFlow, Gson (already in project for `targetConfig` parsing).

**Spec:** `docs/superpowers/specs/2026-03-19-home-screen-content-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `domain/coaching/CoachingInsight.kt` | Create | Data class + icon enum |
| `domain/coaching/CoachingInsightEngine.kt` | Create | Pure function: workouts → highest-priority insight |
| `ui/home/HomeViewModel.kt` | Modify | Add new state fields, compute weekly volume, call engine |
| `ui/home/HomeScreen.kt` | Modify | Add 3 new composables below stat chips |
| `ui/navigation/NavGraph.kt` | Modify | Fix `onGoToBootcamp` + `onGoToProgress` + `onGoToHistory` nav params |

Test files:
| File | Action |
|------|--------|
| `domain/coaching/CoachingInsightEngineTest.kt` | Create |

---

### Task 1: Fix Navigation Bug

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

This is the quick-win bug fix. Do it first so the user gets immediate relief.

- [ ] **Step 1: Fix `onGoToBootcamp` in the HOME composable (around line 300)**

Change:
```kotlin
onGoToBootcamp = {
    navController.navigate(Routes.BOOTCAMP) {
        launchSingleTop = true
    }
}
```
To:
```kotlin
onGoToBootcamp = {
    navController.navigate(Routes.BOOTCAMP) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 2: Add `restoreState = true` to `onGoToProgress` (around line 282)**

Change:
```kotlin
onGoToProgress = {
    navController.navigate(Routes.PROGRESS) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
    }
}
```
To:
```kotlin
onGoToProgress = {
    navController.navigate(Routes.PROGRESS) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 3: Add `restoreState = true` to `onGoToHistory` (around line 288)**

Same pattern — add `restoreState = true` after `launchSingleTop = true`.

- [ ] **Step 4: Add `restoreState = true` to `onGoToAccount` (around line 294)**

Same pattern for consistency.

- [ ] **Step 5: Build to verify no compile errors**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
feat(nav): fix home tab unreachable after bootcamp navigation

Add popUpTo + saveState + restoreState to all HomeScreen navigation
callbacks to match the bottom bar tab pattern. Without this, navigating
to Bootcamp from the hero card created a rogue back-stack entry that
prevented the Home tab from working.
```

---

### Task 2: Create CoachingInsight Data Model

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsight.kt`

- [ ] **Step 1: Create the data class and icon enum**

```kotlin
package com.hrcoach.domain.coaching

enum class CoachingIcon {
    LIGHTBULB,   // default / general tip
    CHART_UP,    // improvement detected
    TROPHY,      // goal reached
    WARNING,     // inactivity or overtraining
    HEART        // recovery suggestion
}

data class CoachingInsight(
    val title: String,
    val subtitle: String,
    val icon: CoachingIcon = CoachingIcon.LIGHTBULB
)
```

- [ ] **Step 2: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat(coaching): add CoachingInsight data model
```

---

### Task 3: Create CoachingInsightEngine with Tests (TDD)

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt`
- Create: `app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt`

The engine is a pure function: `(workouts, weeklyCount, weeklyTarget, hasBootcamp) → CoachingInsight`. This makes it trivially testable.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/hrcoach/domain/coaching/CoachingInsightEngineTest.kt`:

```kotlin
package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachingInsightEngineTest {

    private fun workout(
        startTime: Long,
        endTime: Long = startTime + 30 * 60_000L,
        distanceMeters: Float = 3000f,
        targetConfig: String = """{"mode":"STEADY_STATE","steadyStateTargetHr":140}"""
    ) = WorkoutEntity(
        startTime = startTime,
        endTime = endTime,
        averageHr = 140,
        maxHr = 160,
        totalDistanceMeters = distanceMeters,
        targetConfig = targetConfig
    )

    @Test
    fun `no workouts returns start-your-first-run insight`() {
        val result = CoachingInsightEngine.generate(
            workouts = emptyList(),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = System.currentTimeMillis()
        )
        assertEquals("Start your first run", result.title)
        assertEquals(CoachingIcon.HEART, result.icon)
    }

    @Test
    fun `7+ days since last run returns inactivity warning`() {
        val eightDaysAgo = System.currentTimeMillis() - 8 * 86_400_000L
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = eightDaysAgo)),
            workoutsThisWeek = 0,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = System.currentTimeMillis()
        )
        assertEquals(CoachingIcon.WARNING, result.icon)
        assert(result.title.contains("get moving", ignoreCase = true))
    }

    @Test
    fun `weekly goal met returns trophy insight`() {
        val now = System.currentTimeMillis()
        val workouts = (0..3).map { workout(startTime = now - it * 86_400_000L) }
        val result = CoachingInsightEngine.generate(
            workouts = workouts,
            workoutsThisWeek = 4,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals(CoachingIcon.TROPHY, result.icon)
        assert(result.title.contains("goal", ignoreCase = true))
    }

    @Test
    fun `default fallback when no rules match`() {
        val now = System.currentTimeMillis()
        val result = CoachingInsightEngine.generate(
            workouts = listOf(workout(startTime = now - 86_400_000L)),
            workoutsThisWeek = 1,
            weeklyTarget = 4,
            hasBootcamp = false,
            nowMs = now
        )
        assertEquals("Consistency is key", result.title)
        assertEquals(CoachingIcon.LIGHTBULB, result.icon)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.domain.coaching.CoachingInsightEngineTest" --info 2>&1 | tail -20`
Expected: FAIL — `CoachingInsightEngine` does not exist

- [ ] **Step 3: Implement the engine**

Create `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsightEngine.kt`:

```kotlin
package com.hrcoach.domain.coaching

import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.google.gson.Gson

object CoachingInsightEngine {

    private val gson = Gson()

    fun generate(
        workouts: List<WorkoutEntity>,
        workoutsThisWeek: Int,
        weeklyTarget: Int,
        hasBootcamp: Boolean,
        nowMs: Long
    ): CoachingInsight {
        // Priority 1: No workouts ever
        if (workouts.isEmpty()) {
            return CoachingInsight(
                title = "Start your first run",
                subtitle = "Connect your HR monitor and hit the trail",
                icon = CoachingIcon.HEART
            )
        }

        val lastWorkoutMs = workouts.first().endTime
        val daysSinceLastRun = ((nowMs - lastWorkoutMs) / 86_400_000L).toInt()

        // Priority 2: Inactivity
        if (daysSinceLastRun >= 7) {
            return CoachingInsight(
                title = "Time to get moving",
                subtitle = "It's been $daysSinceLastRun days since your last run",
                icon = CoachingIcon.WARNING
            )
        }

        // Priority 3: Consecutive hard sessions
        val recentTypes = workouts.take(5).map { classifySession(it) }
        val consecutiveHard = recentTypes.takeWhile { it == SessionType.HARD }.size
        if (consecutiveHard >= 3) {
            return CoachingInsight(
                title = "Consider an easy day",
                subtitle = "$consecutiveHard hard sessions in a row — an easy run helps recovery",
                icon = CoachingIcon.HEART
            )
        }

        // Priority 4: Z2 pace improvement
        val z2Improvement = computeZ2PaceImprovement(workouts, nowMs)
        if (z2Improvement != null && z2Improvement >= 5) {
            return CoachingInsight(
                title = "Z2 pace improved ${z2Improvement}%",
                subtitle = "Your aerobic base is growing — keep it up",
                icon = CoachingIcon.CHART_UP
            )
        }

        // Priority 5: Weekly goal met
        if (workoutsThisWeek >= weeklyTarget && weeklyTarget > 0) {
            return CoachingInsight(
                title = "Weekly goal reached!",
                subtitle = "$workoutsThisWeek runs this week — nice consistency",
                icon = CoachingIcon.TROPHY
            )
        }

        // Priority 6: Bootcamp behind schedule (past Thursday = day 4+)
        if (hasBootcamp && weeklyTarget > 0) {
            val dayOfWeek = java.time.Instant.ofEpochMilli(nowMs)
                .atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value
            val halfDone = workoutsThisWeek.toFloat() / weeklyTarget < 0.5f
            if (dayOfWeek >= 4 && halfDone) {
                val remaining = weeklyTarget - workoutsThisWeek
                return CoachingInsight(
                    title = "Pick up the pace this week",
                    subtitle = "$workoutsThisWeek/$weeklyTarget sessions done — $remaining left to stay on track",
                    icon = CoachingIcon.WARNING
                )
            }
        }

        // Priority 7: Default
        return CoachingInsight(
            title = "Consistency is key",
            subtitle = "Regular training builds a stronger aerobic base",
            icon = CoachingIcon.LIGHTBULB
        )
    }

    private enum class SessionType { HARD, EASY, UNKNOWN }

    private fun classifySession(workout: WorkoutEntity): SessionType {
        val config = parseConfig(workout.targetConfig) ?: return SessionType.UNKNOWN
        if (config.mode == WorkoutMode.FREE_RUN) return SessionType.UNKNOWN
        val presetId = config.presetId ?: ""
        return when {
            presetId.contains("Z4", ignoreCase = true) ||
            presetId.contains("TEMPO", ignoreCase = true) ||
            presetId.contains("INTERVAL", ignoreCase = true) -> SessionType.HARD
            presetId.contains("Z2", ignoreCase = true) ||
            presetId.contains("EASY", ignoreCase = true) ||
            presetId.contains("AEROBIC", ignoreCase = true) -> SessionType.EASY
            else -> SessionType.UNKNOWN
        }
    }

    private fun parseConfig(json: String?): WorkoutConfig? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, WorkoutConfig::class.java) }.getOrNull()
    }

    /** Returns % improvement in Z2 pace (positive = faster), or null if insufficient data. */
    private fun computeZ2PaceImprovement(workouts: List<WorkoutEntity>, nowMs: Long): Int? {
        val fourWeeksMs = 28L * 86_400_000L
        val recentCutoff = nowMs - fourWeeksMs
        val olderCutoff = nowMs - 2 * fourWeeksMs

        fun isZ2(w: WorkoutEntity): Boolean {
            val config = parseConfig(w.targetConfig) ?: return false
            if (config.mode != WorkoutMode.STEADY_STATE) return false
            val id = config.presetId ?: ""
            return id.contains("Z2", true) || id.contains("EASY", true) || id.contains("AEROBIC", true)
        }

        fun avgPace(list: List<WorkoutEntity>): Double? {
            val valid = list.filter { it.totalDistanceMeters > 100f && (it.endTime - it.startTime) > 60_000L }
            if (valid.size < 2) return null
            return valid.map { it.totalDistanceMeters.toDouble() / ((it.endTime - it.startTime) / 1000.0) }.average()
        }

        val recent = workouts.filter { isZ2(it) && it.startTime >= recentCutoff }
        val older = workouts.filter { isZ2(it) && it.startTime in olderCutoff until recentCutoff }

        val recentPace = avgPace(recent) ?: return null
        val olderPace = avgPace(older) ?: return null
        if (olderPace <= 0) return null

        val improvementPct = ((recentPace - olderPace) / olderPace * 100).toInt()
        return if (improvementPct >= 5) improvementPct else null
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.domain.coaching.CoachingInsightEngineTest" --info 2>&1 | tail -20`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```
feat(coaching): add CoachingInsightEngine with static rule engine

Priority-ranked tips: no workouts, inactivity, consecutive hard sessions,
Z2 pace improvement, weekly goal reached, bootcamp behind schedule, and
a default fallback.
```

---

### Task 4: Extend HomeUiState and HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` (HomeUiState is defined here at line 31)

- [ ] **Step 1: Add new fields to HomeUiState (around line 31)**

Add these fields to the existing `HomeUiState` data class, after the existing `sensorLastSeenMs` field:

```kotlin
    val totalDistanceThisWeekMeters: Double = 0.0,
    val totalTimeThisWeekMinutes: Long = 0,
    val weeklyDistanceTargetKm: Double = 15.0,
    val weeklyTimeTargetMinutes: Long = 90,
    val bootcampTotalWeeks: Int = 12,
    val bootcampPercentComplete: Float = 0f,
    val coachingInsight: CoachingInsight? = null,
```

Add import at top:
```kotlin
import com.hrcoach.domain.coaching.CoachingInsight
```

- [ ] **Step 2: Add new imports to HomeViewModel**

```kotlin
import com.hrcoach.domain.coaching.CoachingInsightEngine
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.model.BootcampGoal
```

- [ ] **Step 3: Compute weekly volume totals in the flatMapLatest block**

Inside the `flatMapLatest` block (around line 62), after the existing `val thisWeek = workouts.count { ... }` line, add:

```kotlin
            val thisWeekWorkouts = workouts.filter { it.startTime >= weekStart }
            val totalDistanceM = thisWeekWorkouts.sumOf { it.totalDistanceMeters.toDouble() }
            val totalTimeMin = thisWeekWorkouts.sumOf {
                ((it.endTime - it.startTime) / 60_000L).coerceAtLeast(0)
            }
```

- [ ] **Step 4: Compute bootcamp total weeks and percent complete**

After the existing `activeEnrollment` computation, add:

```kotlin
            val bootcampGoal = activeEnrollment?.let {
                runCatching { BootcampGoal.valueOf(it.goalType) }.getOrNull()
            }
            val engine = bootcampGoal?.let {
                PhaseEngine(
                    goal = it,
                    phaseIndex = activeEnrollment.currentPhaseIndex,
                    weekInPhase = activeEnrollment.currentWeekInPhase,
                    runsPerWeek = activeEnrollment.runsPerWeek,
                    targetMinutes = activeEnrollment.targetMinutesPerRun
                )
            }
            val bootcampTotalWeeks = engine?.totalWeeks ?: 12
            val currentAbsoluteWeek = engine?.absoluteWeek ?: 1
            val bootcampPercentComplete = currentAbsoluteWeek.toFloat() / bootcampTotalWeeks
```

- [ ] **Step 5: Compute weekly volume targets**

After the above, add:

```kotlin
            val weeklyDistanceTargetKm = if (activeEnrollment != null) {
                // Conservative estimate: targetMinutes * runsPerWeek * 0.15 km/min (~9 km/hr easy pace)
                activeEnrollment.targetMinutesPerRun * activeEnrollment.runsPerWeek * 0.15
            } else 15.0

            val weeklyTimeTargetMin = if (activeEnrollment != null) {
                (activeEnrollment.targetMinutesPerRun * activeEnrollment.runsPerWeek).toLong()
            } else 90L
```

- [ ] **Step 6: Generate coaching insight**

After the volume targets, add:

```kotlin
            val coachingInsight = CoachingInsightEngine.generate(
                workouts = workouts,
                workoutsThisWeek = thisWeek,
                weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
                hasBootcamp = activeEnrollment != null,
                nowMs = System.currentTimeMillis()
            )
```

- [ ] **Step 7: Wire all new fields into the emit() call**

Add the new fields to the existing `emit(HomeUiState(...))` call (around line 108):

```kotlin
                totalDistanceThisWeekMeters = totalDistanceM,
                totalTimeThisWeekMinutes = totalTimeMin,
                weeklyDistanceTargetKm = weeklyDistanceTargetKm,
                weeklyTimeTargetMinutes = weeklyTimeTargetMin,
                bootcampTotalWeeks = bootcampTotalWeeks,
                bootcampPercentComplete = bootcampPercentComplete,
                coachingInsight = coachingInsight,
```

- [ ] **Step 8: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```
feat(home): compute weekly volume, bootcamp progress, and coaching insight

HomeViewModel now calculates total distance/time this week, derives
bootcamp total weeks from PhaseEngine, and generates coaching insights
via CoachingInsightEngine.
```

---

### Task 5: Add Composables to HomeScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add new imports at top of file**

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hrcoach.domain.coaching.CoachingIcon
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientPurple
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.util.metersToKm
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.nativeCanvas
```

- [ ] **Step 2: Add BootcampProgressRing composable**

Add this after the existing `StatChipsRow` composable (after the closing `}` around line 146):

```kotlin
@Composable
private fun BootcampProgressRing(
    currentWeek: Int,
    totalWeeks: Int,
    percentComplete: Float,
    modifier: Modifier = Modifier
) {
    val gradientPink = GradientPink
    val gradientPurple = GradientPurple
    val textPrimary = CardeaTheme.colors.textPrimary
    val textSecondary = CardeaTheme.colors.textSecondary
    val trackColor = CardeaTheme.colors.glassBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BOOTCAMP",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            Canvas(modifier = Modifier.size(88.dp)) {
                val strokeWidth = 6.dp.toPx()
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Progress arc
                drawArc(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        colors = listOf(gradientPink, gradientPurple)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * percentComplete.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "W$currentWeek",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        lineHeight = 20.sp
                    ),
                    color = textPrimary
                )
                Text(
                    text = "of $totalWeeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${(percentComplete * 100).toInt()}% complete",
            style = MaterialTheme.typography.labelSmall,
            color = textSecondary
        )
    }
}
```

- [ ] **Step 3: Add WeeklyVolumeCard composable**

```kotlin
@Composable
private fun WeeklyVolumeCard(state: HomeUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Distance",
            value = "%.1f / %.0f km".format(
                metersToKm(state.totalDistanceThisWeekMeters.toFloat()),
                state.weeklyDistanceTargetKm
            ),
            progress = (metersToKm(state.totalDistanceThisWeekMeters.toFloat()) / state.weeklyDistanceTargetKm).toFloat(),
            gradientColors = listOf(Color(0xFFFF5A5F), Color(0xFFFF2DA6))
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Time",
            value = "${state.totalTimeThisWeekMinutes} / ${state.weeklyTimeTargetMinutes} min",
            progress = (state.totalTimeThisWeekMinutes.toFloat() / state.weeklyTimeTargetMinutes).coerceIn(0f, 1f),
            gradientColors = listOf(Color(0xFF5B5BFF), Color(0xFF00D1FF))
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Runs",
            value = "${state.workoutsThisWeek} / ${state.weeklyTarget}",
            progress = (state.workoutsThisWeek.toFloat() / state.weeklyTarget.coerceAtLeast(1)).coerceIn(0f, 1f),
            gradientColors = listOf(Color(0xFF00D1FF), Color(0xFF4DFF88))
        )
    }
}

@Composable
private fun VolumeBar(
    label: String,
    value: String,
    progress: Float,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardeaTheme.colors.glassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.linearGradient(gradientColors))
            )
        }
    }
}
```

- [ ] **Step 4: Add CoachingInsightCard composable**

```kotlin
@Composable
private fun CoachingInsightCard(insight: CoachingInsight, modifier: Modifier = Modifier) {
    val iconEmoji = when (insight.icon) {
        CoachingIcon.LIGHTBULB -> "\uD83D\uDCA1"
        CoachingIcon.CHART_UP  -> "\uD83D\uDCC8"
        CoachingIcon.TROPHY    -> "\uD83C\uDFC6"
        CoachingIcon.WARNING   -> "\u26A0\uFE0F"
        CoachingIcon.HEART     -> "\u2764\uFE0F"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF5B5BFF).copy(alpha = 0.3f),
                            Color(0xFF00D1FF).copy(alpha = 0.2f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconEmoji, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = insight.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
```

- [ ] **Step 5: Wire composables into HomeScreen's Column**

In the `HomeScreen` composable, replace the existing `Spacer(modifier = Modifier.height(8.dp))` at the bottom of the Column (around line 477) with:

```kotlin
                // Progress ring + Weekly volume side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.hasActiveBootcamp) {
                        BootcampProgressRing(
                            currentWeek = state.currentWeekNumber,
                            totalWeeks = state.bootcampTotalWeeks,
                            percentComplete = state.bootcampPercentComplete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    WeeklyVolumeCard(
                        state = state,
                        modifier = if (state.hasActiveBootcamp) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    )
                }

                // Coaching insight
                state.coachingInsight?.let { insight ->
                    CoachingInsightCard(insight = insight)
                }

                Spacer(modifier = Modifier.height(8.dp))
```

- [ ] **Step 6: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
feat(home): add progress ring, weekly volume, and coaching insight cards

Three new composables fill the empty bottom half of the home screen:
- BootcampProgressRing: Canvas arc showing week X of Y
- WeeklyVolumeCard: Distance/time/runs progress bars
- CoachingInsightCard: Priority-ranked contextual tip

Visible when bootcamp is active; volume card shown standalone otherwise.
```

---

### Task 6: Final Verification

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew.bat testDebugUnitTest --info 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 2: Run full debug build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify on device/emulator**

Manual check:
1. Open app → Home tab shows new cards below stat chips
2. Navigate to Bootcamp from hero card → tap Home tab → returns to Home (bug fixed)
3. Progress ring shows correct week/total
4. Volume bars reflect actual this-week data
5. Coaching insight shows a contextual tip

- [ ] **Step 4: Final commit if any adjustments needed**
