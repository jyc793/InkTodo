package com.inktodo.app.feature.pomodoro.domain

import com.inktodo.app.core.alarm.PomodoroAlarmScheduler
import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.PomodoroSessionPreferences
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import com.inktodo.app.core.notification.PomodoroNotificationController
import com.inktodo.app.core.datastore.FullFocusPreferences
import com.inktodo.app.core.focus.DndActivation
import com.inktodo.app.core.focus.DoNotDisturbController
import com.inktodo.app.core.focus.FullFocusController
import android.content.Intent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroSessionLogicTest {
    @Test
    fun customDurationsStartFromAbsoluteSystemTime() {
        val preferences = UserPreferences(focusMinutes = 37, breakMinutes = 8)

        val session = PomodoroSessionLogic.start(
            preferences.pomodoroSession,
            preferences,
            nowEpochMillis = 1_000L,
        )

        assertEquals(PomodoroRunState.RUNNING, session.runState)
        assertEquals(1_000L + 37L * 60_000L, session.targetEndEpochMillis)
    }

    @Test
    fun staleClockTickNeverDisplaysMoreThanConfiguredDuration() {
        val preferences = UserPreferences(focusMinutes = 45)
        val session = PomodoroSessionLogic.start(
            preferences.pomodoroSession,
            preferences,
            nowEpochMillis = 10_000L,
        )

        assertEquals(
            45L * 60_000L,
            PomodoroSessionLogic.remainingMillis(session, preferences, nowEpochMillis = 9_500L),
        )
        assertEquals(
            45L * 60_000L - 1_000L,
            PomodoroSessionLogic.remainingMillis(session, preferences, nowEpochMillis = 11_000L),
        )
    }

    @Test
    fun backgroundTimeJumpUsesTargetInsteadOfTickCount() {
        val preferences = UserPreferences(focusMinutes = 25)
        val session = PomodoroSessionLogic.start(preferences.pomodoroSession, preferences, 10_000L)

        assertEquals(
            5L * 60_000L,
            PomodoroSessionLogic.remainingMillis(
                session,
                preferences,
                nowEpochMillis = 10_000L + 20L * 60_000L,
            ),
        )
    }

    @Test
    fun pauseAndResumePreserveRealRemainingDuration() {
        val preferences = UserPreferences(focusMinutes = 25)
        val running = PomodoroSessionLogic.start(preferences.pomodoroSession, preferences, 0L)
        val paused = PomodoroSessionLogic.pause(running, 5L * 60_000L)
        val resumed = PomodoroSessionLogic.resume(paused, 99L * 60_000L)

        assertEquals(20L * 60_000L, paused.pausedRemainingMillis)
        assertEquals(119L * 60_000L, resumed.targetEndEpochMillis)
    }

    @Test
    fun ordinaryImmersiveStatePersistsThroughPauseAndResumeButClearsOnReset() {
        val preferences = UserPreferences(focusMinutes = 25)
        val running = PomodoroSessionLogic.start(preferences.pomodoroSession, preferences, 0L)
            .copy(ordinaryImmersive = true)
        val paused = PomodoroSessionLogic.pause(running, 60_000L)
        val resumed = PomodoroSessionLogic.resume(paused, 120_000L)

        assertTrue(paused.ordinaryImmersive)
        assertTrue(resumed.ordinaryImmersive)
        assertFalse(PomodoroSessionLogic.reset(resumed).ordinaryImmersive)
        assertFalse(PomodoroSessionLogic.finish(resumed).ordinaryImmersive)
    }

    @Test
    fun floatingTimerPersistsThroughPauseAndResumeButClearsOnResetAndFinish() {
        val preferences = UserPreferences(focusMinutes = 25)
        val running = PomodoroSessionLogic.start(preferences.pomodoroSession, preferences, 0L)
            .copy(floatingVisible = true)
        val paused = PomodoroSessionLogic.pause(running, 60_000L)
        val resumed = PomodoroSessionLogic.resume(paused, 120_000L)

        assertTrue(paused.floatingVisible)
        assertTrue(resumed.floatingVisible)
        assertFalse(PomodoroSessionLogic.reset(resumed).floatingVisible)
        assertFalse(PomodoroSessionLogic.finish(resumed).floatingVisible)
    }

    @Test
    fun coordinatorDistinguishesOrdinaryStartFromFullFocusStart() = runBlocking {
        val ordinaryRepository = FakePreferencesRepository(UserPreferences())
        val fullFocusRepository = FakePreferencesRepository(UserPreferences())

        PomodoroCoordinator(ordinaryRepository, FakeScheduler(), FakeNotifications()).start()
        PomodoroCoordinator(fullFocusRepository, FakeScheduler(), FakeNotifications())
            .start(ordinaryImmersive = false)

        assertTrue(ordinaryRepository.value.pomodoroSession.ordinaryImmersive)
        assertFalse(fullFocusRepository.value.pomodoroSession.ordinaryImmersive)
    }

    @Test
    fun presentationOnlyChangesDoNotRescheduleOrCancelTheAlarm() = runBlocking {
        val repository = FakePreferencesRepository(UserPreferences())
        val scheduler = FakeScheduler()
        val coordinator = PomodoroCoordinator(repository, scheduler, FakeNotifications())
        coordinator.start()
        val scheduledAfterStart = scheduler.scheduled.toList()
        val cancelsAfterStart = scheduler.cancelCount

        coordinator.setOrdinaryImmersive(false)
        coordinator.setOrdinaryImmersive(true)
        coordinator.setFloatingVisible(true)
        coordinator.setFloatingVisible(false)

        assertEquals(scheduledAfterStart, scheduler.scheduled)
        assertEquals(cancelsAfterStart, scheduler.cancelCount)
    }

    @Test
    fun floatingTimerCannotBeEnabledWithoutAnActiveSession() = runBlocking {
        val repository = FakePreferencesRepository(UserPreferences())
        val coordinator = PomodoroCoordinator(repository, FakeScheduler(), FakeNotifications())

        coordinator.setFloatingVisible(true)

        assertFalse(repository.value.pomodoroSession.floatingVisible)
    }

    @Test
    fun completionMovesFocusToIdleBreakAndBreakBackToFocus() {
        val focusDone = PomodoroSessionLogic.finish(
            PomodoroSessionPreferences(phase = PomodoroPhase.FOCUS),
        )
        val breakDone = PomodoroSessionLogic.finish(
            PomodoroSessionPreferences(phase = PomodoroPhase.BREAK),
        )

        assertEquals(PomodoroPhase.BREAK, focusDone.phase)
        assertEquals(PomodoroRunState.IDLE, focusDone.runState)
        assertEquals(PomodoroPhase.FOCUS, breakDone.phase)
    }

    @Test
    fun coordinatorRecoversFutureAlarmAndFinishesOverdueSessionOnce() = runBlocking {
        val clock = MutableClock(Instant.ofEpochMilli(1_000L))
        val repository = FakePreferencesRepository(
            UserPreferences(
                pomodoroSession = PomodoroSessionPreferences(
                    phase = PomodoroPhase.FOCUS,
                    runState = PomodoroRunState.RUNNING,
                    targetEndEpochMillis = 11_000L,
                ),
            ),
        )
        val scheduler = FakeScheduler()
        val notifications = FakeNotifications()
        val coordinator = PomodoroCoordinator(repository, scheduler, notifications, clock)

        coordinator.recover()
        assertEquals(listOf(11_000L), scheduler.scheduled)
        assertFalse(notifications.wasShown)

        clock.instant = Instant.ofEpochMilli(12_000L)
        assertTrue(coordinator.finishIfDue())
        assertFalse(coordinator.finishIfDue())
        assertEquals(PomodoroPhase.BREAK, repository.value.pomodoroSession.phase)
        assertTrue(notifications.wasShown)
    }

    @Test
    fun `completion automatically exits full focus and disables app dnd`() = runBlocking {
        val clock = MutableClock(Instant.ofEpochMilli(12_000L))
        val repository = FakePreferencesRepository(
            UserPreferences(
                pomodoroSession = PomodoroSessionPreferences(
                    phase = PomodoroPhase.FOCUS,
                    runState = PomodoroRunState.RUNNING,
                    targetEndEpochMillis = 11_000L,
                ),
            ),
        )
        val dnd = FakeDnd()
        val fullFocus = FullFocusController(repository, dnd)
        assertEquals(
            com.inktodo.app.core.focus.FullFocusStartResult.STARTED,
            fullFocus.activate("123456", enableDnd = true),
        )
        val notifications = FakeNotifications()
        val coordinator = PomodoroCoordinator(
            repository,
            FakeScheduler(),
            notifications,
            clock,
            fullFocus,
        )

        assertTrue(coordinator.finishIfDue())

        assertFalse(repository.value.fullFocus.enabled)
        assertEquals(1, dnd.disableCount)
        assertTrue(notifications.wasShown)
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private class FakeScheduler : PomodoroAlarmScheduler {
        val scheduled = mutableListOf<Long>()
        var cancelCount = 0
        override fun schedule(targetEndEpochMillis: Long) { scheduled += targetEndEpochMillis }
        override fun cancel() { cancelCount += 1 }
    }

    private class FakeNotifications : PomodoroNotificationController {
        var wasShown = false
        override fun showCompletion(completedPhase: PomodoroPhase) { wasShown = true }
    }

    private class FakeDnd : DoNotDisturbController {
        var disableCount = 0
        override val hasPolicyAccess: Boolean = true
        override fun settingsIntent(): Intent = error("not used")
        override fun enable(): DndActivation = DndActivation(3)
        override fun disable(activation: DndActivation) {
            disableCount += 1
        }
    }

    private class FakePreferencesRepository(initial: UserPreferences) : PreferencesRepository {
        private val state = MutableStateFlow(initial)
        val value: UserPreferences get() = state.value
        override val preferences: Flow<UserPreferences> = state
        override suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) {
            state.value = state.value.copy(focusMinutes = focusMinutes, breakMinutes = breakMinutes)
        }
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
