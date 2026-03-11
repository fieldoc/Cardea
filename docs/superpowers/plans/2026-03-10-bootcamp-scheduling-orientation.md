# Bootcamp Scheduling Orientation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the disorienting training-days-only list + floating status cards with a unified 7-day week strip and an adaptive today-context card that correctly handles run-done, run-upcoming, and rest-day states.

**Architecture:** Extract `computeDayKind`/`computeRelativeLabel`/`computeWeekDateRange` as pure internal functions for TDD, add `TodayState` sealed class and `WeekDayItem` to the UI model, update the ViewModel to compute and emit these, then replace `WeekSessionList` + `NextSessionCard` in the Compose layer with `WeekStripCard` + `TodayContextCard`.

**Tech Stack:** Kotlin, Jetpack Compose, Room (read-only in this change), `java.time.LocalDate`, Canvas for the tick+glow indicator, JUnit 4 unit tests.

**Spec:** `docs/superpowers/specs/2026-03-10-bootcamp-scheduling-orientation-design.md`

---

## Chunk 1: Data model + logic

### Task 1: Update `BootcampUiState.kt`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`

- [ ] **Step 1: Add `TodayState` sealed class and `WeekDayItem` data class**

  Open `BootcampUiState.kt`. Add the following **before** the existing `BootcampUiState` data class:

  ```kotlin
  sealed class TodayState {
      /** Today has a session that has not yet been started. */
      data class RunUpcoming(
          val session: PlannedSession
      ) : TodayState()

      /** Today had a session that is now completed. */
      data class RunDone(
          val nextSession: PlannedSession?,
          val nextSessionDayLabel: String?,       // e.g. "Wed"
          val nextSessionRelativeLabel: String?   // e.g. "in 2 days"
      ) : TodayState()

      /** Today has no session scheduled (rest day or all sessions done). */
      data class RestDay(
          val nextSession: PlannedSession?,
          val nextSessionDayLabel: String?,
          val nextSessionRelativeLabel: String?
      ) : TodayState()
  }

  /** One slot in the 7-day week strip. `session` is null for rest days. */
  data class WeekDayItem(
      val dayOfWeek: Int,       // 1=Mon … 7=Sun
      val dayLabel: String,     // single-letter narrow format: "M" "T" "W" …
      val isToday: Boolean,
      val session: SessionUiItem? // null = rest day
  )
  ```

  > **Spec deviations (intentional):**
  > - The spec's `WeekDayItem` includes `calendarDate: Int`. It is omitted here because the design never displays per-pill dates — the week date range ("Mar 10–16") in the card header provides all necessary calendar grounding. If per-pill tap-to-show-date is added later, add `calendarDate` back.
  > - The spec puts `nextSessionRelativeLabel` as a top-level `BootcampUiState` field. This plan folds it directly into `TodayState.RunDone` and `TodayState.RestDay` — keeping related data co-located and removing the need for a separate nullable field on the outer state.

