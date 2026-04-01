# Onboarding & First-Run Tutorial — Design Spec

**Date:** 2026-03-31
**Issue:** 18a — Core app has no guided intro
**Status:** Design

## Problem

New users launch Cardea and land on the HomeScreen with no context. They see empty metrics, a "Set Up Bootcamp" CTA, and three OS permission dialogs fired in rapid succession. There is no explanation of what Cardea does, how HR zone coaching works, what hardware is needed, or how the app is organized. The bootcamp wizard collects training preferences but assumes users already understand the app's value proposition and have their profile set up.

## Solution

An 8-screen linear onboarding flow shown on first launch, between the splash screen and the HomeScreen. Implemented as a `HorizontalPager` with progress dots. Covers: value proposition, user profile collection, HR zone education, BLE monitor guidance, contextual permission requests, tab tour, and a launch pad funneling users toward Bootcamp setup.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Flow structure | Linear (not grouped/chapters) | Simpler to build (HorizontalPager), each concept gets full attention, permission requests land naturally in context |
| BLE pairing | Educational only, no live scan | Avoids interrupting flow; points user to Workout tab for actual pairing |
| HRmax collection | Auto-calculate from age (220-age) with override | Most users don't know their HRmax; showing a calculated value educates while providing a reasonable default |
| Permission timing | Contextual per-screen (not batch) | Pre-permission explainers before each OS dialog improve grant rates vs. cold prompts at launch |
| Bootcamp transition | Strong CTA at end, not chained | Prevents fatigue from 8 onboarding + 5 bootcamp screens back-to-back; keeps bootcamp wizard reusable |
| Skip option | Always visible in top-right | Respects impatient users; still sets `onboarding_completed` flag |
| Existing users | Never shown again | `onboarding_completed` SharedPreferences flag checked at splash; existing installs get flag auto-set on upgrade |

## Screen-by-Screen Specification

### Screen 1: Welcome ("Meet Cardea")

**Purpose:** Brand moment + value proposition.

**Content:**
- Cardea logo (animated, reusing `CardeaLogo` composable at `LogoSize.LARGE`)
- Headline: "Meet Cardea"
- Subtext: "Your AI running coach that listens to your heart. Real-time zone coaching, adaptive training plans, and audio alerts that keep you in the zone."
- CTA button: "Get Started"

**Actions:** None. Purely informational. Tapping "Get Started" or swiping advances to Screen 2.

### Screen 2: Your Profile

**Purpose:** Collect age, weight, and HRmax to personalize training zones.

**Content:**
- Headline: "Tell us about you"
- Subtext: "We'll use this to personalize your training zones"
- Input fields:
  - **Age** — number picker or text field (required)
  - **Weight** — number field with unit toggle (lbs/kg) (optional but encouraged)
  - **Estimated Max Heart Rate** — auto-calculated as `220 - age`, displayed with gradient text treatment (Tier 1). Shows formula explanation: "Calculated as 220 - your age." Tap-to-override expands a text field for users who know their actual HRmax from a field test.

**Actions:**
- Persists age, weight, and HRmax to `UserProfileRepository` (SharedPreferences)
- HRmax recalculates live as age changes
- "Next" button enabled only when age is provided (weight can be skipped)

**Validation:**
- Age: 13-99 range
- Weight: 60-400 lbs / 27-180 kg
- HRmax override: 120-220 bpm

### Screen 3: How Coaching Works (HR Zones)

**Purpose:** Educate users on the 5-zone model that drives all Cardea coaching.

**Content:**
- Headline: "Heart Rate Zones"
- Subtext: "Cardea coaches you in real-time to stay in the right zone"
- Five zone rows, each showing:
  - Zone badge (Z1-Z5) with zone color
  - Human-readable name: Easy/Recovery, Aerobic Base, Tempo, Threshold, Max Effort
  - Percentage range of HRmax (50-60%, 60-70%, 70-80%, 80-90%, 90-100%)
  - If HRmax was entered on Screen 2, also shows actual BPM range (e.g., "94-113 bpm")

**Actions:** Educational only. No data collected.

**Zone colors:** Use existing `ZoneGreen` for Z1-Z2, `ZoneAmber` for Z3, orange for Z4, `ZoneRed` for Z5 (matching the palette already used in workout screens).

### Screen 4: Your Heart Rate Monitor

**Purpose:** Explain BLE compatibility, set expectations about ANT+ vs Bluetooth, and show where to connect in the app.

