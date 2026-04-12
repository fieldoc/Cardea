package com.hrcoach.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CardeaColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val surfaceVariant: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassSurface: Color,
    val glassFillBrush: Brush,
    val glassElevation: Dp,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val gradient: Brush,
    val ctaGradient: Brush,
    val navGradient: Brush,
    val zoneGreen: Color,
    val zoneAmber: Color,
    val zoneRed: Color,
    val divider: Color,
    val accentPink: Color,
    val onGradient: Color,
    val blackoutBg: Color,
    val blackoutBorder: Color,
    val blackoutText: Color,
    val chartGrid: Color,
    val mapOverlayBg: Color,
    val achievementSlateBorder: Color,
    val achievementSlateBg: Color,
    val achievementSkyBorder: Color,
    val achievementSkyBg: Color,
    val achievementGoldBorder: Color,
    val achievementGoldBg: Color,
    val isDark: Boolean,
)

private val DarkGlassFillBrush = Brush.verticalGradient(
    colors = listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF))
)

private val LightGlassFillBrush = Brush.verticalGradient(
    colors = listOf(Color(0x00000000), Color(0x00000000))
)

val DarkCardeaColors = CardeaColors(
    bgPrimary = CardeaBgPrimary,
    bgSecondary = CardeaBgSecondary,
    surfaceVariant = SurfaceVariant,
    glassBorder = GlassBorder,
    glassHighlight = GlassHighlight,
    glassSurface = GlassSurface,
    glassFillBrush = DarkGlassFillBrush,
    glassElevation = 0.dp,
    textPrimary = CardeaTextPrimary,
    textSecondary = CardeaTextSecondary,
    textTertiary = CardeaTextTertiary,
    gradient = CardeaGradient,
    ctaGradient = CardeaCtaGradient,
    navGradient = CardeaNavGradient,
    zoneGreen = ZoneGreen,
    zoneAmber = ZoneAmber,
    zoneRed = ZoneRed,
    divider = GlassBorder,
    accentPink = CardeaAccentPink,
    onGradient = Color.White,
    blackoutBg = BlackoutBg,
    blackoutBorder = BlackoutBorder,
    blackoutText = BlackoutText,
    chartGrid = ChartGridDark,
    mapOverlayBg = MapOverlayBg,
    achievementSlateBorder = AchievementSlateBorder,
    achievementSlateBg = AchievementSlateBg,
    achievementSkyBorder = AchievementSkyBorder,
    achievementSkyBg = AchievementSkyBg,
    achievementGoldBorder = AchievementGoldBorder,
    achievementGoldBg = AchievementGoldBg,
    isDark = true,
)

val LightCardeaColors = CardeaColors(
    bgPrimary = CardeaLightBgPrimary,
    bgSecondary = CardeaLightBgSecondary,
    surfaceVariant = CardeaLightSurfaceVariant,
    glassBorder = LightGlassBorder,
    glassHighlight = LightGlassHighlight,
    glassSurface = LightGlassSurface,
    glassFillBrush = LightGlassFillBrush,
    glassElevation = 2.dp,
    textPrimary = CardeaLightTextPrimary,
    textSecondary = CardeaLightTextSecondary,
    textTertiary = CardeaLightTextTertiary,
    gradient = CardeaGradient,
    ctaGradient = CardeaCtaGradient,
    navGradient = CardeaNavGradient,
    zoneGreen = ZoneGreen,
    zoneAmber = ZoneAmber,
    zoneRed = ZoneRed,
    divider = LightGlassBorder,
    accentPink = CardeaAccentPink,
    onGradient = Color.White,
    blackoutBg = LightBlackoutBg,
    blackoutBorder = LightBlackoutBorder,
    blackoutText = LightBlackoutText,
    chartGrid = ChartGridLight,
    mapOverlayBg = MapOverlayBg,
    achievementSlateBorder = LightAchievementSlateBorder,
    achievementSlateBg = LightAchievementSlateBg,
    achievementSkyBorder = LightAchievementSkyBorder,
    achievementSkyBg = LightAchievementSkyBg,
    achievementGoldBorder = LightAchievementGoldBorder,
    achievementGoldBg = LightAchievementGoldBg,
    isDark = false,
)

val LocalCardeaColors = staticCompositionLocalOf { DarkCardeaColors }

object CardeaTheme {
    val colors: CardeaColors
        @Composable @ReadOnlyComposable
        get() = LocalCardeaColors.current
}
