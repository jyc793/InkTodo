package com.inktodo.app.core.focus

import android.content.Intent
import com.inktodo.app.core.datastore.FullFocusPreferences
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.PomodoroSessionPreferences
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullFocusControllerTest {
    @Test
    fun `pin verifier is salted and never stores plaintext`() {
        val credential = FullFocusPinSecurity.create("123456", ByteArray(16) { it.toByte() })

        assertNotEquals("123456", credential.verifierBase64)
        assertFalse(credential.verifierBase64.contains("123456"))
        assertTrue(FullFocusPinSecurity.verify("123456", credential))
        assertFalse(FullFocusPinSecurity.verify("654321", credential))
        assertFalse(FullFocusPinSecurity.verify("12345", credential))
    }

    @Test
    fun `correct third confirmation clears focus and app dnd rule`() = runBlocking {
        val repository = FakePreferencesRepository(
            UserPreferences(
                pomodoroSession = PomodoroSessionPreferences(
                    runState = PomodoroRunState.RUNNING,
                    targetEndEpochMillis = 99_000L,
                ),
            ),
        )
        val dnd = FakeDnd(hasAccess = true)
        val controller = FullFocusController(repository, dnd)

        assertEquals(FullFocusStartResult.STARTED, controller.activate("123456", enableDnd = true))
        assertTrue(repository.value.fullFocus.enabled)
        assertTrue(repository.value.fullFocus.dndManaged)
        assertEquals(1, dnd.enableCount)

        assertFalse(controller.verifyAndDeactivate("654321"))
        assertTrue(repository.value.fullFocus.enabled)
        assertEquals(0, dnd.disableCount)

        assertTrue(controller.verifyAndDeactivate("123456"))
        assertFalse(repository.value.fullFocus.enabled)
        assertEquals(1, dnd.disableCount)
    }

    @Test
    fun `dnd access is required before focus state is written`() = runBlocking {
        val repository = FakePreferencesRepository(UserPreferences())
        val controller = FullFocusController(repository, FakeDnd(hasAccess = false))

        assertEquals(
            FullFocusStartResult.DND_ACCESS_REQUIRED,
            controller.activate("123456", enableDnd = true),
        )
        assertFalse(repository.value.fullFocus.enabled)
    }

    private class FakeDnd(
        private val hasAccess: Boolean,
    ) : DoNotDisturbController {
        var enableCount = 0
        var disableCount = 0
        override val hasPolicyAccess: Boolean get() = hasAccess
        override fun settingsIntent(): Intent = error("not used")
        override fun enable(): DndActivation? {
            enableCount += 1
            return DndActivation(previousInterruptionFilter = 3)
        }
        override fun disable(activation: DndActivation) {
            disableCount += 1
            assertEquals(3, activation.previousInterruptionFilter)
        }
    }

    private class FakePreferencesRepository(initial: UserPreferences) : PreferencesRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        override val preferences: Flow<UserPreferences> = state
        override suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) = Unit
        override suspend fun setLastReminderType(value: ReminderType) = Unit
        override suspend fun setLastUseSystemAlarm(value: Boolean) = Unit
        override suspend fun setThemeMode(value: ThemeMode) = Unit
        override suspend fun setTaskSortOrder(value: TaskSortOrder) = Unit
        override suspend fun setWidgetMaxTasks(value: Int) = Unit
        override suspend fun setPomodoroSession(value: PomodoroSessionPreferences) {
            state.value = state.value.copy(pomodoroSession = value)
        }
        override suspend fun setFullFocus(value: FullFocusPreferences) {
            state.value = state.value.copy(fullFocus = value)
        }
    }
}
