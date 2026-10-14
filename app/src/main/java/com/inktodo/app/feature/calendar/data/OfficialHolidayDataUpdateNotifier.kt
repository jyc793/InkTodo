package com.inktodo.app.feature.calendar.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inktodo.app.MainActivity
import com.inktodo.app.feature.calendar.domain.OfficialHolidayScheduleData
import java.time.Instant
import java.time.Month
import java.time.ZoneId

class OfficialHolidayDataUpdateNotifier(context: Context) {
    private val applicationContext = context.applicationContext

    fun reconcile(now: Instant) {
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!OfficialHolidayScheduleData.shouldShowUpdateNotice(today)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (today.month < Month.DECEMBER) return
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_NOTIFIED_YEAR, 0) == today.year) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "日历数据更新", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_HOLIDAY_INFO, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("下一年度节假日数据待更新")
                .setContentText("请安装包含下一年度国务院正式安排的新版本。")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
        preferences.edit().putInt(KEY_NOTIFIED_YEAR, today.year).apply()
    }

    private companion object {
        const val CHANNEL_ID = "calendar_data_update_v1"
        const val NOTIFICATION_ID = 61026
        const val REQUEST_CODE = 61026
        const val PREFERENCES = "official_holiday_data_status"
        const val KEY_NOTIFIED_YEAR = "notified_year"
    }
}
