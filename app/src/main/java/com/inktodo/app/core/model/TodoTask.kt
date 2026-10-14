package com.inktodo.app.core.model

import java.time.Instant
import java.time.LocalDate

data class TodoTask(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val date: LocalDate,
    val reminder: TaskReminder? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceSeriesId: String? = null,
    val specialDateId: Long? = null,
    val specialOccurrenceDate: LocalDate? = null,
    val specialOffsetDays: Int? = null,
)

sealed interface TaskReminder {
    val startAt: Instant

    data class AtTime(
        override val startAt: Instant,
        val useSystemAlarm: Boolean = false,
    ) : TaskReminder

    data class TimeRange(
        override val startAt: Instant,
        val endAt: Instant,
        val isManuallyStopped: Boolean = false,
        val repeatIntervalMinutes: Int? = null,
    ) : TaskReminder
}

enum class ReminderType {
    AT_TIME,
    TIME_RANGE,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TaskSortOrder {
    TIME_ASCENDING,
    CREATED_DESCENDING,
}
