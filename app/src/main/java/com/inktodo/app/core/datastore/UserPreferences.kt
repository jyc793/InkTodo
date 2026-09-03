package com.inktodo.app.core.datastore

import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode

data class UserPreferences(
    val focusMinutes: Int = DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
    val lastReminderType: ReminderType = ReminderType.AT_TIME,
    val lastUseSystemAlarm: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val taskSortOrder: TaskSortOrder = TaskSortOrder.TIME_ASCENDING,
    val widgetMaxTasks: Int = DEFAULT_WIDGET_MAX_TASKS,
    val pomodoroSession: PomodoroSessionPreferences = PomodoroSessionPreferences(),
    val fullFocus: FullFocusPreferences = FullFocusPreferences(),
) {
    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val DEFAULT_WIDGET_MAX_TASKS = 5
        const val MIN_TIMER_MINUTES = 1
        const val MAX_TIMER_MINUTES = 24 * 60
        const val MIN_WIDGET_TASKS = 1
        const val MAX_WIDGET_TASKS = 20
    }
}

enum class PomodoroPhase { FOCUS, BREAK }

enum class PomodoroRunState { IDLE, RUNNING, PAUSED }

data class PomodoroSessionPreferences(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val runState: PomodoroRunState = PomodoroRunState.IDLE,
    val targetEndEpochMillis: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
)

data class FullFocusPreferences(
    val enabled: Boolean = false,
    val pinSaltBase64: String = "",
    val pinVerifierBase64: String = "",
    val dndManaged: Boolean = false,
    val previousInterruptionFilter: Int = DEFAULT_INTERRUPTION_FILTER,
    val entryPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val entryTargetEndEpochMillis: Long = 0L,
) {
    companion object {
        const val DEFAULT_INTERRUPTION_FILTER = 1
    }
}
