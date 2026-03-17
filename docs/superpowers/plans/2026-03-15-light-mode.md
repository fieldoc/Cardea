# Light Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a consciously-designed light mode with a 3-way theme toggle (System/Light/Dark) in Profile.

**Architecture:** Replace top-level color constants with a `CardeaColors` data class served via `CompositionLocal`. `CardeaTheme` selects dark or light palette based on a persisted `ThemeMode` preference. `GlassCard` becomes theme-adaptive (gradient fill in dark, elevated solid fill in light). Active workout screen forces dark mode.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, SharedPreferences, Hilt DI

**Spec:** `docs/superpowers/specs/2026-03-15-light-mode-design.md`

---

## File Map

| Action | Path | Responsibility |
|--------|------|---------------|
| Create | `domain/model/ThemeMode.kt` | ThemeMode enum |
| Create | `data/repository/ThemePreferencesRepository.kt` | Persist theme choice |
| Create | `ui/theme/CardeaColors.kt` | CardeaColors data class + dark/light instances + CompositionLocal |
| Modify | `ui/theme/Color.kt` | Keep raw palette vals, add light-specific raw vals |
| Modify | `ui/theme/Theme.kt` | Wire CardeaColors into CardeaTheme, accept isDarkTheme |
| Modify | `ui/theme/Type.kt` | Remove hardcoded SubtleText from labelSmall |
| Modify | `ui/components/GlassCard.kt` | Read fill/elevation from CardeaColors |
| Modify | `ui/components/CardeaInputs.kt` | Use theme tokens |
| Modify | `ui/components/ActiveSessionCard.kt` | Use theme tokens |
| Modify | `ui/components/AchievementCard.kt` | Use theme tokens |
| Modify | `ui/account/AccountViewModel.kt` | Add themeMode flow |
| Modify | `ui/account/AccountScreen.kt` | Add Appearance section + migrate colors |
| Modify | `MainActivity.kt` | Observe theme preference, pass to CardeaTheme |
| Modify | `ui/home/HomeScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/bootcamp/BootcampScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/bootcamp/BootcampSettingsScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/setup/SetupScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/history/HistoryDetailScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/history/HistoryListScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/progress/ProgressScreen.kt` | Migrate hardcoded colors |
| Modify | `ui/navigation/NavGraph.kt` | Migrate icon tint |
| Modify | `ui/charts/BarChart.kt` | Use chartGrid token |
| Modify | `ui/charts/ScatterPlot.kt` | Use chartGrid token |
| Modify | `ui/charts/CalendarHeatmap.kt` | Use theme tokens |
| Modify | `ui/postrun/PostRunSummaryScreen.kt` | Ambient washes stay, minor fixes |
| Modify | `ui/workout/ActiveWorkoutScreen.kt` | Wrap in forced-dark CompositionLocal |
| Modify | `ui/splash/SplashScreen.kt` | Use bgPrimary token |
| Create | `test/.../theme/CardeaColorsTest.kt` | Verify both palettes are structurally valid |
| Create | `test/.../repository/ThemePreferencesRepositoryTest.kt` | Persist/read theme mode |

All paths relative to `app/src/main/java/com/hrcoach/` (or `app/src/test/java/com/hrcoach/` for tests).

---

## Chunk 1: Token Infrastructure

### Task 1: Create ThemeMode enum

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/model/ThemeMode.kt`

- [ ] **Step 1: Create ThemeMode enum**

```kotlin
package com.hrcoach.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/ThemeMode.kt
git commit -m "feat(theme): add ThemeMode enum"
```

---

### Task 2: Add light-mode raw color values to Color.kt

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Color.kt`

- [ ] **Step 1: Add light-mode background and surface values**

Add at the bottom of Color.kt, before the legacy aliases section:

