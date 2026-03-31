# Emblems + Logo Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 10 Unicode avatar symbols with 24 Canvas-drawn athletic emblems, and replace the old bezier heart logo with the ECG-to-heart morph animation.

**Architecture:** Emblems are pure Canvas composables stored in an EmblemRegistry (enum + draw functions). The avatar system switches from storing Unicode strings to emblem ID strings in SharedPreferences. The logo rewrite replaces CardeaLogo.kt's bezier paths with parametric heart formula + ECG waveform animation. Both are visual-only changes — no DB migration, no new dependencies.

**Tech Stack:** Kotlin, Jetpack Compose Canvas API, existing SharedPreferences storage.

**Reference:** Emblem designs at `C:/tmp/brainstorm/cardea-emblems.html`, logo design at `C:/tmp/brainstorm/cardea-logo-explorer.html` (Direction E).

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `domain/emblem/Emblem.kt` | Create | Pure enum of 24 emblem IDs with display names (no Compose deps) |
| `ui/emblem/EmblemRenderer.kt` | Create | Canvas DrawScope draw functions for each emblem |
| `ui/components/EmblemIcon.kt` | Create | Composables: `EmblemIcon`, `EmblemIconWithRing` |
| `ui/components/EmblemPicker.kt` | Create | 6x4 grid picker composable |
| `ui/components/CardeaLogo.kt` | Rewrite | ECG-to-heart morph animation |
| `ui/account/AccountScreen.kt` | Modify | Replace Unicode avatar with emblem composables |
| `ui/account/AccountViewModel.kt` | Modify | Change `avatarSymbol: String` to `emblemId: String` |
| `data/repository/UserProfileRepository.kt` | Modify | Store emblem ID, migrate from old avatar symbol |
| `test/.../EmblemRegistryTest.kt` | Create | Verify all 24 emblems exist, default fallback |
| `test/.../EmblemRendererTest.kt` | Create | Crash-safety test: draw all 24 on ImageBitmap |
| `test/.../CardeaLogoTest.kt` | Create | Verify logo path generation doesn't crash |

**Layer note:** `Emblem.kt` is pure Kotlin (domain layer). `EmblemRenderer.kt` has Compose Canvas deps so lives in `ui/emblem/`, not `domain/`.

---

### Task 1: Emblem Enum and Registry

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/emblem/Emblem.kt`
- Test: `app/src/test/java/com/hrcoach/domain/emblem/EmblemRegistryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.emblem

import org.junit.Assert.*
import org.junit.Test

class EmblemRegistryTest {

    @Test
    fun `all 24 emblems are registered`() {
        assertEquals(24, Emblem.entries.size)
    }

