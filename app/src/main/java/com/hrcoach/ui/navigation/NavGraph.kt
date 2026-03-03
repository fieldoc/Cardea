package com.hrcoach.ui.navigation

import android.content.Intent
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hrcoach.R
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.account.AccountScreen
import com.hrcoach.ui.history.HistoryDetailScreen
import com.hrcoach.ui.history.HistoryListScreen
import com.hrcoach.ui.home.HomeScreen
import com.hrcoach.ui.postrun.PostRunSummaryScreen
import com.hrcoach.ui.progress.ProgressScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.splash.SplashScreen
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GradientBlue
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
    const val HISTORY_DETAIL   = "history/{workoutId}"
    const val POST_RUN_SUMMARY = "postrun/{workoutId}"

    fun historyDetail(workoutId: Long): String = "history/$workoutId"
    fun postRunSummary(workoutId: Long): String = "postrun/$workoutId"
}

@Composable
fun HrCoachNavGraph(
    windowSizeClass: WindowSizeClass
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val workoutSnapshot by WorkoutState.snapshot.collectAsState()
    val isWorkoutRunning = workoutSnapshot.isRunning
    val completedWorkoutId = workoutSnapshot.completedWorkoutId
    val isWideLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

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
                navController.navigate(Routes.postRunSummary(finishedWorkoutId)) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
                WorkoutState.clearCompletedWorkoutId()
            } else {
                navController.navigate(Routes.SETUP) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    val tabRoutes = setOf(Routes.HOME, Routes.SETUP, Routes.PROGRESS, Routes.HISTORY, Routes.ACCOUNT)
    val showBottomBar = !isWorkoutRunning && currentRoute in tabRoutes

    Scaffold(
        containerColor = CardeaBgPrimary,
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
                NavigationBar(
                    containerColor = CardeaBgPrimary,
                    tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = GlassBorder,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    data class NavItem(val route: String, val icon: ImageVector, val labelRes: Int)
                    val navItems = listOf(
                        NavItem(Routes.HOME,     Icons.Default.Home,                  R.string.nav_home),
                        NavItem(Routes.SETUP,    Icons.Default.FavoriteBorder,        R.string.nav_workout),
                        NavItem(Routes.HISTORY,  Icons.AutoMirrored.Filled.List,      R.string.nav_history),
                        NavItem(Routes.PROGRESS, Icons.AutoMirrored.Filled.ShowChart, R.string.nav_progress),
                        NavItem(Routes.ACCOUNT,  Icons.Default.Person,               R.string.nav_account)
                    )
                    navItems.forEach { (route, icon, labelRes) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                GradientNavIcon(
                                    imageVector = icon,
                                    isSelected = selected,
                                    contentDescription = stringResource(labelRes)
                                )
                            },
                            label = { Text(stringResource(labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = Color.Unspecified,
                                unselectedIconColor = CardeaTextSecondary,
                                selectedTextColor   = GradientBlue,
                                unselectedTextColor = CardeaTextSecondary,
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
                SplashScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
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
                        }
                    },
                    onGoToHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onGoToAccount = {
                        navController.navigate(Routes.ACCOUNT) {
                            popUpTo(Routes.HOME) { saveState = true }
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
                        if (!PermissionGate.hasAllRuntimePermissions(context)) {
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
                                Toast.makeText(context, "Unable to start workout.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            composable(
                route = Routes.WORKOUT,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                ActiveWorkoutScreen(
                    onPauseResume = {
                        val action = if (workoutSnapshot.isPaused) {
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
                    }
                )
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
                    }
                )
            }

            composable(
                route = Routes.ACCOUNT,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                AccountScreen()
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
                        navController.navigate(Routes.postRunSummary(workoutId)) {
                            launchSingleTop = true
                        }
                    },
                    onDeleteWorkout = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.POST_RUN_SUMMARY,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                PostRunSummaryScreen(
                    workoutId = workoutId,
                    onViewProgress = {
                        navController.navigate(Routes.PROGRESS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onViewHistory = {
                        navController.navigate(Routes.historyDetail(workoutId)) {
                            popUpTo(Routes.HISTORY) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
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
                    drawRect(brush = CardeaGradient, blendMode = BlendMode.SrcIn)
                }
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = CardeaTextSecondary
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
