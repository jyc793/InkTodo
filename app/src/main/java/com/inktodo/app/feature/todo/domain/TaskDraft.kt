package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class TaskDraft(
    val title: String = "",
    val note: String = "",
    val date: LocalDate,
    val reminderType: ReminderType = ReminderType.AT_TIME,
    val atTime: LocalTime,
    val rangeStartDate: LocalDate = date,
    val rangeStartTime: LocalTime,
    val rangeEndDate: LocalDate = date,
    val rangeEndTime: LocalTime,
    val useSystemAlarm: Boolean = false,
)

sealed interface TaskDraftValidation {
    data class Valid(val reminder: TaskReminder) : TaskDraftValidation
    data class Invalid(val message: String) : TaskDraftValidation
}

object TaskDraftValidator {
    fun validate(
        draft: TaskDraft,
        now: ZonedDateTime,
        zoneId: ZoneId = now.zone,
    ): TaskDraftValidation {
        if (draft.title.isBlank()) {
            return TaskDraftValidation.Invalid("请输入任务名称。")
        }

        return when (draft.reminderType) {
            ReminderType.AT_TIME -> {
                val reminderAt = draft.date.atTime(draft.atTime).atZone(zoneId)
                if (!reminderAt.isAfter(now)) {
                    TaskDraftValidation.Invalid("提醒时间必须晚于当前时间。")
                } else {
                    TaskDraftValidation.Valid(
                        TaskReminder.AtTime(
                            startAt = reminderAt.toInstant(),
                            useSystemAlarm = draft.useSystemAlarm,
                        ),
                    )
                }
            }
            ReminderType.TIME_RANGE -> {
                val startAt = draft.rangeStartDate.atTime(draft.rangeStartTime).atZone(zoneId)
                val endAt = draft.rangeEndDate.atTime(draft.rangeEndTime).atZone(zoneId)
                when {
                    !endAt.isAfter(startAt) ->
                        TaskDraftValidation.Invalid("结束时间必须晚于开始时间。")
                    !endAt.isAfter(now) ->
                        TaskDraftValidation.Invalid("结束时间必须晚于当前时间。")
                    else -> TaskDraftValidation.Valid(
                        TaskReminder.TimeRange(startAt.toInstant(), endAt.toInstant()),
                    )
                }
            }
        }
    }
}

fun TaskDraft.toTodoTask(
    existing: TodoTask?,
    reminder: TaskReminder,
    now: Instant,
): TodoTask = TodoTask(
    id = existing?.id ?: 0,
    title = title.trim(),
    note = note.trim().takeIf(String::isNotEmpty),
    date = if (reminderType == ReminderType.TIME_RANGE) rangeStartDate else date,
    reminder = reminder.preserveManualStopWhenUnchanged(existing?.reminder),
    isCompleted = existing?.isCompleted ?: false,
    completedAt = existing?.completedAt,
    createdAt = existing?.createdAt ?: now,
    updatedAt = now,
)

private fun TaskReminder.preserveManualStopWhenUnchanged(existing: TaskReminder?): TaskReminder {
    val newRange = this as? TaskReminder.TimeRange ?: return this
    val oldRange = existing as? TaskReminder.TimeRange ?: return this
    return if (newRange.startAt == oldRange.startAt && newRange.endAt == oldRange.endAt) {
        newRange.copy(isManuallyStopped = oldRange.isManuallyStopped)
    } else {
        newRange
    }
}

fun TodoTask.toDraft(zoneId: ZoneId): TaskDraft {
    val reminder = requireNotNull(reminder) { "Task editor requires a reminder" }
    return when (reminder) {
        is TaskReminder.AtTime -> TaskDraft(
            title = title,
            note = note.orEmpty(),
            date = date,
            reminderType = ReminderType.AT_TIME,
            atTime = reminder.startAt.atZone(zoneId).toLocalTime(),
            rangeStartDate = reminder.startAt.atZone(zoneId).toLocalDate(),
            rangeStartTime = reminder.startAt.atZone(zoneId).toLocalTime(),
            rangeEndDate = reminder.startAt.atZone(zoneId).toLocalDate(),
            rangeEndTime = reminder.startAt.atZone(zoneId).toLocalTime().plusHours(1),
            useSystemAlarm = reminder.useSystemAlarm,
        )
        is TaskReminder.TimeRange -> TaskDraft(
            title = title,
            note = note.orEmpty(),
            date = reminder.startAt.atZone(zoneId).toLocalDate(),
            reminderType = ReminderType.TIME_RANGE,
            atTime = reminder.startAt.atZone(zoneId).toLocalTime(),
            rangeStartDate = reminder.startAt.atZone(zoneId).toLocalDate(),
            rangeStartTime = reminder.startAt.atZone(zoneId).toLocalTime(),
            rangeEndDate = reminder.endAt.atZone(zoneId).toLocalDate(),
            rangeEndTime = reminder.endAt.atZone(zoneId).toLocalTime(),
            useSystemAlarm = false,
        )
    }
}
