package com.inktodo.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.inktodo.app.feature.pomodoro.data.PomodoroCompletionReceiver

interface PomodoroAlarmScheduler {
    fun schedule(targetEndEpochMillis: Long)
    fun cancel()
}

class AndroidPomodoroAlarmScheduler(context: Context) : PomodoroAlarmScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun schedule(targetEndEpochMillis: Long) {
        val pendingIntent = PomodoroAlarmPendingIntents.create(applicationContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetEndEpochMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetEndEpochMillis,
                pendingIntent,
            )
        }
    }

    override fun cancel() {
        val pendingIntent = PomodoroAlarmPendingIntents.find(applicationContext) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}

object PomodoroAlarmPendingIntents {
    fun create(context: Context): PendingIntent = requireNotNull(get(context, PendingIntent.FLAG_UPDATE_CURRENT))
    fun find(context: Context): PendingIntent? = get(context, PendingIntent.FLAG_NO_CREATE)

    private fun get(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, PomodoroCompletionReceiver::class.java)
            .setAction(ACTION_COMPLETE),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_CODE = 7_701
    private const val ACTION_COMPLETE = "com.inktodo.app.action.POMODORO_COMPLETE"
}
