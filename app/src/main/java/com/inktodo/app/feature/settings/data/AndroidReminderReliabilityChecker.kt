package com.inktodo.app.feature.settings.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import com.inktodo.app.core.notification.AndroidTaskNotificationController
import com.inktodo.app.core.notification.TaskNotificationController
import com.inktodo.app.core.alarm.ReminderHealthStore
import com.inktodo.app.core.alarm.NoOpReminderHealthStore
import com.inktodo.app.feature.settings.domain.ReminderChannelCapability
import com.inktodo.app.feature.settings.domain.PlatformReminderCapabilities
import com.inktodo.app.feature.settings.domain.ReminderReliabilityPolicy
import com.inktodo.app.feature.settings.domain.ReminderReliabilitySnapshot

interface ReminderReliabilityChecker {
    fun check(): ReminderReliabilitySnapshot
}

class AndroidReminderReliabilityChecker(
    context: Context,
    private val notificationController: TaskNotificationController,
    private val healthStore: ReminderHealthStore = NoOpReminderHealthStore,
) : ReminderReliabilityChecker {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
    private val powerManager = applicationContext.getSystemService(PowerManager::class.java)

    override fun check(): ReminderReliabilitySnapshot {
        notificationController.createChannels()
        val unhealthy = healthStore.getAll().filter { it.needsAttention }
        return ReminderReliabilityPolicy.evaluate(
            PlatformReminderCapabilities(
                notificationPermissionGranted = notificationPermissionGranted(),
                appNotificationsEnabled = notificationManager.areNotificationsEnabled(),
                exactAlarmsEnabled = exactAlarmsEnabled(),
                fullScreenIntentEnabled = fullScreenIntentEnabled(),
                strongNotificationChannelsEnabled = strongChannelsEnabled(),
                batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(
                    applicationContext.packageName,
                ),
                systemClockAvailable = systemClockAvailable(),
                isHyperOsDevice = isHyperOsDevice(),
                notificationChannels = channelCapabilities(),
                unhealthyReminderCount = unhealthy.size,
                lastReminderHealthDetail = unhealthy.firstOrNull()?.detail,
                overlayPermissionGranted = Settings.canDrawOverlays(applicationContext),
            ),
        )
    }

    private fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun exactAlarmsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun fullScreenIntentEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    private fun strongChannelsEnabled(): Boolean =
        notificationManager.getNotificationChannel(
            AndroidTaskNotificationController.CHANNEL_FORMAL_REMINDER,
        ).meetsRequirements(
            requiredImportance = NotificationManager.IMPORTANCE_HIGH,
            soundRequired = false,
            vibrationRequired = true,
        ) && notificationManager.getNotificationChannel(
            AndroidTaskNotificationController.CHANNEL_RANGE_REMINDER,
        ).meetsRequirements(
            requiredImportance = NotificationManager.IMPORTANCE_HIGH,
            soundRequired = true,
            vibrationRequired = true,
        )

    private fun channelCapabilities(): List<ReminderChannelCapability> = listOf(
        channelCapability(
            AndroidTaskNotificationController.CHANNEL_PRE_REMINDER,
            "15 分钟预提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
            soundRequired = true,
            vibrationRequired = true,
        ),
        channelCapability(
            AndroidTaskNotificationController.CHANNEL_FORMAL_REMINDER,
            "正式强提醒",
            NotificationManager.IMPORTANCE_HIGH,
            soundRequired = false,
            vibrationRequired = true,
        ),
        channelCapability(
            AndroidTaskNotificationController.CHANNEL_RANGE_REMINDER,
            "持续任务开始提醒",
            NotificationManager.IMPORTANCE_HIGH,
            soundRequired = true,
            vibrationRequired = true,
        ),
        channelCapability(
            AndroidTaskNotificationController.CHANNEL_RANGE_PERSISTENT,
            "持续任务常驻通知",
            NotificationManager.IMPORTANCE_DEFAULT,
            soundRequired = false,
            vibrationRequired = false,
        ),
    )

    private fun channelCapability(
        id: String,
        title: String,
        requiredImportance: Int,
        soundRequired: Boolean,
        vibrationRequired: Boolean,
    ): ReminderChannelCapability {
        val channel = notificationManager.getNotificationChannel(id)
        return ReminderChannelCapability(
            id = id,
            title = title,
            importance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE,
            requiredImportance = requiredImportance,
            soundRequired = soundRequired,
            soundEnabled = channel?.sound != null,
            vibrationRequired = vibrationRequired,
            vibrationEnabled = channel?.shouldVibrate() == true,
        )
    }

    private fun systemClockAvailable(): Boolean =
        Intent(AlarmClock.ACTION_SET_ALARM).resolveActivity(applicationContext.packageManager) != null

    private fun isHyperOsDevice(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Redmi", ignoreCase = true) ||
            Build.BRAND.equals("Xiaomi", ignoreCase = true)

    private fun NotificationChannel?.meetsRequirements(
        requiredImportance: Int,
        soundRequired: Boolean,
        vibrationRequired: Boolean,
    ): Boolean = this != null &&
        importance >= requiredImportance &&
        (!soundRequired || sound != null) &&
        (!vibrationRequired || shouldVibrate())
}
