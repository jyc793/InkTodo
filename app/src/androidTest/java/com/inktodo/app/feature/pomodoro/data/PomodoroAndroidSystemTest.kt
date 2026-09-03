package com.inktodo.app.feature.pomodoro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.alarm.AndroidPomodoroAlarmScheduler
import com.inktodo.app.core.alarm.PomodoroAlarmPendingIntents
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
