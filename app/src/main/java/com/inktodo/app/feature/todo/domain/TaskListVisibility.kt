package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Future recurrence rows remain scheduled but are not shown as today's to-dos. */
fun TodoTask.isVisibleInTodo(today: LocalDate, zoneId: ZoneId, now: Instant = Instant.now()): Boolean {
    if (specialDateId != null) return date == today
    if (!recurrence.isRepeating) return true
    if (date == today) return true
    val range = reminder as? TaskReminder.TimeRange ?: return false
    return date == today.minusDays(1) &&
        range.endAt.isAfter(today.atStartOfDay(zoneId).toInstant()) &&
        range.endAt.isAfter(now)
}