**Content:**
- Headline: "Connect Your Monitor"
- Subtext: "Cardea reads your heart rate in real-time via Bluetooth"
- Compatibility section:
  - Green card: "Works with Bluetooth (BLE)" — "Most chest straps and armbands from the last 5+ years. If it says 'Bluetooth' on the box, you're good."
  - Red card: "ANT+ only — not supported" — "Some older Garmin straps use ANT+ only. Many modern monitors support both — if yours does, Cardea will use the Bluetooth side."
- Info callout: "You'll connect your monitor from the **Workout** tab before each run. No need to pair now."

**Actions:**
- Requests **Bluetooth permissions** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+)
- Pre-permission explainer shown before OS dialog: "Cardea needs Bluetooth access to find and connect to your heart rate monitor."
- If user denies: show a note that they can grant later in Settings; don't block progression

### Screen 5: Route Tracking

**Purpose:** Explain GPS tracking capabilities and request location permission.

**Content:**
- Headline: "Track Your Runs"
- Subtext: "Cardea uses GPS to map your route, measure distance, and calculate pace"
- Visual: Stylized route map illustration with sample metrics (distance, pace, time)

**Actions:**
- Requests **Location permissions** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)
- Pre-permission explainer: "Cardea needs location access to track your running route and calculate distance and pace."
- If denied: note that GPS features won't work until granted; don't block progression

### Screen 6: Smart Alerts

**Purpose:** Explain the audio coaching system and request notification permission.

**Content:**
- Headline: "Stay in the Zone"
- Subtext: "Audio cues that play over your music — no need to look at your phone"
- Three alert types with icons:
  - **In Zone** — gentle confirmation tone
  - **Drifting High** — "Ease up" descending tone pattern
  - **Too Low** — "Pick it up" ascending tone pattern
- Note: "Alerts layer over your music without pausing it"

**Actions:**
- Requests **Notification permission** (`POST_NOTIFICATIONS` on Android 13+)
- Pre-permission explainer: "Cardea needs notification access to alert you during runs, even when the screen is off."
- On older Android (< 13): skip permission request, just show the educational content

### Screen 7: Your Tabs

**Purpose:** Quick visual tour of the 5-tab navigation structure, highlighting the Workout tab as the star.

**Content:**
- Headline: "Find Your Way Around"
- Five rows, one per tab:
  - **Home** — "Your dashboard — training status, next session, streaks"
  - **Workout** — "Start runs, connect your HR monitor, see bootcamp sessions" *(highlighted with gradient text and accent border — Tier 1 treatment)*
  - **History** — "Past runs with route maps and HR charts"
  - **Progress** — "Trends, volume, and fitness analytics over time"
  - **Account** — "Settings, audio preferences, theme, and profile"

**Actions:** None. Visual tour only.

**Design note:** The Workout tab row gets gradient text treatment and a subtle accent border to visually call it out as the primary action center. All other rows use standard white text on glass.

### Screen 8: Launch Pad

**Purpose:** Funnel users toward Bootcamp setup with a strong CTA, while offering a secondary "explore first" option.

**Content:**
- Headline: "You're All Set!"
- Subtext: "Ready to build your personalized training plan? Bootcamp adapts to your schedule and fitness level."
- Primary CTA: "Start Bootcamp Setup" (gradient button, `CardeaCtaGradient`)
- Secondary link: "Explore the app first" (subtle text, `CardeaTextTertiary`)

**Actions:**
- **"Start Bootcamp Setup":** Sets `onboarding_completed = true`, navigates to `Routes.BOOTCAMP`
- **"Explore the app first":** Sets `onboarding_completed = true`, navigates to `Routes.HOME`
- Both paths clear onboarding from the backstack (`popUpTo(Routes.ONBOARDING) { inclusive = true }`)

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `ui/onboarding/OnboardingScreen.kt` | Main screen with `HorizontalPager`, progress dots, skip button |
| `ui/onboarding/OnboardingViewModel.kt` | Manages page state, permission requests, profile data collection |
| `ui/onboarding/OnboardingPages.kt` | Individual page composables (Welcome, Profile, Zones, BLE, GPS, Alerts, Tabs, LaunchPad) |
| `data/repository/OnboardingRepository.kt` | Wraps SharedPreferences for `onboarding_completed` flag |

### Modified Files

| File | Change |
|------|--------|
| `ui/navigation/NavGraph.kt` | Add `Routes.ONBOARDING` route; change splash destination logic to check onboarding flag |
| `ui/splash/SplashScreen.kt` | Accept `onboardingCompleted: Boolean` param; call `onFinished(destination)` with either ONBOARDING or HOME |
| `MainActivity.kt` | Remove bulk permission request from `onCreate` (permissions now requested contextually in onboarding) |
| `util/PermissionGate.kt` | Add helper methods for requesting individual permission groups (BLE, GPS, Notifications separately) |
| `di/AppModule.kt` | Provide `OnboardingRepository` (or it can be constructor-injected directly with `@Inject`) |