- [ ] **Step 2: Update `BootcampUiState` fields**

  In the `BootcampUiState` data class make these changes:

  **Remove** these three fields:
  ```kotlin
  val nextSession: PlannedSession? = null,
  val nextSessionDayLabel: String? = null,
  val scheduledRestDay: Boolean = false,
  ```

  **Replace** `currentWeekSessions`:
  ```kotlin
  // Remove:
  val currentWeekSessions: List<SessionUiItem> = emptyList(),
  // Add:
  val currentWeekDays: List<WeekDayItem> = emptyList(),
  ```

  **Add** two new fields (after `currentWeekDays`):
  ```kotlin
  val todayState: TodayState = TodayState.RestDay(null, null, null),
  val currentWeekDateRange: String = "",
  ```

  The final field list (reordered for readability — keep existing ordering for all untouched fields):
  ```kotlin
  data class BootcampUiState(
      val isLoading: Boolean = true,
      val loadError: String? = null,
      val hasActiveEnrollment: Boolean = false,
      val isPaused: Boolean = false,
      // Enrollment details
      val goal: BootcampGoal? = null,
      val currentPhase: TrainingPhase? = null,
      val absoluteWeek: Int = 0,
      val totalWeeks: Int = 0,
      val weekInPhase: Int = 0,
      val isRecoveryWeek: Boolean = false,
      val weeksUntilNextRecovery: Int? = null,
      val showGraduationCta: Boolean = false,
      // Week view
      val currentWeekDays: List<WeekDayItem> = emptyList(),
      val currentWeekDateRange: String = "",
      val todayState: TodayState = TodayState.RestDay(null, null, null),
      val activePreferredDays: List<DayPreference> = emptyList(),
      val upcomingWeeks: List<UpcomingWeekItem> = emptyList(),
      val swapRestMessage: String? = null,
      // Onboarding
      val showOnboarding: Boolean = false,
      val onboardingStep: Int = 0,
      val onboardingGoal: BootcampGoal? = null,
      val onboardingMinutes: Int = 30,
      val onboardingRunsPerWeek: Int = 3,
      val onboardingTimeWarning: String? = null,
      // Gap return
      val welcomeBackMessage: String? = null,
      val needsCalibration: Boolean = false,
      // Fitness
      val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN,
      val tuningDirection: TuningDirection = TuningDirection.HOLD,
      val illnessFlag: Boolean = false,
      val tierPromptDirection: TierPromptDirection = TierPromptDirection.NONE,
      val tierPromptEvidence: String? = null,
      val missedSession: Boolean = false,
      val showDeleteConfirmDialog: Boolean = false,
      // Reschedule bottom sheet
      val rescheduleSheetSessionId: Long? = null,
      val rescheduleAutoTargetDay: Int? = null,
      val rescheduleAutoTargetLabel: String? = null,
      val rescheduleDropSessionId: Long? = null,
      val rescheduleAvailableDays: List<Int> = emptyList(),
      val rescheduleAvailableLabels: List<String> = emptyList(),
      // Session detail sheet
      val showSessionDetail: Boolean = false,
      val sessionDetailItem: SessionUiItem? = null,
      // Goal detail sheet
      val showGoalDetail: Boolean = false,
      val goalProgressPercentage: Int = 0
  )
  ```

- [ ] **Step 3: Compile check**

  ```bash
  .\gradlew.bat :app:compileDebugKotlin 2>&1 | tail -30
  ```

  Expected: errors in `BootcampViewModel.kt` and `BootcampScreen.kt` referencing removed fields — that's correct and expected at this stage. No errors inside `BootcampUiState.kt` itself.

---

### Task 2: Create `BootcampDayStateComputer.kt` (TDD)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputer.kt`
- Create: `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputerTest.kt`

