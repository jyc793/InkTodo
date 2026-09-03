package com.inktodo.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.alarm.ReminderSnapshotKind
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import com.inktodo.app.core.alarm.toScheduledSnapshot
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderNotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RANGE_NOTIFICATION_DISMISSED) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                restoreIfStillActive(context, taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreIfStillActive(context: Context, taskId: Long) {
        val container = (context.applicationContext as InkTodoApplication).appContainer
        val userManager = context.getSystemService(UserManager::class.java)
        val snapshot = if (userManager.isUserUnlocked) {
            container.taskRepository.findById(taskId)?.toScheduledSnapshot()
        } else {
            container.reminderSnapshotStore.get(taskId)
        }
        if (snapshot?.isActiveRangeAt(Instant.now()) == true) {
            container.notificationController.showRangeStarted(snapshot, alert = false)
            runCatching {
                container.reminderHealthStore.record(
                    taskId,
                    com.inktodo.app.core.alarm.ReminderHealthState.POSTED_TO_SYSTEM,
                    detail = "持续通知被清除后已重新提交给系统",
                )
            }
        }
    }

    companion object {
        const val ACTION_RANGE_NOTIFICATION_DISMISSED =
            "com.inktodo.app.action.RANGE_NOTIFICATION_DISMISSED"
        const val EXTRA_TASK_ID = "taskId"
    }
}

fun ScheduledTaskSnapshot.isActiveRangeAt(now: Instant): Boolean =
    kind == ReminderSnapshotKind.TIME_RANGE &&
        !startAt.isAfter(now) &&
        endAt?.isAfter(now) == true
