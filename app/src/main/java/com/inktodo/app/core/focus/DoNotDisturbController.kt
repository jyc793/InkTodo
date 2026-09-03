package com.inktodo.app.core.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class DndActivation(val previousInterruptionFilter: Int)

interface DoNotDisturbController {
    val hasPolicyAccess: Boolean
    fun settingsIntent(): Intent
    fun enable(): DndActivation?
    fun disable(activation: DndActivation)
}

class AndroidDoNotDisturbController(context: Context) : DoNotDisturbController {
    private val notificationManager =
        context.applicationContext.getSystemService(NotificationManager::class.java)

    override val hasPolicyAccess: Boolean
        get() = notificationManager.isNotificationPolicyAccessGranted

    override fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    override fun enable(): DndActivation? {
        if (!hasPolicyAccess) return null
        return runCatching {
            val activation = DndActivation(notificationManager.currentInterruptionFilter)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            activation
        }.getOrNull()
    }

    override fun disable(activation: DndActivation) {
        if (!hasPolicyAccess) return
        runCatching {
            val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                activation.previousInterruptionFilter.takeIf(::isKnownFilter)
                    ?: NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
        }
    }

    private fun isKnownFilter(value: Int): Boolean = value in setOf(
        NotificationManager.INTERRUPTION_FILTER_ALL,
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_NONE,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
    )
}