- [ ] **Step 1: Write the failing tests**

  Create `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputerTest.kt`:

  ```kotlin
  package com.hrcoach.ui.bootcamp

  import com.hrcoach.data.db.BootcampSessionEntity
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull
  import org.junit.Test

  class BootcampDayStateComputerTest {

      private fun session(dayOfWeek: Int, status: String = BootcampSessionEntity.STATUS_SCHEDULED) =
          BootcampSessionEntity(
              id = dayOfWeek.toLong(),
              enrollmentId = 1L,
              weekNumber = 1,
              dayOfWeek = dayOfWeek,
              sessionType = "EASY",
              targetMinutes = 30,
              status = status
          )

      // ── computeDayKind ──────────────────────────────────────────────────────

      @Test
      fun `computeDayKind returns RunUpcoming when today has scheduled session`() {
          val sessions = listOf(session(1), session(3), session(5))
          assertEquals(DayKind.RUN_UPCOMING, computeDayKind(sessions, todayDow = 1))
      }

      @Test
      fun `computeDayKind returns RunDone when today session is completed`() {
          val sessions = listOf(
              session(1, BootcampSessionEntity.STATUS_COMPLETED),
              session(3),
              session(5)
          )
          assertEquals(DayKind.RUN_DONE, computeDayKind(sessions, todayDow = 1))
      }

      @Test
      fun `computeDayKind returns Rest when no session on today`() {
          val sessions = listOf(session(1), session(3), session(5))
          assertEquals(DayKind.REST, computeDayKind(sessions, todayDow = 2))
      }

      @Test
      fun `computeDayKind returns Rest when session list is empty`() {
          assertEquals(DayKind.REST, computeDayKind(emptyList(), todayDow = 3))
      }

      @Test
      fun `computeDayKind returns RunDone for skipped session on today`() {
          val sessions = listOf(session(1, BootcampSessionEntity.STATUS_SKIPPED))
          assertEquals(DayKind.RUN_DONE, computeDayKind(sessions, todayDow = 1))
      }

      // ── computeRelativeLabel ────────────────────────────────────────────────

      @Test
      fun `computeRelativeLabel returns today when same day`() {
          assertEquals("today", computeRelativeLabel(targetDow = 3, todayDow = 3))
      }

      @Test
      fun `computeRelativeLabel returns tomorrow when next day`() {
          assertEquals("tomorrow", computeRelativeLabel(targetDow = 4, todayDow = 3))
      }

      @Test
      fun `computeRelativeLabel handles week wrap correctly`() {
          // Today is Friday (5), next session is Monday (1) — 3 days away
          assertEquals("in 3 days", computeRelativeLabel(targetDow = 1, todayDow = 5))
      }

      @Test
      fun `computeRelativeLabel returns in N days for further sessions`() {
          assertEquals("in 2 days", computeRelativeLabel(targetDow = 5, todayDow = 3))
      }

      // ── computeWeekDateRange ────────────────────────────────────────────────

      @Test
      fun `computeWeekDateRange formats same-month week`() {
          val monday = java.time.LocalDate.of(2026, 3, 9) // Mon Mar 9
          assertEquals("Mar 9–15", computeWeekDateRange(monday))
      }

      @Test
      fun `computeWeekDateRange formats cross-month week`() {
          val monday = java.time.LocalDate.of(2026, 3, 30) // Mon Mar 30
          assertEquals("Mar 30–Apr 5", computeWeekDateRange(monday))
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  .\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.BootcampDayStateComputerTest" 2>&1 | tail -20
  ```

  Expected: compilation failure — `computeDayKind`, `DayKind`, `computeRelativeLabel`, `computeWeekDateRange` not defined.

