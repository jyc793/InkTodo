package com.inktodo.app.feature.pomodoro.domain

import com.inktodo.app.core.alarm.PomodoroAlarmScheduler
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.datastore.PomodoroSessionPreferences
import com.inktodo.app.core.notification.PomodoroNotificationController
import com.inktodo.app.core.focus.FullFocusController
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PomodoroCoordinator(
    private val preferencesRepository: PreferencesRepository,
    private val alarmScheduler: PomodoroAlarmScheduler,
    private val notifications: PomodoroNotificationController,
    private val clock: Clock = Clock.systemUTC(),
    private val fullFocusController: FullFocusController? = null,
) {
    private val mutex = Mutex()

    suspend fun start() = mutate { preferences ->
        PomodoroSessionLogic.start(preferences.pomodoroSession, preferences, clock.millis())
    }

    suspend fun pause() = mutate { preferences ->
        PomodoroSessionLogic.pause(preferences.pomodoroSession, clock.millis())
    }

    suspend fun resume() = mutate { preferences ->
        PomodoroSessionLogic.resume(preferences.pomodoroSession, clock.millis())
    }

    suspend fun reset() = mutate { preferences ->
        PomodoroSessionLogic.reset(preferences.pomodoroSession)
    }

    suspend fun end() = mutex.withLock {
        alarmScheduler.cancel()
        preferencesRepository.setPomodoroSession(PomodoroSessionPreferences())
        fullFocusController?.deactivate()
    }

    suspend fun finishIfDue(showNotification: Boolean = true): Boolean = mutex.withLock {
        val preferences = preferencesRepository.preferences.first()
        val session = preferences.pomodoroSession
        if (session.runState != PomodoroRunState.RUNNING ||
            session.targetEndEpochMillis > clock.millis()
        ) return@withLock false

        alarmScheduler.cancel()
        preferencesRepository.setPomodoroSession(PomodoroSessionLogic.finish(session))
        fullFocusController?.deactivate()
        if (showNotification) notifications.showCompletion(session.phase)
        true
    }

    suspend fun recover() = mutex.withLock {
        val session = preferencesRepository.preferences.first().pomodoroSession
        if (session.runState != PomodoroRunState.RUNNING) {
            alarmScheduler.cancel()
            fullFocusController?.deactivate()
        } else if (session.targetEndEpochMillis <= clock.millis()) {
            alarmScheduler.cancel()
            preferencesRepository.setPomodoroSession(PomodoroSessionLogic.finish(session))
            fullFocusController?.deactivate()
            notifications.showCompletion(session.phase)
        } else {
            alarmScheduler.schedule(session.targetEndEpochMillis)
        }
    }

    private suspend fun mutate(
        transform: (com.inktodo.app.core.datastore.UserPreferences) -> PomodoroSessionPreferences,
    ) = mutex.withLock {
        val preferences = preferencesRepository.preferences.first()
        val session = transform(preferences)
        preferencesRepository.setPomodoroSession(session)
        if (session.runState == PomodoroRunState.RUNNING) {
            alarmScheduler.schedule(session.targetEndEpochMillis)
        } else {
            alarmScheduler.cancel()
        }
    }
}
