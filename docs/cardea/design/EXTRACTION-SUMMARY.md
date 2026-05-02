# Cardea Design Handoff — Extraction Summary
Source: `docs/cardea/design/cardea/` (handoff bundle from claude.ai/design)
Date: 2026-05-01

This is a distilled, implementation-ready spec extracted from the full bundle. The authoritative sources are still `cardea/project/Cardea Design System.html` and `cardea/project/Home Hero States.html`; this doc is for quick reference during Compose implementation.

---

## A — Intent (from chats)

- **Chat 5** consolidated the design system from prior mockups (Bootcamp / Account / Post-Run / home-final) into the unified `Cardea Design System.html`.
- **Chat 4** designed the three Home heroes on top of that system. No further iteration after delivery.
- **Hard rules:**
  - GraduateHero gets the Tier-1 4-stop `CardeaGradient` headline. ResumeCard does NOT — gradient on a paused user "feels like marketing trying to win them back." NoBootcampCard keeps existing gradient title (refresh, not rewrite).
  - Stats trio is glass + white, NOT gradient. "Gradient on stats would steal from the headline."
  - Race chips on GraduateHero are equal-weight, no winner. Cardio Health is the recommended path.
  - Tertiary copy for graduates: "Just freestyle today" — explicitly NOT "Manual Run".
  - Resume CTA glow `0.22α`, dialed back from active-run `0.25α` ("warm invitation, not fire alarm").
  - "Paused · Week N" eyebrow tells where, not why; no red, no warning chrome — pause is neutral.
  - Reuse `BlendMode.SrcIn` gradient-text trick from `NoBootcampCard:970-980` exactly. Reuse `PulseLabel`, `FeatureItem`, `CardeaButton` primitives.

## B — Design system tokens

### Colors (locked)

```
--cardea-gradient: linear-gradient(135deg, #FF4D5A 0%, #FF2DA6 35%, #4D61FF 65%, #00E5FF 100%)
--cta-gradient:    linear-gradient(135deg, #FF2DA6 0%, #FF4D5A 50%, #FF2DA6 100%)

--grad-red:   #FF4D5A   --grad-pink:  #FF2DA6
--grad-blue:  #4D61FF   --grad-cyan:  #00E5FF

--bg-primary:   #050505    --bg-secondary: #0D0D0D
--bg-page:      #08080D    --bg-tier-2:    #10111A

--text-primary:    #FFFFFF
--text-secondary:  #9AA4B2
--text-tertiary:   #5A6573
--text-quaternary: #3A424E   (hero file only — for trailing arrows in tertiary links)

--green: #2ED47A   --amber: #FFB020   --red: #E5484D

--glass-fill:        linear-gradient(180deg, rgba(255,255,255,0.05), rgba(255,255,255,0.015))
--glass-fill-strong: linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0.03))
--glass-border:      rgba(255,255,255,0.08)
--glass-border-2:    rgba(255,255,255,0.12)   (Cardea Design System token)
--glass-border-strong: rgba(255,255,255,0.14) (Hero States token — slightly stronger; see Section E)
--glass-highlight:   rgba(255,255,255,0.03)
```

### Typography

| Style | Family / weight | Size | Line-height | Letter-spacing |
|---|---|---|---|---|
| Display | Playfair 900 | 84 px | 95% | -1.5 |
| Headline (section) | Playfair 700 | 44 px | 105% | -0.8 |
| Title L | DM Sans 800 | 32 px | 110% | -0.5 |
| Title M | DM Sans 700 | 22 px | 120% | -0.2 |
| Body | DM Sans 400 | 15 px | 155% | — |
| Body 2 | DM Sans 400 | 14 px | 155% | — (`text-secondary` tint) |
| Eyebrow (system) | JetBrains Mono 500 | 11 px | 200% | +2 (uppercase) |
| Eyebrow (hero file `.pulse-label`) | JetBrains Mono 700 | 10 px | — | +1.5 |
| Data | JetBrains Mono 600 | 28 px | 100% | -0.3 |

Hero-card title: Playfair 700, 30 px, 105%, -0.4. Italic accent inside Playfair uses 4-stop gradient via `background-clip: text`.

