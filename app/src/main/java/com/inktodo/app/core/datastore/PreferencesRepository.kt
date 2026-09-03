package com.inktodo.app.core.datastore

import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int)
    suspend fun setLastReminderType(value: ReminderType)
    suspend fun setLastUseSystemAlarm(value: Boolean)
    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setTaskSortOrder(value: TaskSortOrder)
    suspend fun setWidgetMaxTasks(value: Int)
    suspend fun setPomodoroSession(value: PomodoroSessionPreferences)
    suspend fun setFullFocus(value: FullFocusPreferences) = Unit
}
