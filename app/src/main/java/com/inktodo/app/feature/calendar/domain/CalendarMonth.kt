package com.inktodo.app.feature.calendar.domain

import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.coveredDatesIn
import com.inktodo.app.core.model.coversDate
import com.inktodo.app.feature.todo.domain.sortedByPreference
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarMonthProjection(
    val month: YearMonth,
    val today: LocalDate,
    val selectedDate: LocalDate,
    val cells: List<LocalDate?>,
    val taskDates: Set<LocalDate>,
    val daySummaries: Map<LocalDate, CalendarDaySummary>,
    val selectedTasks: List<TodoTask>,
    val dayDecorations: Map<LocalDate, CalendarDayDecoration> = emptyMap(),
    val selectedDecoration: CalendarDayDecoration? = null,
    val holidayDataUpdateNeeded: Boolean = false,
)

data class CalendarDaySummary(val count: Int, val titles: List<String>) {
    val preview: String get() = titles.joinToString("、")
}

object CalendarMonthBuilder {
    fun cells(month: YearMonth): List<LocalDate?> {
        val leadingEmpty = month.atDay(1).dayOfWeek.value - 1
        return List(CELL_COUNT) { index ->
            val day = index - leadingEmpty + 1
            day.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
        }
    }

    fun project(
        tasks: List<TodoTask>,
        month: YearMonth,
        selectedDate: LocalDate,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CalendarMonthProjection {
        val dates = tasks.flatMapTo(mutableSetOf()) { it.coveredDatesIn(month, zoneId).asIterable() }
        val dailyTasks = dates.associateWith { date -> tasksForDate(tasks, date, zoneId) }
        return CalendarMonthProjection(
            month = month,
            today = today,
            selectedDate = selectedDate,
            cells = cells(month),
            taskDates = dates,
            daySummaries = dailyTasks.mapValues { (_, dayTasks) ->
                CalendarDaySummary(dayTasks.size, dayTasks.map(TodoTask::title))
            },
            selectedTasks = tasksForDate(tasks, selectedDate, zoneId),
        )
    }

    private fun tasksForDate(tasks: List<TodoTask>, date: LocalDate, zoneId: ZoneId): List<TodoTask> =
        tasks.filter { it.coversDate(date, zoneId) }
            .groupBy { it.recurrenceSeriesId ?: "task-${it.id}" }
            .values
            .mapNotNull { series -> series.filter { !it.date.isAfter(date) }.maxByOrNull(TodoTask::date) }
            .sortedByPreference(TaskSortOrder.TIME_ASCENDING)

    const val COLUMN_COUNT = 7
    const val CELL_COUNT = 42
}
