package com.inktodo.app.feature.todo.data

import com.inktodo.app.core.database.ReminderEntity
import com.inktodo.app.core.database.ReminderKind
import com.inktodo.app.core.database.TaskEntity
import com.inktodo.app.core.database.TaskWithReminder
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate

internal data class TaskPersistenceModel(
    val task: TaskEntity,
    val reminder: ReminderEntity?,
)

internal fun TodoTask.toPersistenceModel(): TaskPersistenceModel {
    require(title.isNotBlank()) { "Task title cannot be blank" }
    require(completedAt == null || isCompleted) { "Only completed tasks can have a completion time" }

    val reminderEntity = when (val value = reminder) {
        null -> null
        is TaskReminder.AtTime -> ReminderEntity(
            taskId = id,
            kind = ReminderKind.AT_TIME,
            startAtEpochMillis = value.startAt.toEpochMilli(),
            endAtEpochMillis = null,
            useSystemAlarm = value.useSystemAlarm,
            isManuallyStopped = false,
        )
        is TaskReminder.TimeRange -> {
            require(value.endAt > value.startAt) { "Reminder end time must be after start time" }
            ReminderEntity(
                taskId = id,
                kind = ReminderKind.TIME_RANGE,
                startAtEpochMillis = value.startAt.toEpochMilli(),
                endAtEpochMillis = value.endAt.toEpochMilli(),
                useSystemAlarm = false,
                isManuallyStopped = value.isManuallyStopped,
            )
        }
    }

    return TaskPersistenceModel(
        task = TaskEntity(
            id = id,
            title = title.trim(),
            note = note?.trim()?.takeIf(String::isNotEmpty),
            taskDateEpochDay = date.toEpochDay(),
            isCompleted = isCompleted,
            completedAtEpochMillis = completedAt?.toEpochMilli(),
            createdAtEpochMillis = createdAt.toEpochMilli(),
            updatedAtEpochMillis = updatedAt.toEpochMilli(),
        ),
        reminder = reminderEntity,
    )
}

internal fun TaskWithReminder.toDomain(): TodoTask {
    val domainReminder = reminder?.let { value ->
        when (value.kind) {
            ReminderKind.AT_TIME -> TaskReminder.AtTime(
                startAt = Instant.ofEpochMilli(value.startAtEpochMillis),
                useSystemAlarm = value.useSystemAlarm,
            )
            ReminderKind.TIME_RANGE -> TaskReminder.TimeRange(
                startAt = Instant.ofEpochMilli(value.startAtEpochMillis),
                endAt = Instant.ofEpochMilli(
                    requireNotNull(value.endAtEpochMillis) { "Time range reminder is missing its end time" },
                ),
                isManuallyStopped = value.isManuallyStopped,
            )
        }
    }
    return TodoTask(
        id = task.id,
        title = task.title,
        note = task.note,
        date = LocalDate.ofEpochDay(task.taskDateEpochDay),
        reminder = domainReminder,
        isCompleted = task.isCompleted,
        completedAt = task.completedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(task.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(task.updatedAtEpochMillis),
    )
}
