# HomeScreen PULSE Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current scrollable HomeScreen with a fixed-height PULSE ECG hero + tiered data tiles layout matching `mockups/home-final.html`.

**Architecture:** The HomeScreen composable switches from a scrollable Column to a non-scrollable Column with weighted/fixed-height sections. No ViewModel changes needed — all data fields already exist in `HomeUiState`. New composables replace existing ones: `PulseHero` replaces `BootcampHeroCard`, `GoalTile`/`StreakTile` replace `StatChipsRow`, compact `BootcampTile`/`VolumeTile` replace the full-width ring+volume cards, and `CoachingStrip` replaces `CoachingInsightCard`. Custom Canvas-drawn icons replace emoji.

**Tech Stack:** Kotlin, Jetpack Compose, Canvas API for ECG line and custom icons, existing Cardea theme tokens.

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` | All composable changes — hero, tiles, coaching strip, root layout |
| No change | `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` | All needed state fields already exist |
| No change | `app/src/main/java/com/hrcoach/domain/coaching/CoachingInsight.kt` | Model unchanged |
| No change | `app/src/main/java/com/hrcoach/ui/theme/Color.kt` | All colors already defined |
| No change | `app/src/main/java/com/hrcoach/ui/components/CardeaButton.kt` | CTA button already exists |
| No change | `app/src/main/java/com/hrcoach/ui/components/GlassCard.kt` | Referenced but tiles use direct Modifier styling |

---

### Task 1: Replace Root Layout — Remove Scroll, Add Fixed Sections

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt:651-796` (the `HomeScreen` composable)

The root `Column` currently uses `.verticalScroll(rememberScrollState())`. Replace with a non-scrollable `Column` using `fillMaxSize()` with weighted children matching the mockup's flex layout.

- [ ] **Step 1: Restructure the HomeScreen composable**

Replace the scrollable Column (L677-794) with this structure:

```kotlin
@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onGoToProgress: () -> Unit,
    onGoToHistory: () -> Unit,
    onGoToAccount: () -> Unit,
    onGoToBootcamp: () -> Unit,
    onGoToWorkout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bgSecondary = CardeaTheme.colors.bgSecondary
    val bgPrimary = CardeaTheme.colors.bgPrimary
    val backgroundBrush = remember(bgSecondary, bgPrimary) {
        Brush.radialGradient(
            colors = listOf(bgSecondary, bgPrimary),
            center = Offset.Zero,
            radius = 1800f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Active session banner (if running)
            if (state.isSessionRunning) {
                ActiveSessionCard(onClick = onGoToWorkout)
            }

            // Greeting row — fixed height
            GreetingRow(
                greeting = state.greeting,
                sensorName = state.sensorName,
                isSessionRunning = state.isSessionRunning,
                onSensorClick = onStartRun,
                onAccountClick = onGoToAccount
            )

            // Hero section
            val nextSession = state.nextSession
            if (state.hasActiveBootcamp && nextSession != null) {
                PulseHero(
                    session = nextSession,
                    weekNumber = state.currentWeekNumber,
                    isToday = state.isNextSessionToday,
                    dayLabel = state.nextSessionDayLabel
                )
            } else {
                NoBootcampCard(
                    onSetupBootcamp = onGoToBootcamp
                )
            }

            // CTA row — fixed height
            CtaRow(
                hasActiveBootcamp = state.hasActiveBootcamp,
                isNextSessionToday = state.isNextSessionToday,
                onStartSession = onGoToBootcamp,
                onSetupBootcamp = onGoToBootcamp
            )

            // Bottom half — fills remaining space
            BottomHalf(
                state = state,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (with stub composables — they'll be empty initially, built in subsequent tasks)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "refactor(home): replace scrollable layout with fixed-height column structure"
```

---

### Task 2: Implement GreetingRow

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

Extract the existing greeting header (L690-745) into a standalone `GreetingRow` composable. The mockup shows: left-aligned "Good morning" in `text-secondary` at 15sp, right side has sensor icon button (32dp circle, glass border) + profile icon button (32dp circle, glass border).

- [ ] **Step 1: Write the GreetingRow composable**

```kotlin
@Composable
private fun GreetingRow(
    greeting: String,
    sensorName: String?,
    isSessionRunning: Boolean,
    onSensorClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Sensor icon button
            IconButton32(onClick = onSensorClick) {
                SensorIcon(
                    isConnected = isSessionRunning,
                    hasName = sensorName != null
                )
            }
            // Profile icon button
            IconButton32(onClick = onAccountClick) {
                ProfileIcon()
            }
        }
    }
}

@Composable
private fun IconButton32(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, CardeaTheme.colors.glassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
```

