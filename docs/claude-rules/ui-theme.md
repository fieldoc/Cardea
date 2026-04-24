# UI & Theme

Load when touching anything under `ui/`, `ui/theme/`, `ui/components/`, composables, design tokens, or the splash screen.

## Design system basics

- **Cardea design system** — dark glass-morphic, background `#050505` (radial with `#0D0D0D`). All tokens in `ui/theme/Color.kt`. Authoritative spec: `docs/plans/2026-03-02-cardea-ui-ux-design.md`. **Token drift rule:** spec conflicts with `Color.kt` → fix the spec.
- **`CardeaTheme`** is primary; `HrCoachTheme` = back-compat wrapper; `HrCoachThemeTokens` = typealias of `CardeaThemeTokens`. Dynamic color `false`. System/Light/Dark are user preferences — light mode is NOT a bug.
- **`cueBannerBorderColor(kind, alpha)`** (`ui/workout/CueBannerColors.kt`) — SSOT for cue-banner kind→hue. `CueBannerOverlay` uses α=0.5; `SoundLibraryScreen` list rows use α=0.4. Do not reintroduce per-screen `borderFor` helpers or hardcoded hex for banner tints.

## Gradients

- **Three gradients — use the right one:**
  - `CardeaGradient` (4-stop `#FF4D5A→#FF2DA6→#4D61FF→#00E5FF`, 135°) — ring foregrounds, gradient text, Tier 1 accent borders only. Do NOT alter stops.
  - `CardeaCtaGradient` (Red→Pink) — all buttons, active chips, selection indicators, day dots. Only ~3 legit `CardeaGradient` uses remain — grep before adding new ones. `BootcampScreen.kt` is fully purged; do NOT reintroduce the 4-stop there.
  - `CardeaNavGradient` (Blue→Cyan) — active nav icons only.
- **One gradient accent per screen** — 3-tier hierarchy: Tier 1 gradient @ 18dp, Tier 2 white-on-glass @ 14dp, Tier 3 secondary-on-glass @ 12dp. BootcampTile ring is plain white 0.55α.

## Glass surfaces & color tokens

