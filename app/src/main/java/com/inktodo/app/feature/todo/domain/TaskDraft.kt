package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.RepeatMode
import com.inktodo.app.core.model.TaskRecurrence
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.util.UUID

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
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatWeekdays: Set<DayOfWeek> = emptySet(),
    val repeatIntervalMinutes: Int? = null,
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
        if (draft.repeatMode == RepeatMode.WEEKLY && draft.repeatWeekdays.isEmpty()) {
            return TaskDraftValidation.Invalid("请至少选择一个重复的星期。")
        }
        val recurrence = draft.recurrence()

        return when (draft.reminderType) {
            ReminderType.AT_TIME -> {
                val configuredAt = draft.date.atTime(draft.atTime).atZone(zoneId)
                val reminderAt = if (recurrence.isRepeating) {
                    nextRepeatingStart(configuredAt, recurrence, now)
                } else configuredAt
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
                    recurrence.isRepeating && Duration.between(startAt, endAt) >= Duration.ofDays(1) ->
                        TaskDraftValidation.Invalid("重复的持续提醒每次须短于 24 小时；长时间段仍可选择不重复。")
                    draft.repeatIntervalMinutes != null && !recurrence.isRepeating ->
                        TaskDraftValidation.Invalid("间隔确认提醒仅适用于每天或每周重复的持续任务。")
                    draft.repeatIntervalMinutes != null && draft.repeatIntervalMinutes !in 10..720 ->
                        TaskDraftValidation.Invalid("间隔时间请设置为 10 至 720 分钟。")
                    draft.repeatIntervalMinutes != null &&
                        Duration.between(startAt, endAt).toMinutes() <= draft.repeatIntervalMinutes ->
                        TaskDraftValidation.Invalid("间隔时间必须短于每次持续时间。")
                    !recurrence.isRepeating && !endAt.isAfter(now) ->
                        TaskDraftValidation.Invalid("结束时间必须晚于当前时间。")
                    else -> {
                        val actualStart = if (recurrence.isRepeating && !endAt.isAfter(now)) {
                            nextRepeatingStart(startAt, recurrence, now)
                        } else if (recurrence.isRepeating && !recurrence.includes(startAt.dayOfWeek)) {
                            nextRepeatingStart(startAt, recurrence, now)
                        } else startAt
                        val daysShifted = actualStart.toLocalDate().toEpochDay() - startAt.toLocalDate().toEpochDay()
                        TaskDraftValidation.Valid(
                            TaskReminder.TimeRange(
                                actualStart.toInstant(),
                                endAt.plusDays(daysShifted).toInstant(),
                                repeatIntervalMinutes = draft.repeatIntervalMinutes,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun nextRepeatingStart(
        configuredAt: ZonedDateTime,
        recurrence: TaskRecurrence,
        now: ZonedDateTime,
    ): ZonedDateTime {
        var date = configuredAt.toLocalDate()
        if (date.isBefore(now.toLocalDate())) date = now.toLocalDate()
        repeat(8) {
            val candidate = date.atTime(configuredAt.toLocalTime()).atZone(configuredAt.zone)
            if (recurrence.includes(date.dayOfWeek) && candidate.isAfter(now)) return candidate
            date = date.plusDays(1)
        }
        error("Unable to find next repeat date")
    }
}

fun TaskDraft.recurrence(): TaskRecurrence = when (repeatMode) {
    RepeatMode.NONE -> TaskRecurrence.NONE
    RepeatMode.DAILY -> TaskRecurrence.DAILY
    RepeatMode.WEEKLY -> TaskRecurrence.weekly(repeatWeekdays)
}

fun TaskDraft.toTodoTask(
    existing: TodoTask?,
    reminder: TaskReminder,
    now: Instant,
): TodoTask = TodoTask(
    id = existing?.id ?: 0,
    title = title.trim(),
    note = note.trim().takeIf(String::isNotEmpty),
    date = reminder.startAt.atZone(ZoneId.systemDefault()).toLocalDate(),
    reminder = reminder.preserveManualStopWhenUnchanged(existing?.reminder),
    isCompleted = existing?.isCompleted ?: false,
    completedAt = existing?.completedAt,
    createdAt = existing?.createdAt ?: now,
    updatedAt = now,
    recurrence = recurrence(),
    recurrenceSeriesId = if (repeatMode == RepeatMode.NONE) null
        else existing?.recurrenceSeriesId ?: UUID.randomUUID().toString(),
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
            repeatMode = recurrence.mode,
            repeatWeekdays = recurrence.selectedDays(),
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
            repeatMode = recurrence.mode,
            repeatWeekdays = recurrence.selectedDays(),
            repeatIntervalMinutes = reminder.repeatIntervalMinutes,
        )
    }
}

private fun TaskRecurrence.selectedDays(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf(), ::includes)