    @Test
    fun `each emblem has a unique id`() {
        val ids = Emblem.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `each emblem has a non-blank display name`() {
        Emblem.entries.forEach { emblem ->
            assertTrue("${emblem.name} has blank displayName", emblem.displayName.isNotBlank())
        }
    }

    @Test
    fun `fromId returns correct emblem`() {
        assertEquals(Emblem.PULSE, Emblem.fromId("pulse"))
        assertEquals(Emblem.BOLT, Emblem.fromId("bolt"))
    }

    @Test
    fun `fromId returns default for unknown id`() {
        assertEquals(Emblem.PULSE, Emblem.fromId("nonexistent"))
    }

    @Test
    fun `fromId handles legacy unicode avatar symbols`() {
        // Old system stored Unicode symbols — migration path
        assertEquals(Emblem.PULSE, Emblem.fromId("\u2665"))
        assertEquals(Emblem.BOLT, Emblem.fromId("\u26A1"))
        assertEquals(Emblem.DIAMOND, Emblem.fromId("\u25C6"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.emblem.EmblemRegistryTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write the Emblem enum**

```kotlin
package com.hrcoach.domain.emblem

enum class Emblem(val id: String, val displayName: String) {
    PULSE("pulse", "Pulse"),
    BOLT("bolt", "Bolt"),
    SUMMIT("summit", "Summit"),
    FLAME("flame", "Flame"),
    COMPASS("compass", "Compass"),
    SHIELD("shield", "Shield"),
    ASCENT("ascent", "Ascent"),
    CROWN("crown", "Crown"),
    ORBIT("orbit", "Orbit"),
    INFINITY("infinity", "Infinity"),
    DIAMOND("diamond", "Diamond"),
    NOVA("nova", "Nova"),
    VORTEX("vortex", "Vortex"),
    ANCHOR("anchor", "Anchor"),
    PHOENIX("phoenix", "Phoenix"),
    ARROW("arrow", "Arrow"),
    CREST("crest", "Crest"),
    PRISM("prism", "Prism"),
    RIPPLE("ripple", "Ripple"),
    COMET("comet", "Comet"),
    THRESHOLD("threshold", "Threshold"),
    CIRCUIT("circuit", "Circuit"),
    APEX("apex", "Apex"),
    FORGE("forge", "Forge");

    companion object {
        private val byId = entries.associateBy { it.id }

        // Legacy Unicode symbol → emblem mapping
        private val legacyMap = mapOf(
            "\u2665" to PULSE,    // ♥
            "\u2605" to NOVA,     // ★
            "\u26A1" to BOLT,     // ⚡
            "\u25C6" to DIAMOND,  // ◆
            "\u25B2" to ASCENT,   // ▲
            "\u25CF" to ORBIT,    // ●
            "\u2726" to NOVA,     // ✦
            "\u2666" to DIAMOND,  // ♦
            "\u2191" to ARROW,    // ↑
            "\u221E" to INFINITY, // ∞
        )

        fun fromId(id: String): Emblem =
            byId[id] ?: legacyMap[id] ?: PULSE
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.emblem.EmblemRegistryTest"`
Expected: 6/6 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/emblem/Emblem.kt app/src/test/java/com/hrcoach/domain/emblem/EmblemRegistryTest.kt
git commit -m "feat(emblem): add Emblem enum with 24 IDs and legacy migration"
```

---

### Task 2: Emblem Canvas Renderer (first 6 emblems)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/emblem/EmblemRenderer.kt`

The renderer is a pure function: `fun DrawScope.drawEmblem(emblem: Emblem, center: Offset, radius: Float, gradient: Brush)`. Each emblem is drawn using Canvas `drawLine`, `drawPath`, `drawArc`, `drawCircle` with the Cardea gradient brush. Port the geometry from the p5.js implementations in `cardea-emblems.html`.

- [ ] **Step 1: Create EmblemRenderer with first 6 emblems**

Port `Pulse`, `Bolt`, `Summit`, `Flame`, `Compass`, `Shield` from the p5.js explorer. Each is a `DrawScope` extension function. The public API is a single `drawEmblem()` dispatch function.

Reference the p5.js code in `C:/tmp/brainstorm/cardea-emblems.html` for exact vertex positions. Convert p5 coordinates (centered at 0,0 with radius `s`) to Compose Canvas coordinates (centered at `center` with `radius`).

Key conversion rules:
- p5 `line(x1,y1,x2,y2)` → `drawLine(color, Offset(x1,y1), Offset(x2,y2), strokeWidth)`
- p5 polygon → `Path().apply { moveTo(); lineTo()...; close() }` then `drawPath(path, brush)`
- p5 `arc(cx,cy,w,h,start,stop)` → `drawArc(brush, topLeft, size, startAngle, sweepAngle)`
- All coordinates are fractions of `radius`: e.g., `s*0.55` becomes `radius * 0.55f`

```kotlin
package com.hrcoach.ui.emblem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

fun DrawScope.drawEmblem(
    emblem: Emblem,
    center: Offset,
    radius: Float,
    gradient: Brush,
    strokeWidth: Float = radius * 0.06f
) {
    when (emblem) {
        Emblem.PULSE -> drawPulse(center, radius, gradient, strokeWidth)
        Emblem.BOLT -> drawBolt(center, radius, gradient)
        Emblem.SUMMIT -> drawSummit(center, radius, gradient, strokeWidth)
        Emblem.FLAME -> drawFlame(center, radius, gradient, strokeWidth)
        Emblem.COMPASS -> drawCompass(center, radius, gradient)
        Emblem.SHIELD -> drawShield(center, radius, gradient, strokeWidth)
        // Remaining 18 — added in Task 3
        else -> drawPulse(center, radius, gradient, strokeWidth) // fallback
    }
}

// Example: Pulse (ECG heartbeat line)
private fun DrawScope.drawPulse(center: Offset, r: Float, brush: Brush, sw: Float) {
    val path = Path().apply {
        moveTo(center.x - r, center.y)
        lineTo(center.x - r * 0.55f, center.y)
        lineTo(center.x - r * 0.35f, center.y - r * 0.55f)
        lineTo(center.x - r * 0.1f, center.y + r * 0.65f)
        lineTo(center.x + r * 0.15f, center.y - r * 0.3f)
        lineTo(center.x + r * 0.35f, center.y + r * 0.15f)
        lineTo(center.x + r * 0.5f, center.y)
        lineTo(center.x + r, center.y)
    }
    drawPath(path, brush, style = Stroke(width = sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ... drawBolt, drawSummit, drawFlame, drawCompass, drawShield
// Port each from the p5.js vertex coordinates in cardea-emblems.html
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/emblem/EmblemRenderer.kt
git commit -m "feat(emblem): Canvas renderer for first 6 emblems (Pulse through Shield)"
```

---

### Task 3: Remaining 18 Emblem Renderers

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/emblem/EmblemRenderer.kt`

- [ ] **Step 1: Add Ascent through Nova (emblems 7-12)**

Port `Ascent`, `Crown`, `Orbit`, `Infinity`, `Diamond`, `Nova` from the p5.js explorer. Update the `when` dispatch in `drawEmblem()`.

- [ ] **Step 2: Add Vortex through Forge (emblems 13-24)**

Port `Vortex`, `Anchor`, `Phoenix`, `Arrow`, `Crest`, `Prism`, `Ripple`, `Comet`, `Threshold`, `Circuit`, `Apex`, `Forge`. Update the `when` dispatch to remove the `else` fallback.

- [ ] **Step 3: Write crash-safety test for all 24 renderers**

Create `app/src/test/java/com/hrcoach/ui/emblem/EmblemRendererTest.kt` — iterates all `Emblem.entries` and calls `drawEmblem()` on a small `ImageBitmap` Canvas. Verifies none throw.

```kotlin
package com.hrcoach.ui.emblem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.hrcoach.domain.emblem.Emblem
import org.junit.Test

class EmblemRendererTest {
    @Test
    fun `all 24 emblems draw without crashing`() {
        val bitmap = ImageBitmap(100, 100)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val gradient = Brush.linearGradient(
            colors = listOf(Color.Red, Color.Blue),
            start = Offset.Zero, end = Offset(100f, 100f)
        )
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(100f, 100f)) {
            Emblem.entries.forEach { emblem ->
                drawEmblem(emblem, Offset(50f, 50f), 40f, gradient)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.emblem.EmblemRendererTest"`
Expected: PASS

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/emblem/EmblemRenderer.kt app/src/test/java/com/hrcoach/ui/emblem/EmblemRendererTest.kt
git commit -m "feat(emblem): complete all 24 emblem renderers with crash-safety test"
```

---

### Task 4: EmblemIcon and EmblemIconWithRing Composables

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/EmblemIcon.kt`

These are the composables that other screens use to display an emblem. `EmblemIcon` draws just the emblem. `EmblemIconWithRing` adds the Cardea gradient ring around it (like the current avatar circle in ProfileHeroCard).

- [ ] **Step 1: Create EmblemIcon composables**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.ui.emblem.drawEmblem
import com.hrcoach.ui.theme.*

// Build the canonical Cardea 135-degree gradient sized to the canvas
private fun cardeaGradient(w: Float, h: Float) = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    ),
    start = Offset.Zero,
    end = Offset(w, h) // 135-degree diagonal
)

@Composable
fun EmblemIcon(
    emblem: Emblem,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val gradient = cardeaGradient(w, h)
        val center = Offset(w / 2, h / 2)
        val radius = this.size.minDimension / 2 * 0.75f
        drawEmblem(emblem, center, radius, gradient)
    }
}

@Composable
fun EmblemIconWithRing(
    emblem: Emblem,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    ringWidth: Dp = 3.dp
) {
    // Read theme color outside Canvas (can't call @Composable inside DrawScope)
    val bgColor = CardeaTheme.colors.bgPrimary

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val gradient = cardeaGradient(w, h)
        val center = Offset(w / 2, h / 2)
        val outerRadius = this.size.minDimension / 2
        val ringW = ringWidth.toPx()

        // Gradient ring
        drawCircle(brush = gradient, radius = outerRadius, center = center, style = Stroke(width = ringW))

        // Dark fill inside ring (theme-aware)
        drawCircle(color = bgColor, radius = outerRadius - ringW, center = center)

        // Emblem
        val emblemRadius = (outerRadius - ringW * 2) * 0.8f
        drawEmblem(emblem, center, emblemRadius, gradient)
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/EmblemIcon.kt
git commit -m "feat(emblem): EmblemIcon and EmblemIconWithRing composables"
```

---

### Task 5: EmblemPicker Composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/EmblemPicker.kt`

A 6x4 grid showing all 24 emblems. Selected emblem has gradient ring highlight, unselected have dim ring. Used in the profile edit bottom sheet.

- [ ] **Step 1: Create EmblemPicker**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.emblem.Emblem

@Composable
fun EmblemPicker(
    selected: Emblem,
    onSelect: (Emblem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(Emblem.entries.toList()) { emblem ->
            EmblemIconWithRing(
                emblem = emblem,
                size = if (emblem == selected) 50.dp else 46.dp,
                ringWidth = if (emblem == selected) 2.5.dp else 1.dp,
                modifier = Modifier
                    .clickable { onSelect(emblem) }
            )
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/EmblemPicker.kt
git commit -m "feat(emblem): EmblemPicker 6x4 grid composable"
```

---

### Task 6: Wire Emblems into Account Screen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

This task replaces the Unicode avatar system with the emblem system end-to-end.

- [ ] **Step 0: Search all references to old avatar system**

Grep the entire project for `avatarSymbol`, `getAvatarSymbol`, `setAvatarSymbol`, `AVATAR_SYMBOLS`, `avatar_symbol` to find all consumers. Currently expected only in AccountScreen.kt, AccountViewModel.kt, and UserProfileRepository.kt — but verify no other screens reference it.

Run: `grep -r "avatarSymbol\|getAvatarSymbol\|setAvatarSymbol\|AVATAR_SYMBOLS\|avatar_symbol" app/src/main/java/`

- [ ] **Step 1: Update UserProfileRepository**

In `UserProfileRepository.kt`:
- Change `getAvatarSymbol()` → `getEmblemId(): String` — returns the stored string (still SharedPreferences)
- Change `setAvatarSymbol(symbol)` → `setEmblemId(id: String)`
- The stored value transitions from Unicode symbols to emblem ID strings (e.g., `"pulse"`)
- `Emblem.fromId()` handles legacy Unicode values transparently

- [ ] **Step 2: Update AccountViewModel**

In `AccountViewModel.kt`:
- Change `_avatarSymbol: MutableStateFlow<String>` → `_emblemId: MutableStateFlow<String>`
- Change `AccountUiState.avatarSymbol: String` → `AccountUiState.emblemId: String`
- Init block: load from `userProfileRepo.getEmblemId()`
- `setEmblemId(id: String)` replaces `setAvatarSymbol`
- `saveProfile()` calls `userProfileRepo.setEmblemId(_emblemId.value)`
- Update `combine` block and all references found in Step 0

- [ ] **Step 3: Update AccountScreen**

In `AccountScreen.kt`:
- **Delete** the `AVATAR_SYMBOLS` list constant
- **ProfileHeroCard**: Replace the `Text(avatarSymbol, fontSize = 24.sp)` with `EmblemIconWithRing(Emblem.fromId(emblemId), size = 56.dp)`
- **ProfileEditBottomSheet**:
  - Remove `avatarSymbol: String` param, add `emblemId: String`
  - Remove `onAvatarChange: (String) -> Unit`, add `onEmblemChange: (String) -> Unit`
  - Replace the `AVATAR_SYMBOLS.chunked(5)` grid block with `EmblemPicker(selected = Emblem.fromId(emblemId), onSelect = { onEmblemChange(it.id) })`
  - Add label: `Text("Emblem", ...)`
- **Update all callers** found in Step 0 (if any exist outside these 3 files)

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Deploy and visually verify**

Run: `./gradlew installDebug` (install to connected phone)
Navigate to Account → tap profile → verify 24 emblems appear in picker grid. Select one, save, verify it persists.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt \
      app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt \
      app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(emblem): wire 24 emblem system into Account screen, replacing Unicode avatars"
```

---

### Task 7: Logo Redesign — ECG-to-Heart Morph

**Files:**
- Rewrite: `app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt`

Replace the old bezier heart + hairpin path with the parametric heart formula and ECG morph animation. Reference the approved Direction E from `C:/tmp/brainstorm/cardea-logo-explorer.html`.

- [ ] **Step 1: Rewrite CardeaLogo.kt**

Keep the same public API: `@Composable fun CardeaLogo(modifier, size, animate)`.

**Heart shape:** Use the parametric formula:
```
x = 16 * sin(t)^3
y = -(13*cos(t) - 5*cos(2t) - 2*cos(3t) - cos(4t))
```
Normalized to fit within the canvas. Start path at bottom cusp (t=PI), traverse left lobe up to cleft, right lobe down to bottom.

**ECG waveform:** Build as a horizontal line with P wave (t=0.30-0.40), QRS spike (t=0.47-0.53), T wave (t=0.63-0.75) centered at midpoint.

**Animation phases (when `animate = true`):**
1. **Draw-on (28%):** ECG expands outward from center. Two leading dots at expanding tips.
2. **Morph (30%):** Staggered fold — endpoints lift first, center follows ~180ms later. Slight elastic overshoot (5%).
3. **Hold (42%):** Static heart shape with subtle ambient glow (alpha ~8).

**Gradient:** Screen-space 135-degree diagonal using `Brush.linearGradient` with `start/end` mapped to canvas diagonal.

**Stroke:** Velocity-based thickness — thicker in curves, thinner on baselines. `StrokeCap.Round`, `StrokeJoin.Round`.

**When `animate = false`:** Draw just the completed heart shape (no ECG, no morph). This is used for the nav badge and static contexts.

Key differences from old implementation:
- Delete `buildOuterHeartPath()` and `buildHairpinPath()` — replaced by parametric formula
- Delete `PathCache` data class
- Delete dash-phase draw-on animation — replaced by time-based morph
- Keep the glow layer concept but use the parametric heart path

- [ ] **Step 2: Verify all references still compile**

CardeaLogo is used in:
- `ui/splash/SplashScreen.kt` — `CardeaLogo(size = 180.dp, animate = true)`
- `ui/components/CardeaLoadingScreen.kt` — `CardeaLogo(size = 80.dp, animate = true)`
- `ui/bootcamp/BootcampOnboardingCarousel.kt` — `CardeaLogo(size = ...)`

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (public API unchanged)

- [ ] **Step 3: Deploy and visually verify**

Run: `./gradlew installDebug`
Open app — verify splash screen shows ECG-to-heart morph animation. Navigate to trigger loading screen — verify it animates correctly at smaller size.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt
git commit -m "feat(logo): ECG-to-heart morph animation replacing bezier heart"
```

---

### Task 8: Final Verification and PR

- [ ] **Step 1: Run full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: All existing tests pass + new EmblemRegistryTest passes

- [ ] **Step 2: Run pre-commit checklist**

Per CLAUDE.md: verify UI wired (not decorative), real logic (not stub), data saved and refreshed, errors surfaced, no phantom state, filter logic tested.

- [ ] **Step 3: Deploy to phone for manual verification**

Run: `./gradlew installDebug`
Check:
- [ ] Splash screen shows ECG → heart morph
- [ ] Loading screen shows animated logo at 80dp
- [ ] Account → profile card shows emblem (not Unicode)
- [ ] Profile edit shows 24 emblems in grid
- [ ] Select emblem → save → reopen → persists
- [ ] Old Unicode avatar (if stored) migrates to correct emblem

- [ ] **Step 4: Merge to main and push**

```bash
git checkout main
git merge <branch> --no-ff -m "feat: 24 athletic emblems + ECG-to-heart logo redesign"
git push origin main
```