### Spacing (4-pt grid)

`4 / 8 / 12 / 16 / 20 / 24 / 32 / 48`. Project rules enforce `4 / 8 / 12 / 16 / 24` rhythm; hero file uses 10/14 in places — accepted by designer.

### Radii

| px | Use |
|---|---|
| 6  | Pills, tags |
| 10 | Small buttons, chips |
| 14 | Buttons, stat tiles |
| 16 | Glass cards (default) |
| 18 | Hero / emphasis |
| 24 | Outer card on Hero States |
| 9999 | Pills, rings |

### Shadows / glows

- Primary CTA glow (system): `0 8px 30px -10px rgba(255,45,166,0.5)`.
- Resume / NoBootcamp CTA glow: `0 0 30px rgba(255,45,166,0.22)` (warmer, dialed back).
- GraduateHero card: no shadow; uses radial gradient washes inside.

### Glass tiers

```
Tier 1 — Default card
  bg = glass-fill | border = glass-border (.08) | radius = 16/18 | blur = 12-20px
Tier 2 — Focused (nav, modals)
  bg = glass-fill-strong | border = glass-border-2 (.12) | radius = 12-16
Tier 3 — Brand-ringed emphasis
  1px outer wrapper with cardea-gradient + inner diagonal wash
```

### Component primitives

- **CardeaButton (primary)**: 50–56 px tall, radius 14–16, font DM Sans 600 / 15 / +0.2, fill `cta-gradient`, glow `0 0 30px rgba(255,45,166,0.22)`.
- **Secondary button**: same h/r, transparent fill, `1px glass-border-strong`, `text-primary` 500 / 13.5.
- **PulseLabel** (eyebrow): mono 10/700/+1.5 uppercase, `text-tertiary`. Default 4×4 cyan dot, no glow. Variants `.warm` (amber dot) and `.bright` (pink + glow).
- **Pill (zone/status)**: `padding 5×12, radius 100, mono 600/10.5/+0.5 uppercase`. Zone bg `rgba(zone, 0.15)` + lightened text.
- **Stat tile**: glass-fill bg, glass-border, radius 14, padding 16. Value mono 600/26/-0.3; unit mono 9.5/600/+1.2 uppercase tertiary; label mono 10/+1.5 uppercase tertiary.
- **Feature row**: gap 12. Icon container 38×38 r12, bg `<color>.copy(α=0.12)`, icon 18×18 same color. Title DM Sans 600/14 primary. Desc 11.5 secondary line-height 1.4.
- **Toggle**: 44×26, radius 100, off bg `α 0.06`, on bg `cardea-gradient`, knob 20×20 white, 250 ms ease.

### Motion

`Duration 250ms`, `easing ease-out`. Allowed: opacity + translate. No scale, no rotate, no spring/bounce. Hero-file in-card chip hover is `160ms ease`.

---

## C — Three Home hero states

### C.1 GraduateHero

Trigger: `enrollment.status == GRADUATED`.

Compose surface:
```
GraduateHero(
  enrollment, weeksCompleted: Int, sessionsCompleted: Int, totalKm: Double,
  onChooseEvergreen: () -> Unit,
  onChooseRace: (RaceGoal) -> Unit,
  onFreestyle: () -> Unit,
  modifier: Modifier = Modifier
)
```

**Vertical composition** (top → bottom, gap after each):

1. Decorative laurel arc + 5 spark dots (absolute inside card)
2. Eyebrow Row: PulseLabel `GRADUATED · {goalLabel}` + Trophy chip `{weeks} WEEKS` — gap 12
3. Gradient headline `Race-tier fitness, locked in.` (★ shipping; period in pink) — gap 8
4. Subtitle `You finished your first marathon prep cycle. Every pace zone, every long run, banked. Here's where to take it next.` — gap 22
5. Stats trio Row, weight=1 each, spacedBy 8: `WEEKS / SESSIONS / KM` — gap 22
6. PulseLabel `CHOOSE YOUR NEXT PROGRAM` — gap 10
7. EvergreenChoiceRow (Cardio Health primary CTA) — gap 10
8. Header `NEW RACE` + 4 race chips `5K / 10K / Half / Full`, spacedBy 8 — gap 18
9. TextButton centered `Just freestyle today →`

