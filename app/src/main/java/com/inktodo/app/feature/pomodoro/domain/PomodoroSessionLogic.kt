package com.inktodo.app.feature.pomodoro.domain

import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.PomodoroSessionPreferences
import com.inktodo.app.core.datastore.UserPreferences

object PomodoroSessionLogic {
    fun durationMillis(phase: PomodoroPhase, preferences: UserPreferences): Long =
        when (phase) {
            PomodoroPhase.FOCUS -> preferences.focusMinutes
            PomodoroPhase.BREAK -> preferences.breakMinutes
        } * MILLIS_PER_MINUTE

    fun remainingMillis(
        session: PomodoroSessionPreferences,
        preferences: UserPreferences,
        nowEpochMillis: Long,
    ): Long = when (session.runState) {
        PomodoroRunState.IDLE -> durationMillis(session.phase, preferences)
        PomodoroRunState.PAUSED -> session.pausedRemainingMillis
        PomodoroRunState.RUNNING -> (session.targetEndEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    }

    fun start(
        session: PomodoroSessionPreferences,
        preferences: UserPreferences,
        nowEpochMillis: Long,
    ): PomodoroSessionPreferences {
        require(session.runState == PomodoroRunState.IDLE)
        return session.copy(
            runState = PomodoroRunState.RUNNING,
            targetEndEpochMillis = nowEpochMillis + durationMillis(session.phase, preferences),
            pausedRemainingMillis = 0L,
        )
    }

    fun pause(session: PomodoroSessionPreferences, nowEpochMillis: Long): PomodoroSessionPreferences {
        require(session.runState == PomodoroRunState.RUNNING)
        return session.copy(
            runState = PomodoroRunState.PAUSED,
            targetEndEpochMillis = 0L,
            pausedRemainingMillis = (session.targetEndEpochMillis - nowEpochMillis).coerceAtLeast(0L),
        )
    }

    fun resume(session: PomodoroSessionPreferences, nowEpochMillis: Long): PomodoroSessionPreferences {
        require(session.runState == PomodoroRunState.PAUSED)
        return session.copy(
            runState = PomodoroRunState.RUNNING,
            targetEndEpochMillis = nowEpochMillis + session.pausedRemainingMillis,
            pausedRemainingMillis = 0L,
        )
    }

    fun reset(session: PomodoroSessionPreferences): PomodoroSessionPreferences =
        session.copy(
            runState = PomodoroRunState.IDLE,
            targetEndEpochMillis = 0L,
            pausedRemainingMillis = 0L,
        )

    fun finish(session: PomodoroSessionPreferences): PomodoroSessionPreferences =
        PomodoroSessionPreferences(
            phase = when (session.phase) {
                PomodoroPhase.FOCUS -> PomodoroPhase.BREAK
                PomodoroPhase.BREAK -> PomodoroPhase.FOCUS
            },
        )

    private const val MILLIS_PER_MINUTE = 60_000L
}
