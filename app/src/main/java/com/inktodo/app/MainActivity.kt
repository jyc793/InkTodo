package com.inktodo.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.inktodo.app.core.alarm.SystemAlarmIntentFactory
import com.inktodo.app.core.alarm.DailyMaintenanceScheduler
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandIssue
import com.inktodo.app.feature.todo.domain.userMessage
import com.inktodo.app.navigation.InkTodoApp
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import com.inktodo.app.feature.pomodoro.floating.PomodoroFloatingTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val openTaskId = MutableStateFlow<Long?>(null)
    private val reliabilityPromptToken = MutableStateFlow(0L)
    private val openPomodoroToken = MutableStateFlow(0L)
    private val openHolidayInfoToken = MutableStateFlow(0L)
    private var lastReminderRecoveryAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        val appContainer = (application as InkTodoApplication).appContainer
        setContent {
            val pendingTaskId by openTaskId.collectAsStateWithLifecycle()
            val pendingReliabilityPrompt by reliabilityPromptToken.collectAsStateWithLifecycle()
            val pendingPomodoroOpen by openPomodoroToken.collectAsStateWithLifecycle()
            val pendingHolidayInfoOpen by openHolidayInfoToken.collectAsStateWithLifecycle()
            val userPreferences by appContainer.preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = userPreferences.themeMode.resolveDarkTheme(
                androidx.compose.foundation.isSystemInDarkTheme(),
            )
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            InkTheme(darkTheme = darkTheme) {
                InkTodoApp(
                    taskRepository = appContainer.taskRepository,
                    taskCommandService = appContainer.taskCommandService,
                    taskCommandIssueNoticeGate = appContainer.taskCommandIssueNoticeGate,
                    reminderReliabilityChecker = appContainer.reminderReliabilityChecker,
                    reminderSettingsNavigator = appContainer.reminderSettingsNavigator,
                    preferencesRepository = appContainer.preferencesRepository,
                    pomodoroCoordinator = appContainer.pomodoroCoordinator,
                    fullFocusController = appContainer.fullFocusController,
                    widgetUpdater = appContainer.widgetUpdater,
                    specialDateRepository = appContainer.specialDateRepository,
                    offlineCalendarService = appContainer.offlineCalendarService,
                    specialDateTaskCoordinator = appContainer.specialDateTaskCoordinator,
                    openPomodoroToken = pendingPomodoroOpen,
                    reliabilityPromptToken = pendingReliabilityPrompt,
                    openTaskId = pendingTaskId,
                    openHolidayInfoToken = pendingHolidayInfoOpen,
                    onOpenTaskConsumed = { openTaskId.value = null },
                    onSystemAlarmRequest = ::openSystemAlarm,
                    onTaskCommandResult = ::handleTaskCommandResult,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DailyMaintenanceScheduler.scheduleNext(this)
        val elapsed = SystemClock.elapsedRealtime()
        if (lastReminderRecoveryAt != 0L && elapsed - lastReminderRecoveryAt < 30_000L) return
        lastReminderRecoveryAt = elapsed
        val container = (application as InkTodoApplication).appContainer
        lifecycleScope.launch(Dispatchers.IO) {
            container.reminderRecoveryCoordinator.recover(userUnlocked = true)
            val preferences = container.preferencesRepository.preferences.first()
            if (
                preferences.pomodoroSession.floatingVisible &&
                preferences.pomodoroSession.runState != PomodoroRunState.IDLE &&
                !preferences.fullFocus.enabled
            ) {
                PomodoroFloatingTimerService.restore(this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        val taskId = launchIntent?.getLongExtra(EXTRA_OPEN_TASK_ID, -1L) ?: -1L
        if (taskId > 0) openTaskId.value = taskId
        if (launchIntent?.getBooleanExtra(EXTRA_OPEN_POMODORO, false) == true) {
            openPomodoroToken.value += 1
        }
        if (launchIntent?.getBooleanExtra(EXTRA_OPEN_HOLIDAY_INFO, false) == true) {
            openHolidayInfoToken.value += 1
        }
        if (launchIntent?.getBooleanExtra(EXTRA_FULL_SCREEN_REMINDER, false) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
        }
    }

    private fun handleTaskCommandResult(result: TaskCommandResult) {
        val noticeGate = (application as InkTodoApplication).appContainer.taskCommandIssueNoticeGate
        val unseenIssues = noticeGate.takeUnseen(result.issues)
        val reliabilityIssues = unseenIssues.filterTo(mutableSetOf()) { issue ->
            issue == TaskCommandIssue.EXACT_ALARM_UNAVAILABLE ||
                issue == TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE ||
                issue == TaskCommandIssue.OVERLAY_PERMISSION_UNAVAILABLE
        }
        if (reliabilityIssues.isNotEmpty()) {
            reliabilityPromptToken.value += 1
        }
        val otherIssues = unseenIssues - reliabilityIssues
        if (otherIssues.isNotEmpty()) {
            Toast.makeText(
                this,
                otherIssues.joinToString("\n") { it.userMessage() },
                Toast.LENGTH_LONG,
            ).show()
        }
        result.systemAlarmRequest?.let(::openSystemAlarm)
    }

    private fun openSystemAlarm(request: SystemAlarmRequest) {
        val target = request.triggerAt.atZone(ZoneId.systemDefault())
        val formatted = SYSTEM_ALARM_DATE_TIME.format(target)
        val repeatDescription = if (request.repeatDays.isEmpty()) "" else
            "\n重复：${request.repeatDays.sortedBy { it.value }.joinToString("、") { "周${"一二三四五六日"[it.value - 1]}" }}"
        AlertDialog.Builder(this)
            .setTitle("请核对系统闹钟日期")
            .setMessage(
                "任务时间：$formatted$repeatDescription\n\nAndroid 会把时间和重复星期交给系统时钟，具体日期仍需在时钟页面确认。" +
                    "请核对后保存；墨待不重复播放正式提醒。",
            )
            .setPositiveButton("打开系统时钟") { _, _ -> launchSystemAlarm(request) }
            .setCancelable(false)
            .show()
    }

    private fun launchSystemAlarm(request: SystemAlarmRequest) {
        try {
            startActivity(SystemAlarmIntentFactory.create(request))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "未找到可处理系统闹钟的时钟应用。", Toast.LENGTH_LONG).show()
            scheduleAppFallback(request, "未找到系统时钟，已恢复 App 正式强提醒")
        } catch (_: SecurityException) {
            Toast.makeText(this, "系统不允许打开时钟，请手动确认闹钟。", Toast.LENGTH_LONG).show()
            scheduleAppFallback(request, "系统阻止打开时钟，已恢复 App 正式强提醒")
        }
    }

    private fun scheduleAppFallback(request: SystemAlarmRequest, reason: String) {
        if (request.taskId <= 0) return
        val container = (application as InkTodoApplication).appContainer
        lifecycleScope.launch(Dispatchers.IO) {
            val task = container.taskRepository.findById(request.taskId) ?: return@launch
            val atTime = task.reminder as? TaskReminder.AtTime
                ?: return@launch
            runCatching {
                val result = container.taskCommandService.save(
                    task.copy(
                        reminder = atTime.copy(useSystemAlarm = false),
                        updatedAt = Instant.now(),
                    ),
                )
                container.reminderHealthStore.record(
                    request.taskId,
                    if (result.issues.any { it != TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW }) {
                        ReminderHealthState.SCHEDULED_WITH_WARNING
                    } else {
                        ReminderHealthState.SCHEDULED
                    },
                    atTime.startAt,
                    reason,
                )
            }.onFailure { error ->
                runCatching {
                    container.reminderHealthStore.record(
                        request.taskId,
                        ReminderHealthState.FAILED,
                        detail = "系统时钟与 App 备用提醒均失败：${error.message}",
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TASK_ID = "com.inktodo.app.extra.OPEN_TASK_ID"
        const val EXTRA_FULL_SCREEN_REMINDER = "com.inktodo.app.extra.FULL_SCREEN_REMINDER"
        const val EXTRA_OPEN_POMODORO = "com.inktodo.app.extra.OPEN_POMODORO"
        const val EXTRA_OPEN_HOLIDAY_INFO = "com.inktodo.app.extra.OPEN_HOLIDAY_INFO"
        private val SYSTEM_ALARM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    }
}