**Card shell:**
```
border-radius: 24px (= HomeCardRadius?)
border: 1px rgba(255,255,255,0.14) (glass-border-strong)
background:
  radial-gradient(ellipse at 80% -10%, rgba(255,45,166,0.18), transparent 50%),
  radial-gradient(ellipse at 0% 100%, rgba(0,229,255,0.10), transparent 55%),
  linear-gradient(180deg, rgba(255,255,255,0.05), rgba(255,255,255,0.015));
padding: 24/22/22 (CSS) — Compose target HomeCardPadding (drop the +4 for consistency)
```

Compose translation: `Brush.verticalGradient(0f → GradientPink α 0.10, 0.55f → GradientBlue α 0.04, 1f → glassHighlight)`.

**Headline:** Playfair Black, **CSS 42 px / Compose 38.sp** (use Compose value), line-height 40 sp, letter-spacing -1.2 sp. Two lines: `Race-tier fitness,\nlocked in.` Apply 4-stop gradient via `BlendMode.SrcIn`. The trailing period is solid `GradientPink`.

**Subtitle:** `bodyMedium.copy(13.5.sp, 19.sp)` `text-secondary`.

**Trophy chip:** mono 9.5/700/+1.2 uppercase, padding 4×9, radius 999, leading 9×9 cup glyph. bg `rgba(255,176,32,0.10)`, border `rgba(255,176,32,0.28)`, text `--amber`.

**Stats tile:** `padding 14/10/12, radius 14, text-align center`. Value DM Sans 800 / 30 / -0.8 / `tabular-nums`. Unit mono 9.5/600/+1.2 uppercase tertiary. Top edge has `1px` highlight gradient `linear-gradient(90deg, transparent, rgba(255,255,255,0.18), transparent)` ("minted shine").

**Cardio Health row (Tier-2 ambient):**
- bg `rgba(255,255,255,0.06)`, border `rgba(255,255,255,0.14)`.
- Inner overlay `linear-gradient(135deg, rgba(0,229,255,0.06), transparent 60%)`.
- Icon square 38×38 r12, bg `rgba(0,229,255,0.10)`, border `rgba(0,229,255,0.28)`, ECG/heart-line glyph color cyan.
- Title `Cardio Health` DM Sans 600/14.5 + EVERGREEN pip (mono 9/700/+1, bg `rgba(0,229,255,0.12)`, border `rgba(0,229,255,0.3)`, text cyan).
- Desc `Maintain race fitness. Tier-aware — keeps you at your level.` (11.5 / line-height 1.4 / secondary).
- Trailing chevron 14×14 tertiary.
- Hover: border tints `rgba(0,229,255,0.4)`.

**Race chip:** DM Sans 600 / 11.5, padding `7×0`, radius 10, bg `rgba(255,255,255,0.025)`, border glass-border, text secondary. Hover → text-primary + glass-border-strong.

**Tertiary:** `Just freestyle today →`. Text `bodySmall` tertiary; arrow text-quaternary.

**Decoration:**
- Laurel SVG 220×28, top 18, opacity 0.55 (pink + cyan strokes 0.8α, gold center dot 0.95α `#FFB020`).
- 5 spark dots: s1 pink 4px, s2 cyan 3px, s3 red 5px α0.8, s4 blue 3px, s5 cyan 2px (no glow on s5). All `box-shadow: 0 0 8/6/10/6 currentColor`.

---

### C.2 ResumeCard

Trigger: `enrollment.status == PAUSED`.

Compose surface:
```
ResumeCard(
  enrollment, sessionsDone: Int, sessionsTotal: Int,
  pausedAtWeek: Int, totalWeeks: Int,
  onResume: () -> Unit, onSwitchProgram: () -> Unit, onManualRun: () -> Unit,
  modifier: Modifier = Modifier
)
```

**Vertical composition:**