- [ ] **Step 3: Implement `BootcampDayStateComputer.kt`**

  Create `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputer.kt`:

  ```kotlin
  package com.hrcoach.ui.bootcamp

  import com.hrcoach.data.db.BootcampSessionEntity
  import java.time.LocalDate
  import java.time.format.DateTimeFormatter
  import java.util.Locale

  /** Discriminant for what kind of day today is from a training perspective. */
  internal enum class DayKind { RUN_UPCOMING, RUN_DONE, REST }

  /**
   * Determines whether today is a run day (upcoming/done) or a rest day.
   *
   * A session is considered "done" if its status is anything other than STATUS_SCHEDULED
   * (i.e., COMPLETED, SKIPPED, or DEFERRED all count as done for orientation purposes).
   */
  internal fun computeDayKind(
      scheduledSessions: List<BootcampSessionEntity>,
      todayDow: Int   // 1=Mon … 7=Sun
  ): DayKind {
      val todaySession = scheduledSessions.find { it.dayOfWeek == todayDow }
          ?: return DayKind.REST
      return if (todaySession.status != BootcampSessionEntity.STATUS_SCHEDULED)
          DayKind.RUN_DONE
      else
          DayKind.RUN_UPCOMING
  }

  /**
   * Returns a human-readable relative label for how far away [targetDow] is from [todayDow].
   * Handles week wrap (e.g., Friday→Monday = 3 days).
   */
  internal fun computeRelativeLabel(targetDow: Int, todayDow: Int): String {
      val days = (targetDow - todayDow + 7) % 7
      return when (days) {
          0 -> "today"
          1 -> "tomorrow"
          else -> "in $days days"
      }
  }

  /**
   * Formats the week's date range as "Mar 10–16" or "Mar 30–Apr 5" for cross-month weeks.
   * [weekStart] must be a Monday.
   */
  internal fun computeWeekDateRange(weekStart: LocalDate): String {
      val weekEnd = weekStart.plusDays(6)
      val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
      return if (weekStart.month == weekEnd.month) {
          "${monthFmt.format(weekStart)} ${weekStart.dayOfMonth}–${weekEnd.dayOfMonth}"
      } else {
          "${monthFmt.format(weekStart)} ${weekStart.dayOfMonth}–${monthFmt.format(weekEnd)} ${weekEnd.dayOfMonth}"
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  .\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.BootcampDayStateComputerTest" 2>&1 | tail -20
  ```

  Expected: `BUILD SUCCESSFUL`, all 11 tests pass.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
          app/src/main/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputer.kt \
          app/src/test/java/com/hrcoach/ui/bootcamp/BootcampDayStateComputerTest.kt
  git commit -m "feat(bootcamp): add TodayState/WeekDayItem model, day-state computer, and unit tests"
  ```

---

### Task 3: Update `BootcampViewModel.kt`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

This task replaces the `sessionItems`/`scheduledRestDay`/`nextScheduledSession` block (lines ~176–248) with the new data model.

- [ ] **Step 1: Verify imports in `BootcampViewModel.kt`**

  At the top of `BootcampViewModel.kt`, ensure these imports are present (add any missing):

  ```kotlin
  import java.time.DayOfWeek
  import java.time.LocalDate
  import java.time.format.TextStyle
  import java.util.Locale
  ```

  > `computeDayKind`, `computeRelativeLabel`, and `computeWeekDateRange` are `internal` top-level functions in `BootcampDayStateComputer.kt` in the **same package** (`com.hrcoach.ui.bootcamp`). No import is required — they resolve automatically.
  >
  > `toPlannedSession()` is an existing private extension function on `BootcampSessionEntity` already defined at the bottom of `BootcampViewModel.kt` (~line 782). No changes needed to it.

- [ ] **Step 2: Replace the `sessionItems` block**

  Find this block starting at line ~176:

  ```kotlin
  val sessionItems = scheduledSessions.map { session ->
      SessionUiItem(
          dayLabel = dayLabelFor(session.dayOfWeek),
          ...
      )
  }

  val nextScheduledSession = scheduledSessions.firstOrNull { it.status == BootcampSessionEntity.STATUS_SCHEDULED }
  val missedSession = scheduledSessions.any { ... }
  val scheduledRestDay = scheduledSessions.none { it.dayOfWeek == today }
  ```

  Replace the entire block (from `val sessionItems` through `val scheduledRestDay`) with:

  ```kotlin
  // Next incomplete session (may be in a future week via repository lookahead)
  val nextScheduledSession = scheduledSessions.firstOrNull {
      it.status == BootcampSessionEntity.STATUS_SCHEDULED
  }

  val missedSession = scheduledSessions.any {
      it.dayOfWeek < today &&
          it.status != BootcampSessionEntity.STATUS_COMPLETED &&
          it.status != BootcampSessionEntity.STATUS_SKIPPED &&
          it.status != BootcampSessionEntity.STATUS_DEFERRED
  }

  // Build 7-day strip items: one WeekDayItem per day M–S
  val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
  val weekDays = (1..7).map { dow ->
      val session = scheduledSessions.find { it.dayOfWeek == dow }
      WeekDayItem(
          dayOfWeek = dow,
          dayLabel = DayOfWeek.of(dow)
              .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
          isToday = dow == today,
          session = session?.let {
              SessionUiItem(
                  dayLabel = dayLabelFor(it.dayOfWeek),
                  typeName = sessionTypeDisplayName(it.sessionType, it.presetId),
                  rawTypeName = it.sessionType,
                  minutes = it.targetMinutes,
                  isCompleted = it.status != BootcampSessionEntity.STATUS_SCHEDULED,
                  isToday = it.dayOfWeek == today,
                  sessionId = it.id,
                  presetId = it.presetId
              )
          }
      )
  }

  // Compute today's context state
  val dayKind = computeDayKind(scheduledSessions, today)
  val nextDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) }
  val nextRelLabel = nextScheduledSession?.let {
      computeRelativeLabel(it.dayOfWeek, today)
  }
  val todayState: TodayState = when (dayKind) {
      DayKind.RUN_UPCOMING -> TodayState.RunUpcoming(
          session = scheduledSessions.first { it.dayOfWeek == today }.toPlannedSession()
      )
      DayKind.RUN_DONE -> TodayState.RunDone(
          nextSession = nextScheduledSession?.toPlannedSession(),
          nextSessionDayLabel = nextDayLabel,
          nextSessionRelativeLabel = nextRelLabel
      )
      DayKind.REST -> TodayState.RestDay(
          nextSession = nextScheduledSession?.toPlannedSession(),
          nextSessionDayLabel = nextDayLabel,
          nextSessionRelativeLabel = nextRelLabel
      )
  }
  ```

- [ ] **Step 3: Update `_uiState.value` construction**

  Find the `_uiState.value = BootcampUiState(...)` block (~line 219). Replace the affected fields:

  Remove:
  ```kotlin
  nextSession = nextScheduledSession?.toPlannedSession(),
  nextSessionDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) },
  currentWeekSessions = sessionItems,
  scheduledRestDay = scheduledRestDay,
  ```

  Add:
  ```kotlin
  currentWeekDays = weekDays,
  currentWeekDateRange = computeWeekDateRange(weekStart),
  todayState = todayState,
  ```

- [ ] **Step 4: Fix `onBootcampWorkoutStarting`**

  Find (~line 295):
  ```kotlin
  val nextScheduled = _uiState.value.currentWeekSessions
      .firstOrNull { it.sessionId != null && !it.isCompleted }
  ```

  Replace with:
  ```kotlin
  val nextScheduled = _uiState.value.currentWeekDays
      .mapNotNull { it.session }
      .firstOrNull { it.sessionId != null && !it.isCompleted }
  ```

- [ ] **Step 5: Compile check**

  ```bash
  .\gradlew.bat :app:compileDebugKotlin 2>&1 | tail -30
  ```

  Expected: errors only in `BootcampScreen.kt` (references to removed composables/fields). No errors in ViewModel or UiState.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
  git commit -m "feat(bootcamp): compute TodayState and WeekDayItem in ViewModel"
  ```

