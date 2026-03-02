package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Cardea Backgrounds ─────────────────────────────────────
val CardeaBgPrimary   = Color(0xFF0B0F17)
val CardeaBgSecondary = Color(0xFF0F1623)

// ── Cardea Glass Surface ───────────────────────────────────
val GlassBorder    = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)
val GlassHighlight = Color(0x14FFFFFF)   // rgba(255,255,255,0.08)

// ── Cardea Core Gradient stops ─────────────────────────────
// Use EXACT values per spec — do NOT alter stops.
val GradientRed  = Color(0xFFFF5A5F)
val GradientPink = Color(0xFFFF2DA6)
val GradientBlue = Color(0xFF5B5BFF)
val GradientCyan = Color(0xFF00D1FF)

/** Cardea core gradient — 135deg. Used for CTAs, ring, active icons. */
val CardeaGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    )
)

// ── Text colors ────────────────────────────────────────────
val CardeaTextPrimary   = Color(0xFFFFFFFF)
val CardeaTextSecondary = Color(0xFF9AA4B2)
val CardeaTextTertiary  = Color(0xFF5A6573)

// ── Zone colors (preserved) ────────────────────────────────
val ZoneGreen = Color(0xFF34D399)
val ZoneAmber = Color(0xFFF59E0B)
val ZoneRed   = Color(0xFFEF4444)

// ── Legacy aliases — keeps existing screen files compiling ──
val Background     = CardeaBgPrimary
val Surface        = Color(0xFF0F1623)
val SurfaceVariant = Color(0xFF131921)
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