- [ ] **Step 2: Draw the custom sensor and profile Canvas icons**

```kotlin
@Composable
private fun SensorIcon(isConnected: Boolean, hasName: Boolean) {
    val color = when {
        isConnected -> CardeaTheme.colors.zoneGreen
        hasName -> CardeaTheme.colors.textSecondary
        else -> CardeaTheme.colors.textTertiary
    }
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Center circle (sensor body)
        drawCircle(
            color = color,
            radius = w * 0.15f,
            center = center,
            style = Stroke(width = 1.5f * density)
        )
        // Left arc
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.08f, h * 0.2f),
            size = Size(w * 0.35f, h * 0.6f)
        )
        // Right arc
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.57f, h * 0.2f),
            size = Size(w * 0.35f, h * 0.6f)
        )
    }
}

@Composable
private fun ProfileIcon() {
    val color = CardeaTheme.colors.textTertiary
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Head circle
        drawCircle(
            color = color,
            radius = w * 0.19f,
            center = Offset(w * 0.5f, h * 0.35f),
            style = Stroke(width = 1.5f * density)
        )
        // Body arc
        drawArc(
            color = color,
            startAngle = -180f,
            sweepAngle = -180f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.15f, h * 0.55f),
            size = Size(w * 0.7f, h * 0.5f)
        )
    }
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add GreetingRow with custom sensor and profile canvas icons"
```

---

### Task 3: Implement PulseHero with ECG Line

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

The PULSE hero replaces `BootcampHeroCard`. It's a 195dp-tall section with:
- Subtle gradient background (`rgba(255,77,90,0.08)` → `rgba(77,97,255,0.08)`)
- Session label: "TODAY'S SESSION · WEEK 5" in monospace 10sp, tertiary color
- Session type: "Z2 Easy Run" in 30sp bold white
- Session detail: "35 min · Aerobic Base" in 14sp secondary
- Zone pill: rounded pill with zone-appropriate color
- ECG line at bottom: Canvas-drawn polyline using Cardea gradient, 0.45 opacity, 85dp tall
- Radial glow behind ECG peak

- [ ] **Step 1: Write the PulseHero composable**

```kotlin
@Composable
private fun PulseHero(
    session: BootcampSessionEntity,
    weekNumber: Int,
    isToday: Boolean,
    dayLabel: String,
    modifier: Modifier = Modifier
) {
    val (pillBg, pillText) = zonePillColors(session.sessionType)
    val sessionLabel = session.sessionType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    val headerText = if (isToday) {
        "TODAY'S SESSION \u00B7 WEEK $weekNumber"
    } else {
        "NEXT RUN \u00B7 $dayLabel"
    }

    val heroAlpha = if (isToday) 1f else 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(195.dp)
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFF4D5A).copy(alpha = 0.08f * heroAlpha),
                        0.5f to Color(0xFF4D61FF).copy(alpha = 0.08f * heroAlpha),
                        1f to Color(0xFF00E5FF).copy(alpha = 0.03f * heroAlpha)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 18.dp)
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 10.sp
                ),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = if (isToday) Color.White else CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${session.targetMinutes} min \u00B7 $sessionLabel",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(12.dp))
            // Zone pill
            Box(
                modifier = Modifier
                    .background(
                        color = pillBg,
                        shape = RoundedCornerShape(100.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = session.sessionType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = pillText
                )
            }
        }

        // ECG glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-15).dp)
                .size(width = 180.dp, height = 60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2DA6).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // ECG line
        EcgLine(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(85.dp),
            alpha = 0.45f
        )
    }
}
```

- [ ] **Step 2: Write the EcgLine Canvas composable**

```kotlin
@Composable
private fun EcgLine(modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    val red = GradientRed
    val pink = GradientPink
    val blue = GradientBlue
    val cyan = GradientCyan

    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        val w = size.width
        val h = size.height

        // ECG path points (normalized 0-1 from the mockup SVG viewBox 393x85)
        val points = listOf(
            0f to 0.612f,
            0.191f to 0.612f,
            0.242f to 0.612f,
            0.285f to 0.212f,
            0.326f to 0.871f,
            0.366f to 0.118f,
            0.407f to 0.706f,
            0.445f to 0.494f,
            0.489f to 0.612f,
            0.682f to 0.612f,
            0.733f to 0.612f,
            0.776f to 0.235f,
            0.817f to 0.847f,
            0.857f to 0.165f,
            0.898f to 0.682f,
            0.936f to 0.518f,
            0.975f to 0.612f,
            1f to 0.612f
        )

        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { i, (nx, ny) ->
                val x = nx * w
                val y = ny * h
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(red, pink, blue, cyan)),
            style = Stroke(
                width = 2.5f * density,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add PulseHero composable with ECG gradient line"
```