---

## Chunk 2: Compose UI

### Task 4: Add `WeekStripCard` to `BootcampScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 1: Add required imports**

  At the top of `BootcampScreen.kt`, add any missing imports:

  ```kotlin
  import androidx.compose.foundation.Canvas
  import androidx.compose.ui.geometry.Offset
  import androidx.compose.ui.graphics.Brush
  import androidx.compose.ui.graphics.StrokeCap
  ```

- [ ] **Step 2: Write `WeekStripCard` composable**

  Add this composable after the `FeatureBullet` function (around line 340), before the `OnboardingWizard` section:

  ```kotlin
  @Composable
  private fun WeekStripCard(
      days: List<WeekDayItem>,
      dateRange: String
  ) {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
          // Header: "This Week" + date range
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
              Text(
                  text = "This Week",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = CardeaTextPrimary
              )
              Text(
                  text = dateRange,
                  style = MaterialTheme.typography.labelSmall,
                  color = CardeaTextTertiary
              )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // 7-day pill row
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
          ) {
              days.forEach { day ->
                  WeekDayPill(day = day)
              }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Tick + glow timeline indicator
          val todayIndex = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
          Canvas(
              modifier = Modifier
                  .fillMaxWidth()
                  .height(10.dp)
          ) {
              val pillHalfWidth = 15.dp.toPx()
              val trackStart = pillHalfWidth
              val trackEnd = size.width - pillHalfWidth
              val trackY = size.height / 2f
              val tickX = trackStart + (trackEnd - trackStart) * todayIndex / 6f

              // Dim track
              drawLine(
                  color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                  start = Offset(trackStart, trackY),
                  end = Offset(trackEnd, trackY),
                  strokeWidth = 1.dp.toPx()
              )

              // Radial glow bloom at today
              drawCircle(
                  brush = Brush.radialGradient(
                      colors = listOf(
                          androidx.compose.ui.graphics.Color.White.copy(alpha = 0.20f),
                          androidx.compose.ui.graphics.Color.Transparent
                      ),
                      center = Offset(tickX, trackY),
                      radius = 9.dp.toPx()
                  ),
                  radius = 9.dp.toPx(),
                  center = Offset(tickX, trackY)
              )

              // Tick line
              drawLine(
                  color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                  start = Offset(tickX, trackY - 4.5.dp.toPx()),
                  end = Offset(tickX, trackY + 4.5.dp.toPx()),
                  strokeWidth = 1.5.dp.toPx(),
                  cap = StrokeCap.Round
              )
          }
      }
  }

  @Composable
  private fun WeekDayPill(day: WeekDayItem) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
          // Day letter
          Text(
              text = day.dayLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = CardeaTextTertiary
          )

          // Pill dot
          val session = day.session
          val dotBackground: androidx.compose.ui.graphics.Color
          val dotBorder: androidx.compose.ui.graphics.Color
          val dotContent: @Composable () -> Unit

          when {
              session == null && day.isToday -> {
                  // today is a rest day — subtle bordered empty dot
                  dotBackground = androidx.compose.ui.graphics.Color.Transparent
                  dotBorder = GlassBorder
                  dotContent = {
                      Text(
                          text = "—",
                          style = MaterialTheme.typography.labelSmall,
                          color = CardeaTextTertiary.copy(alpha = 0.4f)
                      )
                  }
              }
              session == null -> {
                  dotBackground = GlassHighlight.copy(alpha = 0.4f)
                  dotBorder = androidx.compose.ui.graphics.Color.Transparent
                  dotContent = {}
              }
              session.isCompleted && day.isToday -> {
                  dotBackground = ZoneGreen.copy(alpha = 0.18f)
                  dotBorder = ZoneGreen.copy(alpha = 0.5f)
                  dotContent = {
                      Icon(
                          Icons.Default.Check, contentDescription = null,
                          tint = ZoneGreen, modifier = Modifier.size(12.dp)
                      )
                  }
              }
              session.isCompleted -> {
                  dotBackground = ZoneGreen.copy(alpha = 0.12f)
                  dotBorder = ZoneGreen.copy(alpha = 0.22f)
                  dotContent = {
                      Icon(
                          Icons.Default.Check, contentDescription = null,
                          tint = ZoneGreen, modifier = Modifier.size(12.dp)
                      )
                  }
              }
              day.isToday -> {
                  dotBackground = GradientPink.copy(alpha = 0.14f)
                  dotBorder = GradientPink.copy(alpha = 0.5f)
                  dotContent = {
                      Text(
                          text = session.rawTypeName.take(2),
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = GradientPink
                      )
                  }
              }
              else -> {
                  dotBackground = GlassHighlight
                  dotBorder = GlassBorder
                  dotContent = {
                      Text(
                          text = session.rawTypeName.take(2),
                          style = MaterialTheme.typography.labelSmall,
                          color = CardeaTextTertiary
                      )
                  }
              }
          }

          Box(
              modifier = Modifier
                  .size(30.dp)
                  .clip(RoundedCornerShape(7.dp))
                  .background(dotBackground)
                  .border(1.dp, dotBorder, RoundedCornerShape(7.dp)),
              contentAlignment = Alignment.Center
          ) {
              dotContent()
          }
      }
  }
  ```

