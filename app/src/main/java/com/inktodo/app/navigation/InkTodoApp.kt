package com.inktodo.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.datastore.UserPreferences
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inktodo.app.core.ui.components.InkBackdrop
import com.inktodo.app.core.ui.components.InkBottomBar
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.todo.domain.TaskRepository
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskCommandIssueNoticeGate
import com.inktodo.app.feature.calendar.ui.CalendarScreen
import com.inktodo.app.feature.calendar.ui.CalendarViewModel
import com.inktodo.app.feature.completed.ui.CompletedViewModel
import com.inktodo.app.feature.completed.ui.CompletedScreen
import com.inktodo.app.feature.pomodoro.ui.PomodoroScreen
import com.inktodo.app.feature.settings.ui.SettingsScreen
import com.inktodo.app.feature.settings.ui.SettingsViewModel
import com.inktodo.app.feature.settings.data.ReminderReliabilityChecker
import com.inktodo.app.feature.settings.data.ReminderSettingsNavigator
import com.inktodo.app.feature.todo.ui.EditTaskViewModel
import com.inktodo.app.feature.todo.ui.EditTaskScreen
import com.inktodo.app.feature.todo.ui.TodoViewModel
import com.inktodo.app.feature.todo.ui.TodoScreen
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.feature.pomodoro.domain.PomodoroCoordinator
import com.inktodo.app.feature.pomodoro.ui.PomodoroViewModel
import com.inktodo.app.core.focus.FullFocusController
import com.inktodo.app.core.widget.WidgetUpdater

