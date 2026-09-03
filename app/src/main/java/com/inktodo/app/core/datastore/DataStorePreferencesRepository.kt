package com.inktodo.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {
    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toUserPreferences)

    override suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) {
        require(focusMinutes in UserPreferences.MIN_TIMER_MINUTES..UserPreferences.MAX_TIMER_MINUTES)
        require(breakMinutes in UserPreferences.MIN_TIMER_MINUTES..UserPreferences.MAX_TIMER_MINUTES)
        dataStore.edit { values ->
            values[Keys.FOCUS_MINUTES] = focusMinutes
            values[Keys.BREAK_MINUTES] = breakMinutes
        }
    }

    override suspend fun setLastReminderType(value: ReminderType) = edit(Keys.LAST_REMINDER_TYPE, value.name)
    override suspend fun setLastUseSystemAlarm(value: Boolean) = edit(Keys.LAST_USE_SYSTEM_ALARM, value)
    override suspend fun setThemeMode(value: ThemeMode) = edit(Keys.THEME_MODE, value.name)
    override suspend fun setTaskSortOrder(value: TaskSortOrder) = edit(Keys.TASK_SORT_ORDER, value.name)

    override suspend fun setWidgetMaxTasks(value: Int) {
        require(value in UserPreferences.MIN_WIDGET_TASKS..UserPreferences.MAX_WIDGET_TASKS)
        edit(Keys.WIDGET_MAX_TASKS, value)
    }

    override suspend fun setPomodoroSession(value: PomodoroSessionPreferences) {
        require(value.targetEndEpochMillis >= 0L)
        require(value.pausedRemainingMillis >= 0L)
        dataStore.edit { values ->
            values[Keys.POMODORO_PHASE] = value.phase.name
            values[Keys.POMODORO_RUN_STATE] = value.runState.name
            values[Keys.POMODORO_TARGET_END] = value.targetEndEpochMillis
            values[Keys.POMODORO_PAUSED_REMAINING] = value.pausedRemainingMillis
        }
    }

    override suspend fun setFullFocus(value: FullFocusPreferences) {
        require(value.entryTargetEndEpochMillis >= 0L)
        dataStore.edit { values ->
            values[Keys.FULL_FOCUS_ENABLED] = value.enabled
            values[Keys.FULL_FOCUS_PIN_SALT] = value.pinSaltBase64
            values[Keys.FULL_FOCUS_PIN_VERIFIER] = value.pinVerifierBase64
            values[Keys.FULL_FOCUS_DND_MANAGED] = value.dndManaged
            values[Keys.FULL_FOCUS_PREVIOUS_FILTER] = value.previousInterruptionFilter
            values[Keys.FULL_FOCUS_ENTRY_PHASE] = value.entryPhase.name
            values[Keys.FULL_FOCUS_ENTRY_TARGET] = value.entryTargetEndEpochMillis
        }
    }

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun toUserPreferences(values: Preferences): UserPreferences = UserPreferences(
        focusMinutes = (values[Keys.FOCUS_MINUTES] ?: UserPreferences.DEFAULT_FOCUS_MINUTES)
            .coerceIn(UserPreferences.MIN_TIMER_MINUTES, UserPreferences.MAX_TIMER_MINUTES),
        breakMinutes = (values[Keys.BREAK_MINUTES] ?: UserPreferences.DEFAULT_BREAK_MINUTES)
            .coerceIn(UserPreferences.MIN_TIMER_MINUTES, UserPreferences.MAX_TIMER_MINUTES),
        lastReminderType = values[Keys.LAST_REMINDER_TYPE].toEnumOrDefault(ReminderType.AT_TIME),
        lastUseSystemAlarm = values[Keys.LAST_USE_SYSTEM_ALARM] ?: false,
        themeMode = values[Keys.THEME_MODE].toEnumOrDefault(ThemeMode.SYSTEM),
        taskSortOrder = values[Keys.TASK_SORT_ORDER].toEnumOrDefault(TaskSortOrder.TIME_ASCENDING),
        widgetMaxTasks = (values[Keys.WIDGET_MAX_TASKS] ?: UserPreferences.DEFAULT_WIDGET_MAX_TASKS)
            .coerceIn(UserPreferences.MIN_WIDGET_TASKS, UserPreferences.MAX_WIDGET_TASKS),
        pomodoroSession = PomodoroSessionPreferences(
            phase = values[Keys.POMODORO_PHASE].toEnumOrDefault(PomodoroPhase.FOCUS),
            runState = values[Keys.POMODORO_RUN_STATE].toEnumOrDefault(PomodoroRunState.IDLE),
            targetEndEpochMillis = (values[Keys.POMODORO_TARGET_END] ?: 0L).coerceAtLeast(0L),
            pausedRemainingMillis = (values[Keys.POMODORO_PAUSED_REMAINING] ?: 0L).coerceAtLeast(0L),
        ),
        fullFocus = FullFocusPreferences(
            enabled = values[Keys.FULL_FOCUS_ENABLED] ?: false,
            pinSaltBase64 = values[Keys.FULL_FOCUS_PIN_SALT].orEmpty(),
            pinVerifierBase64 = values[Keys.FULL_FOCUS_PIN_VERIFIER].orEmpty(),
            dndManaged = values[Keys.FULL_FOCUS_DND_MANAGED] ?: false,
            previousInterruptionFilter = values[Keys.FULL_FOCUS_PREVIOUS_FILTER]
                ?: FullFocusPreferences.DEFAULT_INTERRUPTION_FILTER,
            entryPhase = values[Keys.FULL_FOCUS_ENTRY_PHASE].toEnumOrDefault(PomodoroPhase.FOCUS),
            entryTargetEndEpochMillis = (values[Keys.FULL_FOCUS_ENTRY_TARGET] ?: 0L).coerceAtLeast(0L),
        ),
    )

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private object Keys {
        val FOCUS_MINUTES = intPreferencesKey("pomodoro_focus_minutes")
        val BREAK_MINUTES = intPreferencesKey("pomodoro_break_minutes")
        val LAST_REMINDER_TYPE = stringPreferencesKey("last_reminder_type")
        val LAST_USE_SYSTEM_ALARM = booleanPreferencesKey("last_use_system_alarm")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TASK_SORT_ORDER = stringPreferencesKey("task_sort_order")
        val WIDGET_MAX_TASKS = intPreferencesKey("widget_max_tasks")
        val POMODORO_PHASE = stringPreferencesKey("pomodoro_phase")
        val POMODORO_RUN_STATE = stringPreferencesKey("pomodoro_run_state")
        val POMODORO_TARGET_END = longPreferencesKey("pomodoro_target_end_epoch_millis")
        val POMODORO_PAUSED_REMAINING = longPreferencesKey("pomodoro_paused_remaining_millis")
        val FULL_FOCUS_ENABLED = booleanPreferencesKey("full_focus_enabled")
        val FULL_FOCUS_PIN_SALT = stringPreferencesKey("full_focus_pin_salt")
        val FULL_FOCUS_PIN_VERIFIER = stringPreferencesKey("full_focus_pin_verifier")
        val FULL_FOCUS_DND_MANAGED = booleanPreferencesKey("full_focus_dnd_managed")
        val FULL_FOCUS_PREVIOUS_FILTER = intPreferencesKey("full_focus_previous_interruption_filter")
        val FULL_FOCUS_ENTRY_PHASE = stringPreferencesKey("full_focus_entry_phase")
        val FULL_FOCUS_ENTRY_TARGET = longPreferencesKey("full_focus_entry_target_epoch_millis")
    }
}
