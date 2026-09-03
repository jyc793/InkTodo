package com.inktodo.app.feature.pomodoro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.feature.pomodoro.domain.PomodoroCoordinator
import com.inktodo.app.feature.pomodoro.domain.PomodoroSessionLogic
import com.inktodo.app.feature.todo.ui.simpleViewModelFactory
import com.inktodo.app.core.datastore.FullFocusPreferences
import com.inktodo.app.core.focus.FullFocusController
import com.inktodo.app.core.focus.FullFocusStartResult
import android.content.Intent
import java.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val runState: PomodoroRunState = PomodoroRunState.IDLE,
    val focusMinutes: Int = UserPreferences.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = UserPreferences.DEFAULT_BREAK_MINUTES,
    val remainingMillis: Long = UserPreferences.DEFAULT_FOCUS_MINUTES * 60_000L,
    val targetEndEpochMillis: Long = 0L,
    val fullFocus: FullFocusPreferences = FullFocusPreferences(),
) {
    val durationMillis: Long
        get() = (if (phase == PomodoroPhase.FOCUS) focusMinutes else breakMinutes) * 60_000L

    val progress: Float
        get() {
            val total = durationMillis
            return if (total <= 0L) 0f else (remainingMillis.toFloat() / total).coerceIn(0f, 1f)
        }

    val elapsedProgress: Float
        get() = if (runState == PomodoroRunState.IDLE) {
            0f
        } else {
            (1f - progress).coerceIn(0f, 1f)
        }
}

class PomodoroViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val coordinator: PomodoroCoordinator,
    private val fullFocusController: FullFocusController,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val visibleClock = flow {
        while (currentCoroutineContext().isActive) {
            coordinator.finishIfDue(showNotification = true)
            emit(clock.millis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<PomodoroUiState> = combine(
        preferencesRepository.preferences,
        visibleClock,
    ) { preferences, now ->
        val session = preferences.pomodoroSession
        PomodoroUiState(
            phase = session.phase,
            runState = session.runState,
            focusMinutes = preferences.focusMinutes,
            breakMinutes = preferences.breakMinutes,
            remainingMillis = PomodoroSessionLogic.remainingMillis(session, preferences, now),
            targetEndEpochMillis = session.targetEndEpochMillis,
            fullFocus = preferences.fullFocus,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        PomodoroUiState(),
    )

    fun setFocusMinutes(value: Int) = setDurations(focusMinutes = value)
    fun setBreakMinutes(value: Int) = setDurations(breakMinutes = value)
    fun start() = launchAction(coordinator::start)
    fun pause() = launchAction(coordinator::pause)
    fun resume() = launchAction(coordinator::resume)
    fun reset() = launchAction(coordinator::reset)
    fun end() = launchAction(coordinator::end)

    val hasDndPolicyAccess: Boolean
        get() = fullFocusController.hasDndPolicyAccess

    fun dndPolicySettingsIntent(): Intent = fullFocusController.dndPolicySettingsIntent()

    fun startFullFocus(
        pin: String,
        enableDnd: Boolean,
        onResult: (FullFocusStartResult) -> Unit,
    ) {
        if (uiState.value.runState != PomodoroRunState.IDLE) {
            onResult(FullFocusStartResult.DND_ACTIVATION_FAILED)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                if (enableDnd && !fullFocusController.hasDndPolicyAccess) {
                    return@runCatching FullFocusStartResult.DND_ACCESS_REQUIRED
                }
                coordinator.start()
                val activation = fullFocusController.activate(pin, enableDnd)
                if (activation != FullFocusStartResult.STARTED) coordinator.end()
                activation
            }.getOrElse { FullFocusStartResult.DND_ACTIVATION_FAILED }
            onResult(result)
        }
    }

    fun exitFullFocus(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(fullFocusController.verifyAndDeactivate(pin))
        }
    }

    private fun setDurations(focusMinutes: Int? = null, breakMinutes: Int? = null) {
        if (uiState.value.runState != PomodoroRunState.IDLE) return
        viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            preferencesRepository.setPomodoroDurations(
                focusMinutes = focusMinutes ?: preferences.focusMinutes,
                breakMinutes = breakMinutes ?: preferences.breakMinutes,
            )
        }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch { runCatching { action() } }
    }

    companion object {
        fun factory(
            preferencesRepository: PreferencesRepository,
            coordinator: PomodoroCoordinator,
            fullFocusController: FullFocusController,
        ): ViewModelProvider.Factory = simpleViewModelFactory {
            PomodoroViewModel(preferencesRepository, coordinator, fullFocusController)
        }
    }
}
