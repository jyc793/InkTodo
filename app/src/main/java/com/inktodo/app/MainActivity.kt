package com.inktodo.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Build
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
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandIssue
import com.inktodo.app.feature.todo.domain.userMessage
import com.inktodo.app.navigation.InkTodoApp
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val openTaskId = MutableStateFlow<Long?>(null)
    private val reliabilityPromptToken = MutableStateFlow(0L)
    private val openPomodoroToken = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        val appContainer = (application as InkTodoApplication).appContainer
        lifecycleScope.launch(Dispatchers.IO) {
            appContainer.reminderRecoveryCoordinator.recover(userUnlocked = true)
        }
        setContent {
            val pendingTaskId by openTaskId.collectAsStateWithLifecycle()
            val pendingReliabilityPrompt by reliabilityPromptToken.collectAsStateWithLifecycle()
            val pendingPomodoroOpen by openPomodoroToken.collectAsStateWithLifecycle()
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
                    openPomodoroToken = pendingPomodoroOpen,
                    reliabilityPromptToken = pendingReliabilityPrompt,
                    openTaskId = pendingTaskId,
                    onOpenTaskConsumed = { openTaskId.value = null },
                    onSystemAlarmRequest = ::openSystemAlarm,
                    onTaskCommandResult = ::handleTaskCommandResult,
                )
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
        AlertDialog.Builder(this)
            .setTitle("请核对系统闹钟日期")
            .setMessage(
                "任务时间：$formatted\n\nAndroid 只能把小时和分钟交给系统时钟，不能传递具体年月日。" +
                    "请在接下来的 HyperOS 时钟页面确认日期/重复规则并保存；墨待不会再重复播放正式提醒。",
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
        private val SYSTEM_ALARM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    }
}