1. Pause glyph absolute top-right (36×36 circle, two 3×12 rounded bars gap 4, bg `rgba(255,255,255,0.04)`, border glass-border, color `text-secondary`)
2. Eyebrow `PAUSED · WEEK {pausedAtWeek}` — gap 12
3. Headline `Ready when\nyou are.` (★ shipping, SOLID white — no gradient) — gap 8
4. Body `You had {sessionsDone} of {sessionsTotal} sessions complete in the {goalLabel} program. Your place is held — pick up exactly where you left off.` (bolds via `SpanStyle(text-primary, SemiBold)` on `"$sessionsDone of $sessionsTotal"` and `goalLabel`) — gap 22
5. Progress thread (glass card padding 14, label `PROGRESS` + value `{n} / {total} sessions · week {pausedAtWeek} of {totalWeeks}`, then 4-px bar with bright cap) — gap 22
6. Primary CTA `Resume training` (CardeaButton, full-width, height 50, leading PlayArrow) — gap 10
7. Secondary `Start a different program` (OutlinedButton, full-width, height 44) — gap 14
8. Tertiary `Manual run →` (TextButton, centered)

Outside card, 18-px separator, then "WHILE YOU'RE PAUSED" reassurance box (PulseLabel + body `No streak penalty. No nagging. Manual runs still log to History — they just don't count toward this program until you resume.`).

**Card shell:** `padding 22/22/18, radius 24, border glass-border, background glassHighlight`.

**Headline:** Playfair 700, **CSS 32 / Compose 30.sp** (use Compose), line-height 32 sp, letter-spacing -0.5 sp. SOLID `text-primary`.

**Progress bar:** track `rgba(255,255,255,0.06)` h=4 r=2; fill `linear-gradient(90deg, rgba(255,255,255,0.55), rgba(255,255,255,0.85))` width = `sessionsDone / sessionsTotal`, with brighter 8-px cap at right edge `rgba(255,255,255,0.95)`.

**Primary CTA:** CTA gradient (the only Tier-1 element on this card), glow `0 0 30px rgba(255,45,166,0.22)`, color white, `innerPadding 24×14`.

**Secondary CTA:** transparent, `1px glass-border-strong`, radius 14, DM Sans 500 / 13.5 text-primary.

---

### C.3 NoBootcampCard refresh

Trigger: no enrollment OR enrollment deleted.

Compose surface (unchanged): `NoBootcampCard(onSetupBootcamp, onStartRun, modifier)`.

**Vertical composition:**

1. Eyebrow `YOUR PERSONAL RUNNING COACH` — gap 10
2. Gradient headline `Train smarter,\nnot harder.` — gap 8
3. Subtitle `An adaptive plan that knows your week, your heart, and your pace.` (★ replaces existing longer subtitle) — gap 22
4. Feature trio nested glass container (clip + border + bg) — gap 22
   - Padding `horizontal 14, vertical 16`
   - `verticalArrangement spacedBy 14.dp`
   - Container clip `RoundedCornerShape(14.dp)`, `1px glass-border`, bg `Color.White.copy(alpha=0.02f)`
5. Primary CTA `Set up bootcamp` (CardeaButton, full-width)

Outside card: `Spacer(12)` then existing `JustRunStrip` (untouched).

**Card shell:** padding `HomeCardPadding` (drop the existing `+4.dp` — explicit refresh ask).

**Feature trio (Material Icons named here):**

| Title | Description | Icon | Color |
|---|---|---|---|
| HR zone coaching | Live alerts keep you in the right zone. | `FavoriteBorder` | `--grad-red` |
| Life-aware scheduling | Adapts to your week — block days, pick your long run. | `Timer` | `--grad-blue` |
| Voice coaching | Spoken pace, zone, and distance cues, in your ear. | `Mic` | `--grad-cyan` |

Each icon tile: `38×38 r12`, bg `<color>.copy(alpha=0.12f)`.

**Headline:** Playfair 800, CSS 36 px, letter-spacing -0.8. Compose: `titleLarge.copy(fontWeight = ExtraBold)` — actual sp inherits theme.

**Subtitle:** `bodySmall` style, `text-secondary`, max-width 320.

