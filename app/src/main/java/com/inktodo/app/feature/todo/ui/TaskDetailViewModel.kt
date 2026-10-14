package com.inktodo.app.feature.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.feature.todo.domain.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val task: TodoTask? = null,
    val isLoading: Boolean = true,
)

class TaskDetailViewModel(
    taskId: Long,
    repository: TaskRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = TaskDetailUiState(
                task = repository.findById(taskId),
                isLoading = false,
            )
        }
    }

    companion object {
        fun factory(taskId: Long, repository: TaskRepository): ViewModelProvider.Factory =
            simpleViewModelFactory { TaskDetailViewModel(taskId, repository) }
    }
}