- [ ] **Step 3: Compile check (ViewModel errors still expected)**

  ```bash
  .\gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
  ```

  Expected: errors only for `WeekSessionList`, `NextSessionCard`, `scheduledRestDay` references in `ActiveBootcampDashboard` — not in the new composables.

---

### Task 5: Add `TodayContextCard` to `BootcampScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 1: Write `TodayContextCard` composable**

  Add this composable immediately after `WeekStripCard` / `WeekDayPill`:

  ```kotlin
  @Composable
  private fun TodayContextCard(
      todayState: TodayState,
      onStartWorkout: (configJson: String) -> Unit,
      onReschedule: (() -> Unit)?,
      onSwapToRest: () -> Unit
  ) {
      val accentColor = when (todayState) {
          is TodayState.RunUpcoming -> GradientPink
          is TodayState.RunDone     -> ZoneGreen
          is TodayState.RestDay     -> CardeaTextTertiary
      }

      val statusText = when (todayState) {
          is TodayState.RunUpcoming -> "Today — ${
              SessionType.displayLabelForPreset(todayState.session.presetId)
                  ?: todayState.session.type.name
                      .lowercase()
                      .replaceFirstChar { it.uppercase() }
          }"
          is TodayState.RunDone -> "✓ Today's run is done"
          is TodayState.RestDay -> "Rest day"
      }

      val subText = when (todayState) {
          is TodayState.RunUpcoming ->
              "${todayState.session.minutes} min"
          is TodayState.RunDone ->
              if (todayState.nextSession != null) {
                  val label = SessionType.displayLabelForPreset(todayState.nextSession.presetId)
                      ?: todayState.nextSession.type.name.lowercase().replaceFirstChar { it.uppercase() }
                  "Next: $label · ${todayState.nextSessionDayLabel ?: ""}" +
                      (todayState.nextSessionRelativeLabel?.let { " · $it" } ?: "")
              } else {
                  "Week complete — great work!"
              }
          is TodayState.RestDay ->
              if (todayState.nextSession != null) {
                  val label = SessionType.displayLabelForPreset(todayState.nextSession.presetId)
                      ?: todayState.nextSession.type.name.lowercase().replaceFirstChar { it.uppercase() }
                  "Next: $label · ${todayState.nextSessionDayLabel ?: ""}" +
                      (todayState.nextSessionRelativeLabel?.let { " · $it" } ?: "")
              } else {
                  "Week complete — great work!"
              }
      }

      // Resolve preview session (the one to show details for)
      val previewSession: PlannedSession? = when (todayState) {
          is TodayState.RunUpcoming -> todayState.session
          is TodayState.RunDone     -> todayState.nextSession
          is TodayState.RestDay     -> todayState.nextSession
      }

      val previewLabel = when (todayState) {
          is TodayState.RunUpcoming -> "Today's session"
          is TodayState.RunDone     -> "Up next" + (todayState.nextSessionDayLabel?.let { " — $it" } ?: "")
          is TodayState.RestDay     -> "Up next" + (todayState.nextSessionDayLabel?.let { " — $it" } ?: "")
      }

      GlassCard(modifier = Modifier.fillMaxWidth()) {
          // Status line
          Text(
              text = statusText,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = accentColor
          )
          if (subText != null) {
              Text(
                  text = subText,
                  style = MaterialTheme.typography.bodySmall,
                  color = CardeaTextSecondary,
                  modifier = Modifier.padding(top = 2.dp)
              )
          }

          if (previewSession != null) {
              HorizontalDivider(
                  color = GlassBorder.copy(alpha = 0.5f),
                  modifier = Modifier.padding(vertical = 10.dp)
              )

              // Preview header
              Text(
                  text = previewLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = CardeaTextTertiary
              )
              Spacer(modifier = Modifier.height(2.dp))

              val sessionDisplayLabel = SessionType.displayLabelForPreset(previewSession.presetId)
                  ?: previewSession.type.name
                      .lowercase()
                      .replaceFirstChar { it.uppercase() }

              Text(
                  text = sessionDisplayLabel,
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = CardeaTextPrimary
              )
              Text(
                  text = "${previewSession.minutes} min",
                  style = MaterialTheme.typography.bodyMedium,
                  color = CardeaTextSecondary
              )

              // Description paragraph
              val description = SessionDescription.forType(
                  previewSession.type.name,
                  previewSession.presetId
              )
              Text(
                  text = description,
                  style = MaterialTheme.typography.bodySmall,
                  color = CardeaTextTertiary,
                  lineHeight = 18.sp,
                  modifier = Modifier.padding(top = 6.dp)
              )

              // CTA — only for RunUpcoming
              if (todayState is TodayState.RunUpcoming) {
                  Spacer(modifier = Modifier.height(12.dp))
                  CardeaButton(
                      text = "Start Run",
                      onClick = { onStartWorkout(buildConfigJson(todayState.session)) },
                      modifier = Modifier
                          .fillMaxWidth()
                          .height(52.dp)
                  )
                  Row(
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 4.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      if (onReschedule != null) {
                          TextButton(
                              onClick = onReschedule,
                              contentPadding = PaddingValues(horizontal = 8.dp)
                          ) {
                              Text(
                                  text = "Reschedule",
                                  style = MaterialTheme.typography.bodySmall,
                                  color = CardeaTextSecondary
                              )
                          }
                      } else {
                          Spacer(modifier = Modifier.width(1.dp))
                      }
                      TextButton(
                          onClick = onSwapToRest,
                          contentPadding = PaddingValues(horizontal = 8.dp)
                      ) {
                          Text(
                              text = "Rest today",
                              style = MaterialTheme.typography.bodySmall,
                              color = CardeaTextTertiary
                          )
                      }
                  }
              }
          }
      }
  }
  ```

  Note: `SessionDescription` is already a `private object` in `BootcampScreen.kt` — it's accessible from `TodayContextCard` since they're in the same file. No change needed.

