package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Cardea Backgrounds ─────────────────────────────────────
// Shift from Navy to Deep Matte Black for an athletic, high-performance feel.
val CardeaBgPrimary   = Color(0xFF050505) // Deepest Matte Black
val CardeaBgSecondary = Color(0xFF0D0D0D) // Elevated Matte Black

// ── Cardea Glass Surface ───────────────────────────────────
// Neutralized glass effects to pop against pure black.
val GlassBorder    = Color(0x26FFFFFF)   // 15% white — visible card edges on OLED
val GlassHighlight = Color(0x0AFFFFFF)   // Subtle highlight
val GlassSurface   = Color(0x33FFFFFF)   // 0.20 alpha — interactive glass overlay

// ── Cardea Core Gradient stops ─────────────────────────────
// Refined to be high-energy but more cohesive (less rainbow-y).
val GradientRed  = Color(0xFFFF4D5A) // More vibrant
val GradientPink = Color(0xFFFF2DA6)
val GradientBlue = Color(0xFF4D61FF) // Slightly more saturated
val GradientCyan = Color(0xFF00E5FF) // Electric Cyan

/** Cardea core gradient — 135deg. Used for main CTAs and rings. */
val CardeaGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    )
)

/** Two-stop CTA gradient (Red → Pink). Used on buttons and action chips. */
val CardeaCtaGradient = Brush.linearGradient(listOf(GradientRed, GradientPink))

/** Focused Navigation Gradient — cooler colors for less visual noise. */
val CardeaNavGradient = Brush.linearGradient(
    colors = listOf(GradientBlue, GradientCyan)
)

// ── Text colors ────────────────────────────────────────────
val CardeaTextPrimary   = Color(0xFFFFFFFF)
val CardeaTextSecondary = Color(0xFFA1A1AA) // Neutral Gray
val CardeaTextTertiary  = Color(0xFF6B6B73) // Neutral Gray — min 3.5:1 on #050505 (WCAG AA)

// ── Zone colors (Athletic Neon) ────────────────────────────
val ZoneGreen = Color(0xFF22C55E) // Sharp Emerald
val ZoneAmber = Color(0xFFFACC15) // High-vis Yellow/Amber
val ZoneRed   = Color(0xFFEF4444) // Performance Red

// ── Achievement prestige colors ──────────────────────────
val AchievementSlate       = Color(0xFF94A3B8)
val AchievementSlateBorder = Color(0x2694A3B8)    // 15% opacity
val AchievementSlateBg     = Color(0x1494A3B8)    // 8% opacity

val AchievementSky         = Color(0xFF7DD3FC)
val AchievementSkyBorder   = Color(0x407DD3FC)    // 25% opacity
val AchievementSkyBg       = Color(0x147DD3FC)    // 8% opacity

val AchievementGold        = Color(0xFFFACC15)
val AchievementGoldBorder  = Color(0x4DFACC15)    // 30% opacity
val AchievementGoldBg      = Color(0x1FFACC15)    // 12% opacity

// ── Light-mode palette values ────────────────────────────────
val CardeaLightBgPrimary    = Color(0xFFFAFAFA)
val CardeaLightBgSecondary  = Color(0xFFF0F0F2)
val CardeaLightSurfaceVariant = Color(0xFFE8E8EC)

val LightGlassBorder    = Color(0x1A000000)
val LightGlassHighlight = Color(0x0A000000)
val LightGlassSurface   = Color(0x0F000000)

val CardeaLightTextPrimary   = Color(0xFF1A1A1A)
val CardeaLightTextSecondary = Color(0xFF6B6B73)
val CardeaLightTextTertiary  = Color(0xFFA1A1AA)

val LightAchievementSlateBorder = Color(0x3394A3B8)
val LightAchievementSlateBg     = Color(0x1A94A3B8)
val LightAchievementSkyBorder   = Color(0x4D7DD3FC)
val LightAchievementSkyBg       = Color(0x1A7DD3FC)
val LightAchievementGoldBorder  = Color(0x59FACC15)
val LightAchievementGoldBg      = Color(0x24FACC15)

val CardeaAccentPink = Color(0xFFFF6B8A)

val BlackoutBg     = Color(0xFF1C1F26)
val BlackoutBorder = Color(0xFF3D2020)
val BlackoutText   = Color(0xFF8B3A3A)

val LightBlackoutBg     = Color(0xFFE8E0E0)
val LightBlackoutBorder = Color(0xFFD4A0A0)
val LightBlackoutText   = Color(0xFF8B3A3A)

// ── Chart 5-zone palette (extends the 3-zone Green/Amber/Red) ──
val ChartZoneEasy     = Color(0xFF34D399) // Teal-green — distinct from ZoneGreen
val ChartZoneModerate = Color(0xFF4F8EF7) // Soft blue
val ChartZoneHard     = Color(0xFFF59E0B) // Orange-amber — distinct from ZoneAmber

// ── Session-type ambient tints ──────────────────────────────────
val SessionTintCool   = Color(0xFF0EA5E9) // Sky-teal (Easy/Strides)
val SessionTintDeep   = Color(0xFF6366F1) // Deep indigo (Long)

// ── Social / partner accent ─────────────────────────────────────
// Muted sky-teal for partner/social surfaces. Distinct from ZoneGreen
// (reserved for HR zones / AllCaughtUp) and from the warm-pink CTA.
val PartnerTeal = Color(0xFF38BDF8) // Tailwind sky-400 — readable on #050505, alive but not electric

val ChartGridDark  = Color(0x0AFFFFFF)
val ChartGridLight = Color(0x14000000)

val MapOverlayBg = Color(0x99000000)

// ── Legacy aliases — keeps existing screen files compiling ──
val Background     = CardeaBgPrimary
val Surface        = CardeaBgSecondary
val SurfaceVariant = Color(0xFF18181B) // Slightly lighter matte gray for variants
val Primary        = GradientBlue
val PrimaryVariant = GradientPink
val OnPrimary      = CardeaTextPrimary
val OnBackground   = CardeaTextPrimary
val OnSurface      = CardeaTextPrimary
val SubtleText     = CardeaTextSecondary   // referenced by Type.kt
val DividerColor   = GlassBorder
val DisabledGray   = CardeaTextTertiary
val HeatmapGreen   = ZoneGreen
val HeatmapYellow  = ZoneAmber
val HeatmapRed     = ZoneRed
val ProgressGreen  = ZoneGreen
val ProgressAmber  = ZoneAmber
val ThresholdLine  = CardeaTextTertiary
