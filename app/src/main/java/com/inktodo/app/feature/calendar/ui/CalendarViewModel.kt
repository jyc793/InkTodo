package com.inktodo.app.feature.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inktodo.app.feature.calendar.domain.CalendarMonthBuilder
import com.inktodo.app.feature.calendar.domain.CalendarMonthProjection
import com.inktodo.app.feature.todo.domain.TaskRepository
import com.inktodo.app.feature.todo.ui.simpleViewModelFactory
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CalendarViewModel(
    repository: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialToday = LocalDate.now(clock.withZone(zoneId))
    private val visibleMonth = MutableStateFlow(YearMonth.from(initialToday))
    private val selectedDate = MutableStateFlow(initialToday)
    private val today = MutableStateFlow(initialToday)

    init {
        viewModelScope.launch {
            while (isActive) {
                val now = java.time.Instant.now(clock)
                val nextDay = now.atZone(zoneId).toLocalDate().plusDays(1)
                    .atStartOfDay(zoneId).toInstant()
                delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000L))
                today.value = LocalDate.now(clock.withZone(zoneId))
            }
        }
    }

    val uiState: StateFlow<CalendarMonthProjection> = combine(
        repository.observePending(),
        visibleMonth,
        selectedDate,
        today,
    ) { tasks, month, selected, currentToday ->
        CalendarMonthBuilder.project(
            tasks = tasks,
            month = month,
            selectedDate = selected,
            today = currentToday,
            zoneId = zoneId,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        CalendarMonthBuilder.project(emptyList(), visibleMonth.value, selectedDate.value, initialToday),
    )

    fun previousMonth() = changeMonth(visibleMonth.value.minusMonths(1))
    fun nextMonth() = changeMonth(visibleMonth.value.plusMonths(1))

    fun goToToday() {
        val today = LocalDate.now(clock.withZone(zoneId))
        this.today.value = today
        visibleMonth.value = YearMonth.from(today)
        selectedDate.value = today
    }

    fun selectDate(date: LocalDate) {
        if (YearMonth.from(date) != visibleMonth.value) return
        selectedDate.value = date
    }

    private fun changeMonth(month: YearMonth) {
        visibleMonth.value = month
        selectedDate.value = month.atDay(1)
    }

    companion object {
        fun factory(repository: TaskRepository): ViewModelProvider.Factory =
            simpleViewModelFactory { CalendarViewModel(repository) }
    }
}