---

### Task 4: Implement CTA Row

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

A 56dp-tall row containing a full-width `CardeaButton`. Shows "Start Session" when bootcamp is active and session is today, "Set Up Bootcamp" when no bootcamp.

- [ ] **Step 1: Write the CtaRow composable**

```kotlin
@Composable
private fun CtaRow(
    hasActiveBootcamp: Boolean,
    isNextSessionToday: Boolean,
    onStartSession: () -> Unit,
    onSetupBootcamp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        CardeaButton(
            text = if (hasActiveBootcamp) "Start Session" else "Set Up Bootcamp",
            onClick = if (hasActiveBootcamp) onStartSession else onSetupBootcamp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
    }
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add CtaRow with gradient CTA button"
```

---

### Task 5: Implement BottomHalf with Primary Row (Goal + Streak Tiles)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

The bottom half fills remaining space with `Column(gap=12dp)`. The primary row uses `Row` with `weight(1f)` to fill. Two tiles:

- **GoalTile** (flex 1.2): Gradient border (`rgba(255,45,166,0.12)`), gradient background, monospace "WEEKLY GOAL" label in tertiary, gradient-painted "3/4" value at 40sp, "one more run" sub in secondary at 0.7 opacity.
- **StreakTile** (flex 0.8): Plain glass border/highlight, monospace "STREAK" label in tertiary, white "7" value at 40sp, "no misses" sub in secondary at 0.7 opacity.

- [ ] **Step 1: Write the BottomHalf composable container**

```kotlin
@Composable
private fun BottomHalf(state: HomeUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tier 1: Goal + Streak — fills available space
        PrimaryRow(
            state = state,
            modifier = Modifier.weight(1f)
        )

        // Tier 2: Bootcamp + Volume — fixed height
        if (state.hasActiveBootcamp) {
            MidRow(state = state)
        }

        // Tier 3: Coaching strip — fixed height
        state.coachingInsight?.let { insight ->
            CoachingStrip(insight = insight)
        }
    }
}
```

- [ ] **Step 2: Write the PrimaryRow, GoalTile, and StreakTile composables**

```kotlin
@Composable
private fun PrimaryRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GoalTile(
            current = state.workoutsThisWeek,
            target = state.weeklyTarget,
            modifier = Modifier.weight(1.2f)
        )
        StreakTile(
            streak = state.sessionStreak,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
private fun GoalTile(current: Int, target: Int, modifier: Modifier = Modifier) {
    val gradient = CardeaTheme.colors.gradient
    val remaining = (target - current).coerceAtLeast(0)
    val subText = when {
        remaining == 0 -> "goal hit!"
        remaining == 1 -> "one more run"
        else -> "$remaining more runs"
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFF4D5A).copy(alpha = 0.06f),
                        1f to Color(0xFF4D61FF).copy(alpha = 0.03f)
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                1.dp,
                Color(0xFFFF2DA6).copy(alpha = 0.12f),
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WEEKLY GOAL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 9.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$current/$target",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 40.sp
            ),
            color = Color.White, // base; gradient paints over
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
                }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = CardeaTheme.colors.textSecondary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StreakTile(streak: Int, modifier: Modifier = Modifier) {
    val subText = if (streak > 0) "no misses" else "start today"

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "STREAK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 9.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = streak.toString(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 40.sp
            ),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = CardeaTheme.colors.textSecondary.copy(alpha = 0.7f)
        )
    }
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add tiered GoalTile (gradient) and StreakTile (white) in primary row"
```

---

### Task 6: Implement MidRow (Bootcamp Tile + Volume Tile)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

Two equal-width tiles side by side at fixed intrinsic height:

- **BootcampTile**: 44dp progress ring (gradient arc on glass track), "W5" label centered, "BOOTCAMP" header + "42%" in white, 5dp gradient progress bar.
- **VolumeTile**: Two rows: DST bar (label + track + "12/15") and MIN bar (label + track + "62/90"). Bars are 4dp tall, white 20% fill.

- [ ] **Step 1: Write the MidRow, BootcampTile, and VolumeTile composables**

