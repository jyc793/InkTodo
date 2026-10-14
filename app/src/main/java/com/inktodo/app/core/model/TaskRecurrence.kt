package com.inktodo.app.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RepeatMode { NONE, DAILY, WEEKLY }

/** Monday uses bit 0, Sunday uses bit 6. */
data class TaskRecurrence(
    val mode: RepeatMode = RepeatMode.NONE,
    val weekdaysMask: Int = 0,
) {
    init {
        require(weekdaysMask in 0..127)
        require(mode != RepeatMode.WEEKLY || weekdaysMask != 0) { "Weekly repeat needs at least one day" }
    }

    val isRepeating: Boolean get() = mode != RepeatMode.NONE

    fun includes(day: DayOfWeek): Boolean = when (mode) {
        RepeatMode.NONE -> false
        RepeatMode.DAILY -> true
        RepeatMode.WEEKLY -> weekdaysMask and (1 shl (day.value - 1)) != 0
    }

    fun nextDateAfter(date: LocalDate): LocalDate? {
        if (!isRepeating) return null
        var candidate = date.plusDays(1)
        repeat(7) {
            if (includes(candidate.dayOfWeek)) return candidate
            candidate = candidate.plusDays(1)
        }
        error("No matching repeat day")
    }

    companion object {
        val NONE = TaskRecurrence()
        val DAILY = TaskRecurrence(RepeatMode.DAILY)

        fun weekly(days: Set<DayOfWeek>): TaskRecurrence = TaskRecurrence(
            RepeatMode.WEEKLY,
            days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
        )
    }
}

/** Materializes one occurrence while preserving local wall-clock time across DST changes. */
fun TodoTask.nextOccurrenceAfter(after: Instant, zoneId: ZoneId, createdAt: Instant): TodoTask? {
    if (!recurrence.isRepeating || isCompleted) return null
    val configured = reminder ?: return null
    val originalStart = configured.startAt.atZone(zoneId)
    var date = originalStart.toLocalDate()
    val afterDate = after.atZone(zoneId).toLocalDate()
    if (date.isBefore(afterDate.minusDays(1))) date = afterDate.minusDays(1)
    do {
        date = recurrence.nextDateAfter(date) ?: return null
    } while (!date.atTime(originalStart.toLocalTime()).atZone(zoneId).toInstant().isAfter(after))

    val nextStart = date.atTime(originalStart.toLocalTime()).atZone(zoneId).toInstant()
    val nextReminder = when (configured) {
        is TaskReminder.AtTime -> configured.copy(startAt = nextStart)
        is TaskReminder.TimeRange -> {
            val originalEnd = configured.endAt.atZone(zoneId)
            val dayOffset = originalEnd.toLocalDate().toEpochDay() - originalStart.toLocalDate().toEpochDay()
            val nextEnd = date.plusDays(dayOffset)
                .atTime(originalEnd.toLocalTime()).atZone(zoneId).toInstant()
            configured.copy(startAt = nextStart, endAt = nextEnd, isManuallyStopped = false)
        }
    }
    return copy(
        id = 0,
        date = date,
        reminder = nextReminder,
        isCompleted = false,
        completedAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
