# Test Fakes & Known Lint

Load when adding DAO methods, writing/fixing unit tests, or investigating a lint/compile error that looks pre-existing.

## Test fakes

- **`FakeBootcampDao`** (`BootcampSessionCompleterTest.kt`) directly implements `BootcampDao`. Adding DAO methods requires fake updates.
- **`AchievementDao` has TWO fakes:** `FakeAchievementDao` class in `AchievementEvaluatorTest.kt`, and an inline anonymous object in `BootcampSessionCompleterTest.kt:27`. Update both on DAO changes.
- **Room `@Insert` returns row ID** — declare `suspend fun insert(...): Long` when you need the auto-generated ID (e.g. for cloud sync). Entity in memory still has `id=0`.
- **Test timezone safety** — tests building epoch-millis via `LocalDate.atStartOfDay(ZoneId.of("UTC"))` must pass that zone to the function under test. Relying on `ZoneId.systemDefault()` fails west of UTC.

## Known Pre-existing Lint / Compile Errors (not regressions)

- `BleHrManager.kt` MissingPermission; `WorkoutForegroundService.kt` MissingSuperCall; `NavGraph.kt` NewApi ×2; `build.gradle.kts` WrongGradleMethod.
- `PartnerSection.kt:294` — `WindowInsets` unresolved + `@Composable` scope errors. Blocks `assembleDebug`. Unrelated to UI polish work.
- `MainActivity` permission handling — `registerForActivityResult` handles permanent denial (Settings redirect) + temporary denial (Toast). `PermissionGate.missingRuntimePermissions()` checked in `onCreate`.
