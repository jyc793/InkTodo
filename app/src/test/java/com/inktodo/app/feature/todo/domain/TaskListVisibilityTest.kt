package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.TaskRecurrence
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListVisibilityTest {
    private val today = LocalDate.of(2026, 8, 26)
    private val base = TodoTask(
        id = 1,
        title = "复习",
        date = today,
        reminder = TaskReminder.AtTime(Instant.parse("2026-08-26T10:00:00Z")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        recurrence = TaskRecurrence.DAILY,
        recurrenceSeriesId = "daily",
    )

    @Test fun onlyTodayRecurringOccurrenceIsVisible() {
        assertTrue(base.isVisibleInTodo(today, ZoneOffset.UTC))
        assertFalse(base.copy(date = today.plusDays(1)).isVisibleInTodo(today, ZoneOffset.UTC))
        assertFalse(base.copy(date = today.minusDays(1)).isVisibleInTodo(today, ZoneOffset.UTC))
        assertTrue(base.copy(date = today.plusDays(1), recurrence = TaskRecurrence.NONE)
            .isVisibleInTodo(today, ZoneOffset.UTC))
    }

    @Test fun overnightRangeRemainsVisibleUntilItsEndDay() {
        val range = base.copy(
            date = today.minusDays(1),
            reminder = TaskReminder.TimeRange(
                Instant.parse("2026-08-25T23:00:00Z"),
                Instant.parse("2026-08-26T01:00:00Z"),
            ),
        )
        assertTrue(range.isVisibleInTodo(today, ZoneOffset.UTC, Instant.parse("2026-08-26T00:30:00Z")))
        assertFalse(range.isVisibleInTodo(today, ZoneOffset.UTC, Instant.parse("2026-08-26T01:00:00Z")))
    }
}
