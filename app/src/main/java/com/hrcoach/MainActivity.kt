package com.hrcoach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.hrcoach.util.PermissionGate
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            val permanentlyDenied = denied.any { perm ->
                !shouldShowRequestPermissionRationale(perm)
            }
            if (permanentlyDenied) {
                Toast.makeText(
                    this,
                    "Permissions required for workouts. Tap to open Settings.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            } else {
                val names = denied.joinToString { PermissionGate.describePermission(it) }
                Toast.makeText(this, "Denied: $names", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        _themeMode.value = themePreferencesRepository.getThemeMode()

        val mapsApiKey = mapsSettingsRepository.getMapsApiKey()
        MapsApiKeyRuntime.applyIfPresent(this, mapsApiKey)

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

        val missing = PermissionGate.missingRuntimePermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