- [ ] **Step 2: Compile check**

  ```bash
  .\gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
  ```

  Expected: errors only in `ActiveBootcampDashboard` for old references — not in new composables.

---

### Task 6: Wire `ActiveBootcampDashboard` and remove old composables

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 1: Update `ActiveBootcampDashboard`**

  Find the `ActiveBootcampDashboard` composable (~line 715). Inside its `Column`, make these changes:

  **Remove** this block:
  ```kotlin
  if (uiState.scheduledRestDay) {
      StatusCard(
          title = "Scheduled rest day",
          detail = "No run is scheduled for today. Recovery supports adaptation."
      )
  }
  ```

  **Replace** the `WeekSessionList` call:
  ```kotlin
  // Remove:
  WeekSessionList(
      sessions = uiState.currentWeekSessions,
      onSessionClick = onSessionClick
  )

  // Add:
  WeekStripCard(
      days = uiState.currentWeekDays,
      dateRange = uiState.currentWeekDateRange
  )
  ```

  **Replace** the `NextSessionCard` block:
  ```kotlin
  // Remove the entire block:
  val nextSession = uiState.nextSession
  if (nextSession != null && !uiState.isPaused) {
      val todaySessionId = uiState.currentWeekSessions
          .firstOrNull { it.isToday && !it.isCompleted }?.sessionId
      NextSessionCard(
          session = nextSession,
          dayLabel = uiState.nextSessionDayLabel,
          swapMessage = uiState.swapRestMessage,
          onSwapToRest = onSwapTodayForRest,
          onStartWorkout = { configJson ->
              onBootcampWorkoutStarting()
              onStartWorkout(configJson)
          },
          onReschedule = if (todaySessionId != null) {
              { onReschedule(todaySessionId) }
          } else null
      )
  }

  // Add (not gated on isPaused for RunDone/RestDay; CTA hidden internally when paused):
  if (!uiState.isPaused || uiState.todayState !is TodayState.RunUpcoming) {
      val todaySessionId = uiState.currentWeekDays
          .firstOrNull { it.isToday && it.session?.isCompleted == false }
          ?.session?.sessionId
      TodayContextCard(
          todayState = uiState.todayState,
          onStartWorkout = { configJson ->
              onBootcampWorkoutStarting()
              onStartWorkout(configJson)
          },
          onReschedule = if (todaySessionId != null) {
              { onReschedule(todaySessionId) }
          } else null,
          onSwapToRest = onSwapTodayForRest
      )
  }
  ```

