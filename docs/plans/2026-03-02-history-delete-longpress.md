# History Delete on Long-Press Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Long-pressing a workout card in the history list reveals an X badge on its corner; tapping the X shows a confirmation dialog that permanently deletes the workout.

**Architecture:** All state lives in `HistoryListScreen` as local `remember` state — one `Long?` for the card currently in delete-mode, one `Boolean` for dialog visibility. `WorkoutCard` receives these as parameters. `deleteWorkout()` already exists in `HistoryViewModel`.

**Tech Stack:** Jetpack Compose `combinedClickable`, `AnimatedVisibility`, `AlertDialog`, Material3.

---

### Task 1: Add delete-mode state and wire `WorkoutCard` signature

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

**Step 1: Add imports** at the top of `HistoryListScreen.kt` (after existing imports):

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
```

**Step 2: Add state in `HistoryListScreen` composable**, right before the `Scaffold` call:

```kotlin
var deleteModeId by remember { mutableStateOf<Long?>(null) }
var showDeleteDialog by remember { mutableStateOf(false) }
```

**Step 3: Update `items` block** in the `LazyColumn` to pass new params:

```kotlin
items(workouts, key = { it.id }) { workout ->
    WorkoutCard(
        workout = workout,
        isDeleteMode = deleteModeId == workout.id,
        onClick = {
            if (deleteModeId != null) {
                deleteModeId = null   // dismiss delete mode on any tap
            } else {
                onWorkoutClick(workout.id)
            }
        },
        onLongClick = { deleteModeId = workout.id },
        onDeleteClick = {
            deleteModeId = workout.id
            showDeleteDialog = true
        }
    )
}
```

**Step 4: Add the confirmation `AlertDialog`** just after the `LazyColumn` closing brace, still inside the `else` branch:

```kotlin
if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = {
            showDeleteDialog = false
            deleteModeId = null
        },
        title = { Text("Delete this run?") },
        text = { Text("This will permanently remove all route data and stats.") },
        confirmButton = {
            TextButton(
                onClick = {
                    deleteModeId?.let { viewModel.deleteWorkout(it) }
                    showDeleteDialog = false
                    deleteModeId = null
                }
            ) {
                Text("Delete", color = Color(0xFFEF4444))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                showDeleteDialog = false
                deleteModeId = null
            }) { Text("Cancel") }
        },
        containerColor = Color(0xFF141B27),
        titleContentColor = Color(0xFFF5F7FB),
        textContentColor = Color(0xFFB6C2D1)
    )
}
```

---

### Task 2: Rework `WorkoutCard` to support delete-mode

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt` — `WorkoutCard` composable (lines ~160–240)

**Step 1: Update the `WorkoutCard` function signature:**

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    isDeleteMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
)
```

**Step 2: Replace the outer `Card(modifier = Modifier.fillMaxWidth().clickable(...))` with a `Box` wrapping the card**, so the X badge can be positioned outside the card bounds:

```kotlin
Box(modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer {
                // Subtle red tint on border achieved via border param below
            },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            1.dp,
            if (isDeleteMode) Color(0x55EF4444) else Color.White.copy(alpha = 0.08f)
        ),
        colors = CardDefaults.cardColors(containerColor = HistoryGlass)
    ) {
        // ... existing Column content unchanged ...
    }

    // X badge — bleeds past top-right corner
    AnimatedVisibility(
        visible = isDeleteMode,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 6.dp, y = (-6).dp)
            .zIndex(1f),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f)
    ) {
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(26.dp)
                .background(color = Color(0xFFEF4444), shape = androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete workout",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
```

**Step 3: Verify the inner `Column` content is unchanged** — only the outer `Card` modifier and wrapper change.

---

### Task 3: Build, install, and verify on device

**Step 1: Build and install:**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/HRapp
ANDROID_SERIAL=R5CW715EPSB ./gradlew installDebug
```

Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

**Step 2: Manual test checklist on the Samsung A54:**
- [ ] Tap a card → navigates to detail (unchanged)
- [ ] Long-press a card → X badge animates in at top-right corner, card border turns red-tinted
- [ ] Long-press a second card while first is in delete-mode → first badge dismisses, second appears
- [ ] Tap card body while in delete-mode → badge dismisses, no navigation
- [ ] Tap X badge → confirmation dialog appears with "Delete this run?"
- [ ] Tap "Cancel" → dialog closes, badge remains (or clears — either is fine)
- [ ] Tap "Delete" → workout removed from list, dialog and badge both cleared
- [ ] Confirm Progress screen no longer shows deleted run in calendar / weekly distance

**Step 3: Commit:**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "feat(history): long-press card to reveal delete badge with confirmation"
```
