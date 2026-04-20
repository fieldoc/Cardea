package com.hrcoach.ui.navigation

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.WorkoutState
import com.hrcoach.service.simulation.SimulationController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.hrcoach.ui.account.AccountScreen
import com.hrcoach.ui.bootcamp.BootcampScreen
import com.hrcoach.ui.bootcamp.BootcampStatusViewModel
import com.hrcoach.ui.history.HistoryDetailScreen
import com.hrcoach.ui.history.HistoryListScreen
import com.hrcoach.ui.home.HomeScreen
import com.hrcoach.ui.postrun.PostRunSummaryScreen
import com.hrcoach.ui.postrun.PostRunSummaryViewModel
import com.hrcoach.ui.progress.ProgressScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.onboarding.OnboardingSplashViewModel
import com.hrcoach.ui.onboarding.OnboardingScreen
import com.hrcoach.ui.splash.SplashScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hrcoach.domain.model.ThemeMode
import com.hrcoach.ui.theme.CardeaNavGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.DarkCardeaColors
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.LocalCardeaColors
import androidx.compose.runtime.CompositionLocalProvider
import com.hrcoach.ui.debug.DebugSimulationScreen
import com.hrcoach.ui.workout.ActiveWorkoutScreen
import com.hrcoach.util.PermissionGate

private const val NavDurationMs = 300

object Routes {
    const val SPLASH           = "splash"
    const val HOME             = "home"
    const val SETUP            = "setup"
    const val WORKOUT          = "workout"
    const val PROGRESS         = "progress"
    const val HISTORY          = "history"
    const val ACCOUNT          = "account"
    const val BOOTCAMP         = "bootcamp"
    const val SIMULATION        = "simulation"
    const val ONBOARDING        = "onboarding"
    const val BOOTCAMP_SETTINGS = "bootcamp_settings"
    const val SOUND_LIBRARY    = "sound_library"
    const val HISTORY_DETAIL   = "history/{workoutId}"
    const val POST_RUN_SUMMARY = "postrun/{workoutId}?fresh={fresh}"

    fun historyDetail(workoutId: Long): String = "history/$workoutId"
    fun postRunSummary(workoutId: Long, fresh: Boolean = false): String = "postrun/$workoutId?fresh=$fresh"
}

