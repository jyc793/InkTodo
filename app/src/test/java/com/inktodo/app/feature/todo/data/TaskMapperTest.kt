package com.inktodo.app.feature.todo.data

import com.inktodo.app.core.database.TaskWithReminder
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TaskMapperTest {
    private val createdAt = Instant.parse("2026-08-25T02:00:00Z")

    @Test
    fun atTimeReminderRoundTripsWithoutLosingSystemAlarmChoice() {
        val source = task(
            reminder = TaskReminder.AtTime(
                startAt = Instant.parse("2026-08-26T00:00:00Z"),
                useSystemAlarm = true,
            ),
        )

        val stored = source.toPersistenceModel()
        val restored = TaskWithReminder(stored.task.copy(id = 7), stored.reminder?.copy(taskId = 7)).toDomain()

        assertEquals(source.copy(id = 7), restored)
    }

    @Test
    fun timeRangeRoundTripsAndCannotUseSystemAlarm() {
        val source = task(
            reminder = TaskReminder.TimeRange(
                startAt = Instant.parse("2026-08-26T06:00:00Z"),
                endAt = Instant.parse("2026-08-26T08:00:00Z"),
                isManuallyStopped = true,
            ),
        )

        val stored = source.toPersistenceModel()

        assertFalse(requireNotNull(stored.reminder).useSystemAlarm)
        assertEquals(true, stored.reminder.isManuallyStopped)
        assertEquals(source, TaskWithReminder(stored.task, stored.reminder).toDomain())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTitleIsRejectedBeforePersistence() {
        task(reminder = null).copy(title = "   ").toPersistenceModel()
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimeRangeIsRejectedBeforePersistence() {
        val instant = Instant.parse("2026-08-26T08:00:00Z")
        task(reminder = TaskReminder.TimeRange(instant, instant)).toPersistenceModel()
    }

    private fun task(reminder: TaskReminder?) = TodoTask(
        title = "Executor 验证",
        note = "阶段二数据测试",
        date = LocalDate.of(2026, 8, 26),
        reminder = reminder,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
