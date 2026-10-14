package com.inktodo.app.feature.todo.ui

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.model.RepeatMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun TodoTask.dateLabel(): String = date.format(dateFormatter)

internal fun TodoTask.reminderLabel(zoneId: ZoneId = ZoneId.systemDefault()): String =
    (when (val value = reminder) {
        null -> "未设置提醒"
        is TaskReminder.AtTime -> buildString {
            append(value.startAt.atZone(zoneId).format(timeFormatter))
            if (value.useSystemAlarm) append(" · 系统闹钟")
        }
        is TaskReminder.TimeRange -> {
            val start = value.startAt.atZone(zoneId)
            val end = value.endAt.atZone(zoneId)
            val endLabel = if (start.toLocalDate() == end.toLocalDate()) {
                end.format(timeFormatter)
            } else {
                end.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
            }
            "${start.format(timeFormatter)} - $endLabel · 持续提醒"
        }
    }) + when (recurrence.mode) {
        RepeatMode.NONE -> ""
        RepeatMode.DAILY -> " · 每天"
        RepeatMode.WEEKLY -> " · 每周" + DayOfWeek.entries
            .filter(recurrence::includes)
            .joinToString("、") { "一二三四五六日"[it.value - 1].toString() }
    }
