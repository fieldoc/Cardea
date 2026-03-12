# Profile & Sharing Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Profile tab an editable identity surface (name + unicode avatar) and lay the data foundation for future Buddy Bootcamp sharing.

**Architecture:** Expand `UserProfileRepository` (SharedPreferences) with name/avatar/userId fields. Add `ShareableBootcampConfig` data class for future export. Update `AccountViewModel` and `AccountScreen` to support editing via a bottom sheet.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, org.json (Android built-in), JUnit 4

**Spec:** `docs/superpowers/specs/2026-03-12-profile-and-sharing-foundation-design.md`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt` | Add displayName, avatarSymbol, userId fields |
| Modify | `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt` | Add profile state + update methods |
| Modify | `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` | Update hero card, add bottom sheet |
| Create | `app/src/main/java/com/hrcoach/domain/model/ShareableBootcampConfig.kt` | Shareable bootcamp data class with JSON serialization |
| Create | `app/src/test/java/com/hrcoach/data/repository/UserProfileSanitizationTest.kt` | Name sanitization unit tests |
| Create | `app/src/test/java/com/hrcoach/domain/model/ShareableBootcampConfigTest.kt` | Serialization round-trip tests |

---

## Chunk 1: Data Layer

### Task 1: Expand UserProfileRepository

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt:7-30`
- Create: `app/src/test/java/com/hrcoach/data/repository/UserProfileSanitizationTest.kt`

- [ ] **Step 1: Write failing tests for new profile fields**

Create `app/src/test/java/com/hrcoach/data/repository/UserProfileRepositoryTest.kt`:

Since `UserProfileRepository` requires Android `Context` (SharedPreferences), and this project has no Robolectric, test the pure logic via `ShareableBootcampConfig` tests and verify the repository manually at compile + runtime. The repository methods are thin wrappers around SharedPreferences with simple trim/take/ifBlank logic — the risk is low.

Instead, add a pure-logic test for the name sanitization rules by extracting them:

```kotlin
package com.hrcoach.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileSanitizationTest {

    @Test
    fun `sanitizeName returns trimmed input`() {
        assertEquals("Bob", sanitizeDisplayName("  Bob  "))
    }

    @Test
    fun `sanitizeName truncates to 20 chars`() {
        assertEquals("A".repeat(20), sanitizeDisplayName("A".repeat(25)))
    }

    @Test
    fun `sanitizeName falls back to Runner for blank`() {
        assertEquals("Runner", sanitizeDisplayName("   "))
    }

    @Test
    fun `sanitizeName falls back to Runner for empty`() {
        assertEquals("Runner", sanitizeDisplayName(""))
    }

    @Test
    fun `sanitizeName preserves normal input`() {
        assertEquals("Alice", sanitizeDisplayName("Alice"))
    }
}
```

This requires extracting a `sanitizeDisplayName` top-level function in `UserProfileRepository.kt`:

```kotlin
internal fun sanitizeDisplayName(name: String): String {
    val trimmed = name.trim().take(20)
    return trimmed.ifBlank { "Runner" }
}
```

Then `setDisplayName` calls `sanitizeDisplayName(name)` instead of inlining the logic.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.data.repository.UserProfileSanitizationTest"`
Expected: FAIL — function `sanitizeDisplayName` doesn't exist yet.

- [ ] **Step 3: Implement new fields in UserProfileRepository**

Add to `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`:

```kotlin
// Add these constants in the companion object:
private const val PREF_DISPLAY_NAME = "display_name"
private const val PREF_AVATAR_SYMBOL = "avatar_symbol"
private const val PREF_USER_ID = "user_id"
private const val DEFAULT_NAME = "Runner"
private const val DEFAULT_AVATAR = "\u2665" // ♥

// Add these methods to the class:
@Synchronized
fun getDisplayName(): String {
    return prefs.getString(PREF_DISPLAY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
}

@Synchronized
fun setDisplayName(name: String) {
    prefs.edit().putString(PREF_DISPLAY_NAME, sanitizeDisplayName(name)).apply()
}

// Top-level function (outside the class, for testability):
internal fun sanitizeDisplayName(name: String): String {
    val trimmed = name.trim().take(20)
    return trimmed.ifBlank { "Runner" }
}

@Synchronized
fun getAvatarSymbol(): String {
    return prefs.getString(PREF_AVATAR_SYMBOL, DEFAULT_AVATAR) ?: DEFAULT_AVATAR
}

@Synchronized
fun setAvatarSymbol(symbol: String) {
    prefs.edit().putString(PREF_AVATAR_SYMBOL, symbol).apply()
}

@Synchronized
fun getUserId(): String {
    val existing = prefs.getString(PREF_USER_ID, null)
    if (existing != null) return existing
    val newId = java.util.UUID.randomUUID().toString()
    prefs.edit().putString(PREF_USER_ID, newId).apply()
    return newId
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.data.repository.UserProfileSanitizationTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt \
       app/src/test/java/com/hrcoach/data/repository/UserProfileSanitizationTest.kt
git commit -m "feat(profile): add displayName, avatarSymbol, userId to UserProfileRepository"
```

