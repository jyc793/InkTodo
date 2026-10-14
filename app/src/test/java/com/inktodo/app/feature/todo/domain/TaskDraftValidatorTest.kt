package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.RepeatMode
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDraftValidatorTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 8, 25, 14, 30, 0, 0, zone)

    @Test
    fun blankTitleIsRejected() {
        assertInvalid(draft(title = " "), "请输入任务名称。")
    }

    @Test
    fun pastAtTimeReminderIsRejected() {
        assertInvalid(draft(atTime = LocalTime.of(14, 0)), "提醒时间必须晚于当前时间。")
    }

    @Test
    fun futureAtTimePreservesSystemAlarmChoice() {
        val result = TaskDraftValidator.validate(
            draft(atTime = LocalTime.of(15, 0), useSystemAlarm = true),
            now,
            zone,
        ) as TaskDraftValidation.Valid

        assertEquals(
            TaskReminder.AtTime(Instant.parse("2026-08-25T15:00:00Z"), true),
            result.reminder,
        )
    }

    @Test
    fun endTimeMustBeAfterStartTime() {
        assertInvalid(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeStartTime = LocalTime.of(16, 0),
                rangeEndTime = LocalTime.of(16, 0),
            ),
            "结束时间必须晚于开始时间。",
        )
    }

    @Test
    fun rangeAlreadyInProgressIsValidUntilItsFutureEnd() {
        val result = TaskDraftValidator.validate(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeStartTime = LocalTime.of(14, 0),
                rangeEndTime = LocalTime.of(16, 0),
            ),
            now,
            zone,
        )

        assertTrue(result is TaskDraftValidation.Valid)
    }

    @Test
    fun endedRangeIsRejected() {
        assertInvalid(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeStartTime = LocalTime.of(13, 0),
                rangeEndTime = LocalTime.of(14, 0),
            ),
            "结束时间必须晚于当前时间。",
        )
    }

    @Test
    fun dailyAtTimePastTodayMovesToTomorrow() {
        val result = TaskDraftValidator.validate(
            draft(atTime = LocalTime.of(14, 0)).copy(repeatMode = RepeatMode.DAILY),
            now,
            zone,
        ) as TaskDraftValidation.Valid

        assertEquals(Instant.parse("2026-08-26T14:00:00Z"), result.reminder.startAt)
    }

    @Test
    fun weeklyOnlySchedulesSelectedWeekdays() {
        val result = TaskDraftValidator.validate(
            draft().copy(
                repeatMode = RepeatMode.WEEKLY,
                repeatWeekdays = setOf(DayOfWeek.THURSDAY),
            ),
            now,
            zone,
        ) as TaskDraftValidation.Valid

        assertEquals(Instant.parse("2026-08-27T15:00:00Z"), result.reminder.startAt)
    }

    @Test
    fun weeklyRequiresAtLeastOneDay() {
        assertInvalid(draft().copy(repeatMode = RepeatMode.WEEKLY), "请至少选择一个重复的星期。")
    }

    @Test
    fun repeatingRangeLongerThanDayIsRejectedWithoutAffectingOneOffRange() {
        assertInvalid(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeEndDate = LocalDate.of(2026, 8, 27),
            ).copy(repeatMode = RepeatMode.DAILY),
            "重复的持续提醒每次须短于 24 小时；长时间段仍可选择不重复。",
        )
    }

    @Test
    fun recurringRangeAcceptsCustomIntervalShorterThanDuration() {
        val result = TaskDraftValidator.validate(
            draft(reminderType = ReminderType.TIME_RANGE)
                .copy(repeatMode = RepeatMode.DAILY, repeatIntervalMinutes = 30),
            now, zone,
        ) as TaskDraftValidation.Valid
        assertEquals(30, (result.reminder as TaskReminder.TimeRange).repeatIntervalMinutes)
    }

    @Test
    fun intervalLongerThanRangeIsRejected() {
        assertInvalid(
            draft(reminderType = ReminderType.TIME_RANGE)
                .copy(repeatMode = RepeatMode.WEEKLY,
                    repeatWeekdays = setOf(DayOfWeek.TUESDAY), repeatIntervalMinutes = 60),
            "间隔时间必须短于每次持续时间。",
        )
    }

    @Test
    fun rangeCanSpanAWeekWithIndependentDates() {
        val result = TaskDraftValidator.validate(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeStartDate = LocalDate.of(2026, 8, 25),
                rangeStartTime = LocalTime.of(16, 0),
                rangeEndDate = LocalDate.of(2026, 9, 1),
                rangeEndTime = LocalTime.of(9, 30),
            ),
            now,
            zone,
        ) as TaskDraftValidation.Valid

        assertEquals(
            TaskReminder.TimeRange(
                Instant.parse("2026-08-25T16:00:00Z"),
                Instant.parse("2026-09-01T09:30:00Z"),
            ),
            result.reminder,
        )
    }

    @Test
    fun rangeEndDateBeforeStartDateIsRejected() {
        assertInvalid(
            draft(
                reminderType = ReminderType.TIME_RANGE,
                rangeStartDate = LocalDate.of(2026, 8, 26),
                rangeEndDate = LocalDate.of(2026, 8, 25),
            ),
            "结束时间必须晚于开始时间。",
        )
    }

    @Test
    fun updatePreservesIdentityCompletionAndCreationTime() {
        val created = Instant.parse("2026-08-01T00:00:00Z")
        val completed = Instant.parse("2026-08-20T00:00:00Z")
        val existing = TodoTask(
            id = 42,
            title = "旧标题",
            date = LocalDate.of(2026, 8, 25),
            reminder = TaskReminder.AtTime(Instant.parse("2026-08-25T15:00:00Z")),
            isCompleted = true,
            completedAt = completed,
            createdAt = created,
            updatedAt = created,
        )
        val changedAt = Instant.parse("2026-08-25T14:30:00Z")
        val updated = draft(title = "  新标题  ").copy(note = "   ").toTodoTask(
            existing = existing,
            reminder = TaskReminder.AtTime(Instant.parse("2026-08-25T16:00:00Z")),
            now = changedAt,
        )

        assertEquals(42, updated.id)
        assertEquals("新标题", updated.title)
        assertEquals(created, updated.createdAt)
        assertTrue(updated.isCompleted)
        assertEquals(completed, updated.completedAt)
        assertEquals(changedAt, updated.updatedAt)
        assertFalse(updated.note != null)
    }

    private fun assertInvalid(draft: TaskDraft, expected: String) {
        val result = TaskDraftValidator.validate(draft, now, zone) as TaskDraftValidation.Invalid
        assertEquals(expected, result.message)
    }

    private fun draft(
        title: String = "Executor 验证",
        reminderType: ReminderType = ReminderType.AT_TIME,
        atTime: LocalTime = LocalTime.of(15, 0),
        rangeStartDate: LocalDate = LocalDate.of(2026, 8, 25),
        rangeStartTime: LocalTime = LocalTime.of(15, 0),
        rangeEndDate: LocalDate = LocalDate.of(2026, 8, 25),
        rangeEndTime: LocalTime = LocalTime.of(16, 0),
        useSystemAlarm: Boolean = false,
    ) = TaskDraft(
        title = title,
        date = LocalDate.of(2026, 8, 25),
        reminderType = reminderType,
        atTime = atTime,
        rangeStartDate = rangeStartDate,
        rangeStartTime = rangeStartTime,
        rangeEndDate = rangeEndDate,
        rangeEndTime = rangeEndTime,
        useSystemAlarm = useSystemAlarm,
    )
}
