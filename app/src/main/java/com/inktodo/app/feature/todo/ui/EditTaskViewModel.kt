package com.inktodo.app.feature.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.RepeatMode
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.feature.todo.domain.TaskDraft
import com.inktodo.app.feature.todo.domain.TaskDraftValidation
import com.inktodo.app.feature.todo.domain.TaskDraftValidator
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskRepository
import com.inktodo.app.feature.todo.domain.toDraft
import com.inktodo.app.feature.todo.domain.toTodoTask
import com.inktodo.app.feature.todo.domain.withReusablePreferences
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditTaskUiState(
    val draft: TaskDraft,
    val isLoading: Boolean,
    val isSaving: Boolean = false,
    val isExistingTask: Boolean = false,
    val canStopOngoingReminder: Boolean = false,
    val validationMessage: String? = null,
    val recurrenceRuleLocked: Boolean = false,
)

sealed interface EditTaskEvent {
    data class Saved(val result: TaskCommandResult) : EditTaskEvent
    data class Deleted(val result: TaskCommandResult) : EditTaskEvent
    data object ReminderStopped : EditTaskEvent
    data class Message(val text: String) : EditTaskEvent
}

class EditTaskViewModel(
    private val taskId: Long?,
    private val repository: TaskRepository,
    private val commands: TaskCommandService,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private var existingTask: TodoTask? = null
    private val initialDraft = newDraft(ZonedDateTime.now(clock).withZoneSameInstant(zoneId))
    private val _uiState = MutableStateFlow(
        EditTaskUiState(
            draft = initialDraft,
            isLoading = true,
            isExistingTask = taskId != null,
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditTaskEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        if (taskId != null) loadTask(taskId) else loadNewTaskPreferences()
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setNote(value: String) = edit { copy(note = value) }
    fun setDate(value: LocalDate) = edit { copy(date = value) }
    fun setReminderType(value: ReminderType) {
        edit { copy(reminderType = value, repeatIntervalMinutes =
            if (value == ReminderType.TIME_RANGE) repeatIntervalMinutes else null) }
        viewModelScope.launch { preferencesRepository.setLastReminderType(value) }
    }
    fun setAtTime(value: LocalTime) = edit { copy(atTime = value) }
    fun setRangeStartDate(value: LocalDate) = edit { copy(rangeStartDate = value) }
    fun setRangeStartTime(value: LocalTime) = edit { copy(rangeStartTime = value) }
    fun setRangeEndDate(value: LocalDate) = edit { copy(rangeEndDate = value) }
    fun setRangeEndTime(value: LocalTime) = edit { copy(rangeEndTime = value) }
    fun setUseSystemAlarm(value: Boolean) {
        edit { copy(useSystemAlarm = value) }
        viewModelScope.launch { preferencesRepository.setLastUseSystemAlarm(value) }
    }
    fun setRepeatMode(value: RepeatMode) {
        if (_uiState.value.recurrenceRuleLocked) return
        edit { copy(repeatMode = value,
            repeatIntervalMinutes = if (value == RepeatMode.NONE) null else repeatIntervalMinutes) }
    }
    fun setRepeatIntervalMinutes(value: Int?) = edit { copy(repeatIntervalMinutes = value) }
    fun toggleRepeatWeekday(day: DayOfWeek) = edit {
        if (_uiState.value.recurrenceRuleLocked) this
        else copy(repeatWeekdays = if (day in repeatWeekdays) repeatWeekdays - day else repeatWeekdays + day)
    }

    private fun loadNewTaskPreferences() {
        viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            _uiState.value = EditTaskUiState(
                draft = initialDraft.withReusablePreferences(preferences),
                isLoading = false,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
        val original = existingTask?.toDraft(zoneId)
        val unchangedTiming = original != null && state.draft.reminderType == original.reminderType &&
            state.draft.date == original.date && state.draft.atTime == original.atTime &&
            state.draft.rangeStartDate == original.rangeStartDate &&
            state.draft.rangeStartTime == original.rangeStartTime &&
            state.draft.rangeEndDate == original.rangeEndDate &&
            state.draft.rangeEndTime == original.rangeEndTime &&
            state.draft.useSystemAlarm == original.useSystemAlarm &&
            state.draft.repeatMode == original.repeatMode &&
            state.draft.repeatWeekdays == original.repeatWeekdays &&
            state.draft.repeatIntervalMinutes == original.repeatIntervalMinutes
        val validation = if (unchangedTiming && state.draft.title.isNotBlank()) {
            TaskDraftValidation.Valid(checkNotNull(existingTask?.reminder))
        } else TaskDraftValidator.validate(state.draft, now, zoneId)
        when (val result = validation) {
            is TaskDraftValidation.Invalid -> {
                _uiState.update { it.copy(validationMessage = result.message) }
            }
            is TaskDraftValidation.Valid -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, validationMessage = null) }
                runCatching {
                    commands.save(
                        state.draft.toTodoTask(
                            existing = existingTask,
                            reminder = result.reminder,
                            now = Instant.now(clock),
                        ),
                    )
                }.onSuccess { result ->
                    _events.emit(EditTaskEvent.Saved(result))
                }.onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(EditTaskEvent.Message(error.message ?: "保存失败，请重试。"))
                }
            }
        }
    }

    fun delete() {
        val id = taskId ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching { commands.delete(id) }
                .onSuccess { _events.emit(EditTaskEvent.Deleted(it)) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(EditTaskEvent.Message(error.message ?: "删除失败，请重试。"))
                }
        }
    }

    fun stopOngoingReminder() {
        val id = taskId ?: return
        if (_uiState.value.isSaving || !_uiState.value.canStopOngoingReminder) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val changedAt = Instant.now(clock)
            runCatching { commands.stopOngoingReminder(id, changedAt) }
                .onSuccess {
                    existingTask = existingTask?.let { task ->
                        val range = task.reminder as? com.inktodo.app.core.model.TaskReminder.TimeRange
                        task.copy(
                            reminder = range?.copy(isManuallyStopped = true),
                            updatedAt = changedAt,
                        )
                    }
                    _uiState.update { state ->
                        state.copy(isSaving = false, canStopOngoingReminder = false)
                    }
                    _events.emit(EditTaskEvent.ReminderStopped)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(EditTaskEvent.Message(error.message ?: "结束提醒失败，请重试。"))
                }
        }
    }

    private fun loadTask(id: Long) {
        viewModelScope.launch {
            val task = repository.findById(id)
            if (task == null) {
                _events.emit(EditTaskEvent.Message("任务不存在或已被删除。"))
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            existingTask = task
            _uiState.value = EditTaskUiState(
                draft = task.toDraft(zoneId),
                isLoading = false,
                isExistingTask = true,
                canStopOngoingReminder = task.hasActiveRangeReminder(Instant.now(clock)),
                recurrenceRuleLocked = task.recurrence.isRepeating,
            )
        }
    }

    private fun edit(transform: TaskDraft.() -> TaskDraft) {
        _uiState.update { state ->
            state.copy(draft = state.draft.transform(), validationMessage = null)
        }
    }

    companion object {
        fun factory(
            taskId: Long?,
            repository: TaskRepository,
            commands: TaskCommandService,
            preferencesRepository: PreferencesRepository,
        ): ViewModelProvider.Factory =
            simpleViewModelFactory {
                EditTaskViewModel(taskId, repository, commands, preferencesRepository)
            }

        private fun newDraft(now: ZonedDateTime): TaskDraft {
            val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            return TaskDraft(
                date = nextHour.toLocalDate(),
                atTime = nextHour.toLocalTime(),
                rangeStartDate = nextHour.toLocalDate(),
                rangeStartTime = nextHour.toLocalTime(),
                rangeEndDate = nextHour.plusHours(1).toLocalDate(),
                rangeEndTime = nextHour.plusHours(1).toLocalTime(),
            )
        }
    }
}

private fun TodoTask.hasActiveRangeReminder(now: Instant): Boolean {
    if (isCompleted) return false
    val range = reminder as? com.inktodo.app.core.model.TaskReminder.TimeRange ?: return false
    return !range.isManuallyStopped && !now.isBefore(range.startAt) && now.isBefore(range.endAt)
}

internal fun simpleViewModelFactory(create: () -> ViewModel): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
