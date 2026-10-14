package com.inktodo.app.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import com.inktodo.app.MainActivity
import com.inktodo.app.core.reminder.FormalReminderActivity
import com.inktodo.app.R
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import com.inktodo.app.core.alarm.ReminderSnapshotKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface TaskNotificationController {
    fun createChannels()
    fun canPostNotifications(): Boolean
    fun canPostChannel(channelId: String): Boolean = canPostNotifications()
    fun showPreReminder(snapshot: ScheduledTaskSnapshot)
    fun showFormalReminder(snapshot: ScheduledTaskSnapshot, alert: Boolean = true)
    fun buildFormalNotification(snapshot: ScheduledTaskSnapshot, alert: Boolean = true): Notification
    fun formalNotificationId(taskId: Long): Int
    fun hasActiveFormal(taskId: Long): Boolean = false
    fun cancelFormal(taskId: Long)
    fun showRangeStarted(snapshot: ScheduledTaskSnapshot, alert: Boolean = true)
    fun cancelRange(taskId: Long)
    fun cancelAll(taskId: Long)
}

class AndroidTaskNotificationController(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : TaskNotificationController {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun createChannels() {
        OBSOLETE_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
        val reminderAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        val preReminder = NotificationChannel(
            CHANNEL_PRE_REMINDER,
            "预提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "正式时间前 15 分钟的温和提醒"
            enableVibration(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, reminderAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val formalReminder = NotificationChannel(
            CHANNEL_FORMAL_REMINDER,
            "正式提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "任务正式时间的强提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 700)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, reminderAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val rangeReminder = NotificationChannel(
            CHANNEL_RANGE_REMINDER,
            "持续任务开始",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "时间段开始时声振一次，并在期间显示持续通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 500)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, reminderAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val rangePersistent = NotificationChannel(
            CHANNEL_RANGE_PERSISTENT,
            "持续任务常驻",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "时间段内持续显示；恢复与续期不会重复播放声音"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(
            listOf(preReminder, formalReminder, rangeReminder, rangePersistent),
        )
    }

    override fun canPostNotifications(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    override fun canPostChannel(channelId: String): Boolean =
        canPostNotifications() &&
            (manager.getNotificationChannel(channelId)?.importance ?: NotificationManager.IMPORTANCE_NONE) !=
            NotificationManager.IMPORTANCE_NONE

    override fun showPreReminder(snapshot: ScheduledTaskSnapshot) {
        if (!canPostChannel(CHANNEL_PRE_REMINDER)) return
        manager.notify(
            notificationId(snapshot.taskId, TYPE_PRE),
            baseBuilder(snapshot, CHANNEL_PRE_REMINDER)
                .setContentTitle("还有 15 分钟：${snapshot.title}")
                .setContentText("提前准备一下，正式时间即将到达。")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun showFormalReminder(snapshot: ScheduledTaskSnapshot, alert: Boolean) {
        if (!canPostChannel(CHANNEL_FORMAL_REMINDER)) return
        manager.notify(notificationId(snapshot.taskId, TYPE_FORMAL), buildFormalNotification(snapshot, alert))
    }

    override fun buildFormalNotification(snapshot: ScheduledTaskSnapshot, alert: Boolean): Notification {
        val confirmationIntent = formalConfirmationIntent(snapshot)
        if (!alert) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FORMAL_RESTORE,
                    "待确认提醒恢复",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val builder = baseBuilder(
            snapshot,
            if (alert) CHANNEL_FORMAL_REMINDER else CHANNEL_FORMAL_RESTORE,
            confirmationIntent,
        )
            .setContentTitle(if (snapshot.kind == ReminderSnapshotKind.TIME_RANGE)
                "进行中，请确认：${snapshot.title}" else "时间到了：${snapshot.title}")
            .setContentText("点击打开确认页；确认前提醒会持续保留。")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(confirmationIntent, true)
        }
        return builder.build()
    }

    override fun cancelFormal(taskId: Long) {
        manager.cancel(notificationId(taskId, TYPE_FORMAL))
    }

    override fun formalNotificationId(taskId: Long): Int = notificationId(taskId, TYPE_FORMAL)

    override fun hasActiveFormal(taskId: Long): Boolean =
        manager.activeNotifications.any { it.id == formalNotificationId(taskId) }

    override fun showRangeStarted(snapshot: ScheduledTaskSnapshot, alert: Boolean) {
        if (!canPostChannel(CHANNEL_RANGE_PERSISTENT)) return
        if (alert && canPostChannel(CHANNEL_RANGE_REMINDER)) {
            manager.notify(
                notificationId(snapshot.taskId, TYPE_RANGE_ALERT),
                baseBuilder(snapshot, CHANNEL_RANGE_REMINDER)
                    .setContentTitle("持续提醒开始：${snapshot.title}")
                    .setContentText("任务已进入持续时间段。")
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        manager.notify(
            notificationId(snapshot.taskId, TYPE_RANGE),
            buildRangeNotification(snapshot, alert),
        )
    }

    fun buildRangeNotification(snapshot: ScheduledTaskSnapshot, alert: Boolean): Notification {
        val endAt = requireNotNull(snapshot.endAt)
        val start = snapshot.startAt.atZone(zoneId)
        val end = endAt.atZone(zoneId)
        val rangeLabel = if (start.toLocalDate() == end.toLocalDate()) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            "${formatter.format(start)} - ${formatter.format(end)}"
        } else {
            val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
            "${formatter.format(start)} - ${formatter.format(end)}"
        }
        return baseBuilder(
            snapshot,
            CHANNEL_RANGE_PERSISTENT,
        )
            .setContentTitle("进行中：${snapshot.title}")
            .setContentText(rangeLabel)
            .setSubText("点按进入任务详情以安全结束")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(rangeDismissedIntent(snapshot.taskId))
            .build()
    }

    override fun cancelRange(taskId: Long) {
        manager.cancel(notificationId(taskId, TYPE_RANGE_ALERT))
        manager.cancel(notificationId(taskId, TYPE_RANGE))
    }

    override fun cancelAll(taskId: Long) {
        manager.cancel(notificationId(taskId, TYPE_PRE))
        manager.cancel(notificationId(taskId, TYPE_FORMAL))
        manager.cancel(notificationId(taskId, TYPE_RANGE_ALERT))
        manager.cancel(notificationId(taskId, TYPE_RANGE))
    }

    private fun baseBuilder(
        snapshot: ScheduledTaskSnapshot,
        channelId: String,
        contentIntent: PendingIntent = taskContentIntent(snapshot.taskId, TYPE_CONTENT),
    ): Notification.Builder = Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(contentIntent)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setWhen(snapshot.startAt.toEpochMilli())
        .setShowWhen(true)
        .setLocalOnly(true)

    private fun taskContentIntent(taskId: Long, kind: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(taskId, kind),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun formalConfirmationIntent(snapshot: ScheduledTaskSnapshot): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(snapshot.taskId, TYPE_FULL_SCREEN),
        FormalReminderActivity.createIntent(context, snapshot.taskId, snapshot.title, snapshot.note,
            preserveSnapshot = snapshot.kind == ReminderSnapshotKind.TIME_RANGE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun rangeDismissedIntent(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(taskId, TYPE_RANGE_DISMISSED),
        Intent(context, ReminderNotificationDismissedReceiver::class.java)
            .setAction(ReminderNotificationDismissedReceiver.ACTION_RANGE_NOTIFICATION_DISMISSED)
            .putExtra(ReminderNotificationDismissedReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    private fun notificationId(taskId: Long, kind: Int): Int = requestCode(taskId, kind)

    private fun requestCode(taskId: Long, kind: Int): Int = 31 * taskId.hashCode() + kind

    companion object {
        const val CHANNEL_PRE_REMINDER = "task_pre_reminder_v1"
        const val CHANNEL_FORMAL_REMINDER = "task_formal_reminder_v4"
        const val CHANNEL_FORMAL_RESTORE = "task_formal_restore_v2"
        const val CHANNEL_RANGE_REMINDER = "task_range_alert_v2"
        const val CHANNEL_RANGE_PERSISTENT = "task_range_persistent_v2"
        @Deprecated("Use CHANNEL_RANGE_PERSISTENT")
        const val CHANNEL_RANGE_RESTORE = CHANNEL_RANGE_PERSISTENT
        private const val TYPE_PRE = 11
        private const val TYPE_FORMAL = 12
        private const val TYPE_RANGE = 13
        private const val TYPE_CONTENT = 14
        private const val TYPE_FULL_SCREEN = 15
        private const val TYPE_RANGE_DISMISSED = 16
        private const val TYPE_RANGE_ALERT = 17
        internal val OBSOLETE_CHANNEL_IDS = listOf(
            "task_formal_reminder_v1",
            "task_formal_reminder_v2",
            "task_formal_reminder_v3",
            "task_formal_pending_v1",
            "task_range_reminder_v1",
            "task_range_restore_v1",
        )
    }
}
