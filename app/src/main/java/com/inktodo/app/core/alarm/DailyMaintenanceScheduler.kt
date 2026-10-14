package com.inktodo.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Cross-day safety net for recurrence cleanup and Widget refresh. */
object DailyMaintenanceScheduler {
    const val ACTION_DAILY_MAINTENANCE = "com.inktodo.app.action.DAILY_MAINTENANCE"
    private val maintenanceTime = LocalTime.of(0, 5)

    fun nextTriggerAt(now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val todayTrigger = today.atTime(maintenanceTime).atZone(zone).toInstant()
        return if (todayTrigger.isAfter(now)) todayTrigger
        else today.plusDays(1).atTime(maintenanceTime).atZone(zone).toInstant()
    }

    fun scheduleNext(context: Context) {
        val applicationContext = context.applicationContext
        val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(applicationContext, ReminderRescheduleReceiver::class.java)
                .setAction(ACTION_DAILY_MAINTENANCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nextTriggerAt(Instant.now(), ZoneId.systemDefault()).toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permission may have changed between the capability check and registration.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}
