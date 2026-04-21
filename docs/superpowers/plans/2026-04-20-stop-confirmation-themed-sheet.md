# Themed Stop-Confirmation Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stock M3 `AlertDialog` at `ActiveWorkoutScreen.kt:407-451` with a Cardea-themed modal bottom sheet that uses `GlassCard` + `CardeaButton` + `CardeaCtaGradient` for the confirm action. The current dialog looks like an unstyled prototype in a highly-styled app.

**Architecture:** Replace `AlertDialog` with Compose Material3 `ModalBottomSheet`. Content is a `Column` of title + supporting text + two stacked buttons (outlined "Cancel" + gradient-red "End Run"). Add tactile feedback — short haptic pulse on confirm tap. Keep the existing `stopConfirmationVisible` state and `onStopConfirmed` callback — only the rendering changes.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.material3.ModalBottomSheet`, existing `CardeaButton`, `CardeaCtaGradient`, `GlassCard`, Android `Vibrator`.

---

## File Structure

- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt` — swap `AlertDialog` for `ModalBottomSheet`.
- Create: `app/src/main/java/com/hrcoach/ui/workout/StopConfirmationSheet.kt` — extracted composable, keeps the screen tidy.

No unit tests (pure layout). Verification is manual + visual.

---

## Task 1: Create the themed sheet composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/StopConfirmationSheet.kt`

- [ ] **Step 1.1: Write the composable**

```kotlin
package com.hrcoach.ui.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.R
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneRed
import kotlinx.coroutines.launch

/**
 * Cardea-themed modal sheet that replaces the stock AlertDialog used to confirm
 * stopping a workout. Uses CardeaBgPrimary as the sheet surface, a red-leaning
 * gradient CTA for the destructive action, and a short haptic on confirm so
 * the user's finger feels the commit moment even with their eyes on the trail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardeaBgPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Drag indicator ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardeaTheme.colors.glassBorder)
                        .width(36.dp)
                        .height(4.dp)
                )
            }

            // ── Red stop icon, centered ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZoneRed.copy(alpha = 0.15f))
                        .border(1.dp, ZoneRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = ZoneRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // ── Title ──
            Text(
                text = stringResource(R.string.dialog_stop_workout_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // ── Supporting text ──
            Text(
                text = stringResource(R.string.dialog_stop_workout_message),
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── End Run (destructive gradient) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardeaCtaGradient)
                    .clickable {
                        performHaptic(context)
                        scope.launch {
                            sheetState.hide()
                            onConfirm()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.button_stop),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
            }

            // ── Cancel (ghost) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dialog_cancel),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

private fun performHaptic(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(40L)
    }
}
```

- [ ] **Step 1.2: Add missing imports that the code above references**

The file above uses `androidx.compose.foundation.clickable` and `androidx.compose.foundation.layout.size` / `width`. Add these imports at the top:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
```

- [ ] **Step 1.3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `Icons.Default.Stop` is unavailable (the CLAUDE.md icon allowlist doesn't include it — verify), substitute `Icons.Default.Close`.

- [ ] **Step 1.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/StopConfirmationSheet.kt
git commit -m "feat(workout): add themed StopConfirmationSheet"
```

---

## Task 2: Swap the AlertDialog for the new sheet

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

- [ ] **Step 2.1: Delete the old `AlertDialog` block**

In `ActiveWorkoutScreen.kt`, remove lines 407-451 (the entire `if (stopConfirmationVisible) { AlertDialog(...) }` block).

- [ ] **Step 2.2: Replace with the new sheet call**

Insert at the same location (after the bottom-action Column ends, before the closing brace of the composable's `Box`):

```kotlin
    if (stopConfirmationVisible) {
        StopConfirmationSheet(
            onConfirm = {
                stopConfirmationVisible = false
                onStopConfirmed()
            },
            onDismiss = { stopConfirmationVisible = false }
        )
    }
```

- [ ] **Step 2.3: Remove unused imports in `ActiveWorkoutScreen.kt`**

After the edit, these may become unused — delete if so:
- `androidx.compose.material3.AlertDialog`
- `androidx.compose.material3.TextButton` (verify with grep — may be used elsewhere in the file)

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 2.4: Add the import for the new sheet**

```kotlin
import com.hrcoach.ui.workout.StopConfirmationSheet
```

(Same package — import likely unnecessary, but include if lint complains.)

- [ ] **Step 2.5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat(workout): use themed StopConfirmationSheet in place of AlertDialog"
```

---

## Task 3: Device verification

- [ ] **Step 3.1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3.2: Observe the sheet**

1. Start any workout (sim or real).
2. Tap the "End Run" button at the bottom of ActiveWorkoutScreen.
3. **Expected:** modal bottom sheet slides up from the bottom with CardeaBgPrimary background. Drag handle at top. Red stop-icon badge. Title + body centred. Gradient red End Run button (52 dp). Outlined Cancel button (48 dp).
4. Tap End Run — **expected:** ~40 ms haptic pulse + sheet dismisses + workout stops as before.
5. Re-open sheet — tap outside (scrim) → dismiss. Tap Cancel → dismiss. Both should behave.

- [ ] **Step 3.3: Test paused-state entry**

The stop sheet is also reachable from the paused state (low-opacity outlined End run button at `ActiveWorkoutScreen.kt:366`). Pause a workout, tap that button, verify the same sheet appears.

- [ ] **Step 3.4: Capture a PR screenshot**

```bash
adb shell screencap //sdcard/stop_sheet.png
adb pull //sdcard/stop_sheet.png /tmp/stop_sheet.png
```

- [ ] **Step 3.5: Run full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.

---

## Self-review checklist

- [x] `ModalBottomSheet` is stock Material3 — no new dependency.
- [x] Haptic uses modern `VibratorManager` on API 31+ with fallback for older APIs.
- [x] `CardeaCtaGradient` is the correct brush for primary CTAs per CLAUDE.md.
- [x] Cancel button is explicit (no reliance on scrim dismissal) per accessibility guidance.
- [x] Red semantics retained via icon tint and button gradient — still reads as destructive.
- [x] Existing string resources reused — no new translations needed.
- [x] Sheet dismissal calls `sheetState.hide()` then callbacks, so the slide-down animation plays before teardown.