- [ ] **Step 2: Remove deleted composables**

  Delete the following composable functions from `BootcampScreen.kt` (they are fully replaced):
  - `WeekSessionList` (the entire `@Composable private fun WeekSessionList(...)` function)
  - `SessionRow` (used only by `WeekSessionList`)
  - `NextSessionCard` (the entire `@Composable private fun NextSessionCard(...)` function)

  Also remove the private `buildConfigJson` call from `NextSessionCard` — verify `buildConfigJson` is now called from `TodayContextCard`. If `buildConfigJson` is a top-level private function in `BootcampScreen.kt`, keep it.

- [ ] **Step 3: Full compile check**

  ```bash
  .\gradlew.bat :app:compileDebugKotlin 2>&1 | tail -30
  ```

  Expected: `BUILD SUCCESSFUL` with 0 errors.

- [ ] **Step 4: Run all bootcamp-related unit tests**

  ```bash
  .\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.*" --tests "com.hrcoach.data.repository.BootcampRepositoryTest" --tests "com.hrcoach.domain.bootcamp.*" 2>&1 | tail -30
  ```

  Expected: all pass.

- [ ] **Step 5: Final commit**

  ```bash
  git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
  git commit -m "feat(bootcamp): replace WeekSessionList+NextSessionCard with WeekStripCard+TodayContextCard"
  ```

---

## Done

The dashboard now:
- Shows all 7 days in a strip with a tick+glow indicator at today's position
- Displays the week's calendar date range ("Mar 10–16")
- Correctly identifies three states: run upcoming, run done, and rest day
- Shows a session preview (description, duration) for the next session in all states
- Only shows the "Start Run" CTA when today has an upcoming session
- Unifies the old `scheduledRestDay` StatusCard and `NextSessionCard` into one adaptive component
