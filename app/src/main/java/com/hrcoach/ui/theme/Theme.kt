package com.hrcoach.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HrCoachDarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = ZoneOrange,
    tertiary = ZoneGreen,
    error = ZoneRed,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    outline = DividerColor
)

private val HrCoachLightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = ZoneOrange,
    tertiary = ZoneGreen,
    error = ZoneRed,
    onPrimary = OnPrimary,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE7EBF2),
    outline = Color(0xFF6C7480)
)

@Composable
fun HrCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> HrCoachDarkColorScheme
        else -> HrCoachLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HrCoachTypography,
        content = content
    )
}
