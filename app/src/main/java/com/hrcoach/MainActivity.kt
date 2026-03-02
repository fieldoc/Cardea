package com.hrcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.ui.navigation.HrCoachNavGraph
import com.hrcoach.ui.theme.HrCoachTheme
import com.hrcoach.util.MapsApiKeyRuntime
import com.hrcoach.util.PermissionGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var mapsSettingsRepository: MapsSettingsRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // UI and service layer should handle denied permissions gracefully.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mapsApiKey = mapsSettingsRepository.getMapsApiKey()
        MapsApiKeyRuntime.applyIfPresent(this, mapsApiKey)

        val missingPermissions = PermissionGate.missingRuntimePermissions(this)

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            HrCoachTheme {
                HrCoachNavGraph(
                    windowSizeClass = calculateWindowSizeClass(this)
                )
            }
        }
    }
}
