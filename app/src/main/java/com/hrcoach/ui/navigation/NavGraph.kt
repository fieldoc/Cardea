package com.hrcoach.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hrcoach.R
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.history.HistoryDetailScreen
import com.hrcoach.ui.history.HistoryListScreen
import com.hrcoach.ui.postrun.PostRunSummaryScreen
import com.hrcoach.ui.progress.ProgressScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.workout.ActiveWorkoutScreen
import com.hrcoach.util.PermissionGate

object Routes {
    const val SETUP = "setup"
    const val WORKOUT = "workout"
    const val PROGRESS = "progress"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{workoutId}"
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
                popUpTo(Routes.SETUP) { inclusive = false }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        if (!isWorkoutRunning && routeNow == Routes.WORKOUT) {
            val finishedWorkoutId = completedWorkoutId
            if (finishedWorkoutId != null) {
                navController.navigate(Routes.postRunSummary(finishedWorkoutId)) {
                    popUpTo(Routes.SETUP) { inclusive = false }
                    launchSingleTop = true
                }
                WorkoutState.clearCompletedWorkoutId()
            } else {
                navController.navigate(Routes.SETUP) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val showBottomBar = !isWorkoutRunning && (
        currentRoute == Routes.SETUP ||
            currentRoute == Routes.PROGRESS ||
            currentRoute == Routes.HISTORY
        )

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(220)
                ) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(220)
                ) + fadeOut(animationSpec = tween(220))
            ) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETUP,
                        onClick = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.nav_setup)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_setup)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.PROGRESS,
                        onClick = {
                            navController.navigate(Routes.PROGRESS) {
                                popUpTo(Routes.SETUP) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = stringResource(R.string.nav_progress)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_progress)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.HISTORY,
                        onClick = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(Routes.SETUP) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.nav_history)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_history)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SETUP,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(
                route = Routes.SETUP,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
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
                                Toast.makeText(
                                    context,
                                    "Unable to start workout.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }

            composable(
                route = Routes.WORKOUT,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                ActiveWorkoutScreen(
                    onPauseResume = {
                        val action = if (workoutSnapshot.isPaused) {
                            WorkoutForegroundService.ACTION_RESUME
                        } else {
                            WorkoutForegroundService.ACTION_PAUSE
                        }
                        val pauseIntent = Intent(context, WorkoutForegroundService::class.java).apply {
                            this.action = action
                        }
                        context.startService(pauseIntent)
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
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                ProgressScreen(
                    onStartWorkout = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.HISTORY,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                HistoryListScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate(Routes.historyDetail(workoutId))
                    },
                    onStartWorkout = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(
                    navArgument("workoutId") {
                        type = NavType.LongType
                    }
                ),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                HistoryDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() },
                    onOpenMapsSetup = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onViewProgress = {
                        navController.navigate(Routes.PROGRESS) {
                            popUpTo(Routes.SETUP) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onViewPostRunSummary = {
                        navController.navigate(Routes.postRunSummary(workoutId)) {
                            launchSingleTop = true
                        }
                    },
                    onDeleteWorkout = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Routes.POST_RUN_SUMMARY,
                arguments = listOf(
                    navArgument("workoutId") {
                        type = NavType.LongType
                    }
                ),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
                PostRunSummaryScreen(
                    workoutId = workoutId,
                    onViewProgress = {
                        navController.navigate(Routes.PROGRESS) {
                            popUpTo(Routes.SETUP) { inclusive = false }
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
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}