```kotlin
// ── Light-mode palette values ────────────────────────────────
val CardeaLightBgPrimary    = Color(0xFFFAFAFA)
val CardeaLightBgSecondary  = Color(0xFFF0F0F2)
val CardeaLightSurfaceVariant = Color(0xFFE8E8EC)

val LightGlassBorder    = Color(0x1A000000)   // 10% black
val LightGlassHighlight = Color(0x0A000000)   // 4% black
val LightGlassSurface   = Color(0x0F000000)   // 6% black

val CardeaLightTextPrimary   = Color(0xFF1A1A1A)
val CardeaLightTextSecondary = Color(0xFF6B6B73)
val CardeaLightTextTertiary  = Color(0xFFA1A1AA)

// Light-mode achievement alpha adjustments
val LightAchievementSlateBorder = Color(0x3394A3B8) // 20%
val LightAchievementSlateBg     = Color(0x1A94A3B8) // 10%
val LightAchievementSkyBorder   = Color(0x4D7DD3FC) // 30%
val LightAchievementSkyBg       = Color(0x1A7DD3FC) // 10%
val LightAchievementGoldBorder  = Color(0x59FACC15) // 35%
val LightAchievementGoldBg      = Color(0x24FACC15) // 14%

// Accent pink — used in profile/avatar, same in both themes
val CardeaAccentPink = Color(0xFFFF6B8A)

// Blackout day colors (bootcamp calendar) — dark theme
val BlackoutBg     = Color(0xFF1C1F26)
val BlackoutBorder = Color(0xFF3D2020)
val BlackoutText   = Color(0xFF8B3A3A)

// Light-mode blackout colors
val LightBlackoutBg     = Color(0xFFE8E0E0)
val LightBlackoutBorder = Color(0xFFD4A0A0)
val LightBlackoutText   = Color(0xFF8B3A3A) // same — legible on both

// Chart grid
val ChartGridDark  = Color(0x0AFFFFFF)
val ChartGridLight = Color(0x14000000)

// Map overlay background
val MapOverlayBg = Color(0x99000000) // same for both — overlays map tiles
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/Color.kt
git commit -m "feat(theme): add light-mode raw color values"
```

---

### Task 3: Create CardeaColors data class and CompositionLocal

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/theme/CardeaColors.kt`

- [ ] **Step 1: Write CardeaColors data class with dark and light instances**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CardeaColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val surfaceVariant: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassSurface: Color,
    val glassFillBrush: Brush,
    val glassElevation: Dp,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val gradient: Brush,
    val ctaGradient: Brush,
    val navGradient: Brush,
    val zoneGreen: Color,
    val zoneAmber: Color,
    val zoneRed: Color,
    val divider: Color,
    val accentPink: Color,
    val onGradient: Color,
    val blackoutBg: Color,
    val blackoutBorder: Color,
    val blackoutText: Color,
    val chartGrid: Color,
    val mapOverlayBg: Color,
    val achievementSlateBorder: Color,
    val achievementSlateBg: Color,
    val achievementSkyBorder: Color,
    val achievementSkyBg: Color,
    val achievementGoldBorder: Color,
    val achievementGoldBg: Color,
    val isDark: Boolean,
)

private val DarkGlassFillBrush = Brush.verticalGradient(
    colors = listOf(Color(0x0FFFFFFF), Color(0x05FFFFFF))
)

private val LightGlassFillBrush = Brush.verticalGradient(
    colors = listOf(Color(0x00000000), Color(0x00000000)) // no visible gradient in light
)

val DarkCardeaColors = CardeaColors(
    bgPrimary = CardeaBgPrimary,
    bgSecondary = CardeaBgSecondary,
    surfaceVariant = SurfaceVariant,
    glassBorder = GlassBorder,
    glassHighlight = GlassHighlight,
    glassSurface = GlassSurface,
    glassFillBrush = DarkGlassFillBrush,
    glassElevation = 0.dp,
    textPrimary = CardeaTextPrimary,
    textSecondary = CardeaTextSecondary,
    textTertiary = CardeaTextTertiary,
    gradient = CardeaGradient,
    ctaGradient = CardeaCtaGradient,
    navGradient = CardeaNavGradient,
    zoneGreen = ZoneGreen,
    zoneAmber = ZoneAmber,
    zoneRed = ZoneRed,
    divider = GlassBorder,
    accentPink = CardeaAccentPink,
    onGradient = Color.White,
    blackoutBg = BlackoutBg,
    blackoutBorder = BlackoutBorder,
    blackoutText = BlackoutText,
    chartGrid = ChartGridDark,
    mapOverlayBg = MapOverlayBg,
    achievementSlateBorder = AchievementSlateBorder,
    achievementSlateBg = AchievementSlateBg,
    achievementSkyBorder = AchievementSkyBorder,
    achievementSkyBg = AchievementSkyBg,
    achievementGoldBorder = AchievementGoldBorder,
    achievementGoldBg = AchievementGoldBg,
    isDark = true,
)

val LightCardeaColors = CardeaColors(
    bgPrimary = CardeaLightBgPrimary,
    bgSecondary = CardeaLightBgSecondary,
    surfaceVariant = CardeaLightSurfaceVariant,
    glassBorder = LightGlassBorder,
    glassHighlight = LightGlassHighlight,
    glassSurface = LightGlassSurface,
    glassFillBrush = LightGlassFillBrush,
    glassElevation = 2.dp,
    textPrimary = CardeaLightTextPrimary,
    textSecondary = CardeaLightTextSecondary,
    textTertiary = CardeaLightTextTertiary,
    gradient = CardeaGradient,
    ctaGradient = CardeaCtaGradient,
    navGradient = CardeaNavGradient,
    zoneGreen = ZoneGreen,
    zoneAmber = ZoneAmber,
    zoneRed = ZoneRed,
    divider = LightGlassBorder,
    accentPink = CardeaAccentPink,
    onGradient = Color.White,
    blackoutBg = LightBlackoutBg,
    blackoutBorder = LightBlackoutBorder,
    blackoutText = BlackoutText,
    chartGrid = ChartGridLight,
    mapOverlayBg = MapOverlayBg,
    achievementSlateBorder = LightAchievementSlateBorder,
    achievementSlateBg = LightAchievementSlateBg,
    achievementSkyBorder = LightAchievementSkyBorder,
    achievementSkyBg = LightAchievementSkyBg,
    achievementGoldBorder = LightAchievementGoldBorder,
    achievementGoldBg = LightAchievementGoldBg,
    isDark = false,
)

val LocalCardeaColors = staticCompositionLocalOf { DarkCardeaColors }

/** Primary access point for Cardea theme colors in composables. */
object CardeaTheme {
    val colors: CardeaColors
        @Composable @ReadOnlyComposable
        get() = LocalCardeaColors.current
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/CardeaColors.kt
git commit -m "feat(theme): add CardeaColors data class with dark/light palettes"
```

