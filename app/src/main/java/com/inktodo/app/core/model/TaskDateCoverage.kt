package com.inktodo.app.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Returns whether this task occupies any instant of [date] in [zoneId]. */
fun TodoTask.coversDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (recurrence.isRepeating && !isCompleted) {
        if (date.isBefore(this.date)) return false
        if (recurrence.includes(date.dayOfWeek)) return true
        val range = reminder as? TaskReminder.TimeRange ?: return false
        val previous = date.minusDays(1)
        return !previous.isBefore(this.date) &&
            recurrence.includes(previous.dayOfWeek) &&
            range.startAt.atZone(zoneId).toLocalDate() != range.endAt.atZone(zoneId).toLocalDate() &&
            range.endAt.atZone(zoneId).toLocalTime() != LocalTime.MIDNIGHT
    }
    val range = reminder as? TaskReminder.TimeRange ?: return this.date == date
    val dayStart = date.atStartOfDay(zoneId).toInstant()
    val nextDayStart = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return range.startAt < nextDayStart && range.endAt > dayStart
}

/** Limits expansion to one visible month even when a range spans a semester or longer. */
fun TodoTask.coveredDatesIn(month: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): Sequence<LocalDate> =
    (1..month.lengthOfMonth())
        .asSequence()
        .map(month::atDay)
        .filter { coversDate(it, zoneId) }
