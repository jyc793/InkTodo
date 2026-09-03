package com.inktodo.app.feature.settings.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.notification.AndroidTaskNotificationController
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_PRE_REMINDER
import com.inktodo.app.feature.settings.domain.ReliabilityAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderReliabilityAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun checkerReturnsEveryReadableCoreCapabilityWithoutPlaceholderState() {
        val checker = AndroidReminderReliabilityChecker(
            context,
            AndroidTaskNotificationController(context),
        )

        val snapshot = checker.check()
        val ids = snapshot.items.map { it.id }.toSet()

        assertTrue(ids.containsAll(CORE_ITEM_IDS))
        assertEquals(ids.size, snapshot.items.size)
    }

    @Test
    fun officialSettingsAndAppDetailsFallbackResolveOnConnectedDevice() {
        val navigator = ReminderSettingsNavigator(context)
        val actions = buildList {
            add(ReliabilityAction.OPEN_NOTIFICATION_SETTINGS)
            add(ReliabilityAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS)
            add(ReliabilityAction.OPEN_EXACT_ALARM_SETTINGS)
            add(ReliabilityAction.OPEN_FULL_SCREEN_SETTINGS)
            add(ReliabilityAction.OPEN_OVERLAY_SETTINGS)
            add(ReliabilityAction.OPEN_BATTERY_SETTINGS)
            add(ReliabilityAction.OPEN_APP_DETAILS)
            if (isXiaomiDevice()) add(ReliabilityAction.OPEN_HYPEROS_AUTOSTART)
        }

        actions.forEach { action ->
            assertTrue(
                "No safe settings destination for $action",
                navigator.canOpen(
                    action,
                    CHANNEL_PRE_REMINDER.takeIf {
                        action == ReliabilityAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS
                    },
                ),
            )
        }
    }

    private fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Redmi", ignoreCase = true) ||
            Build.BRAND.equals("Xiaomi", ignoreCase = true)

    private companion object {
        val CORE_ITEM_IDS = setOf(
            "notifications",
            "exact_alarm",
            "full_screen",
            "channel_task_pre_reminder_v1",
            "overlay",
            "channel_task_formal_reminder_v3",
            "channel_task_range_alert_v2",
            "channel_task_range_persistent_v2",
            "reminder_health",
            "lock_screen",
            "battery_unrestricted",
            "system_clock",
        )
    }
}
