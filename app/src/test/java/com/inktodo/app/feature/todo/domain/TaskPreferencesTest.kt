package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPreferencesTest {
    @Test
    fun newDraftInheritsHabitsButNeverOneOffContent() {
        val source = TaskDraft(
            title = "",
            note = "",
            date = LocalDate.of(2026, 8, 26),
            atTime = LocalTime.of(9, 0),
            rangeStartTime = LocalTime.of(9, 0),
            rangeEndTime = LocalTime.of(10, 0),
        )

        val result = source.withReusablePreferences(
            UserPreferences(
                lastReminderType = ReminderType.TIME_RANGE,
                lastUseSystemAlarm = true,
            ),
        )

        assertEquals(ReminderType.TIME_RANGE, result.reminderType)
        assertTrue(result.useSystemAlarm)
        assertEquals("", result.title)
        assertEquals("", result.note)
        assertEquals(source.date, result.date)
        assertEquals(source.atTime, result.atTime)
    }

    @Test
    fun timeAndCreationSortingAreDeterministic() {
        val newerLater = task(1, "2026-08-27T10:00:00Z", "2026-08-25T02:00:00Z")
        val olderEarlier = task(2, "2026-08-26T10:00:00Z", "2026-08-25T01:00:00Z")

        assertEquals(
            listOf(2L, 1L),
            listOf(newerLater, olderEarlier)
                .sortedByPreference(TaskSortOrder.TIME_ASCENDING)
                .map(TodoTask::id),
        )
        assertEquals(
            listOf(1L, 2L),
            listOf(newerLater, olderEarlier)
                .sortedByPreference(TaskSortOrder.CREATED_DESCENDING)
                .map(TodoTask::id),
        )
    }

    private fun task(id: Long, reminderAt: String, createdAt: String): TodoTask {
        val reminder = Instant.parse(reminderAt)
        return TodoTask(
            id = id,
            title = "任务$id",
            date = reminder.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
            reminder = com.inktodo.app.core.model.TaskReminder.AtTime(reminder),
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(createdAt),
        )
    }
}
