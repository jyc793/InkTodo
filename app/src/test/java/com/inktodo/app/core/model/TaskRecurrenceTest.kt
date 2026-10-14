package com.inktodo.app.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRecurrenceTest {
    @Test
    fun dailyRepeatPreservesLocalTimeAcrossSpringDst() {
        val zone = ZoneId.of("Europe/Berlin")
        val start = LocalDate.of(2026, 3, 28).atTime(9, 0).atZone(zone).toInstant()
        val task = task(start, TaskRecurrence.DAILY)

        val next = requireNotNull(task.nextOccurrenceAfter(start, zone, start))

        assertEquals(LocalDate.of(2026, 3, 29), next.date)
        assertEquals(9, next.reminder!!.startAt.atZone(zone).hour)
    }

    @Test
    fun weeklyRepeatVisitsOnlySelectedDays() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-10-12T14:00:00Z") // Monday
        val rule = TaskRecurrence.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val monday = task(start, rule)

        val thursday = requireNotNull(monday.nextOccurrenceAfter(start, zone, start))
        val followingMonday = requireNotNull(thursday.nextOccurrenceAfter(thursday.reminder!!.startAt, zone, start))

        assertEquals(LocalDate.of(2026, 10, 15), thursday.date)
        assertEquals(LocalDate.of(2026, 10, 19), followingMonday.date)
    }

    @Test
    fun overnightRangeRepeatsWithItsOwnEndDate() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-10-12T22:00:00Z")
        val original = task(start, TaskRecurrence.DAILY).copy(
            reminder = TaskReminder.TimeRange(start, Instant.parse("2026-10-13T01:00:00Z")),
        )

        val next = requireNotNull(original.nextOccurrenceAfter(start, zone, start))
        val range = next.reminder as TaskReminder.TimeRange

        assertEquals(Instant.parse("2026-10-13T22:00:00Z"), range.startAt)
        assertEquals(Instant.parse("2026-10-14T01:00:00Z"), range.endAt)
    }

    private fun task(start: Instant, recurrence: TaskRecurrence) = TodoTask(
        id = 8,
        title = "重复任务",
        date = LocalDate.ofInstant(start, ZoneId.of("UTC")),
        reminder = TaskReminder.AtTime(start),
        createdAt = start,
        updatedAt = start,
        recurrence = recurrence,
        recurrenceSeriesId = "series-8",
    )
}