---

### Task 2: ShareableBootcampConfig data class

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/model/ShareableBootcampConfig.kt`
- Create: `app/src/test/java/com/hrcoach/domain/model/ShareableBootcampConfigTest.kt`

- [ ] **Step 1: Write failing tests for serialization**

Create `app/src/test/java/com/hrcoach/domain/model/ShareableBootcampConfigTest.kt`:

```kotlin
package com.hrcoach.domain.model

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import org.junit.Assert.*
import org.junit.Test

class ShareableBootcampConfigTest {

    private val sample = ShareableBootcampConfig(
        goalType = "ENDURANCE",
        targetMinutesPerRun = 30,
        runsPerWeek = 3,
        preferredDays = listOf(
            DayPreference(1, DaySelectionLevel.AVAILABLE),
            DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS),
            DayPreference(6, DaySelectionLevel.AVAILABLE)
        ),
        tierIndex = 1,
        sharerUserId = "abc-123",
        sharerDisplayName = "Alice"
    )

    @Test
    fun `toJson produces valid JSON with day preferences as objects`() {
        val json = sample.toJson()
        assertTrue(json.has("goalType"))
        assertEquals("ENDURANCE", json.getString("goalType"))
        assertEquals(30, json.getInt("targetMinutesPerRun"))
        assertEquals(3, json.getInt("runsPerWeek"))
        assertEquals(1, json.getInt("tierIndex"))
        assertEquals("abc-123", json.getString("sharerUserId"))
        assertEquals("Alice", json.getString("sharerDisplayName"))

        val days = json.getJSONArray("preferredDays")
        assertEquals(3, days.length())
        assertEquals(1, days.getJSONObject(0).getInt("day"))
        assertEquals("AVAILABLE", days.getJSONObject(0).getString("level"))
        assertEquals("LONG_RUN_BIAS", days.getJSONObject(1).getString("level"))
    }

    @Test
    fun `fromJson round-trips correctly`() {
        val json = sample.toJson()
        val restored = ShareableBootcampConfig.fromJson(json)
        assertEquals(sample, restored)
    }

    @Test
    fun `fromJson handles empty preferredDays`() {
        val empty = sample.copy(preferredDays = emptyList())
        val restored = ShareableBootcampConfig.fromJson(empty.toJson())
        assertEquals(emptyList<DayPreference>(), restored.preferredDays)
    }

    @Test
    fun `toShareable maps enrollment fields correctly`() {
        val enrollment = BootcampEnrollmentEntity(
            id = 1,
            goalType = "ENDURANCE",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(
                DayPreference(1, DaySelectionLevel.AVAILABLE),
                DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS)
            ),
            startDate = System.currentTimeMillis(),
            tierIndex = 2
        )
        val shareable = enrollment.toShareable(userId = "user-1", displayName = "Bob")
        assertEquals("ENDURANCE", shareable.goalType)
        assertEquals(30, shareable.targetMinutesPerRun)
        assertEquals(3, shareable.runsPerWeek)
        assertEquals(2, shareable.tierIndex)
        assertEquals("user-1", shareable.sharerUserId)
        assertEquals("Bob", shareable.sharerDisplayName)
        assertEquals(2, shareable.preferredDays.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.model.ShareableBootcampConfigTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement ShareableBootcampConfig**

Create `app/src/main/java/com/hrcoach/domain/model/ShareableBootcampConfig.kt`:

```kotlin
package com.hrcoach.domain.model

import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import org.json.JSONArray
import org.json.JSONObject

data class ShareableBootcampConfig(
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: List<DayPreference>,
    val tierIndex: Int,
    val sharerUserId: String,
    val sharerDisplayName: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("goalType", goalType)
        put("targetMinutesPerRun", targetMinutesPerRun)
        put("runsPerWeek", runsPerWeek)
        put("tierIndex", tierIndex)
        put("sharerUserId", sharerUserId)
        put("sharerDisplayName", sharerDisplayName)
        put("preferredDays", JSONArray().apply {
            preferredDays.forEach { dp ->
                put(JSONObject().apply {
                    put("day", dp.day)
                    put("level", dp.level.name)
                })
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ShareableBootcampConfig {
            val daysArray = json.getJSONArray("preferredDays")
            val days = (0 until daysArray.length()).map { i ->
                val obj = daysArray.getJSONObject(i)
                DayPreference(
                    day = obj.getInt("day"),
                    level = DaySelectionLevel.valueOf(obj.getString("level"))
                )
            }
            return ShareableBootcampConfig(
                goalType = json.getString("goalType"),
                targetMinutesPerRun = json.getInt("targetMinutesPerRun"),
                runsPerWeek = json.getInt("runsPerWeek"),
                preferredDays = days,
                tierIndex = json.getInt("tierIndex"),
                sharerUserId = json.getString("sharerUserId"),
                sharerDisplayName = json.getString("sharerDisplayName"),
            )
        }
    }
}
```

- [ ] **Step 4: Add toShareable extension on BootcampEnrollmentEntity**

Add at the bottom of `app/src/main/java/com/hrcoach/domain/model/ShareableBootcampConfig.kt`:

```kotlin
fun com.hrcoach.data.db.BootcampEnrollmentEntity.toShareable(
    userId: String,
    displayName: String,
): ShareableBootcampConfig = ShareableBootcampConfig(
    goalType = goalType,
    targetMinutesPerRun = targetMinutesPerRun,
    runsPerWeek = runsPerWeek,
    preferredDays = preferredDays,
    tierIndex = tierIndex,
    sharerUserId = userId,
    sharerDisplayName = displayName,
)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.model.ShareableBootcampConfigTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/ShareableBootcampConfig.kt \
       app/src/test/java/com/hrcoach/domain/model/ShareableBootcampConfigTest.kt
git commit -m "feat(sharing): add ShareableBootcampConfig with JSON serialization"
```

---

## Chunk 2: ViewModel & UI

### Task 3: Wire profile fields into AccountViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt:21-98`

- [ ] **Step 1: Add displayName and avatarSymbol to AccountUiState**

In `AccountViewModel.kt`, add two fields to `AccountUiState`:

```kotlin
data class AccountUiState(
    val displayName: String = "Runner",
    val avatarSymbol: String = "\u2665", // ♥
    val totalWorkouts: Int = 0,
    // ... rest unchanged
)
```

- [ ] **Step 2: Add MutableStateFlow fields and init loading**

In `AccountViewModel`, after the existing `_autoPauseEnabled` field (~line 55):

```kotlin
private val _displayName = MutableStateFlow("Runner")
private val _avatarSymbol = MutableStateFlow("\u2665")
```

In the `init` block, add inside the `viewModelScope.launch`:

```kotlin
_displayName.value = userProfileRepo.getDisplayName()
_avatarSymbol.value = userProfileRepo.getAvatarSymbol()
```

- [ ] **Step 3: Add profile fields to uiState combine chain**

Append another `.combine` stage to the existing chain (before `.stateIn`):

```kotlin
.combine(
    combine(_displayName, _avatarSymbol) { name, avatar -> name to avatar }
) { base, (name, avatar) ->
    base.copy(displayName = name, avatarSymbol = avatar)
}
```

- [ ] **Step 4: Add update methods**

Add to `AccountViewModel`:

```kotlin
fun setDisplayName(name: String) {
    _displayName.value = name.trim().take(20).ifBlank { "Runner" }
}

fun setAvatarSymbol(symbol: String) {
    _avatarSymbol.value = symbol
}

fun saveProfile() {
    userProfileRepo.setDisplayName(_displayName.value)
    userProfileRepo.setAvatarSymbol(_avatarSymbol.value)
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt
git commit -m "feat(profile): wire displayName and avatarSymbol into AccountViewModel"
```

---

### Task 4: Update ProfileHeroCard and add bottom sheet

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt:72-335`

- [ ] **Step 1: Update ProfileHeroCard signature and content**

Change `ProfileHeroCard` to accept new params and display them:

```kotlin
@Composable
private fun ProfileHeroCard(
    displayName: String,
    avatarSymbol: String,
    runCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientRed.copy(alpha = 0.10f),
                        GradientPink.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, GradientRed.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Gradient ring with unicode symbol
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CardeaGradient),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CardeaBgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarSymbol,
                        fontSize = 24.sp,
                        color = Color(0xFFFF6B8A)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = CardeaTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$runCount runs recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add ProfileEditBottomSheet composable**

Add a new composable in `AccountScreen.kt`. The curated symbol set and the bottom sheet layout:

```kotlin
private val AVATAR_SYMBOLS = listOf(
    "\u2665", // ♥
    "\u2605", // ★
    "\u26A1", // ⚡
    "\u25C6", // ◆
    "\u25B2", // ▲
    "\u25CF", // ●
    "\u2726", // ✦
    "\u2666", // ♦
    "\u2191", // ↑
    "\u221E", // ∞
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditBottomSheet(
    displayName: String,
    avatarSymbol: String,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgSecondary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaTextTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Name field
            Text("Name", style = MaterialTheme.typography.labelMedium, color = CardeaTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 20) onNameChange(it) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6B8A),
                    unfocusedBorderColor = CardeaTextTertiary,
                    cursorColor = Color(0xFFFF6B8A),
                    focusedTextColor = CardeaTextPrimary,
                    unfocusedTextColor = CardeaTextPrimary,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Avatar picker
            Text("Avatar", style = MaterialTheme.typography.labelMedium, color = CardeaTextSecondary)
            Spacer(modifier = Modifier.height(10.dp))

            // 5x2 grid
            for (row in AVATAR_SYMBOLS.chunked(5)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { symbol ->
                        val selected = symbol == avatarSymbol
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) CardeaGradient
                                    else Brush.linearGradient(
                                        listOf(
                                            CardeaTextTertiary.copy(alpha = 0.3f),
                                            CardeaTextTertiary.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .clickable { onAvatarChange(symbol) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CardeaBgPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = symbol,
                                    fontSize = 20.sp,
                                    color = if (selected) Color(0xFFFF6B8A)
                                            else CardeaTextTertiary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            GradientSaveButton(onClick = onSave)
        }
    }
}
```

- [ ] **Step 3: Wire into AccountScreen**

In the `AccountScreen` composable, add state for showing the bottom sheet and update the `ProfileHeroCard` call:

```kotlin
// Add near the top of AccountScreen:
var showProfileSheet by remember { mutableStateOf(false) }

// Replace the existing ProfileHeroCard call with:
ProfileHeroCard(
    displayName = state.displayName,
    avatarSymbol = state.avatarSymbol,
    runCount = state.totalWorkouts,
    onClick = { showProfileSheet = true }
)

// Add the bottom sheet (after the Scaffold or at the end of the composable body):
if (showProfileSheet) {
    ProfileEditBottomSheet(
        displayName = state.displayName,
        avatarSymbol = state.avatarSymbol,
        onNameChange = viewModel::setDisplayName,
        onAvatarChange = viewModel::setAvatarSymbol,
        onSave = {
            viewModel.saveProfile()
            showProfileSheet = false
        },
        onDismiss = { showProfileSheet = false }
    )
}
```

- [ ] **Step 4: Add required imports**

Add any missing imports to AccountScreen.kt:
- `androidx.compose.material3.ExperimentalMaterial3Api`
- `androidx.compose.material3.ModalBottomSheet`
- `androidx.compose.material3.OutlinedTextField`
- `androidx.compose.material3.OutlinedTextFieldDefaults`
- `androidx.compose.foundation.clickable`
- `androidx.compose.runtime.mutableStateOf`
- `androidx.compose.runtime.setValue`
- `androidx.compose.runtime.getValue`

- [ ] **Step 5: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(profile): editable name + avatar via bottom sheet on ProfileHeroCard"
```

---

### Task 5: Verify all tests pass

- [ ] **Step 1: Run full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: ALL PASS — no regressions.

- [ ] **Step 2: Final commit if any fixups were needed**

Only if changes were required to fix test failures.
