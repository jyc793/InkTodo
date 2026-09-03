package com.inktodo.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.inktodo.app.core.notification.TaskNotificationController
import java.time.Clock
import java.time.Instant

enum class ReminderScheduleIssue {
    EXACT_ALARM_UNAVAILABLE,
    NOTIFICATION_PERMISSION_UNAVAILABLE,
    OVERLAY_PERMISSION_UNAVAILABLE,
}

data class ReminderScheduleResult(
    val plan: ReminderPlan,
    val issues: Set<ReminderScheduleIssue> = emptySet(),
)

interface ReminderScheduler {
    fun schedule(
        snapshot: ScheduledTaskSnapshot,
        alertIfRangeAlreadyActive: Boolean = true,
    ): ReminderScheduleResult

    fun cancel(taskId: Long)
    fun restoreAwaitingConfirmation(snapshot: ScheduledTaskSnapshot) = Unit
}

class AndroidReminderScheduler(
    private val context: Context,
    private val notifications: TaskNotificationController,
    private val clock: Clock = Clock.systemUTC(),
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(
        snapshot: ScheduledTaskSnapshot,
        alertIfRangeAlreadyActive: Boolean,
    ): ReminderScheduleResult {
        cancel(snapshot.taskId)
        notifications.createChannels()
        val plan = ReminderPlanner.plan(snapshot, Instant.now(clock))
        val exactAvailable = canScheduleExactAlarms()
        plan.alarms.forEach { alarm -> scheduleAlarm(snapshot, alarm, exactAvailable) }
        if (plan.startRangeImmediately) {
            notifications.showRangeStarted(snapshot, alertIfRangeAlreadyActive)
        }
        val issues = buildSet {
            if (plan.alarms.isNotEmpty() && !exactAvailable) add(ReminderScheduleIssue.EXACT_ALARM_UNAVAILABLE)
            if ((plan.alarms.isNotEmpty() || plan.startRangeImmediately) && !notifications.canPostNotifications()) {
                add(ReminderScheduleIssue.NOTIFICATION_PERMISSION_UNAVAILABLE)
            }
            if (
                plan.alarms.any { it.kind == ReminderAlarmKind.FORMAL_REMINDER } &&
                !Settings.canDrawOverlays(context)
            ) {
                add(ReminderScheduleIssue.OVERLAY_PERMISSION_UNAVAILABLE)
            }
        }
        return ReminderScheduleResult(plan, issues)
    }

    override fun cancel(taskId: Long) {
        ReminderAlarmKind.entries.forEach { kind ->
            ReminderAlarmPendingIntents.find(context, taskId, kind)?.let { operation ->
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }
        notifications.cancelAll(taskId)
    }

    override fun restoreAwaitingConfirmation(snapshot: ScheduledTaskSnapshot) {
        notifications.createChannels()
        notifications.showFormalReminder(snapshot, alert = false)
    }

    private fun scheduleAlarm(
        snapshot: ScheduledTaskSnapshot,
        alarm: PlannedReminderAlarm,
        exactAvailable: Boolean,
    ) {
        val operation = ReminderAlarmPendingIntents.create(context, snapshot.taskId, alarm)
        if (exactAvailable) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAt.toEpochMilli(),
                operation,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAt.toEpochMilli(),
                operation,
            )
        }
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        const val ACTION_REMINDER_ALARM = "com.inktodo.app.action.REMINDER_ALARM"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_ALARM_KIND = "alarmKind"
        const val EXTRA_EXPECTED_TRIGGER_AT = "expectedTriggerAt"
    }
}

object ReminderAlarmPendingIntents {
    fun create(
        context: Context,
        taskId: Long,
        alarm: PlannedReminderAlarm,
    ): PendingIntent = get(
        context = context,
        taskId = taskId,
        kind = alarm.kind,
        lookupFlag = PendingIntent.FLAG_UPDATE_CURRENT,
        triggerAt = alarm.triggerAt,
    ) ?: error("Unable to create alarm PendingIntent")

    fun find(context: Context, taskId: Long, kind: ReminderAlarmKind): PendingIntent? =
        get(context, taskId, kind, PendingIntent.FLAG_NO_CREATE)

    private fun get(
        context: Context,
        taskId: Long,
        kind: ReminderAlarmKind,
        lookupFlag: Int,
        triggerAt: Instant? = null,
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = AndroidReminderScheduler.ACTION_REMINDER_ALARM
            data = Uri.Builder()
                .scheme("inktodo")
                .authority("alarm")
                .appendPath(taskId.toString())
                .appendPath(kind.name)
                .build()
            putExtra(AndroidReminderScheduler.EXTRA_TASK_ID, taskId)
            putExtra(AndroidReminderScheduler.EXTRA_ALARM_KIND, kind.name)
            triggerAt?.let {
                putExtra(AndroidReminderScheduler.EXTRA_EXPECTED_TRIGGER_AT, it.toEpochMilli())
            }
        }
        return PendingIntent.getBroadcast(
            context,
            kind.code,
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
