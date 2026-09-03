package com.inktodo.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.reminder.FormalReminderOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderScheduler.ACTION_REMINDER_ALARM) return
        val taskId = intent.getLongExtra(AndroidReminderScheduler.EXTRA_TASK_ID, -1L)
        val kind = intent.getStringExtra(AndroidReminderScheduler.EXTRA_ALARM_KIND)
            ?.let { runCatching { ReminderAlarmKind.valueOf(it) }.getOrNull() }
        val expectedTriggerAt = intent.getLongExtra(AndroidReminderScheduler.EXTRA_EXPECTED_TRIGGER_AT, -1L)
        if (taskId <= 0 || kind == null || expectedTriggerAt <= 0) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, taskId, kind, expectedTriggerAt)
            } catch (error: Throwable) {
                val container = (context.applicationContext as InkTodoApplication).appContainer
                recordHealth(container, taskId, ReminderHealthState.FAILED, "Receiver 执行失败：${error.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        taskId: Long,
        kind: ReminderAlarmKind,
        expectedTriggerAt: Long,
    ) {
        val container = (context.applicationContext as InkTodoApplication).appContainer
        val userManager = context.getSystemService(UserManager::class.java)
        val snapshot = if (userManager.isUserUnlocked) {
            container.taskRepository.findById(taskId)?.toScheduledSnapshot()
        } else {
            container.reminderSnapshotStore.get(taskId)
        }
        val notifications = container.notificationController
        notifications.createChannels()
        if (snapshot == null) {
            if (kind == ReminderAlarmKind.RANGE_END) notifications.cancelRange(taskId)
            recordHealth(container, taskId, ReminderHealthState.DROPPED, "到时后未找到有效任务或设备保护快照")
            return
        }
        val expected = java.time.Instant.ofEpochMilli(expectedTriggerAt)
        recordHealth(container, taskId, ReminderHealthState.TRIGGERED, "系统已触发 ${kind.name}")
        val delivered = when (kind) {
            ReminderAlarmKind.PRE_REMINDER -> if (
                snapshot.kind == ReminderSnapshotKind.AT_TIME &&
                snapshot.startAt.minus(ReminderPlanner.PRE_REMINDER_OFFSET) == expected
            ) {
                postIfAllowed(notifications) { notifications.showPreReminder(snapshot) }
            } else false
            ReminderAlarmKind.FORMAL_REMINDER -> if (
                snapshot.kind == ReminderSnapshotKind.AT_TIME &&
                !snapshot.useSystemAlarm &&
                snapshot.startAt == expected
            ) {
                postIfAllowed(notifications) {
                    notifications.showFormalReminder(snapshot)
                    FormalReminderOverlayService.show(context, snapshot)
                }
            } else false
            ReminderAlarmKind.RANGE_START -> if (
                snapshot.kind == ReminderSnapshotKind.TIME_RANGE && snapshot.startAt == expected
            ) {
                postIfAllowed(notifications) {
                    notifications.showRangeStarted(snapshot, alert = true)
                }
            } else false
            ReminderAlarmKind.RANGE_END -> if (
                snapshot.kind == ReminderSnapshotKind.TIME_RANGE && snapshot.endAt == expected
            ) {
                notifications.cancelRange(taskId)
                container.reminderSnapshotStore.remove(taskId)
                true
            } else false
            ReminderAlarmKind.RANGE_RENEW -> if (
                snapshot.kind == ReminderSnapshotKind.TIME_RANGE &&
                !expected.isBefore(snapshot.startAt) &&
                snapshot.endAt?.isAfter(expected) == true &&
                snapshot.endAt.isAfter(java.time.Instant.now())
            ) {
                val posted = postIfAllowed(notifications) {
                    notifications.showRangeStarted(snapshot, alert = false)
                }
                container.reminderScheduler.schedule(snapshot, alertIfRangeAlreadyActive = false)
                posted
            } else false
        }
        if (delivered) {
            val state = if (kind == ReminderAlarmKind.FORMAL_REMINDER) {
                ReminderHealthState.AWAITING_CONFIRMATION
            } else {
                ReminderHealthState.POSTED_TO_SYSTEM
            }
            val detail = if (kind == ReminderAlarmKind.FORMAL_REMINDER) {
                "正式提醒已展示，等待用户确认"
            } else {
                "${kind.name} 已提交给 Android 通知系统"
            }
            recordHealth(container, taskId, state, detail)
        } else if (!notifications.canPostNotifications()) {
            recordHealth(container, taskId, ReminderHealthState.FAILED, "通知权限关闭，${kind.name} 无法展示")
        } else {
            recordHealth(container, taskId, ReminderHealthState.DROPPED, "任务当前配置与过期 Alarm 不一致：${kind.name}")
        }
    }

    private fun postIfAllowed(
        notifications: com.inktodo.app.core.notification.TaskNotificationController,
        block: () -> Unit,
    ): Boolean {
        if (!notifications.canPostNotifications()) return false
        block()
        return true
    }

    private fun recordHealth(
        container: com.inktodo.app.AppContainer,
        taskId: Long,
        state: ReminderHealthState,
        detail: String,
    ) {
        runCatching { container.reminderHealthStore.record(taskId, state, detail = detail) }
    }
}
