package com.inktodo.app.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReliabilityPolicyTest {
    @Test
    fun enabledCoreCapabilitiesAreReportedWithoutFakeHyperOsState() {
        val result = ReminderReliabilityPolicy.evaluate(capabilities())

        assertEquals(0, result.disabledCount)
        assertEquals(1, result.confirmationCount)
        assertEquals(ReliabilityState.SYSTEM_LIMITED, result.item("lock_screen").state)
        assertTrue(result.summary.contains("1 项需手动确认"))
    }

    @Test
    fun missingRuntimeNotificationPermissionUsesContextualPermissionRequest() {
        val result = ReminderReliabilityPolicy.evaluate(
            capabilities(notificationPermissionGranted = false),
        )

        assertEquals(ReliabilityState.DISABLED, result.item("notifications").state)
        assertEquals(
            ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION,
            result.item("notifications").action,
        )
    }

    @Test
    fun missingOverlayPermissionExplainsWhyUnlockedPhoneCannotAutoShowDialog() {
        val result = ReminderReliabilityPolicy.evaluate(
            capabilities(overlayPermissionGranted = false),
        )

        assertEquals(ReliabilityState.DISABLED, result.item("overlay").state)
        assertEquals(ReliabilityAction.OPEN_OVERLAY_SETTINGS, result.item("overlay").action)
        assertTrue(result.item("overlay").description.contains("无需点击通知"))
    }

    @Test
    fun disabledCoreCapabilitiesAreCountedSeparatelyFromManualConfirmation() {
        val result = ReminderReliabilityPolicy.evaluate(
            capabilities(
                exactAlarmsEnabled = false,
                fullScreenIntentEnabled = false,
                strongNotificationChannelsEnabled = false,
                batteryUnrestricted = false,
            ),
        )

        assertEquals(4, result.disabledCount)
        assertEquals(1, result.confirmationCount)
        assertTrue(result.summary.contains("4 项需要设置"))
    }

    @Test
    fun hyperOsAddsHonestManualAutostartAndBackgroundChecks() {
        val result = ReminderReliabilityPolicy.evaluate(capabilities(isHyperOsDevice = true))

        assertEquals(ReliabilityState.SYSTEM_LIMITED, result.item("hyperos_autostart").state)
        assertEquals(
            ReliabilityAction.OPEN_HYPEROS_AUTOSTART,
            result.item("hyperos_autostart").action,
        )
        assertEquals(ReliabilityState.SYSTEM_LIMITED, result.item("hyperos_background").state)
        assertEquals(3, result.confirmationCount)
    }

    @Test
    fun unavailableSystemClockIsReportedAsSystemLimit() {
        val result = ReminderReliabilityPolicy.evaluate(
            capabilities(systemClockAvailable = false),
        )

        assertEquals(ReliabilityState.SYSTEM_LIMITED, result.item("system_clock").state)
        assertEquals(ReliabilityAction.OPEN_APP_DETAILS, result.item("system_clock").action)
    }

    private fun capabilities(
        notificationPermissionGranted: Boolean = true,
        appNotificationsEnabled: Boolean = true,
        exactAlarmsEnabled: Boolean = true,
        fullScreenIntentEnabled: Boolean = true,
        strongNotificationChannelsEnabled: Boolean = true,
        batteryUnrestricted: Boolean = true,
        systemClockAvailable: Boolean = true,
        isHyperOsDevice: Boolean = false,
        overlayPermissionGranted: Boolean = true,
    ) = PlatformReminderCapabilities(
        notificationPermissionGranted = notificationPermissionGranted,
        appNotificationsEnabled = appNotificationsEnabled,
        exactAlarmsEnabled = exactAlarmsEnabled,
        fullScreenIntentEnabled = fullScreenIntentEnabled,
        strongNotificationChannelsEnabled = strongNotificationChannelsEnabled,
        batteryUnrestricted = batteryUnrestricted,
        systemClockAvailable = systemClockAvailable,
        isHyperOsDevice = isHyperOsDevice,
        overlayPermissionGranted = overlayPermissionGranted,
    )

    private fun ReminderReliabilitySnapshot.item(id: String): ReliabilityItem =
        items.single { it.id == id }
}
