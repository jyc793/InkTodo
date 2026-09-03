package com.inktodo.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStorePreferencesRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var preferencesFile: File
    private lateinit var repository: DataStorePreferencesRepository

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferencesFile = File(context.cacheDir, "preferences-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = DataStorePreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { preferencesFile },
            ),
        )
    }

    @After
    fun cleanUp() {
        scope.cancel()
        preferencesFile.delete()
    }

    @Test
    fun defaultsContainOnlyReusablePreferences() = runBlocking {
        val values = repository.preferences.first()

        assertEquals(25, values.focusMinutes)
        assertEquals(5, values.breakMinutes)
        assertEquals(ReminderType.AT_TIME, values.lastReminderType)
        assertFalse(values.lastUseSystemAlarm)
        assertEquals(ThemeMode.SYSTEM, values.themeMode)
    }

    @Test
    fun allSupportedHabitsArePersisted() = runBlocking {
        repository.setPomodoroDurations(37, 8)
        repository.setLastReminderType(ReminderType.TIME_RANGE)
        repository.setLastUseSystemAlarm(true)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setTaskSortOrder(TaskSortOrder.CREATED_DESCENDING)
        repository.setWidgetMaxTasks(9)
        repository.setPomodoroSession(
            PomodoroSessionPreferences(
                phase = PomodoroPhase.BREAK,
                runState = PomodoroRunState.PAUSED,
                pausedRemainingMillis = 123_000L,
            ),
        )

        assertEquals(
            UserPreferences(
                focusMinutes = 37,
                breakMinutes = 8,
                lastReminderType = ReminderType.TIME_RANGE,
                lastUseSystemAlarm = true,
                themeMode = ThemeMode.DARK,
                taskSortOrder = TaskSortOrder.CREATED_DESCENDING,
                widgetMaxTasks = 9,
                pomodoroSession = PomodoroSessionPreferences(
                    phase = PomodoroPhase.BREAK,
                    runState = PomodoroRunState.PAUSED,
                    pausedRemainingMillis = 123_000L,
                ),
            ),
            repository.preferences.first(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPomodoroDurationIsRejected() = runBlocking {
        repository.setPomodoroDurations(0, 5)
    }
}