---

### Task 4: Rewrite Theme.kt to support light mode

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Theme.kt`

- [ ] **Step 1: Add a light color scheme and wire CardeaTheme to accept isDarkTheme**

Replace Theme.kt contents with:

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val CardeaDarkColorScheme = darkColorScheme(
    primary          = GradientBlue,
    secondary        = ZoneAmber,
    tertiary         = ZoneGreen,
    error            = ZoneRed,
    onPrimary        = CardeaTextPrimary,
    background       = CardeaBgPrimary,
    onBackground     = CardeaTextPrimary,
    surface          = CardeaBgSecondary,
    onSurface        = CardeaTextPrimary,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = CardeaTextSecondary,
    outline          = GlassBorder,
    outlineVariant   = GlassHighlight
)

private val CardeaLightColorScheme = lightColorScheme(
    primary          = GradientBlue,
    secondary        = ZoneAmber,
    tertiary         = ZoneGreen,
    error            = ZoneRed,
    onPrimary        = Color.White,
    background       = CardeaLightBgPrimary,
    onBackground     = CardeaLightTextPrimary,
    surface          = CardeaLightBgSecondary,
    onSurface        = CardeaLightTextPrimary,
    surfaceVariant   = CardeaLightSurfaceVariant,
    onSurfaceVariant = CardeaLightTextSecondary,
    outline          = LightGlassBorder,
    outlineVariant   = LightGlassHighlight
)

// Legacy CompositionLocals — deprecated, use CardeaTheme.colors instead
val LocalGlassBorder     = staticCompositionLocalOf { GlassBorder }
val LocalSubtleText      = staticCompositionLocalOf { CardeaTextSecondary }
val LocalCardeaGradient  = staticCompositionLocalOf<Brush> { CardeaGradient }

object CardeaThemeTokens {
    val glassBorder: Color
        @Composable @ReadOnlyComposable
        get() = LocalGlassBorder.current

    val subtleText: Color
        @Composable @ReadOnlyComposable
        get() = LocalSubtleText.current

    val gradient: Brush
        @Composable @ReadOnlyComposable
        get() = LocalCardeaGradient.current
}

typealias HrCoachThemeTokens = CardeaThemeTokens

@Composable
fun CardeaTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDarkTheme) DarkCardeaColors else LightCardeaColors
    val materialScheme = if (isDarkTheme) CardeaDarkColorScheme else CardeaLightColorScheme

    CompositionLocalProvider(
        LocalCardeaColors   provides colors,
        LocalGlassBorder    provides colors.glassBorder,
        LocalSubtleText     provides colors.textSecondary,
        LocalCardeaGradient provides colors.gradient
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography  = HrCoachTypography,
            content     = content
        )
    }
}

@Composable
fun HrCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CardeaTheme(isDarkTheme = darkTheme, content = content)
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/Theme.kt
git commit -m "feat(theme): wire CardeaTheme to support dark/light switching"
```

---

### Task 5: Fix Type.kt hardcoded color

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Type.kt`

- [ ] **Step 1: Remove hardcoded `color = SubtleText` from labelSmall**

In `Type.kt`, change `labelSmall` from:
```kotlin
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = SubtleText
    )
