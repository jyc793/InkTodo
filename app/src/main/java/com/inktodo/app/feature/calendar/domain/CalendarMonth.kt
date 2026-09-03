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
    val selectedTasks: List<TodoTask>,
)

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
    ): CalendarMonthProjection = CalendarMonthProjection(
        month = month,
        today = today,
        selectedDate = selectedDate,
        cells = cells(month),
        taskDates = tasks.flatMapTo(mutableSetOf()) { it.coveredDatesIn(month, zoneId).asIterable() },
        selectedTasks = tasks
            .filter { it.coversDate(selectedDate, zoneId) }
            .sortedByPreference(TaskSortOrder.TIME_ASCENDING),
    )

    const val COLUMN_COUNT = 7
    const val CELL_COUNT = 42
}
