# Repository Guidelines

## Project Structure & Module Organization
`HRCoach` is a single-module Android app. Main code lives in `app/src/main/java/com/hrcoach`, grouped by layer and feature: `ui/` for Compose screens and view models, `domain/` for workout and bootcamp logic, `data/` for Room entities, DAOs, and repositories, `service/` for foreground, BLE, audio, and worker services, plus `di/` and `util/`. Resources are in `app/src/main/res`. Unit tests mirror production packages under `app/src/test/java/com/hrcoach`. Room schema snapshots are checked into `app/schemas`. Planning docs live in `docs/plans/`; helper scripts are in `scripts/`.

## Build, Test, and Development Commands
Run commands from the repository root.

- `.\gradlew.bat :app:assembleDebug` builds the debug APK.
- `.\gradlew.bat :app:compileDebugKotlin` performs a fast Kotlin compile check.
- `.\gradlew.bat :app:testDebugUnitTest` runs JVM unit tests in `app/src/test`.
- `.\gradlew.bat :app:installDebug` installs the debug build on a connected device or emulator.

Use Android Studio for interactive runs, previews, and layout inspection.

## Coding Style & Naming Conventions
Follow Kotlin and Compose defaults: 4-space indentation, one top-level class or composable per file when practical, and descriptive names over abbreviations. Use `PascalCase` for classes, composables, and view models, `camelCase` for functions and properties, and keep package names lowercase. New screens belong under the relevant `ui/<feature>/` package; business rules should stay in `domain/`, not in composables. No formatter or lint plugin is currently wired into Gradle, so use Android Studio's Kotlin formatter before submitting.

## Testing Guidelines
This repo uses JUnit 4 and `kotlinx-coroutines-test` for unit coverage. Place tests in the matching package under `app/src/test/java` and name files `*Test.kt`, for example `SessionReschedulerTest.kt`. Add or update tests for domain logic, repositories, and service policies when behavior changes. If you touch Room entities or migrations, verify the generated schema JSONs in `app/schemas`.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit style with scopes, such as `feat(bootcamp): ...`, `fix(bootcamp): ...`, and `refactor(bootcamp): ...`. Keep commits focused and use the same pattern. PRs should include a short summary, testing notes, a linked issue or plan doc when relevant, and screenshots for Compose UI changes.

## Navigation & Tab Architecture (current as of 2026-03-10)

Four-tab bottom nav: **Home** (status/insight) | **Training** (action — Bootcamp if enrolled, SetupScreen if not) | **Activity** (History log + Trends toggle) | **Profile** (settings). The Training tab routes to `Routes.BOOTCAMP` when `BootcampStatusViewModel.hasActiveEnrollment == true`, otherwise to `Routes.SETUP`. The Activity tab (label "Activity") maps to `Routes.HISTORY` but both `Routes.HISTORY` and `Routes.PROGRESS` show the nav item as selected. A Log|Trends `SegmentedButton` toggle at the top of each screen navigates between them. Post-run "Done" lands on the history detail for that workout (not Home).

## Design System Tokens

All UI uses Cardea design tokens from `ui/theme/Color.kt`. Never hardcode colors.

| Token | Use |
|---|---|
| `CardeaTextPrimary` | Primary white body text |
| `CardeaTextSecondary` | Dimmed labels, subtitles |
| `CardeaTextTertiary` | Very subtle UI chrome |
| `CardeaGradient` | CTAs, active rings, accent strips |
| `GlassHighlight` / `GlassBorder` | Card surfaces |
| `ZoneGreen/Amber/Red` | Semantic HR zone indicators |

Component wrappers in `ui/components/CardeaInputs.kt` (`CardeaSlider`, `CardeaSwitch`, `cardeaSegmentedButtonColors()`) prevent M3 purple/lavender defaults from leaking in. Always use these instead of bare M3 components.

## Security & Configuration Tips
Secrets are loaded through the Maps secrets Gradle plugin. Keep local keys in `local.properties`; `local.defaults.properties` should remain a placeholder only. Never commit real API keys or machine-specific settings.