### Navigation Flow

```
App Launch
    → SplashScreen (animated, 2.4s)
    → Check onboarding_completed flag
        → false → OnboardingScreen (8-page pager)
            → "Start Bootcamp" → BootcampScreen
            → "Explore first" → HomeScreen
        → true → HomeScreen
```

### State Management

```kotlin
data class OnboardingUiState(
    val currentPage: Int = 0,
    val age: String = "",
    val weight: String = "",
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val estimatedHrMax: Int? = null,
    val hrMaxOverride: String = "",
    val isHrMaxOverrideExpanded: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
)

enum class WeightUnit { LBS, KG }
```

### Permission Strategy

Permissions move from a batch request in `MainActivity.onCreate()` to contextual requests within the onboarding flow:

1. **Screen 4 (BLE):** Request `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (Android 12+)
2. **Screen 5 (GPS):** Request `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
3. **Screen 6 (Alerts):** Request `POST_NOTIFICATIONS` (Android 13+)

Each screen shows a brief explainer before triggering the OS dialog via `rememberLauncherForActivityResult(RequestMultiplePermissions())`.

**Post-onboarding permission handling:** If a user skips onboarding or denies permissions, the existing `PermissionGate` checks in `SetupScreen` (before starting a workout) remain as the safety net. Those checks are unchanged.

**Upgrade path for existing users:** On app upgrade, if `onboarding_completed` is not set in SharedPreferences, existing users would see onboarding. To prevent this, the `OnboardingRepository` should check whether the user has any existing workout data (via `WorkoutRepository.getWorkoutCount()`). If count > 0, auto-set `onboarding_completed = true` and skip onboarding.

### Data Persistence

Profile data (age, weight, HRmax) persists to `UserProfileRepository`, which already exists and uses SharedPreferences. The onboarding flow writes to the same store that the Account screen reads from — no new persistence layer needed for profile data.

The only new SharedPreferences entry is:
- **`onboarding_prefs`** → `onboarding_completed: Boolean` (managed by `OnboardingRepository`)

## UI Design

### Overall Style
- Dark background (`CardeaBgPrimary` #050505) matching the rest of the app
- Content on glass cards (`GlassCard` composable) where appropriate
- Gradient text treatment (`CardeaGradient`) for the most important element per screen (HRmax value, Workout tab name)
- Progress dots at the bottom showing current page (gradient fill for active, `CardeaTextTertiary` for inactive)

### Skip Button
- Top-right corner, all screens except Screen 8 (which has explicit exit CTAs)
- Text: "Skip" in `CardeaTextSecondary`
- Tapping Skip: sets `onboarding_completed = true`, navigates to HomeScreen

### Page Transitions
- `HorizontalPager` with default swipe gesture
- "Next" / "Get Started" / "Continue" button at the bottom of each page
- Back swipe returns to previous page (no back button on Screen 1)

### Accessibility
- All content readable by TalkBack
- Zone color badges include content descriptions (e.g., "Zone 1, Easy Recovery, 50 to 60 percent")
- Skip button has minimum 48dp touch target
- Permission explainer text is screen-reader friendly

## Edge Cases

| Scenario | Handling |
|----------|----------|
| User force-kills app mid-onboarding | Profile data saved per-screen; onboarding restarts from Screen 1 (no partial resume — flow is short enough) |
| User rotates device during onboarding | `HorizontalPager` state survives configuration change via `rememberPagerState` saved in ViewModel |
| User denies all permissions | All screens allow progression regardless of permission state; `PermissionGate` catches missing permissions before workout start |
| Existing user upgrades app | `OnboardingRepository` checks for existing workout data; if found, auto-sets `onboarding_completed = true` |
| User taps "Skip" on Screen 1 | Goes directly to HomeScreen; profile data not collected (user can set it in Account tab later) |
| Android < 12 (no runtime BLE perms) | Screen 4 skips the permission request, shows only the educational content |
| Android < 13 (no runtime notification perms) | Screen 6 skips the permission request, shows only the educational content |

## Testing Strategy

- **Unit tests:** `OnboardingViewModel` — profile validation, HRmax calculation, page navigation logic, permission state tracking
- **Unit tests:** `OnboardingRepository` — flag persistence, upgrade-path logic (existing user detection)
- **UI tests:** Each onboarding page renders correctly with expected content
- **Integration test:** Full flow from splash → onboarding → HomeScreen and splash → onboarding → Bootcamp
- **Regression:** Verify that the existing permission checks in `SetupScreen` still work as a safety net when onboarding is skipped
