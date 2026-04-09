# Cardea UI/UX Audit — 2026-04-08

Audit performed by screenshot walkthrough + source code analysis across all navigable screens: Home, Training (Bootcamp + Setup), Activity (History), Profile (Account). Device: Samsung physical (R5CW715EPSB, 1080×2340, light mode enabled).

---

## Bug 1 — HIGH: Home shows "Set Up Bootcamp" card when bootcamp IS active

**Screen:** Home  
**Symptom:** `NoBootcampCard` ("STRUCTURED TRAINING / Start Bootcamp / Set Up Bootcamp") renders even though the Training tab shows an active 10K bootcamp enrollment (Wk 1/12).

**Root cause:** `HomeScreen.kt:926–935` — the condition for showing `PulseHero` is:
```kotlin
if (state.hasActiveBootcamp && nextSession != null) { PulseHero(...) }
else { NoBootcampCard(...) }  // ← also shows when bootcamp active but nextSession==null
```
When `nextSession` is `null` (likely because the sim run marked today's session COMPLETED, and no future SCHEDULED sessions exist yet), the home screen falls through to `NoBootcampCard` despite `hasActiveBootcamp == true`. The card copy "Start Bootcamp" / "Set Up Bootcamp" falsely implies the user has no enrollment.

**Fix:** Add a third state branch in `HomeScreen.kt`:
```kotlin
when {
    state.hasActiveBootcamp && state.nextSession != null ->
        PulseHero(...)
    state.hasActiveBootcamp && state.nextSession == null ->
        AllCaughtUpCard(onGoToTraining = onGoToBootcamp)  // new composable
    else ->
        NoBootcampCard(onSetupBootcamp = onGoToBootcamp)
}
```
`AllCaughtUpCard` should say something like "You're all caught up — next week's plan is on the way." with a soft gradient border, not the onboarding CTA copy.

**Files:** `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

---

## Bug 2 — MEDIUM: Home stat tiles stretch to fill entire screen (layout)

**Screen:** Home (bottom half)  
**Symptom:** Weekly Goal and Streak tiles are tall, nearly empty cards. The label + number + subtext are centered in a box that spans ~300dp.

**Root cause:** `HomeScreen.kt:438–443` — `PrimaryRow` is passed `modifier = Modifier.weight(1f)` from `BottomHalf`, and `GoalTile`/`StreakTile` each use `fillMaxHeight()`. When no `MidRow` (bootcamp tiles) or `CoachingStrip` exists, `PrimaryRow` absorbs the full remaining height of the screen.

**Fix in `HomeScreen.kt`:**
```kotlin
// BottomHalf — detect whether lower tiers exist
val hasLowerContent = state.hasActiveBootcamp || state.coachingInsight != null

PrimaryRow(
    state = state,
    modifier = if (hasLowerContent) Modifier.weight(1f)
               else Modifier.fillMaxWidth().height(180.dp)  // cap when alone
)
```
And in `GoalTile`/`StreakTile`, change `fillMaxHeight()` to `wrapContentHeight()` — the tiles shouldn't try to claim all vertical space; the tiles already have `Arrangement.Center` which is enough.

**Files:** `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

---

## Bug 3 — MEDIUM: Hardcoded `Color.White` invisible on light background

**Screen:** Home  
**Symptom (light mode):** Multiple progress bar tracks and the quote-bar decoration in `PulseHero` are invisible because they use white-with-alpha on a white/near-white background.

**Locations:**

| Composable | Line | Current | Fix |
|---|---|---|---|
| `BootcampTile` ring track | ~619 | `Color.White.copy(alpha = 0.05f)` | `CardeaTheme.colors.glassBorder` |
| `BootcampTile` bar track | ~676 | `Color.White.copy(alpha = 0.06f)` | `CardeaTheme.colors.glassBorder` |
| `BootcampTile` percent text | ~662 | `color = Color.White` | `CardeaTheme.colors.textPrimary` |
| `VolumeRow` bar track | ~743 | `Color.White.copy(alpha = 0.06f)` | `CardeaTheme.colors.glassBorder` |
| `PulseHero` quote bar | ~301 | `Color.White.copy(alpha = 0.18f)` | `CardeaTheme.colors.glassBorder` |

`CardeaColors` already has light-mode variants for `glassBorder` (`LightGlassBorder = Color(0x1A000000)`). Replacing hardcoded white with the theme token fixes all instances at once.

**Files:** `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

---

## Bug 4 — LOW: Account `OutlinedTextField` fields missing Cardea colors

**Screen:** Profile (Account)  
**Symptom (light mode):** Maps API Key and Max HR input fields use the default Material 3 focus border (blue) and default text colors. They look visually disconnected from the Cardea palette.

**Root cause:** `AccountScreen.kt:223` and `AccountScreen.kt:255` — both `OutlinedTextField` calls have no `colors` param. The profile-edit bottom sheet already applies the correct pattern at line 548–554.

**Fix:** Apply the same `OutlinedTextFieldDefaults.colors()` block to both fields:
```kotlin
OutlinedTextField(
    ...
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CardeaTheme.colors.accentPink,
        unfocusedBorderColor = CardeaTheme.colors.glassBorder,
        cursorColor = CardeaTheme.colors.accentPink,
        focusedTextColor = CardeaTheme.colors.textPrimary,
        unfocusedTextColor = CardeaTheme.colors.textPrimary,
        focusedLabelColor = CardeaTheme.colors.accentPink,
        unfocusedLabelColor = CardeaTheme.colors.textTertiary,
    ),
)
```

**Files:** `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

---

## Bug 5 — LOW: Setup screen "Open Bootcamp" button uses full 4-stop gradient

**Screen:** Setup (Training tab when not enrolled)  
**Symptom:** The "Open Bootcamp →" button in `BootcampEntryCard` uses `CardeaGradient` (4-stop: Red→Pink→Blue→Cyan). This is visually correct but inconsistent — all other action buttons (`CardeaButton`, `GradientSaveButton`) use `CardeaCtaGradient` (2-stop: Red→Pink). The blue/cyan tail of the full gradient makes this button look like a navigation element rather than a CTA.

**Fix in `SetupScreen.kt:502`:**
```kotlin
.background(CardeaCtaGradient)  // was: CardeaGradient
```

**Files:** `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

---

## Bug 6 — LOW: Session description text truncated on Training/Bootcamp screen

**Screen:** Training (Bootcamp)  
**Symptom:** Session description "Builds stroke volume and capillary densit..." is cut off with `...`. Full text: "Builds stroke volume and capillary density — same pace, less effort over time."

**Root cause:** The description `Text` in `BootcampScreen.kt` has `maxLines = 1` or is width-constrained in a single-line row layout.

**Fix:** Find the session description `Text` in `BootcampScreen.kt` and change `maxLines = 1` to `maxLines = 2` and ensure `overflow = TextOverflow.Ellipsis`.

**Files:** `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

---

## Priority Order

1. **Bug 1** — misleading "Set Up Bootcamp" copy is a UX regression that erodes trust
2. **Bug 2** — visual layout looks broken/half-finished on the home screen
3. **Bug 3** — invisible UI elements in light mode (functional regression)
4. **Bug 4** — cosmetic inconsistency on Profile/Account
5. **Bug 5** — minor gradient inconsistency on Setup
6. **Bug 6** — text truncation on Training

Bugs 1–3 should be fixed in a single PR. Bugs 4–6 can follow or be batched.
