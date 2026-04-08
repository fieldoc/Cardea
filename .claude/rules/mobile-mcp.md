# mobile-mcp — Usage Rules & Playbook

Mobile MCP replaces the old `android-debug-bridge-mcp`. Tools are prefixed `mcp__mobile-mcp__` (exact prefix depends on server registration name).

## Prerequisites

Before invoking any mobile-mcp tool, verify a device/emulator is available:

```bash
adb devices
```

At least one entry (other than "List of devices attached") must appear. If the emulator is not running, start it from Android Studio or via `emulator -avd <name>`.

## Core Tools & When to Use Them

| Task | Tool | Notes |
|------|------|-------|
| Verify UI renders correctly | `screenshot` | Use after build+install to confirm composables look right |
| Check what's on screen | `get_accessibility_tree` | Structured snapshot — use before tapping to find element IDs |
| Tap a UI element | `tap` | Prefer accessibility ID over coordinates when available |
| Type text into a field | `type_text` | Focuses field first, then types |
| Scroll a list | `scroll` | Specify direction and distance |
| Launch the app | `launch_app` | Package: `com.hrcoach` (verify in build.gradle) |
| Install a new build | `install_app` | Point at the debug APK path |
| Read logcat | `get_logs` | Filter by tag to reduce noise: `tag:WorkoutForegroundService` |
| Clear app data | `clear_app_data` | Useful for testing first-run flows |

## Standard Verification Loop

After implementing a UI feature and building:

1. `install_app` — install the debug APK
2. `launch_app` — open Cardea
3. Navigate to the relevant screen (tap as needed)
4. `screenshot` — capture the current state
5. Verify against the Cardea design spec in `docs/plans/2026-03-02-cardea-ui-ux-design.md`
6. If wrong: identify the issue, fix the Compose code, rebuild, repeat from step 1

## Cardea-Specific Notes

- **Package name:** verify in `app/build.gradle` (`applicationId`)
- **Debug APK path:** `app/build/outputs/apk/debug/app-debug.apk`
- **Active workout screen** hides the bottom nav bar — don't expect it to be visible during a workout
- **BLE and GPS** are not available in emulators — test HR zone logic with unit tests; use mobile-mcp only for UI shape verification
- **Dark theme only** — Cardea enforces its own palette, so system light/dark mode doesn't apply

## Troubleshooting

**"No devices found":** Run `adb devices`. If empty, ensure USB debugging is on (physical device) or start the emulator.

**Screenshot is black:** App may be on a protected screen or the activity isn't foregrounded. Try `launch_app` again.

**Accessibility tree is empty:** Some custom Canvas composables (`ui/charts/`) don't expose accessibility nodes. Fall back to coordinate-based taps for those.

**App crashes on install:** Run `adb logcat -s AndroidRuntime` to see the exception.