```kotlin
@Composable
private fun MidRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BootcampTile(
            currentWeek = state.currentWeekNumber,
            totalWeeks = state.bootcampTotalWeeks,
            percentComplete = state.bootcampPercentComplete,
            modifier = Modifier.weight(1f)
        )
        VolumeTile(
            distanceKm = metersToKm(state.totalDistanceThisWeekMeters.toFloat()),
            distanceTargetKm = state.weeklyDistanceTargetKm,
            timeMinutes = state.totalTimeThisWeekMinutes,
            timeTargetMinutes = state.weeklyTimeTargetMinutes,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BootcampTile(
    currentWeek: Int,
    totalWeeks: Int,
    percentComplete: Float,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percentComplete,
        animationSpec = tween(durationMillis = 1000),
        label = "bootcampRing"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Compact progress ring
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(44.dp)) {
                val strokeW = 4.dp.toPx()
                val radius = (size.minDimension - strokeW) / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = radius,
                    style = Stroke(width = strokeW)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(GradientRed, GradientPink, GradientBlue)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedPercent.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    alpha = 0.75f
                )
            }
            Text(
                text = "W$currentWeek",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = CardeaTheme.colors.textSecondary
            )
        }

        // Info column
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "BOOTCAMP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 9.sp
                    ),
                    color = CardeaTheme.colors.textSecondary
                )
                Text(
                    text = "${(percentComplete * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = Color.White
                )
            }
            Spacer(Modifier.height(6.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPercent.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(brush = CardeaTheme.colors.gradient, alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun VolumeTile(
    distanceKm: Float,
    distanceTargetKm: Double,
    timeMinutes: Long,
    timeTargetMinutes: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        VolumeRow(
            label = "DST",
            value = "%.0f/%.0f".format(distanceKm, distanceTargetKm),
            progress = (distanceKm / distanceTargetKm.toFloat()).coerceIn(0f, 1f)
        )
        VolumeRow(
            label = "MIN",
            value = "$timeMinutes/$timeTargetMinutes",
            progress = (timeMinutes.toFloat() / timeTargetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun VolumeRow(label: String, value: String, progress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                fontSize = 9.sp
            ),
            color = CardeaTheme.colors.textTertiary,
            modifier = Modifier.width(26.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.20f))
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
    }
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add compact BootcampTile with ring and VolumeTile with DST/MIN bars"
```

---

### Task 7: Implement CoachingStrip with Custom ECG Icon

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

Replaces `CoachingInsightCard` with a single-line strip: 12dp rounded glass card, 18dp ECG icon at 0.5 opacity (gradient polyline), coaching text in text-secondary with italic emphasis suffix.

- [ ] **Step 1: Write the CoachingStrip and EcgIcon composables**

```kotlin
@Composable
private fun CoachingStrip(insight: CoachingInsight, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CoachingEcgIcon(modifier = Modifier.size(18.dp))
        Text(
            text = insight.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = CardeaTheme.colors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun CoachingEcgIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.graphicsLayer { alpha = 0.5f }) {
        val w = size.width
        val h = size.height
        // ECG polyline: 1,9 4.5,9 6,3.5 7.5,14.5 9.5,5.5 11,11 12.5,9 17,9
        // Normalized to 0-1 range (viewBox 0 0 18 18)
        val points = listOf(
            0.056f to 0.5f,
            0.25f to 0.5f,
            0.333f to 0.194f,
            0.417f to 0.806f,
            0.528f to 0.306f,
            0.611f to 0.611f,
            0.694f to 0.5f,
            0.944f to 0.5f
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { i, (nx, ny) ->
                val x = nx * w
                val y = ny * h
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(GradientRed, GradientPink, GradientBlue)
            ),
            style = Stroke(
                width = 1.8f * density,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): add CoachingStrip with custom ECG canvas icon replacing emoji"
```

---

### Task 8: Clean Up — Remove Old Composables and Unused Imports

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

Delete the old composables that are no longer called:
- `StatChip` (L76-122)
- `StatChipsRow` (L124-167)
- `BootcampProgressRing` (L169-273)
- `WeeklyVolumeCard` (L275-324)
- `VolumeBar` (L326-367) — note: the old VolumeBar, not the new VolumeRow
- `CoachingInsightCard` (L369-440)
- `BootcampHeroCard` (L442-602)

Keep: `zonePillColors`, `NoBootcampCard`, and all new composables.

Also clean up unused imports (e.g., `Icons.Default.Bluetooth`, `verticalScroll`, `rememberScrollState`, `IntrinsicSize`).

- [ ] **Step 1: Delete old composables and unused imports**

Remove the 7 composables listed above and any imports that are no longer referenced.

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "refactor(home): remove old scrollable HomeScreen composables and unused imports"
```

---

### Task 9: Build and Install APK

**Files:** None modified — build and deploy only.

- [ ] **Step 1: Build debug APK**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on connected device**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: Success

- [ ] **Step 3: Verify the app launches**

Run: `adb shell am start -n com.hrcoach/.MainActivity`
Expected: App opens to the HomeScreen with the new PULSE layout.
