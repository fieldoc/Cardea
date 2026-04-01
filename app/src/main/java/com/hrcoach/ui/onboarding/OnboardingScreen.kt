package com.hrcoach.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.ui.theme.*
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 8

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToBootcamp: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun advancePage() {
        scope.launch {
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    fun skip() {
        viewModel.completeOnboarding()
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaBgPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip button (hidden on last page)
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, end = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Skip",
                        color = CardeaTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { skip() }
                            .padding(8.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProfilePage(
                        uiState = uiState,
                        onAgeChanged = viewModel::onAgeChanged,
                        onWeightChanged = viewModel::onWeightChanged,
                        onToggleWeightUnit = viewModel::toggleWeightUnit,
                        onHrMaxOverrideChanged = viewModel::onHrMaxOverrideChanged,
                        onToggleHrMaxOverride = viewModel::toggleHrMaxOverride,
                    )
                    2 -> ZonesPage(effectiveHrMax = viewModel.effectiveHrMax())
                    3 -> BlePage(
                        permissionGranted = uiState.bluetoothPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.BLUETOOTH, granted)
                        },
                    )
                    4 -> GpsPage(
                        permissionGranted = uiState.locationPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.LOCATION, granted)
                        },
                    )
                    5 -> AlertsPage(
                        permissionGranted = uiState.notificationPermissionGranted,
                        onPermissionResult = { granted ->
                            viewModel.onPermissionResult(PermissionType.NOTIFICATION, granted)
                        },
                    )
                    6 -> TabTourPage()
                    7 -> LaunchPadPage(
                        onStartBootcamp = {
                            viewModel.completeOnboarding()
                            onNavigateToBootcamp()
                        },
                        onExploreFirst = {
                            viewModel.completeOnboarding()
                            onNavigateToHome()
                        },
                    )
                }
            }

            // Bottom: CTA button + progress dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // CTA button (not on last page — it has its own CTAs)
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    val buttonText = when (pagerState.currentPage) {
                        0 -> "Get Started"
                        1 -> if (viewModel.isAgeValid()) "Next" else "Enter your age to continue"
                        else -> "Next"
                    }
                    val enabled = pagerState.currentPage != 1 || viewModel.isAgeValid()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enabled) CardeaCtaGradient
                                else Brush.linearGradient(listOf(CardeaTextTertiary, CardeaTextTertiary))
                            )
                            .then(
                                if (enabled) Modifier.clickable {
                                    if (pagerState.currentPage == 1) viewModel.saveProfile()
                                    advancePage()
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = buttonText,
                            color = if (enabled) CardeaTextPrimary else CardeaTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    repeat(PAGE_COUNT) { index ->
                        val isActive = index == pagerState.currentPage
                        val width by animateDpAsState(
                            targetValue = if (isActive) 20.dp else 6.dp,
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) CardeaCtaGradient
                                    else Brush.linearGradient(
                                        listOf(CardeaTextTertiary, CardeaTextTertiary)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