- **Glass surfaces:** `GlassBorder = 0x1AFFFFFF` (10%), `GlassHighlight = 0x0AFFFFFF`. Use `ui/components/GlassCard.kt` for all cards. `GlassCard.containerColor` IS wired — pass `borderColor = Color.Transparent` when using an outer `Modifier.border(brush=…)` to avoid double-borders. Same when adding tinted borders (e.g. `ZoneGreen.copy(alpha = 0.15f)`).
- **`DarkGlassFillBrush`** = 8%/4% white (strengthened from 6%/2%) so glass is visible on `#050505`.
- **`CardeaTextTertiary`** = `#6B6B73` (raised from `#52525B` for WCAG AA on `#050505`); `DisabledGray` and `ThresholdLine` follow.
- **Single green** — `ZoneGreen` (#22C55E) is reserved for HR-zone indicators and `AllCaughtUpCard`. `NudgeGreen` (#4ADE80) is removed — do not reintroduce. **Partner/social accent** is `PartnerTeal` (in `Color.kt`), NOT green — used in `PartnerNudgeBanner` and any future partner/social surface. Rationale: green collided with the warm-pink CTA hierarchy on Home.

## Typography & sizing

- **Zone-status text** (HR pills/indicators) is safety-critical — min 12sp with adequate padding. Never decorative micro-labels.
- **Min font size 10sp** globally (9sp absolute floor for micro-labels only). Known remaining violations: `HomeScreen.kt` (8sp ~722; 9sp ~509/550/666/751), `BootcampSettingsScreen.kt` (9sp ~811), `CalendarHeatmap.kt` (9sp ~90), `MissionCard.kt` (9sp ~135).
- **Typography scale completeness** — `HrCoachTypography` must define every style used; undefined styles silently fall back to M3 defaults. Defined: `displaySmall` 34 Bold, `headlineSmall` 22 SemiBold, `titleSmall` 15 SemiBold, `labelMedium` 12 Medium. Add to `Type.kt` before use.

## Logo, icon, splash

- **`CardeaLogo`** — Canvas heart+ECG+orbital ring with gradient fill. `LARGE` (splash 180dp, though splash now uses its own Canvas animation), `SMALL` (nav 32dp).
- **App icon "Pure Signal"** — single ECG P-QRS-T trace bleeding off both edges; gradient left→right; bg `#0D0D14`; QRS spike near-full-height, needle-sharp. No heart shape. Mipmaps generated via PIL (`gen_cardea_icon.py`, `/c/Python314/python.exe`). Sizes: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192. Both `ic_launcher.png` and `ic_launcher_round.png` per density.
- **Splash screen** (`SplashScreen.kt`) — full Canvas animation via `withFrameMillis` loop (no `CardeaLogo`, no `animateFloatAsState`). Phases: 1600ms draw-on / 500ms hold / 500ms fade. `onFinished()` fires once. Do NOT add Compose animation APIs. Capture on device: `adb shell am force-stop com.hrcoach && adb shell monkey -p com.hrcoach -c android.intent.category.LAUNCHER 1 >/dev/null & sleep 0.9 && adb shell screencap //data/local/tmp/s.png && adb pull //data/local/tmp/s.png /tmp/s.png`.
- **Splash freeze-at-hold** — loop caps at `cycleIdx >= 1` and freezes `elapsed` at `PHASE_DRAW_MS + PHASE_HOLD_MS - 1` (fully-drawn, pre-fade). Both the `withFrameMillis` block AND the `Canvas` block MUST apply the same cap — they re-derive `elapsed` independently. Without both, slow cloud restore either re-animates from scratch or fades to black.
- **NavGraph splash contract:** navigation fires only when **both** `animationFinished` (from `onFinished`) AND `destination != null` (from `autoCompleteForExistingUser` + `checkAndRestoreIfNeeded`) are true. Falling back to `ONBOARDING` on null destination misroutes existing users whose cloud restore is slow.

## Active run chrome

- **Active run top-right chrome** — screen-level actions (gear, etc.) live in a dedicated top-bar `Row` at the top of the workout `Column`, NOT a floating `Box.TopEnd` child. MissionCard's top-right is occupied by the NO SIGNAL / zone pill; floating overlays collide. Use a 36dp glass chip (`GlassHighlight` + `GlassBorder`, 12dp radius) with an 18dp icon for the affordance.
- **Active-run settings sheet** (`ActiveRunSettingsSheet.kt`) is the canonical pattern for mid-run mutable settings: stateless `AudioSettingsSection` shared with AccountScreen, `ActiveRunSettingsViewModel` persists each event and fires `ACTION_RELOAD_AUDIO_SETTINGS`. Destructive finishes (e.g. "End session early" for bootcamp) use `OutlinedButton` with `ZoneRed` — CTA gradient is reserved for primary forward actions.
- **Countdown overlay invariants** (`ActiveWorkoutScreen.kt`): `countdownActive = countdownSecondsRemaining != null`. While active: (1) `CueBannerOverlay` is suppressed (BLE/GPS cues would stack over "3-2-1-GO"), (2) the stat-card `Column` is entirely hidden (zeroed values bleed through 0.92α overlay), (3) backdrop is a radial gradient `Black(0.92) → bgPrimary(0.82)`, not flat black. Don't regress any of these.
- **Active workout screen spacing** — 12dp vertical (`Arrangement.spacedBy`). Radius tiers: 16dp MissionCard / 14dp hero stats, guidance, buttons, projected pill / 12dp tertiary stats, zone badge. No 10/20dp radii.

## Compose patterns

- **Canvas glow in Compose** — use `drawIntoCanvas { canvas -> canvas.drawPath(path, Paint().also { it.asFrameworkPaint().maskFilter = BlurMaskFilter(...) }) }`. No CSS-blur equivalent exists.
- **Gradient nav icons** — `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` with `CardeaNavGradient` for pixel-perfect gradient fill on any `ImageVector`.
- **Compose `remember` rule** — `remember`, `rememberInfiniteTransition`, `rememberCoroutineScope` NEVER in conditional branches. Call unconditionally, use the result conditionally. Violating crashes when the condition toggles.
- **`FlowRow`** requires `@OptIn(ExperimentalLayoutApi::class)`.

## Charts

- **Charts are custom Canvas** (`ui/charts/`) — BarChart, PieChart, ScatterPlot use `DrawScope` directly. Must use `CardeaTheme.colors` (M3-free as of 2026-04-12). `HrCoachThemeTokens.subtleText` is legacy — use `CardeaTheme.colors.textSecondary`. Canvas sizing must use `X.dp.toPx()`, not raw float px.
- **`BarChart.color`** is a solid fill with alpha modulation (latest=1.0, others=0.55). Don't reintroduce hardcoded gradients. `SectionHeader` = uppercase labelMedium Bold, 2sp letterSpacing, `textSecondary`; subtitle `textTertiary`.

## Component-specific

- **`hrPercentColor()`** (SetupScreen) uses `ZoneRed`/`ZoneAmber`/`ZoneGreen` tokens — do NOT hardcode hex.
- **`CardeaSlider`** thumb/active track = `GradientPink` (distinct from M3 and from CTA layering). Bootcamp sliders also `GradientPink` directly.
- **Segmented button accent** is neutral: `cardeaSegmentedButtonColors()` uses `textPrimary`/`textSecondary`, not `GradientBlue`. Active stands out via white text + border.
- **M3 button text color leak:** `OutlinedButton`/`TextButton` default text → `colorScheme.primary` (= `GradientBlue`). Always pass explicit `color = CardeaTheme.colors.textPrimary` (or `textSecondary`) to inner `Text()`. `CardeaButton` is exempt.
- **`OutlinedTextField` dark mode** — always set focused/unfocused `containerColor` + `placeholderColor` in `OutlinedTextFieldDefaults.colors()`. Recommended: `Color(0x14FFFFFF)` focused, `Color(0x0AFFFFFF)` unfocused.
- **`TabRow` vs `ScrollableTabRow`** — ≤3 fixed tabs → `TabRow` (equal distribution). Custom indicator via `Box(Modifier.tabIndicatorOffset(tabPositions[selected]).height(2.dp).background(GradientPink))`. In M3 BOM ≥ 2024.12.01, `tabIndicatorOffset` is a member of `TabRowDefaults`; wrap: `with(TabRowDefaults) { ... }`.
- **`CardeaButton` default `innerPadding = 0.dp`** — for wrap-content width, always pass `PaddingValues(horizontal = 24.dp, vertical = 14.dp)` to keep ≥44dp touch target.
- **`AllCaughtUpCard`** — 40dp Canvas ring, `ZoneGreen` arc (800ms tween) + Check icon. Title `headlineMedium` 26sp; CTA wrap-content w/ `innerPadding`.
- **Material icons availability** — only a subset ships in `Icons.Default`. Verify via grep before use. Confirmed: `Home`, `Map`, `Favorite`, `FavoriteBorder`, `VolumeUp`, `Mic`, `Settings`, `Notifications`, `Group`, `Timer`, `Person`, `Check`, `Close`, `Add`, `Search`, `Bluetooth`, `ExpandLess`, `ExpandMore`, `ArrowUpward`, `ArrowDownward`, `ChevronRight`.

## Screen-specific hierarchy

- **Home screen gradient hierarchy** — `PulseHero` = sole Tier 1 (gradient text, conditional on `isToday`; future sessions use `textSecondary`, ECG α drops 0.45→0.20). `GoalTile` = Tier 2. `BootcampTile` progress bar uses `ctaGradient`; `VolumeTile` uses subtle inline gradient. No gradient borders/text on stat tiles.
- **BootcampEntryCard CTA** = ghost outlined (no fill) so "Start Run" is the sole gradient primary CTA on the Training tab.

## Design docs

- Authoritative spec: `docs/plans/2026-03-02-cardea-ui-ux-design.md` (tokens match `Color.kt`).
- Design review: `docs/mockups/cardea-design-review.html` (phone-frame mockups).
- Polish proposals: `docs/mockups/cardea-ui-polish-2026-04-11.html`.
- UI plan: `docs/plans/2026-03-02-cardea-ui-ux-plan.md`.
- Guided workouts UX: `docs/plans/2026-03-02-guided-workouts-ux-design.md`.
- Guided workouts impl: `docs/plans/2026-03-01-preset-workout-profiles.md`.
- E2E audit: `docs/2026-04-11-e2e-happy-path-audit.md`.
- Legacy (superseded): `docs/plans/2026-02-25-hr-coaching-app-design.md` — data model + alert sections still valid.
