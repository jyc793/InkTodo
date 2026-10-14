package com.inktodo.app.feature.calendar.domain

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthBuilderTest {
    @Test
    fun monthGridIsAlwaysSixMondayFirstWeeks() {
        val cells = CalendarMonthBuilder.cells(YearMonth.of(2026, 8))

        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 8, 1), cells[5])
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 8, 31), cells[35])
    }

    @Test
    fun leapFebruaryContainsTwentyNineRealDates() {
        val cells = CalendarMonthBuilder.cells(YearMonth.of(2028, 2))

        assertEquals(29, cells.filterNotNull().size)
        assertTrue(LocalDate.of(2028, 2, 29) in cells)
    }

    @Test
    fun projectionCreatesInkDotDatesAndSelectedPendingTasks() {
        val selected = LocalDate.of(2026, 8, 25)
        val earlier = task(1, selected, "2026-08-25T09:00:00Z")
        val later = task(2, selected, "2026-08-25T14:00:00Z")
        val anotherDay = task(3, selected.plusDays(1), "2026-08-26T10:00:00Z")

        val result = CalendarMonthBuilder.project(
            tasks = listOf(later, anotherDay, earlier),
            month = YearMonth.of(2026, 8),
            selectedDate = selected,
            today = selected,
        )

        assertEquals(setOf(selected, selected.plusDays(1)), result.taskDates)
        assertEquals(listOf(1L, 2L), result.selectedTasks.map(TodoTask::id))
        assertEquals(2, result.daySummaries[selected]?.count)
        assertEquals("任务1、任务2", result.daySummaries[selected]?.preview)
    }

    @Test
    fun repeatedSeriesCountsOncePerDayEvenWhenOldOccurrenceRemains() {
        val first = task(1, LocalDate.of(2026, 8, 24), "2026-08-24T09:00:00Z")
            .copy(recurrence = com.inktodo.app.core.model.TaskRecurrence.DAILY,
                recurrenceSeriesId = "daily")
        val second = task(2, LocalDate.of(2026, 8, 25), "2026-08-25T09:00:00Z")
            .copy(recurrence = com.inktodo.app.core.model.TaskRecurrence.DAILY,
                recurrenceSeriesId = "daily")
        val date = LocalDate.of(2026, 8, 25)
        val result = CalendarMonthBuilder.project(
            tasks = listOf(first, second),
            month = YearMonth.of(2026, 8),
            selectedDate = date,
            today = date,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(1, result.daySummaries[date]?.count)
        assertEquals(listOf(2L), result.selectedTasks.map(TodoTask::id))
    }

    @Test
    fun crossDateRangeAppearsOnEveryOccupiedDateButNotAtExclusiveMidnightEnd() {
        val range = TodoTask(
            id = 4,
            title = "跨日任务",
            date = LocalDate.of(2026, 8, 25),
            reminder = TaskReminder.TimeRange(
                Instant.parse("2026-08-25T06:00:00Z"),
                Instant.parse("2026-08-28T16:00:00Z"),
            ),
            createdAt = Instant.parse("2026-08-25T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-25T00:00:00Z"),
        )

        val result = CalendarMonthBuilder.project(
            tasks = listOf(range),
            month = YearMonth.of(2026, 8),
            selectedDate = LocalDate.of(2026, 8, 27),
            today = LocalDate.of(2026, 8, 25),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(
            setOf(
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 28),
            ),
            result.taskDates,
        )
        assertEquals(listOf(4L), result.selectedTasks.map(TodoTask::id))

        val midnightEnd = range.copy(
            reminder = TaskReminder.TimeRange(
                Instant.parse("2026-08-25T06:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"),
            ),
        )
        val midnightResult = CalendarMonthBuilder.project(
            listOf(midnightEnd),
            YearMonth.of(2026, 8),
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 8, 25),
            ZoneOffset.UTC,
        )
        assertTrue(LocalDate.of(2026, 8, 28) !in midnightResult.taskDates)
        assertTrue(midnightResult.selectedTasks.isEmpty())
    }

    private fun task(id: Long, date: LocalDate, reminderAt: String): TodoTask = TodoTask(
        id = id,
        title = "任务$id",
        date = date,
        reminder = TaskReminder.AtTime(Instant.parse(reminderAt)),
        createdAt = date.atStartOfDay().toInstant(ZoneOffset.UTC),
        updatedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC),
    )
}
