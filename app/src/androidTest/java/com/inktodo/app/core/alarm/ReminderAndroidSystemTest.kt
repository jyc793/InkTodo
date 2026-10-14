package com.inktodo.app.core.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.notification.AndroidTaskNotificationController
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_FORMAL_REMINDER
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_PRE_REMINDER
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_RANGE_REMINDER
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_RANGE_RESTORE
import com.inktodo.app.core.notification.AndroidTaskNotificationController.Companion.CHANNEL_RANGE_PERSISTENT
import com.inktodo.app.core.alarm.SystemAlarmIntentFactory
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import com.inktodo.app.core.reminder.FormalReminderActivity
import com.inktodo.app.core.reminder.FormalReminderOverlayService
import com.inktodo.app.core.reminder.formalOverlayPalette
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAndroidSystemTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = AndroidTaskNotificationController(context, ZoneOffset.UTC)
    private val now = Instant.parse("2026-08-25T09:00:00Z")
    private val scheduler = AndroidReminderScheduler(
        context = context,
        notifications = notifications,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @After
    fun cancelTestAlarms() {
        scheduler.cancel(9_801)
        scheduler.cancel(9_803)
    }

    @Test
    fun channelsAndOngoingRangeNotificationUseRequiredSystemFlags() {
        notifications.createChannels()
        val manager = context.getSystemService(NotificationManager::class.java)

        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            manager.getNotificationChannel(CHANNEL_PRE_REMINDER).importance,
        )
        assertNotNull(manager.getNotificationChannel(CHANNEL_PRE_REMINDER).sound)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(CHANNEL_FORMAL_REMINDER).importance,
        )
        assertNull(manager.getNotificationChannel(CHANNEL_FORMAL_REMINDER).sound)
        listOf(
            "task_formal_reminder_v1",
            "task_formal_reminder_v2",
            "task_formal_pending_v1",
            "task_range_reminder_v1",
            "task_range_restore_v1",
        ).forEach { obsoleteId -> assertNull(manager.getNotificationChannel(obsoleteId)) }
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(CHANNEL_RANGE_REMINDER).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            manager.getNotificationChannel(CHANNEL_RANGE_PERSISTENT).importance,
        )

        val notification = notifications.buildRangeNotification(
            ScheduledTaskSnapshot(
                taskId = 9_802,
                title = "Executor 验证",
                kind = ReminderSnapshotKind.TIME_RANGE,
                startAt = now,
                endAt = now.plusSeconds(7_200),
            ),
            alert = true,
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertEquals(CHANNEL_RANGE_PERSISTENT, notification.channelId)
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.deleteIntent)
    }

    @Test
    fun formalReminderCannotAutoCancelAndAlwaysOpensDedicatedConfirmationActivity() {
        notifications.createChannels()
        val snapshot = ScheduledTaskSnapshot(
            taskId = 9_804,
            title = "确认暑期课程",
            note = "带宿舍钥匙和课程资料",
            kind = ReminderSnapshotKind.AT_TIME,
            startAt = now,
        )

        val notification = notifications.buildFormalNotification(snapshot)
        val restored = notifications.buildFormalNotification(snapshot, alert = false)
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, FormalReminderActivity::class.java),
            0,
        )
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, FormalReminderOverlayService::class.java),
            0,
        )
        val confirmationIntent = FormalReminderActivity.createIntent(
            context,
            snapshot.taskId,
            snapshot.title,
            snapshot.note,
        )

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(CHANNEL_FORMAL_REMINDER, notification.channelId)
        assertEquals(CHANNEL_FORMAL_REMINDER, restored.channelId)
        assertNotNull(notification.contentIntent)
        assertEquals(false, activityInfo.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
        assertEquals(
            snapshot.note,
            confirmationIntent.getStringExtra(FormalReminderActivity.EXTRA_TASK_NOTE),
        )
        assertEquals(false, serviceInfo.exported)
        assertTrue(
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0,
        )
        assertNotNull(context.getDrawable(com.inktodo.app.R.drawable.ink_mountain_master_v1))
        assertNotNull(context.getDrawable(com.inktodo.app.R.drawable.widget_mountain_cool_v1))
        assertNotNull(context.getDrawable(com.inktodo.app.R.drawable.ink_cloud_ribbon_v1))
    }

    @Test
    fun darkFormalOverlayPaletteKeepsTaskTextClearlyReadable() {
        val palette = formalOverlayPalette(darkTheme = true)
        val card = ColorUtils.compositeColors(palette.card, Color.rgb(3, 10, 8))

        assertTrue(ColorUtils.calculateContrast(palette.ink, card) >= 7.0)
        assertTrue(ColorUtils.calculateContrast(palette.inkSoft, card) >= 4.5)
        assertTrue(ColorUtils.calculateContrast(Color.WHITE, palette.button) >= 4.5)
    }

    @Test
    fun lightFormalOverlayPaletteIsCoolAndReadable() {
        val palette = formalOverlayPalette(darkTheme = false)
        val card = ColorUtils.compositeColors(palette.card, Color.rgb(216, 233, 239))

        assertTrue(Color.blue(palette.card) > Color.red(palette.card))
        assertTrue(ColorUtils.calculateContrast(palette.ink, card) >= 7.0)
        assertTrue(ColorUtils.calculateContrast(palette.inkSoft, card) >= 4.5)
        assertTrue(ColorUtils.calculateContrast(Color.WHITE, palette.button) >= 4.5)
    }

    @Test
    fun systemClockTaskDoesNotCreateDuplicateAppFormalPendingIntent() {
        val snapshot = ScheduledTaskSnapshot(
            taskId = 9_803,
            title = "系统闹钟会议",
            kind = ReminderSnapshotKind.AT_TIME,
            startAt = now.plusSeconds(3_600),
            useSystemAlarm = true,
        )

        val result = scheduler.schedule(snapshot)

        assertEquals(listOf(ReminderAlarmKind.PRE_REMINDER), result.plan.alarms.map { it.kind })
        assertNotNull(ReminderAlarmPendingIntents.find(context, 9_803, ReminderAlarmKind.PRE_REMINDER))
        assertNull(ReminderAlarmPendingIntents.find(context, 9_803, ReminderAlarmKind.FORMAL_REMINDER))
    }

    @Test
    fun schedulerCreatesBothAtTimeAlarmsAndCancellationRemovesTheirPendingIntents() {
        val snapshot = ScheduledTaskSnapshot(
            taskId = 9_801,
            title = "上课",
            kind = ReminderSnapshotKind.AT_TIME,
            startAt = now.plusSeconds(3_600),
        )

        val result = scheduler.schedule(snapshot)

        assertEquals(
            listOf(ReminderAlarmKind.PRE_REMINDER, ReminderAlarmKind.FORMAL_REMINDER),
            result.plan.alarms.map(PlannedReminderAlarm::kind),
        )
        assertNotNull(ReminderAlarmPendingIntents.find(context, 9_801, ReminderAlarmKind.PRE_REMINDER))
        assertNotNull(ReminderAlarmPendingIntents.find(context, 9_801, ReminderAlarmKind.FORMAL_REMINDER))

        scheduler.cancel(9_801)

        assertNull(ReminderAlarmPendingIntents.find(context, 9_801, ReminderAlarmKind.PRE_REMINDER))
        assertNull(ReminderAlarmPendingIntents.find(context, 9_801, ReminderAlarmKind.FORMAL_REMINDER))
    }

    @Test
    fun HyperOsClockResolvesOfficialSetAlarmIntentWithoutCreatingAlarm() {
        val intent = SystemAlarmIntentFactory.create(
            SystemAlarmRequest("会议", now.plusSeconds(3_600)),
            ZoneOffset.UTC,
        )

        assertNotNull(intent.resolveActivity(context.packageManager))
    }
}
