package com.inktodo.app.core.model

import java.time.Instant
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

    private fun task(start: String, end: String) = TodoTask(
        id = 1,
        title = "跨日",
        date = LocalDate.of(2026, 8, 25),
        reminder = TaskReminder.TimeRange(Instant.parse(start), Instant.parse(end)),
        createdAt = Instant.parse(start),
        updatedAt = Instant.parse(start),
    )
}