@Composable
fun InkTodoApp(
    taskRepository: TaskRepository,
    taskCommandService: TaskCommandService,
    taskCommandIssueNoticeGate: TaskCommandIssueNoticeGate,
    reminderReliabilityChecker: ReminderReliabilityChecker,
    reminderSettingsNavigator: ReminderSettingsNavigator,
    preferencesRepository: PreferencesRepository,
    pomodoroCoordinator: PomodoroCoordinator,
    fullFocusController: FullFocusController,
    widgetUpdater: WidgetUpdater,
    openPomodoroToken: Long = 0,
    reliabilityPromptToken: Long = 0,
    openTaskId: Long? = null,
    onOpenTaskConsumed: () -> Unit = {},
    onSystemAlarmRequest: (SystemAlarmRequest) -> Unit = {},
    onTaskCommandResult: (TaskCommandResult) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = AppDestination.bottomDestinations.any { it.route == currentRoute }
    val motion = InkTheme.motion
    val scene = when (currentRoute) {
        AppDestination.Calendar.route -> InkScene.CALENDAR
        AppDestination.Pomodoro.route -> InkScene.POMODORO
        AppDestination.Completed.route -> InkScene.COMPLETED
        AppDestination.Settings.route -> InkScene.SETTINGS
        AppDestination.EditTask.route, AppDestination.EDIT_TASK_PATTERN -> InkScene.EDITOR
        else -> InkScene.TODO
    }
    var showReliabilityPrompt by remember { mutableStateOf(false) }
    val userPreferences by preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())

    LaunchedEffect(openTaskId) {
        openTaskId?.let { taskId ->
            navController.navigate(AppDestination.editTaskRoute(taskId)) {
                launchSingleTop = true
            }
            onOpenTaskConsumed()
        }
    }

    LaunchedEffect(reliabilityPromptToken) {
        if (reliabilityPromptToken > 0) showReliabilityPrompt = true
    }

    LaunchedEffect(openPomodoroToken) {
        if (openPomodoroToken > 0) {
            navController.navigate(AppDestination.Pomodoro.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(userPreferences.fullFocus.enabled) {
        if (userPreferences.fullFocus.enabled &&
            currentRoute != AppDestination.Pomodoro.route
        ) {
            navController.navigate(AppDestination.Pomodoro.route) {
                launchSingleTop = true
            }
        }
    }

    InkBackdrop(scene = scene) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    InkBottomBar(
                        currentRoute = currentRoute,
                        onDestinationSelected = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.Todo.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Todo.route,
                modifier = Modifier.padding(contentPadding),
                enterTransition = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.standardMillis else 0,
                            easing = motion.enterEasing,
                        ),
                    ) + slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.standardMillis else 0,
                            easing = motion.moveEasing,
                        ),
                        initialOffsetX = { width -> if (motion.enabled) width / 28 else 0 },
                    )
                },
                exitTransition = {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.quickMillis else 0,
                            easing = motion.enterEasing,
                        ),
                    ) + slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.quickMillis else 0,
                            easing = motion.moveEasing,
                        ),
                        targetOffsetX = { width -> if (motion.enabled) -width / 40 else 0 },
                    )
                },
                popEnterTransition = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.standardMillis else 0,
                            easing = motion.enterEasing,
                        ),
                    ) + slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.standardMillis else 0,
                            easing = motion.moveEasing,
                        ),
                        initialOffsetX = { width -> if (motion.enabled) -width / 28 else 0 },
                    )
                },
                popExitTransition = {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.quickMillis else 0,
                            easing = motion.enterEasing,
                        ),
                    ) + slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = if (motion.enabled) motion.quickMillis else 0,
                            easing = motion.moveEasing,
                        ),
                        targetOffsetX = { width -> if (motion.enabled) width / 40 else 0 },
                    )
                },
            ) {
                composable(AppDestination.Todo.route) {
                    val todoViewModel: TodoViewModel = viewModel(
                        factory = TodoViewModel.factory(
                            taskRepository,
                            taskCommandService,
                            taskCommandIssueNoticeGate,
                            preferencesRepository,
                        ),
                    )
                    TodoScreen(
                        viewModel = todoViewModel,
                        onAddTask = { navController.navigate(AppDestination.EditTask.route) },
                        onOpenTask = { taskId ->
                            navController.navigate(AppDestination.editTaskRoute(taskId))
                        },
                        onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                    )
                }
                composable(AppDestination.Calendar.route) {
                    val calendarViewModel: CalendarViewModel = viewModel(
                        factory = CalendarViewModel.factory(taskRepository),
                    )
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        onOpenTask = { taskId ->
                            navController.navigate(AppDestination.editTaskRoute(taskId))
                        },
                    )
                }
                composable(AppDestination.Pomodoro.route) {
                    val pomodoroViewModel: PomodoroViewModel = viewModel(
                        factory = PomodoroViewModel.factory(
                            preferencesRepository,
                            pomodoroCoordinator,
                            fullFocusController,
                        ),
                    )
                    PomodoroScreen(pomodoroViewModel)
                }
                composable(AppDestination.Completed.route) {
                    val completedViewModel: CompletedViewModel = viewModel(
                        factory = CompletedViewModel.factory(
                            taskRepository,
                            taskCommandService,
                            taskCommandIssueNoticeGate,
                            preferencesRepository,
                        ),
                    )
                    CompletedScreen(
                        viewModel = completedViewModel,
                        onOpenTask = { taskId ->
                            navController.navigate(AppDestination.editTaskRoute(taskId))
                        },
                        onSystemAlarmRequest = onSystemAlarmRequest,
                    )
                }
                composable(AppDestination.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.factory(
                            reminderReliabilityChecker,
                            preferencesRepository,
                            widgetUpdater,
                        ),
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        navigator = reminderSettingsNavigator,
                        onBack = navController::navigateUp,
                    )
                }
                composable(AppDestination.EditTask.route) {
                    val editViewModel: EditTaskViewModel = viewModel(
                        key = "new_task",
                        factory = EditTaskViewModel.factory(
                            null,
                            taskRepository,
                            taskCommandService,
                            preferencesRepository,
                        ),
                    )
                    EditTaskScreen(
                        viewModel = editViewModel,
                        onBack = navController::navigateUp,
                        onFinished = { result ->
                            onTaskCommandResult(result)
                            navController.navigateUp()
                        },
                    )
                }
                composable(
                    route = AppDestination.EDIT_TASK_PATTERN,
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
                ) { entry ->
                    val taskId = requireNotNull(entry.arguments).getLong("taskId")
                    val editViewModel: EditTaskViewModel = viewModel(
                        key = "edit_task_$taskId",
                        factory = EditTaskViewModel.factory(
                            taskId,
                            taskRepository,
                            taskCommandService,
                            preferencesRepository,
                        ),
                    )
                    EditTaskScreen(
                        viewModel = editViewModel,
                        onBack = navController::navigateUp,
                        onFinished = { result ->
                            onTaskCommandResult(result)
                            navController.navigateUp()
                        },
                    )
                }
            }
        }
    }

    if (showReliabilityPrompt) {
        AlertDialog(
            onDismissRequest = { showReliabilityPrompt = false },
            title = { Text("开启强提醒能力") },
            text = {
                Text("为保证锁屏、息屏和后台状态下仍能准时提醒，请检查通知与精确闹钟设置。每项权限只会由你在需要时手动开启。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReliabilityPrompt = false
                        navController.navigate(AppDestination.Settings.route) { launchSingleTop = true }
                    },
                ) { Text("开启强提醒权限") }
            },
            dismissButton = {
                TextButton(onClick = { showReliabilityPrompt = false }) { Text("稍后") }
            },
        )
    }
}
