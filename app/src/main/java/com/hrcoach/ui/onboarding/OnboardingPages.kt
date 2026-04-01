package com.hrcoach.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable fun WelcomePage() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Welcome") } }
@Composable fun ProfilePage(uiState: OnboardingUiState, onAgeChanged: (String) -> Unit, onWeightChanged: (String) -> Unit, onToggleWeightUnit: () -> Unit, onHrMaxOverrideChanged: (String) -> Unit, onToggleHrMaxOverride: () -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Profile") } }
@Composable fun ZonesPage(effectiveHrMax: Int?) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Zones") } }
@Composable fun BlePage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("BLE") } }
@Composable fun GpsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("GPS") } }
@Composable fun AlertsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Alerts") } }
@Composable fun TabTourPage() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Tab Tour") } }
@Composable fun LaunchPadPage(onStartBootcamp: () -> Unit, onExploreFirst: () -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Launch Pad") } }
