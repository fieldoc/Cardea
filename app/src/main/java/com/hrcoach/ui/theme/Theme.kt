package com.hrcoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    surface          = Surface,
    onSurface        = CardeaTextPrimary,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = CardeaTextSecondary,
    outline          = GlassBorder,
    outlineVariant   = GlassHighlight
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

// Backward-compat alias — existing imports of HrCoachThemeTokens continue to work.
typealias HrCoachThemeTokens = CardeaThemeTokens

@Composable
fun CardeaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlassBorder    provides GlassBorder,
        LocalSubtleText     provides CardeaTextSecondary,
        LocalCardeaGradient provides CardeaGradient
    ) {
        MaterialTheme(
            colorScheme = CardeaDarkColorScheme,
            typography  = HrCoachTypography,
            content     = content
        )
    }
}

// Backward-compat wrapper — MainActivity calls HrCoachTheme { } and still compiles.
@Composable
fun HrCoachTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CardeaTheme(content)
