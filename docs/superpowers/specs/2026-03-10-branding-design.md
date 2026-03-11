# Branding Design Spec: Logo · Launcher · Splash
Date: 2026-03-10

## Concept

The Cardea logo is a **running route that traces a heart**. Two thick parallel strokes form the edges of a road; the road bends into a heart shape, with a hairpin turn on the left lobe. The metaphor: your run traces a heart. Premium restraint — one symbol, no noise.

---

## 1. Logo Shape (`CardeaLogo.kt`)

### Geometry
Two `Path` objects drawn with a thick stroke, no fill:

**Path A — Outer heart:**
A smooth rounded heart curve from apex to bottom point. Bezier control points tuned for a wide, athletic silhouette (not the narrow/geometric current version).

**Path B — Hairpin (left lobe inner road edge):**
A smaller curve that enters from the apex, loops around the inside of the left lobe in a hairpin, and exits back toward center. This creates the "road crossover" — the visual cue that the path folds back on itself like a real running route.

### Stroke treatment
- **Stroke width**: ~8% of the logo size (e.g. 8dp at 100dp)
- **Cap**: `StrokeCap.Round`
- **Join**: `StrokeJoin.Round`
- **Glow layer**: same path at ~2× stroke width, `alpha = 0.20`, with `BlurMaskFilter` (radius ~15% of size). Drawn underneath.

### Gradient
`Brush.linearGradient` from top-left to bottom-right:
```
0.00  →  #FF2D55  (GradientRed)
0.35  →  #E040A0  (GradientPink)
0.65  →  #3A6FFF  (GradientBlue)
1.00  →  #00D4FF  (GradientCyan)
```
Same stops as the existing `CardeaGradient`. Do not change these.

### Size-responsive behaviour
| Size | Paths drawn |
|------|-------------|
| ≥ 40dp | Both paths (outer heart + hairpin) |
| < 40dp | Outer heart only (hairpin becomes noise at small sizes) |

This threshold applies to the **28dp** usage in the Home header avatar — it renders only the outer heart with glow.

### Animation modes
Controlled by existing `animate: Boolean` parameter. Two behaviours:

**`animate = true` (splash screen only):**
1. **Draw-on phase** (~1.8s): Both paths trace themselves from start to finish via `PathEffect.dashPathEffect`, animating phase offset from `pathLength → 0`. Easing: `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`. Start with a tiny visible leading dot so the first frame isn't blank.
2. **Pulse phase** (after draw completes, infinite): Glow layer alpha animates `0.18 → 0.36 → 0.18` with a 2.5s `tween`, `RepeatMode.Reverse`. The stroke itself stays fully opaque.

**`animate = false` (all other usages — Home header, Account):**
Static. No animation. Glow layer drawn at fixed `alpha = 0.20`.

### Path measurement note
Path length for the dash animation must be measured at draw time via `PathMeasure`. Cache the result — it only needs to be computed once per size. The two paths have different lengths; animate them with separate `animateFloat` values sharing the same `InfiniteTransition`.

---

## 2. App Launcher Icon

### Format
Android Adaptive Icon (`res/mipmap-anydpi-v26/ic_launcher.xml`):
- **Background**: vector drawable, solid dark gradient (`#0d0d14` to `#13131f` radial, or flat `#0d0d14`)
- **Foreground**: vector drawable of the logo heart (Path A + Path B), centered in the safe zone (66dp of the 108dp foreground canvas)
- **Shape mask**: circle (the Android system applies the mask; no change needed in XML)

### Foreground vector
Convert the Compose `Path` bezier curves to `<path android:pathData="...">` format. Two path elements:
1. `heart_outer` — gradient fill via `<aapt:attr>` with the four-stop `linearGradient`
2. `heart_hairpin` — same gradient
Both use `strokeWidth="7"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, `fillColor="@android:color/transparent"`.

### Legacy icon
`res/mipmap-*/ic_launcher.png` — rasterize the foreground vector at each density bucket for pre-API-26 devices.

---

## 3. Splash Screen (`SplashScreen.kt`)

### Layout
```
Box(fillMaxSize, background = radial CardeaBgSecondary→CardeaBgPrimary)
  └─ Column(center, Alignment.CenterHorizontally)
       ├─ CardeaLogo(size=110dp, animate=true)
       ├─ Spacer(36dp)
       ├─ Text "CARDEA"            ← fade-up reveal
       └─ Text "HEART-LED PERFORMANCE"  ← fade-up reveal (delayed)
```

### Typography
- **"CARDEA"**: `displaySmall`, `FontWeight.Black`, `letterSpacing = 8sp`, `Color.White`
- **"HEART-LED PERFORMANCE"**: `labelMedium`, `FontWeight.Bold`, `letterSpacing = 2.5sp`, `CardeaTextSecondary.copy(alpha = 0.55f)`

### Reveal animation
- `contentAlpha` starts at `0f`, animates to `1f` over `700ms` with `LinearOutSlowInEasing`, triggered by `LaunchedEffect(Unit)` with a `300ms` delay after screen composition (gives the logo draw-on animation time to start first).
- Wordmark and tagline share the same alpha. Optionally offset tagline by an additional 200ms for a subtle stagger (one `animateFloatAsState` each).

### Timing
`LaunchedEffect` delays `onFinished()` call by `2400ms` — enough for draw-on (1.8s) + wordmark reveal + one full pulse cycle.

### Background
Keep existing `Brush.radialGradient(CardeaBgSecondary, CardeaBgPrimary)`. Remove the secondary ambient glow `Box` (it adds noise without benefit now that the logo has its own bloom).

---

## What does NOT change
- `CardeaGradient` color stops — unchanged
- `GlassCard`, button styles, zone colors — all unaffected
- Navigation, ViewModels, data layer — no changes
- Splash timing contract (`onFinished` callback) — unchanged
- `animate` parameter API on `CardeaLogo` — unchanged
