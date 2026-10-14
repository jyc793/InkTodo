package com.inktodo.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.datastore.UserPreferences
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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
import com.inktodo.app.feature.calendar.ui.HolidayDataInfoScreen
import com.inktodo.app.feature.calendar.ui.SpecialDateEditScreen
import com.inktodo.app.feature.calendar.ui.SpecialDateEditViewModel
import com.inktodo.app.feature.calendar.ui.SpecialDateListScreen
import com.inktodo.app.feature.calendar.ui.SpecialDateListViewModel
import com.inktodo.app.feature.calendar.domain.OfflineCalendarService
import com.inktodo.app.feature.calendar.domain.SpecialDateRepository
import com.inktodo.app.feature.calendar.domain.SpecialDateTaskCoordinator
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
import com.inktodo.app.feature.todo.ui.TaskDetailScreen
import com.inktodo.app.feature.todo.ui.TaskDetailViewModel
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
    specialDateRepository: SpecialDateRepository,
    offlineCalendarService: OfflineCalendarService,
    specialDateTaskCoordinator: SpecialDateTaskCoordinator,
    openPomodoroToken: Long = 0,
    reliabilityPromptToken: Long = 0,
    openTaskId: Long? = null,
    openHolidayInfoToken: Long = 0,
    onOpenTaskConsumed: () -> Unit = {},
    onSystemAlarmRequest: (SystemAlarmRequest) -> Unit = {},
    onTaskCommandResult: (TaskCommandResult) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = AppDestination.bottomDestinations.any { it.route == currentRoute }
    var pomodoroBottomBarVisible by rememberSaveable { mutableStateOf(true) }
    var pomodoroTransitionRunning by remember { mutableStateOf(false) }
    val motion = InkTheme.motion
    val scene = when (currentRoute) {
        AppDestination.Calendar.route -> InkScene.CALENDAR
        AppDestination.Pomodoro.route -> InkScene.POMODORO
        AppDestination.Completed.route -> InkScene.COMPLETED
        AppDestination.Settings.route -> InkScene.SETTINGS
        AppDestination.SpecialDates.route,
        AppDestination.EditSpecialDate.route, AppDestination.EDIT_SPECIAL_DATE_PATTERN,
        AppDestination.HolidayDataInfo.route -> InkScene.CALENDAR
        AppDestination.EditTask.route, AppDestination.EDIT_TASK_PATTERN,
        AppDestination.TaskDetail.route, AppDestination.TASK_DETAIL_PATTERN -> InkScene.EDITOR
        else -> InkScene.TODO
    }
    val contentMaxWidth = when (currentRoute) {
        AppDestination.Calendar.route -> 1280.dp
        AppDestination.Pomodoro.route -> 960.dp
        AppDestination.Todo.route, AppDestination.Completed.route -> 1040.dp
        AppDestination.TaskDetail.route, AppDestination.TASK_DETAIL_PATTERN -> 720.dp
        else -> 880.dp
    }
    var showReliabilityPrompt by remember { mutableStateOf(false) }
    val userPreferences by preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())
    val bottomBarContentPadding = InkTheme.spacing.bottomBarHeight + InkTheme.spacing.xs * 2

    LaunchedEffect(openTaskId) {
        openTaskId?.let { taskId ->
            navController.navigate(AppDestination.taskDetailRoute(taskId)) {
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

    LaunchedEffect(openHolidayInfoToken) {
        if (openHolidayInfoToken > 0) {
            navController.navigate(AppDestination.HolidayDataInfo.route) { launchSingleTop = true }
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

    LaunchedEffect(currentRoute) {
        if (currentRoute != AppDestination.Pomodoro.route) {
            pomodoroBottomBarVisible = true
            pomodoroTransitionRunning = false
        }
    }

    InkBackdrop(scene = scene) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (userPreferences.highRefreshRateAnimations && pomodoroTransitionRunning) {
                        Modifier.preferredFrameRate(FrameRateCategory.High)
                    } else {
                        Modifier
                    },
                )
                .semantics { testTagsAsResourceId = true },
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Todo.route,
                        modifier = Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxSize()
                            .padding(
                                bottom = if (reserveBottomBarContentSpace(
                                        showBottomBar = showBottomBar,
                                        isPomodoroRoute = currentRoute == AppDestination.Pomodoro.route,
                                    )
                                ) {
                                    bottomBarContentPadding
                                } else {
                                    0.dp
                                },
                            ),
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
                            navController.navigate(AppDestination.taskDetailRoute(taskId))
                        },
                        onEditTask = { taskId -> navController.navigate(AppDestination.editTaskRoute(taskId)) },
                        onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                    )
                }
                composable(AppDestination.Calendar.route) {
                    val calendarViewModel: CalendarViewModel = viewModel(
                        factory = CalendarViewModel.factory(
                            taskRepository,
                            specialDateRepository,
                            offlineCalendarService,
                        ),
                    )
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        onOpenTask = { taskId ->
                            navController.navigate(AppDestination.taskDetailRoute(taskId))
                        },
                        onEditTask = { taskId -> navController.navigate(AppDestination.editTaskRoute(taskId)) },
                        onManageSpecialDates = { navController.navigate(AppDestination.SpecialDates.route) },
                        onOpenHolidayInfo = { navController.navigate(AppDestination.HolidayDataInfo.route) },
                    )
                }
                composable(AppDestination.SpecialDates.route) {
                    val listViewModel: SpecialDateListViewModel = viewModel(
                        factory = SpecialDateListViewModel.factory(specialDateRepository),
                    )
                    SpecialDateListScreen(
                        viewModel = listViewModel,
                        onBack = navController::navigateUp,
                        onAdd = { navController.navigate(AppDestination.editSpecialDateRoute(0L)) },
                        onEdit = { id -> navController.navigate(AppDestination.editSpecialDateRoute(id)) },
                    )
                }
                composable(
                    route = AppDestination.EDIT_SPECIAL_DATE_PATTERN,
                    arguments = listOf(navArgument("specialDateId") { type = NavType.LongType }),
                ) { entry ->
                    val id = requireNotNull(entry.arguments).getLong("specialDateId")
                    val editSpecialDateViewModel: SpecialDateEditViewModel = viewModel(
                        key = "special_date_edit_$id",
                        factory = SpecialDateEditViewModel.factory(
                            id,
                            specialDateRepository,
                            specialDateTaskCoordinator,
                        ),
                    )
                    SpecialDateEditScreen(
                        viewModel = editSpecialDateViewModel,
                        onBack = navController::navigateUp,
                        onFinished = navController::navigateUp,
                    )
                }
                composable(AppDestination.HolidayDataInfo.route) {
                    HolidayDataInfoScreen(onBack = navController::navigateUp)
                }
                composable(AppDestination.Pomodoro.route) {
                    val pomodoroViewModel: PomodoroViewModel = viewModel(
                        factory = PomodoroViewModel.factory(
                            preferencesRepository,
                            pomodoroCoordinator,
                            fullFocusController,
                        ),
                    )
                    PomodoroScreen(
                        viewModel = pomodoroViewModel,
                        onBottomBarVisibilityChanged = { pomodoroBottomBarVisible = it },
                        onTransitionRunningChanged = { pomodoroTransitionRunning = it },
                        normalBottomPadding = bottomBarContentPadding,
                    )
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
                            navController.navigate(AppDestination.taskDetailRoute(taskId))
                        },
                        onEditTask = { taskId -> navController.navigate(AppDestination.editTaskRoute(taskId)) },
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
                    route = AppDestination.TASK_DETAIL_PATTERN,
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
                ) { entry ->
                    val taskId = requireNotNull(entry.arguments).getLong("taskId")
                    val detailViewModel: TaskDetailViewModel = viewModel(
                        key = "task_detail_$taskId",
                        factory = TaskDetailViewModel.factory(taskId, taskRepository),
                    )
                    TaskDetailScreen(
                        viewModel = detailViewModel,
                        onBack = navController::navigateUp,
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
            if (showBottomBar) {
                AnimatedBottomBarOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = currentRoute != AppDestination.Pomodoro.route ||
                        pomodoroBottomBarVisible,
                    motionEnabled = motion.enabled,
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

@Composable
private fun AnimatedBottomBarOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    motionEnabled: Boolean,
    currentRoute: String?,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val easing = remember { CubicBezierEasing(0.25f, 1f, 0.5f, 1f) }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) 750 else 0,
            easing = easing,
        ),
        label = "bottom bar slide",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) 550 else 0,
            easing = easing,
        ),
        label = "bottom bar fade",
    )
    var measuredHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { measuredHeight = it.height }
            .graphicsLayer {
                this.alpha = alpha
                translationY = bottomBarTranslationY(measuredHeight, slideProgress)
            }
            .then(if (alpha <= 0.01f) Modifier.clearAndSetSemantics { } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        InkBottomBar(
            currentRoute = currentRoute,
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
        )
    }
}
