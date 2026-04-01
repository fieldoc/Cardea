package com.hrcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.domain.model.ThemeMode
import com.hrcoach.ui.navigation.HrCoachNavGraph
import com.hrcoach.ui.theme.HrCoachTheme
import com.hrcoach.util.MapsApiKeyRuntime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var mapsSettingsRepository: MapsSettingsRepository

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        _themeMode.value = themePreferencesRepository.getThemeMode()

        val mapsApiKey = mapsSettingsRepository.getMapsApiKey()
        MapsApiKeyRuntime.applyIfPresent(this, mapsApiKey)

        // Permissions are now requested contextually during onboarding flow.
        // SetupScreen safety-net permission check remains as fallback.

        setContent {
            val themeMode by _themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HrCoachTheme(darkTheme = isDark) {
                HrCoachNavGraph(
                    windowSizeClass = calculateWindowSizeClass(this),
                    onThemeModeChanged = { mode ->
                        themePreferencesRepository.setThemeMode(mode)
                        _themeMode.value = mode
                    },
                    currentThemeMode = themeMode
                )
            }
        }
    }
}
