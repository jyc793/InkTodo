package com.inktodo.app.feature.settings.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.inktodo.app.feature.settings.domain.ReliabilityAction

data class SettingsOpenResult(val opened: Boolean, val message: String? = null)

class ReminderSettingsNavigator(context: Context) {
    private val applicationContext = context.applicationContext
    private val packageUri = Uri.parse("package:${applicationContext.packageName}")

    fun open(action: ReliabilityAction, channelId: String? = null): SettingsOpenResult {
        if (action == ReliabilityAction.NONE ||
            action == ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION
        ) return SettingsOpenResult(false)

        val primary = primaryIntent(action, channelId)
        val usedFallback = !canResolve(primary)
        val target = if (usedFallback) appDetailsIntent() else primary
        return try {
            applicationContext.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SettingsOpenResult(
                opened = true,
                message = if (usedFallback) {
                    "系统未提供对应页面，已打开应用详情；请按说明手动确认。"
                } else {
                    null
                },
            )
        } catch (_: ActivityNotFoundException) {
            SettingsOpenResult(false, "系统没有可打开的设置页面。")
        } catch (_: SecurityException) {
            SettingsOpenResult(false, "系统限制了该设置页面，请从应用详情中手动开启。")
        }
    }

    fun canOpen(action: ReliabilityAction, channelId: String? = null): Boolean = when (action) {
        ReliabilityAction.NONE, ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION -> false
        else -> canResolve(primaryIntent(action, channelId)) || canResolve(appDetailsIntent())
    }

    private fun primaryIntent(action: ReliabilityAction, channelId: String?): Intent = when (action) {
        ReliabilityAction.OPEN_NOTIFICATION_SETTINGS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
        ReliabilityAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS ->
            if (channelId == null) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
            } else {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        ReliabilityAction.OPEN_EXACT_ALARM_SETTINGS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } else {
            appDetailsIntent()
        }
        ReliabilityAction.OPEN_FULL_SCREEN_SETTINGS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            } else {
                appDetailsIntent()
            }
        ReliabilityAction.OPEN_OVERLAY_SETTINGS ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        ReliabilityAction.OPEN_BATTERY_SETTINGS ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        ReliabilityAction.OPEN_HYPEROS_AUTOSTART -> Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        }
        ReliabilityAction.OPEN_APP_DETAILS -> appDetailsIntent()
        ReliabilityAction.NONE, ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION -> appDetailsIntent()
    }

    private fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    private fun canResolve(intent: Intent): Boolean =
        intent.resolveActivity(applicationContext.packageManager) != null
}