```
to:
```kotlin
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/Type.kt
git commit -m "fix(theme): remove hardcoded SubtleText color from labelSmall typography"
```

---

## Chunk 2: Persistence and Plumbing

### Task 6: Create ThemePreferencesRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/ThemePreferencesRepository.kt`
- Create: `app/src/test/java/com/hrcoach/data/repository/ThemePreferencesRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hrcoach.domain.model.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemePreferencesRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repo: ThemePreferencesRepository

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        prefs = mockk {
            every { getString("theme_mode", "SYSTEM") } returns "SYSTEM"
            every { edit() } returns editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences("cardea_theme_prefs", Context.MODE_PRIVATE) } returns prefs
        }
        repo = ThemePreferencesRepository(context)
    }

    @Test
    fun `default theme mode is SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }

    @Test
    fun `setThemeMode persists DARK`() {
        every { editor.putString(any(), any()) } returns editor
        repo.setThemeMode(ThemeMode.DARK)
        verify { editor.putString("theme_mode", "DARK") }
        verify { editor.apply() }
    }

    @Test
    fun `getThemeMode reads stored LIGHT`() {
        every { prefs.getString("theme_mode", "SYSTEM") } returns "LIGHT"
        assertEquals(ThemeMode.LIGHT, repo.getThemeMode())
    }

    @Test
    fun `invalid stored value falls back to SYSTEM`() {
        every { prefs.getString("theme_mode", "SYSTEM") } returns "GARBAGE"
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.data.repository.ThemePreferencesRepositoryTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write the implementation**

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import com.hrcoach.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "cardea_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(stored ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.data.repository.ThemePreferencesRepositoryTest"`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/ThemePreferencesRepository.kt \
       app/src/main/java/com/hrcoach/domain/model/ThemeMode.kt \
       app/src/test/java/com/hrcoach/data/repository/ThemePreferencesRepositoryTest.kt
git commit -m "feat(theme): add ThemePreferencesRepository with tests"
```

---

### Task 7: Wire MainActivity to observe theme preference

**Files:**
- Modify: `app/src/main/java/com/hrcoach/MainActivity.kt`

- [ ] **Step 1: Inject ThemePreferencesRepository and pass isDarkTheme to HrCoachTheme**

Replace `MainActivity.kt` contents with:

```kotlin
package com.hrcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.domain.model.ThemeMode
import com.hrcoach.ui.navigation.HrCoachNavGraph
import com.hrcoach.ui.theme.HrCoachTheme
import com.hrcoach.util.MapsApiKeyRuntime
import com.hrcoach.util.PermissionGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var mapsSettingsRepository: MapsSettingsRepository

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _themeMode.value = themePreferencesRepository.getThemeMode()

        val mapsApiKey = mapsSettingsRepository.getMapsApiKey()
        MapsApiKeyRuntime.applyIfPresent(this, mapsApiKey)

        val missingPermissions = PermissionGate.missingRuntimePermissions(this)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            val themeMode by _themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HrCoachTheme(darkTheme = isDark) {
                HrCoachNavGraph(
                    windowSizeClass = calculateWindowSizeClass(this),
                    onThemeModeChanged = { mode ->
                        themePreferencesRepository.setThemeMode(mode)
                        _themeMode.value = mode
                    },
                    currentThemeMode = themeMode
                )
            }
        }
    }
}
```

Note: This adds `onThemeModeChanged` and `currentThemeMode` parameters to `HrCoachNavGraph`. These will be wired in Task 8.

- [ ] **Step 2: Update HrCoachNavGraph signature to accept theme parameters**

In `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`, add the new parameters to the function signature:

```kotlin
@Composable
fun HrCoachNavGraph(
    windowSizeClass: WindowSizeClass,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM
)
```

Add the necessary import:
```kotlin
import com.hrcoach.domain.model.ThemeMode
```

Then pass these through to `AccountScreen` where the route is defined. Find the `composable(route = "account")` block and update it to:

```kotlin
composable(route = "account") {
    AccountScreen(
        onThemeModeChanged = onThemeModeChanged,
        currentThemeMode = currentThemeMode
    )
}
```

- [ ] **Step 3: Verify it compiles** (it won't yet — AccountScreen needs the parameters too. That's Task 8. Just verify no syntax errors by checking the file is well-formed.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/MainActivity.kt \
       app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(theme): wire MainActivity to observe theme preference"
```

---

### Task 8: Add Appearance section to AccountScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`

- [ ] **Step 1: Add theme parameters to AccountScreen**

Update the `AccountScreen` function signature:

```kotlin
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel(),
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM
)
```

Add import:
```kotlin
import com.hrcoach.domain.model.ThemeMode
```

- [ ] **Step 2: Add Appearance section between Profile hero and Configuration**

After the profile hero card and its spacer, before `SectionLabel("Configuration")`, insert:

```kotlin
            // ── Appearance ────────────────────────────────────────────
            SectionLabel("Appearance")
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(8.dp))
                            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textPrimary
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        .forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                colors = cardeaSegmentedButtonColors()
                            ) { Text(label) }
                        }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
```

Add imports:
```kotlin
import androidx.compose.material.icons.filled.Palette
import com.hrcoach.ui.theme.CardeaTheme
```

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(theme): add Appearance section with theme toggle to Profile screen"
```

---

## Chunk 3: Core Component Migration

### Task 9: Make GlassCard theme-aware

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/components/GlassCard.kt`

- [ ] **Step 1: Replace hardcoded fill brush with theme values**

Replace `GlassCard.kt` contents with:

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTheme

private val GlassShape = RoundedCornerShape(18.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    borderColor: Color = CardeaTheme.colors.glassBorder,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardeaTheme.colors
    Card(
        modifier = modifier.wrapContentHeight(),
        shape = GlassShape,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (colors.isDark) Color.Transparent else colors.bgSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = colors.glassElevation)
    ) {
        Box(modifier = Modifier.background(colors.glassFillBrush)) {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = CardeaTheme.colors.textPrimary,
            textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/GlassCard.kt
git commit -m "feat(theme): make GlassCard theme-aware with adaptive fill and elevation"
```

---

### Task 10: Update CardeaInputs to use theme tokens

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/components/CardeaInputs.kt`

- [ ] **Step 1: Replace Color.White and direct token imports with CardeaTheme.colors**

Replace contents with:

```kotlin
package com.hrcoach.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun CardeaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val colors = CardeaTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = colors.gradient.let { com.hrcoach.ui.theme.GradientBlue },
            activeTrackColor = com.hrcoach.ui.theme.GradientBlue,
            inactiveTrackColor = colors.glassHighlight,
            activeTickColor = com.hrcoach.ui.theme.GradientBlue,
            inactiveTickColor = colors.textTertiary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun cardeaSegmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = CardeaTheme.colors.glassHighlight,
    activeContentColor = com.hrcoach.ui.theme.GradientBlue,
    activeBorderColor = com.hrcoach.ui.theme.GradientBlue,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = CardeaTheme.colors.textSecondary,
    inactiveBorderColor = CardeaTheme.colors.glassBorder
)

@Composable
fun CardeaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CardeaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = com.hrcoach.ui.theme.GradientPink,
            checkedThumbColor = colors.onGradient,
            uncheckedTrackColor = colors.glassHighlight,
            uncheckedThumbColor = colors.textTertiary
        )
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/CardeaInputs.kt
git commit -m "feat(theme): update CardeaInputs to use CardeaTheme.colors"
```

---

### Task 11: Update ActiveSessionCard

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/components/ActiveSessionCard.kt`

- [ ] **Step 1: Replace Color.White with theme tokens**

Replace `Color.White` on line 42 and line 53 with `CardeaTheme.colors.textPrimary` and `CardeaTheme.colors.textPrimary` respectively. Replace `CardeaTextSecondary` import with `CardeaTheme.colors.textSecondary`. Replace `CardeaGradient` import with `CardeaTheme.colors.gradient`.

Updated file:

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun ActiveSessionCard(onClick: () -> Unit) {
    val colors = CardeaTheme.colors
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, colors.gradient, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Session",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    text = "Your run is still being recorded in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Resume",
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/ActiveSessionCard.kt
git commit -m "feat(theme): update ActiveSessionCard to use CardeaTheme.colors"
```

---

## Chunk 4: Screen-by-Screen Color Migration

**Strategy for each screen:** Replace `Color.White` text → `CardeaTheme.colors.textPrimary` (or `.onGradient` if on a gradient background). Replace `Color(0x0FFFFFFF)` glass surfaces → `CardeaTheme.colors.glassHighlight`. Replace `CardeaBgPrimary` → `CardeaTheme.colors.bgPrimary`. Replace blackout hex literals → `CardeaTheme.colors.blackout*`. Replace `Color(0xFFFF6B8A)` → `CardeaTheme.colors.accentPink`.

Each task below follows the same pattern: make replacements, compile, commit. I'm listing only the specific replacements per file rather than full file contents (too large).

### Task 12: Migrate AccountScreen colors

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Add `val colors = CardeaTheme.colors` at the top of key composables and replace:**

| Line(s) | Old | New |
|---------|-----|-----|
| 102 | `CardeaBgPrimary` | `CardeaTheme.colors.bgPrimary` |
| 113 | `CardeaTextPrimary` | `CardeaTheme.colors.textPrimary` |
| 176 | `GlassBorder` | `CardeaTheme.colors.glassBorder` |
| 246 | `GlassBorder` | `CardeaTheme.colors.glassBorder` |
| 263 | `GlassBorder` | `CardeaTheme.colors.glassBorder` |
| 352 | `Color(0xFFFF6B8A)` | `CardeaTheme.colors.accentPink` |
| 408 | `CardeaBgPrimary` (bottom sheet) | `CardeaTheme.colors.bgSecondary` |
| 440-442 | `Color(0xFFFF6B8A)` (3 times) | `CardeaTheme.colors.accentPink` |
| 488 | `Color(0xFFFF6B8A)` | `CardeaTheme.colors.accentPink` |
| 482 | `CardeaBgPrimary` (avatar inner bg) | `CardeaTheme.colors.bgPrimary` |
| 346 | `CardeaBgPrimary` (avatar inner bg) | `CardeaTheme.colors.bgPrimary` |
| 536, 577 | `Color(0x0FFFFFFF)` | `CardeaTheme.colors.glassHighlight` |
| 628 | `Color.White` (on gradient button) | `CardeaTheme.colors.onGradient` |
| All `CardeaTextPrimary` imports | direct import | `CardeaTheme.colors.textPrimary` |
| All `CardeaTextSecondary` imports | direct import | `CardeaTheme.colors.textSecondary` |
| All `CardeaTextTertiary` imports | direct import | `CardeaTheme.colors.textTertiary` |

Remove unused color imports after migration.

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(theme): migrate AccountScreen to CardeaTheme.colors"
```

---

### Task 13: Migrate HomeScreen colors

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

- [ ] **Step 1: Replace hardcoded colors**

| Old | New | Notes |
|-----|-----|-------|
| `CardeaBgPrimary` | `CardeaTheme.colors.bgPrimary` | Background |
| `CardeaTextPrimary` | `CardeaTheme.colors.textPrimary` | All text |
| `CardeaTextSecondary` | `CardeaTheme.colors.textSecondary` | Subtitle text |
| `CardeaTextTertiary` | `CardeaTheme.colors.textTertiary` | Hint text |
| `Color(0xFF22C55E)` (line 424) | `CardeaTheme.colors.zoneGreen` | Status dot |
| `GlassBorder` | `CardeaTheme.colors.glassBorder` | Any dividers/borders |

Keep ambient gradient washes (`Color(0x33FF2DA6)`, `Color(0x14E5FFFF)`, etc.) as-is — they work on both themes.

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(theme): migrate HomeScreen to CardeaTheme.colors"
```

---

### Task 14: Migrate BootcampScreen + BootcampSettingsScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt`

- [ ] **Step 1: BootcampScreen replacements**

| Old | New |
|-----|-----|
| `Color(0x1AFFFFFF)` (divider, line 734) | `CardeaTheme.colors.divider` |
| `Color(0xFFFFB74D)` (line 763) | `ZoneAmber` via `CardeaTheme.colors.zoneAmber` |
| `Color(0x0EFFFFFF)` / `Color(0x0FFFFFFF)` (glass surfaces) | `CardeaTheme.colors.glassHighlight` |
| `Color.White` (line 1565) | `CardeaTheme.colors.onGradient` or `.textPrimary` (context-dependent) |
| `Color(0xFF1C1F26)` (blackout bg) | `CardeaTheme.colors.blackoutBg` |
| `Color(0xFF3D2020)` (blackout border) | `CardeaTheme.colors.blackoutBorder` |
| `Color(0xFF8B3A3A)` (blackout text) | `CardeaTheme.colors.blackoutText` |
| `CardeaTextPrimary` (line 1987) | `CardeaTheme.colors.textPrimary` |
| `Color.White` (icon tints inside gradient) | `CardeaTheme.colors.onGradient` |

- [ ] **Step 2: BootcampSettingsScreen replacements**

Same blackout color pattern, plus:

| Old | New |
|-----|-----|
| `Color.White` (text on dark surfaces, ~10 sites) | `CardeaTheme.colors.textPrimary` |
| `Color.White` (text/icon on gradient surfaces) | `CardeaTheme.colors.onGradient` |
| `Color.White` (slider thumb/track, lines 248-249) | `CardeaTheme.colors.onGradient` for thumb; keep active track |
| `Color(0xFFFFB74D)` | `CardeaTheme.colors.zoneAmber` |
| `Color(0x15FF4D5A)` / `Color(0x35FF4D5A)` (exit banner) | Keep as-is (alpha tint on red, works both themes) |

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
       app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt
git commit -m "feat(theme): migrate Bootcamp screens to CardeaTheme.colors"
```

---

### Task 15: Migrate SetupScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

- [ ] **Step 1: Replace hardcoded colors**

| Old | New | Notes |
|-----|-----|-------|
| `Color.White` (text, ~5 sites) | `CardeaTheme.colors.textPrimary` | Body text |
| `Color(0xFF4CAF50)` (connected green) | `CardeaTheme.colors.zoneGreen` | |
| `Color(0xFF4D9FFF)` (mode chip, selection) | `GradientBlue` (keep — it's an accent, not theme-dependent) | |
| `Color(0xFFFF85A1)` / `Color(0xAAFF85A1)` | `CardeaTheme.colors.accentPink` / `.accentPink.copy(alpha = 0.67f)` | |
| `Color(0xFF2B8C6E)` (zone segment default) | Keep as-is — data-encoding color | |
| `Color(0xFFFF5A5F)` / `Color(0xFFE8A838)` (zone pct) | Keep as-is — data-encoding | |
| Ambient gradient washes (`0x1AFF4D6D`, `0x144D9FFF`, etc.) | Keep as-is — alpha tints | |
| `CardeaBgPrimary` | `CardeaTheme.colors.bgPrimary` | |

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat(theme): migrate SetupScreen to CardeaTheme.colors"
```

---

### Task 16: Migrate History screens

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

- [ ] **Step 1: HistoryDetailScreen replacements**

| Old | New | Notes |
|-----|-----|-------|
| `Color.White` (text, ~8 sites) | `CardeaTheme.colors.textPrimary` | |
| `Color(0xFF141B27)` (dialog bg) | `CardeaTheme.colors.bgSecondary` | |
| `Color(0x0FFFFFFF)` / `Color(0x0AFFFFFF)` (glass surfaces) | `CardeaTheme.colors.glassHighlight` | |
| `Color(0x99000000)` (map overlay) | `CardeaTheme.colors.mapOverlayBg` | Keep same — over map tiles |

- [ ] **Step 2: HistoryListScreen replacements**

| Old | New |
|-----|-----|
| `Color(0xFFEF4444)` (delete bg) | `CardeaTheme.colors.zoneRed` |
| `Color.White` (delete icon) | `CardeaTheme.colors.onGradient` |

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt \
       app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "feat(theme): migrate History screens to CardeaTheme.colors"
```

---

### Task 17: Migrate ProgressScreen, charts, NavGraph, PostRunSummary

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/BarChart.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/ScatterPlot.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/CalendarHeatmap.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1: ProgressScreen**

Change `valueColor: Color = Color.White` default parameter (line 312) to `valueColor: Color = CardeaTheme.colors.textPrimary`.

Note: Since this is a default parameter in a Composable, `CardeaTheme.colors` is available at the call site. If the compiler rejects a composable call in a default parameter, instead set it to `Color.Unspecified` and resolve inside the function body:
```kotlin
val resolvedColor = if (valueColor == Color.Unspecified) CardeaTheme.colors.textPrimary else valueColor
```

- [ ] **Step 2: BarChart and ScatterPlot**

Replace `val gridColor = Color(0x0AFFFFFF)` with `val gridColor = CardeaTheme.colors.chartGrid` in both files.

- [ ] **Step 3: CalendarHeatmap**

Replace:
- `Color(0xFF1C2030)` (empty cell) → `CardeaTheme.colors.surfaceVariant`
- `Color(0xFFA1A1AA)` (text) → `CardeaTheme.colors.textSecondary`

- [ ] **Step 4: NavGraph**

Replace `tint = Color.White` (line 576) → `tint = CardeaTheme.colors.textPrimary`

- [ ] **Step 5: PostRunSummaryScreen**

The ambient gradient washes (`Color(0x28FF4D5A)`, `Color(0x124D61FF)`) work on both themes. Replace only:
- Any `CardeaBgPrimary` → `CardeaTheme.colors.bgPrimary`
- Any `Color.White` text → `CardeaTheme.colors.textPrimary`

- [ ] **Step 6: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt \
       app/src/main/java/com/hrcoach/ui/charts/BarChart.kt \
       app/src/main/java/com/hrcoach/ui/charts/ScatterPlot.kt \
       app/src/main/java/com/hrcoach/ui/charts/CalendarHeatmap.kt \
       app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
       app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(theme): migrate Progress, charts, NavGraph, PostRun to CardeaTheme.colors"
```

---

### Task 18: Migrate SplashScreen and AchievementCard

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/components/AchievementCard.kt`

- [ ] **Step 1: SplashScreen**

Replace any `CardeaBgPrimary` → `CardeaTheme.colors.bgPrimary`. Replace any `Color.White` text → `CardeaTheme.colors.textPrimary`.

- [ ] **Step 2: AchievementCard**

Replace direct `AchievementSlateBorder`, `AchievementSlateBg`, etc. imports with `CardeaTheme.colors.achievementSlateBorder`, etc.

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt \
       app/src/main/java/com/hrcoach/ui/components/AchievementCard.kt
git commit -m "feat(theme): migrate SplashScreen and AchievementCard to CardeaTheme.colors"
```

---

## Chunk 5: Forced Dark Mode for Active Workout + Final Testing

### Task 19: Force dark theme on ActiveWorkoutScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` (the composable block for the workout route)

- [ ] **Step 1: Wrap ActiveWorkoutScreen in a forced-dark CompositionLocalProvider**

Find the composable route for the active workout screen in NavGraph.kt. Wrap its content:

```kotlin
composable(route = "workout") {
    CompositionLocalProvider(
        LocalCardeaColors provides DarkCardeaColors
    ) {
        ActiveWorkoutScreen(...)
    }
}
```

Add imports:
```kotlin
import com.hrcoach.ui.theme.LocalCardeaColors
import com.hrcoach.ui.theme.DarkCardeaColors
```

This ensures the workout screen always renders with dark colors regardless of the app theme. The MaterialTheme colorScheme still follows the app theme, but since ActiveWorkoutScreen uses `CardeaTheme.colors` (after migration) and hardcoded dark colors, it will look correct.

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(theme): force dark theme on ActiveWorkoutScreen"
```

---

### Task 20: Write CardeaColors consistency test

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/theme/CardeaColorsTest.kt`

- [ ] **Step 1: Write tests verifying both palettes**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CardeaColorsTest {

    @Test
    fun `dark and light palettes have different backgrounds`() {
        assertNotEquals(DarkCardeaColors.bgPrimary, LightCardeaColors.bgPrimary)
        assertNotEquals(DarkCardeaColors.bgSecondary, LightCardeaColors.bgSecondary)
    }

    @Test
    fun `dark and light palettes have different text colors`() {
        assertNotEquals(DarkCardeaColors.textPrimary, LightCardeaColors.textPrimary)
        assertNotEquals(DarkCardeaColors.textSecondary, LightCardeaColors.textSecondary)
    }

    @Test
    fun `gradients are identical in both palettes`() {
        assertEquals(DarkCardeaColors.gradient, LightCardeaColors.gradient)
        assertEquals(DarkCardeaColors.ctaGradient, LightCardeaColors.ctaGradient)
        assertEquals(DarkCardeaColors.navGradient, LightCardeaColors.navGradient)
    }

    @Test
    fun `zone colors are identical in both palettes`() {
        assertEquals(DarkCardeaColors.zoneGreen, LightCardeaColors.zoneGreen)
        assertEquals(DarkCardeaColors.zoneAmber, LightCardeaColors.zoneAmber)
        assertEquals(DarkCardeaColors.zoneRed, LightCardeaColors.zoneRed)
    }

    @Test
    fun `onGradient is always white`() {
        assertEquals(Color.White, DarkCardeaColors.onGradient)
        assertEquals(Color.White, LightCardeaColors.onGradient)
    }

    @Test
    fun `dark palette isDark is true, light palette isDark is false`() {
        assertEquals(true, DarkCardeaColors.isDark)
        assertEquals(false, LightCardeaColors.isDark)
    }

    @Test
    fun `light mode glass elevation is non-zero`() {
        assert(LightCardeaColors.glassElevation.value > 0f)
        assertEquals(0f, DarkCardeaColors.glassElevation.value, 0.001f)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.theme.CardeaColorsTest"`
Expected: All PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hrcoach/ui/theme/CardeaColorsTest.kt
git commit -m "test(theme): add CardeaColors consistency tests for dark/light palettes"
```

---

### Task 21: Full build + test verification

- [ ] **Step 1: Run full unit test suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: All tests pass (existing + new)

- [ ] **Step 2: Run full debug build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any compilation errors from screen migrations**

If any screens fail to compile due to missing imports, wrong CardeaTheme.colors property names, or Composable context issues, fix them one by one and re-run.

- [ ] **Step 4: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix(theme): resolve compilation issues from light mode migration"
```

---

## Implementation Notes

- **Parallel work**: Tasks 12-18 (screen migrations) are independent and can be parallelized across subagents.
- **Tasks 1-11 are sequential** — each builds on the previous.
- **Task 19 depends on Task 17** (NavGraph migration must be done first).
- **ProgressViewModel chart series colors** (`Color(0xFF34D399)` etc.) are data-encoding colors, not theme-dependent. Leave them as-is.
- **`enableEdgeToEdge`**: The plan adds it to MainActivity. If it causes layout issues with existing screens (status bar overlap), you may need to add `Modifier.statusBarsPadding()` to root layouts. The active workout screen already uses `statusBarsPadding()`.
