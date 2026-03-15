package com.hrcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val CardeaDarkColorScheme = darkColorScheme(
    primary          = GradientBlue,
    secondary        = ZoneAmber,
    tertiary         = ZoneGreen,
    error            = ZoneRed,
    onPrimary        = CardeaTextPrimary,
    background       = CardeaBgPrimary,
    onBackground     = CardeaTextPrimary,
    surface          = CardeaBgSecondary,
    onSurface        = CardeaTextPrimary,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = CardeaTextSecondary,
    outline          = GlassBorder,
    outlineVariant   = GlassHighlight
)

private val CardeaLightColorScheme = lightColorScheme(
    primary          = GradientBlue,
    secondary        = ZoneAmber,
    tertiary         = ZoneGreen,
    error            = ZoneRed,
    onPrimary        = Color.White,
    background       = CardeaLightBgPrimary,
    onBackground     = CardeaLightTextPrimary,
    surface          = CardeaLightBgSecondary,
    onSurface        = CardeaLightTextPrimary,
    surfaceVariant   = CardeaLightSurfaceVariant,
    onSurfaceVariant = CardeaLightTextSecondary,
    outline          = LightGlassBorder,
    outlineVariant   = LightGlassHighlight
)

val LocalGlassBorder     = staticCompositionLocalOf { GlassBorder }
val LocalSubtleText      = staticCompositionLocalOf { CardeaTextSecondary }
val LocalCardeaGradient  = staticCompositionLocalOf<Brush> { CardeaGradient }

object CardeaThemeTokens {
    val glassBorder: Color
        @Composable @ReadOnlyComposable
        get() = LocalGlassBorder.current

    val subtleText: Color
        @Composable @ReadOnlyComposable
        get() = LocalSubtleText.current

    val gradient: Brush
        @Composable @ReadOnlyComposable
        get() = LocalCardeaGradient.current
}

typealias HrCoachThemeTokens = CardeaThemeTokens

@Composable
fun CardeaTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDarkTheme) DarkCardeaColors else LightCardeaColors
    val materialScheme = if (isDarkTheme) CardeaDarkColorScheme else CardeaLightColorScheme

    CompositionLocalProvider(
        LocalCardeaColors   provides colors,
        LocalGlassBorder    provides colors.glassBorder,
        LocalSubtleText     provides colors.textSecondary,
        LocalCardeaGradient provides colors.gradient
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography  = HrCoachTypography,
            content     = content
        )
    }
}

@Composable
fun HrCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CardeaTheme(isDarkTheme = darkTheme, content = content)