**JustRunStrip (existing, untouched):** bg `rgba(255,255,255,0.025)`, border glass-border, radius 24, padding `12×16`, gap 12. Avatar 32×32 r999 bg `rgba(255,255,255,0.06)`. Title `Just run` 13.5/600 primary. Sub `No plan, no zones — just track it` 11.5 secondary. Trailing chevron `#5A6573`.

---

## D — Cross-state hierarchy

- **Tier-1 gradient placement:** GraduateHero → headline; NoBootcampCard → headline; ResumeCard → CTA. **One Tier-1 element per screen, always.**
- Decoration only on GraduateHero (laurel, sparks, trophy, amber pip).
- Card geometry shared (393 wide, radius 24, status/greeting/tab-bar chrome).
- Tone: Graduate triumphant present-tense; Resume neutral reassuring; NoBootcamp pitch / system-sell.
- Reused primitives: `PulseLabel`, `FeatureItem`, `CardeaButton`, `BlendMode.SrcIn` gradient text trick (per `NoBootcampCard:970-980`).

---

## E — Open questions (resolution decisions)

| # | Issue | Resolution |
|---|---|---|
| 1 | GraduateHero headline 42 px (CSS) vs 38.sp (Compose) | **Use 38.sp** — Compose is closer to truth; sp accounts for accessibility. |
| 2 | ResumeCard headline 32 (CSS) vs 30.sp (Compose) | **Use 30.sp**. |
| 3 | NoBootcamp Compose sp not given | **Use `titleLarge.copy(fontWeight = ExtraBold)`** — inherits theme. |
| 4 | GraduateHero Compose retains `+4.dp` padding while refresh drops it on NoBootcamp | **Drop `+4.dp` on all three** — explicit "feels like the same card family" goal. |
| 5 | `HomeCardPadding`/`HomeCardRadius`/`HomeGutter` literal values | **Read from `ui/theme/`** at impl time; do not redefine. |
| 6 | Trophy chip / Cardio Health pulse-line icons | **Inline `Canvas` SVG paths** in Compose; no Material Icon match. |
| 7 | `RaceGoal.entries` and `goal.shortLabel` | **Confirm enum exists** at impl time; if missing, define inline `enum class RaceGoal(val shortLabel: String) { _5K("5K"), _10K("10K"), HALF("Half"), FULL("Full") }`. |
| 8 | Stats trio empty/zero rendering | **Render zeros as-is** (`0 WEEKS`); designer punted, simplest is correct. |
| 9 | `enrollment.goalType` null/unknown | **Fallback to `"PROGRAM"`** in eyebrow if null/empty after replace. |
| 10 | `--glass-border-strong` (0.14) vs `--glass-border-2` (0.12) | **Use 0.14 (`glass-border-strong`)** for hero outer borders per Hero States file; treat 0.12 as legacy. |
| 11 | `pulse-label` 10/700/+1.5 (hero) vs eyebrow 11/500/+2 (system) | **Use the hero file's `pulse-label`** — it's authoritative for these heroes. |
| 12 | Off-grid spacing (10, 14) | **Honor as-designed** — designer accepted these values for these compositions. |
| 13 | "While you're paused" reassurance card | **Implement** as a separate composable below `ResumeCard`, not inside it. |
| 14 | GraduateHero card border token | **Use `glass-border-strong` (0.14)** matching CSS, not `glassBorder`. |

---

## F — Compose translation cheatsheet

```kotlin
// Theme tokens to add to CardeaTheme.colors if not present:
val gradientCardea: Brush = Brush.linearGradient(
    listOf(GradientRed, GradientPink, GradientBlue, GradientCyan),
    start = Offset(0f, 0f), end = Offset(1f, 1f) // 135deg
)
val gradientCta: Brush = Brush.linearGradient(
    listOf(GradientPink, GradientRed, GradientPink),
    start = Offset(0f, 0f), end = Offset(1f, 1f)
)
val glassBorderStrong = Color(0xFF000000).copy(alpha = 0f) // → rgba(255,255,255,0.14)
// Define as Color.White.copy(alpha = 0.14f)

// Gradient text trick (existing pattern at NoBootcampCard:970-980):
Text(text = "...",
    modifier = Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = heroGradient, blendMode = BlendMode.SrcIn)
        }
)
```