@Composable
fun HrCoachNavGraph(
    windowSizeClass: WindowSizeClass,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    data class NavWorkoutState(val isRunning: Boolean, val completedId: Long?, val isPaused: Boolean)
    val navState by remember {
        WorkoutState.snapshot
            .map { NavWorkoutState(it.isRunning, it.completedWorkoutId, it.isPaused) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(NavWorkoutState(false, null, false))
    val isWorkoutRunning = navState.isRunning
    val completedWorkoutId = navState.completedId
    val isWideLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // Lightweight enrollment check — drives Training tab adaptive routing
    val enrollmentVm: BootcampStatusViewModel = hiltViewModel()
    val isBootcampEnrolled by enrollmentVm.hasActiveEnrollment.collectAsStateWithLifecycle()

    LaunchedEffect(isWorkoutRunning, completedWorkoutId) {
        val routeNow = navController.currentBackStackEntry?.destination?.route
        if (isWorkoutRunning && routeNow != Routes.WORKOUT) {
            navController.navigate(Routes.WORKOUT) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        if (!isWorkoutRunning && routeNow == Routes.WORKOUT) {
            val finishedWorkoutId = completedWorkoutId
            if (finishedWorkoutId != null) {
                navController.navigate(Routes.postRunSummary(finishedWorkoutId, fresh = true)) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Routes.SETUP) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // Activity tab covers both History and Progress routes
    val tabRoutes = setOf(Routes.HOME, Routes.SETUP, Routes.BOOTCAMP, Routes.PROGRESS, Routes.HISTORY, Routes.ACCOUNT)
    val showBottomBar = !isWorkoutRunning && currentRoute in tabRoutes && currentRoute != Routes.ONBOARDING

    Scaffold(
        containerColor = CardeaTheme.colors.bgPrimary,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring()
                ) + fadeIn(animationSpec = tween(NavDurationMs)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = spring()
                ) + fadeOut(animationSpec = tween(NavDurationMs))
            ) {
                val navBorderColor = CardeaTheme.colors.glassBorder
                NavigationBar(
                    containerColor = CardeaTheme.colors.bgPrimary,
                    tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = navBorderColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    data class NavItem(
                        val route: String,
                        val icon: ImageVector,
                        val labelRes: Int,
                        val isSelected: (String?) -> Boolean = { it == route }
                    )

                    // Training tab target depends on enrollment state
                    val trainingRoute = if (isBootcampEnrolled) Routes.BOOTCAMP else Routes.SETUP

                    val navItems = listOf(
                        NavItem(Routes.HOME,    Icons.Default.Home,                  R.string.nav_home),
                        NavItem(
                            route = trainingRoute,
                            icon = Icons.Default.FavoriteBorder,
                            labelRes = R.string.nav_workout,
                            isSelected = { it == Routes.SETUP || it == Routes.BOOTCAMP }
                        ),
                        NavItem(
                            route = Routes.HISTORY,
                            icon = Icons.AutoMirrored.Filled.List,
                            labelRes = R.string.nav_history,
                            isSelected = { it == Routes.HISTORY || it == Routes.PROGRESS }
                        ),
                        NavItem(Routes.ACCOUNT, Icons.Default.Person, R.string.nav_account)
                    )
                    navItems.forEach { navItem ->
                        val selected = navItem.isSelected(currentRoute)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(navItem.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                GradientNavIcon(
                                    imageVector = navItem.icon,
                                    isSelected = selected,
                                    contentDescription = stringResource(navItem.labelRes)
                                )
                            },
                            label = { Text(stringResource(navItem.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = Color.Unspecified,
                                unselectedIconColor = CardeaTheme.colors.textSecondary,
                                selectedTextColor   = GradientPink,
                                unselectedTextColor = CardeaTheme.colors.textSecondary,
                                indicatorColor      = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(
                route = Routes.SPLASH,
                enterTransition = { fadeIn(animationSpec = tween(NavDurationMs)) },
                exitTransition = {
                    scaleOut(targetScale = 1.03f, animationSpec = tween(NavDurationMs)) +
                        fadeOut(animationSpec = tween(NavDurationMs))
                }
            ) {
                val splashVm: OnboardingSplashViewModel = hiltViewModel()
                var destination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val completed = splashVm.onboardingRepository.autoCompleteForExistingUser()
                    // Await restore before navigating — keeps splash visible during restore
                    splashVm.checkAndRestoreIfNeeded()
                    destination = if (completed) Routes.HOME else Routes.ONBOARDING
                }

                SplashScreen(
                    onFinished = {
                        val dest = destination ?: Routes.ONBOARDING
                        navController.navigate(dest) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.ONBOARDING,
                enterTransition = { fadeIn(animationSpec = tween(NavDurationMs)) },
                exitTransition = { fadeOut(animationSpec = tween(NavDurationMs)) },
            ) {
                OnboardingScreen(
                    onNavigateToHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBootcamp = {
                        navController.navigate(Routes.BOOTCAMP) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.HOME,
                enterTransition = { defaultEnter(-1) },
                exitTransition = { defaultExit(-1) }
            ) {
                HomeScreen(
                    onStartRun = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onGoToProgress = {
                        navController.navigate(Routes.PROGRESS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGoToHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGoToAccount = {
                        navController.navigate(Routes.ACCOUNT) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGoToBootcamp = {
                        navController.navigate(Routes.BOOTCAMP) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGoToWorkout = {
                        navController.navigate(Routes.WORKOUT) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.SETUP,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                SetupScreen(
                    isWideLayout = isWideLayout,
                    onStartWorkout = { configJson, deviceAddress ->
                        if (!PermissionGate.hasAllRuntimePermissions(context) && !SimulationController.isActive) {
                            Toast.makeText(
                                context,
                                "Grant required permissions before starting workout.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_START
                                putExtra(WorkoutForegroundService.EXTRA_CONFIG_JSON, configJson)
                                putExtra(WorkoutForegroundService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                            }
                            runCatching {
                                context.startForegroundService(intent)
                            }.onFailure {
                                Log.e("NavGraph", "Failed to start workout service", it)
                                val msg = if (it is ForegroundServiceStartNotAllowedException) {
                                    "Cannot start workout in background. Open the app and try again."
                                } else {
                                    "Unable to start workout."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onGoToBootcamp = {
                        navController.navigate(Routes.BOOTCAMP) {
                            launchSingleTop = true
                        }
                    },
                    onGoToSoundLibrary = {
                        navController.navigate(Routes.SOUND_LIBRARY) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.WORKOUT,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                CompositionLocalProvider(
                    LocalCardeaColors provides DarkCardeaColors
                ) {
                    ActiveWorkoutScreen(
                        onPauseResume = {
                            val action = if (navState.isPaused) {
                                WorkoutForegroundService.ACTION_RESUME
                            } else {
                                WorkoutForegroundService.ACTION_PAUSE
                            }
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                this.action = action
                            }
                            context.startService(intent)
                        },
                        onStopConfirmed = {
                            val stopIntent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                        },
                        onConnectHr = {
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_RESCAN_BLE
                            }
                            context.startService(intent)
                        },
                        onToggleAutoPause = {
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_TOGGLE_AUTO_PAUSE
                            }
                            context.startService(intent)
                        }
                    )
                }
            }

            composable(
                route = Routes.PROGRESS,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                ProgressScreen(
                    onStartWorkout = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onGoToLog = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HISTORY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.HISTORY,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                HistoryListScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate(Routes.historyDetail(workoutId))
                    },
                    onStartWorkout = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onGoToTrends = {
                        navController.navigate(Routes.PROGRESS) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.ACCOUNT,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                AccountScreen(
                    onThemeModeChanged = onThemeModeChanged,
                    currentThemeMode = currentThemeMode,
                    onNavigateToSimulation = {
                        navController.navigate(Routes.SIMULATION) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSoundLibrary = {
                        navController.navigate(Routes.SOUND_LIBRARY) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.SOUND_LIBRARY,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                com.hrcoach.ui.account.SoundLibraryScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.BOOTCAMP,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) { backStackEntry ->
                val isTabRoot = navController.previousBackStackEntry?.destination?.route == null ||
                    navController.previousBackStackEntry?.destination?.route == Routes.HOME
                BootcampScreen(
                    onStartWorkout = { configJson, deviceAddress ->
                        if (!PermissionGate.hasAllRuntimePermissions(context) && !SimulationController.isActive) {
                            Toast.makeText(
                                context,
                                "Grant required permissions before starting workout.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_START
                                putExtra(WorkoutForegroundService.EXTRA_CONFIG_JSON, configJson)
                                putExtra(WorkoutForegroundService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                            }
                            runCatching {
                                context.startForegroundService(intent)
                            }.onFailure {
                                Log.e("NavGraph", "Failed to start workout service", it)
                                val msg = if (it is ForegroundServiceStartNotAllowedException) {
                                    "Cannot start workout in background. Open the app and try again."
                                } else {
                                    "Unable to start workout."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onGoToSettings = { navController.navigate(Routes.BOOTCAMP_SETTINGS) },
                    onGoToManualSetup = {
                        navController.navigate(Routes.SETUP) { launchSingleTop = true }
                    },
                    onGoToSoundLibrary = {
                        navController.navigate(Routes.SOUND_LIBRARY) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.BOOTCAMP_SETTINGS,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                com.hrcoach.ui.bootcamp.BootcampSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SIMULATION,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                DebugSimulationScreen(
                    onNavigateToSetup = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                HistoryDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() },
                    onOpenMapsSetup = {
                        navController.navigate(Routes.ACCOUNT) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onViewProgress = {
                        navController.navigate(Routes.PROGRESS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onViewPostRunSummary = {
                        navController.navigate(Routes.postRunSummary(workoutId, fresh = false)) {
                            launchSingleTop = true
                        }
                    },
                    onDeleteWorkout = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.POST_RUN_SUMMARY,
                arguments = listOf(
                    navArgument("workoutId") { type = NavType.LongType },
                    navArgument("fresh") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                val postRunViewModel: PostRunSummaryViewModel = hiltViewModel()
                val postRunState by postRunViewModel.uiState.collectAsStateWithLifecycle()
                PostRunSummaryScreen(
                    workoutId = workoutId,
                    onViewHistory = {
                        navController.navigate(Routes.historyDetail(workoutId)) {
                            popUpTo(Routes.HISTORY) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onDone = {
                        if (postRunState.isBootcampRun) {
                            navController.navigate(Routes.BOOTCAMP) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(Routes.historyDetail(workoutId)) {
                                popUpTo(Routes.HISTORY) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    viewModel = postRunViewModel
                )
            }
        }
    }
}

@Composable
private fun GradientNavIcon(
    imageVector: ImageVector,
    isSelected: Boolean,
    contentDescription: String?
) {
    if (isSelected) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = CardeaNavGradient, blendMode = BlendMode.SrcIn)
                }
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = CardeaTheme.colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = CardeaTheme.colors.textSecondary
        )
    }
}

private fun defaultEnter(direction: Int): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { full -> direction * full / 3 },
        animationSpec = tween(NavDurationMs)
    ) + fadeIn(animationSpec = tween(NavDurationMs))
}

private fun defaultExit(direction: Int): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { full -> direction * full / 4 },
        animationSpec = tween(NavDurationMs)
    ) + fadeOut(animationSpec = tween(NavDurationMs))
}
