package com.inktodo.app.core.model

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDateCoverageTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun rangeUsesExclusiveEndAndCoversIntermediateDates() {
        val task = task(
            "2026-08-25T06:00:00Z",
            "2026-08-28T00:00:00Z",
        )

        assertTrue(task.coversDate(LocalDate.of(2026, 8, 25), zone))
        assertTrue(task.coversDate(LocalDate.of(2026, 8, 26), zone))
        assertTrue(task.coversDate(LocalDate.of(2026, 8, 28), zone))
        assertFalse(task.coversDate(LocalDate.of(2026, 8, 29), zone))
    }

    @Test
    fun rangeEndingAtLocalMidnightDoesNotOccupyNextDate() {
        val task = task(
            "2026-08-25T06:00:00Z",
            "2026-08-27T16:00:00Z",
        )

        assertFalse(task.coversDate(LocalDate.of(2026, 8, 28), zone))
    }

    @Test
    fun weeklyRepeatMarksOnlySelectedDaysAndOvernightTail() {
        val start = Instant.parse("2026-10-12T14:00:00Z") // Monday 22:00 Shanghai
        val repeating = TodoTask(
            id = 2,
            title = "夜间任务",
            date = LocalDate.of(2026, 10, 12),
            reminder = TaskReminder.TimeRange(start, Instant.parse("2026-10-12T17:00:00Z")),
            createdAt = start,
            updatedAt = start,
            recurrence = TaskRecurrence.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            recurrenceSeriesId = "night-series",
        )

        assertTrue(repeating.coversDate(LocalDate.of(2026, 10, 12), zone))
        assertTrue(repeating.coversDate(LocalDate.of(2026, 10, 13), zone))
        assertFalse(repeating.coversDate(LocalDate.of(2026, 10, 14), zone))
        assertTrue(repeating.coversDate(LocalDate.of(2026, 10, 15), zone))
    }

    private fun task(start: String, end: String) = TodoTask(
        id = 1,
        title = "跨日",
        date = LocalDate.of(2026, 8, 25),
        reminder = TaskReminder.TimeRange(Instant.parse(start), Instant.parse(end)),
        createdAt = Instant.parse(start),
        updatedAt = Instant.parse(start),
    )
}
