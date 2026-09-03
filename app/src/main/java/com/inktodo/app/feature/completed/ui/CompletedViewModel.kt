package com.inktodo.app.feature.completed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskCommandIssueNoticeGate
import com.inktodo.app.feature.todo.domain.TaskRepository
import com.inktodo.app.feature.todo.domain.userMessage
import com.inktodo.app.feature.todo.domain.sortedByPreference
import com.inktodo.app.feature.todo.ui.simpleViewModelFactory
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CompletedUiState(
    val tasks: List<TodoTask> = emptyList(),
    val isLoading: Boolean = true,
)

class CompletedViewModel(
    private val repository: TaskRepository,
    private val commands: TaskCommandService,
    private val noticeGate: TaskCommandIssueNoticeGate,
    preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    val uiState: StateFlow<CompletedUiState> = combine(
        repository.observeCompleted(),
        preferencesRepository.preferences,
    ) { tasks, preferences ->
        CompletedUiState(tasks = tasks.sortedByPreference(preferences.taskSortOrder), isLoading = false)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompletedUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _systemAlarmRequests = MutableSharedFlow<SystemAlarmRequest>(extraBufferCapacity = 1)
    val systemAlarmRequests = _systemAlarmRequests.asSharedFlow()

    fun restore(taskId: Long) = change {
        commands.setCompleted(taskId, false, Instant.now(clock))
    }

    fun delete(taskId: Long) = change {
        commands.delete(taskId)
    }

    private fun change(block: suspend () -> TaskCommandResult) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { result ->
                    val unseenIssues = noticeGate.takeUnseen(result.issues)
                    if (unseenIssues.isNotEmpty()) {
                        _messages.emit(unseenIssues.joinToString("\n") { it.userMessage() })
                    }
                    result.systemAlarmRequest?.let { _systemAlarmRequests.emit(it) }
                }
                .onFailure { _messages.emit(it.message ?: "操作失败，请重试。") }
        }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            commands: TaskCommandService,
            noticeGate: TaskCommandIssueNoticeGate,
            preferencesRepository: PreferencesRepository,
        ): ViewModelProvider.Factory =
            simpleViewModelFactory {
                CompletedViewModel(repository, commands, noticeGate, preferencesRepository)
            }
    }
}
