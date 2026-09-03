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
import com.inktodo.app.R
import com.inktodo.app.core.datastore.PomodoroPhase

interface PomodoroNotificationController {
    fun showCompletion(completedPhase: PomodoroPhase)
}

class AndroidPomodoroNotificationController(context: Context) : PomodoroNotificationController {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(NotificationManager::class.java)

    override fun showCompletion(completedPhase: PomodoroPhase) {
        if (!canPostNotifications()) return
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_POMODORO, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val focusCompleted = completedPhase == PomodoroPhase.FOCUS
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (focusCompleted) "专注完成" else "休息结束")
                .setContentText(if (focusCompleted) "辛苦了，休息一下。" else "水静心明，可以开始下一轮专注。")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun createChannel() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "番茄钟完成", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "专注或休息计时结束提醒"
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "pomodoro_completion_v1"
        const val NOTIFICATION_ID = 7_701
    }
}
