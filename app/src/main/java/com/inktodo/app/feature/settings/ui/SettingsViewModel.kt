package com.inktodo.app.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import com.inktodo.app.feature.settings.data.ReminderReliabilityChecker
import com.inktodo.app.feature.settings.domain.ReminderReliabilitySnapshot
import com.inktodo.app.feature.todo.ui.simpleViewModelFactory
import com.inktodo.app.core.widget.NoOpWidgetUpdater
import com.inktodo.app.core.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val reliability: ReminderReliabilitySnapshot,
    val preferences: UserPreferences = UserPreferences(),
)

class SettingsViewModel(
    private val checker: ReminderReliabilityChecker,
    private val preferencesRepository: PreferencesRepository,
    private val widgetUpdater: WidgetUpdater = NoOpWidgetUpdater,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(checker.check()))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(reliability = checker.check()) }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(value)
            widgetUpdater.requestUpdate()
        }
    }

    fun setTaskSortOrder(value: TaskSortOrder) {
        viewModelScope.launch { preferencesRepository.setTaskSortOrder(value) }
    }

    companion object {
        fun factory(
            checker: ReminderReliabilityChecker,
            preferencesRepository: PreferencesRepository,
            widgetUpdater: WidgetUpdater = NoOpWidgetUpdater,
        ): ViewModelProvider.Factory =
            simpleViewModelFactory { SettingsViewModel(checker, preferencesRepository, widgetUpdater) }
    }
}
