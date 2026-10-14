package com.inktodo.app.feature.calendar.domain

import com.inktodo.app.core.model.TaskRecurrence
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpecialDateTaskCoordinator(
    private val specialDates: SpecialDateRepository,
    private val tasks: TaskRepository,
    private val commands: TaskCommandService,
    private val calendar: OfflineCalendarService,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId? = null,
) {
    private val mutex = Mutex()

    suspend fun save(value: SpecialDate): Long = mutex.withLock {
        require(value.title.isNotBlank()) { "请输入特殊日期名称。" }
        validate(value)
        val now = Instant.now(clock)
        val normalized = value.copy(
            title = value.title.trim(),
            note = value.note?.trim()?.takeIf(String::isNotEmpty),
            createdAt = value.createdAt.takeIf { value.id > 0 } ?: now,
            updatedAt = now,
        )
        if (value.id > 0) clearTasks(value.id)
        val id = specialDates.save(normalized)
        reconcileDefinition(checkNotNull(specialDates.findById(id)), now)
        id
    }

    suspend fun delete(id: Long): Boolean = mutex.withLock {
        clearTasks(id)
        specialDates.delete(id)
    }

    suspend fun reconcile(now: Instant = Instant.now(clock)) = mutex.withLock {
        val zone = zoneId ?: ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        tasks.getExpiredSpecialDateTasks(today).forEach { task ->
            runCatching { commands.delete(task.id) }
        }
        specialDates.getAll().forEach { value -> reconcileDefinition(value, now) }
    }

    private suspend fun reconcileDefinition(value: SpecialDate, now: Instant) {
        if (!value.reminderEnabled) return
        val zone = zoneId ?: ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val occurrence = calendar.nextOccurrence(value, today, GENERATION_LEAD_DAYS) ?: return
        if (occurrence.isAfter(today.plusDays(GENERATION_LEAD_DAYS))) return
        for (offset in FIRST_TASK_OFFSET..0) {
            val taskDate = occurrence.plusDays(offset.toLong())
            if (taskDate.isBefore(today)) continue
            if (tasks.findSpecialOccurrence(value.id, occurrence, taskDate) != null) continue
            val startAt = taskDate.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant()
            val endAt = taskDate.plusDays(1).atStartOfDay(zone).toInstant()
            if (!endAt.isAfter(now)) continue
            val task = TodoTask(
                title = titleFor(value.title, offset),
                note = value.note,
                date = taskDate,
                reminder = TaskReminder.TimeRange(
                    startAt = startAt,
                    endAt = endAt,
                    repeatIntervalMinutes = 60,
                ),
                createdAt = now,
                updatedAt = now,
                recurrence = TaskRecurrence.NONE,
                specialDateId = value.id,
                specialOccurrenceDate = occurrence,
                specialOffsetDays = offset,
            )
            runCatching { commands.save(task) }
                .onFailure { error ->
                    if (tasks.findSpecialOccurrence(value.id, occurrence, taskDate) == null) throw error
                }
        }
    }

    private suspend fun clearTasks(specialDateId: Long) {
        tasks.getSpecialDateTasks(specialDateId).forEach { task ->
            runCatching { commands.delete(task.id) }
        }
    }

    private fun validate(value: SpecialDate) {
        when (value.repeatMode) {
            SpecialDateRepeatMode.ONCE,
            SpecialDateRepeatMode.ANNUAL_GREGORIAN -> {
                require(value.calendarType == SpecialDateCalendarType.GREGORIAN) { "公历重复需使用公历日期。" }
                requireNotNull(value.gregorianDate) { "请选择公历日期。" }
            }
            SpecialDateRepeatMode.ANNUAL_LUNAR -> {
                require(value.calendarType == SpecialDateCalendarType.LUNAR) { "农历重复需使用农历日期。" }
                require(value.lunarMonth in 1..12) { "农历月份无效。" }
                require(value.lunarDay in 1..30) { "农历日期无效。" }
            }
        }
    }

    private fun titleFor(name: String, offset: Int): String = when (offset) {
        -1 -> "明天是$name"
        0 -> "今天是$name"
        else -> "距离${name}还有${-offset}天"
    }

    companion object {
        const val GENERATION_LEAD_DAYS = 5L
        const val FIRST_TASK_OFFSET = -4
    }
}
