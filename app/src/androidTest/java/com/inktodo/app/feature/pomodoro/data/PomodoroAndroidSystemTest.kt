package com.inktodo.app.feature.pomodoro.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.alarm.AndroidPomodoroAlarmScheduler
import com.inktodo.app.core.alarm.PomodoroAlarmPendingIntents
import com.inktodo.app.feature.pomodoro.floating.PomodoroFloatingTimerService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroAndroidSystemTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = AndroidPomodoroAlarmScheduler(context)

    @After
    fun cleanUp() = scheduler.cancel()

    @Test
    fun schedulingAndCancellationUseOneStableSystemPendingIntent() {
        scheduler.schedule(System.currentTimeMillis() + 3_600_000L)
        assertNotNull(PomodoroAlarmPendingIntents.find(context))

        scheduler.cancel()
        assertNull(PomodoroAlarmPendingIntents.find(context))
    }

    @Test
    fun floatingTimerServiceIsPrivateAndDeclaresItsForegroundType() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, PomodoroFloatingTimerService::class.java),
            0,
        )

        assertFalse(info.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(
                info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0,
            )
        }
    }
}
