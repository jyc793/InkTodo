package com.inktodo.app.feature.calendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSecondaryButton
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.feature.calendar.domain.CalendarMonthBuilder
import com.inktodo.app.feature.calendar.domain.CalendarMonthProjection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年 M月")
private val selectedDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = InkTheme.spacing.lg, vertical = InkTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
    ) {
        InkSceneHeader(
            title = "日历",
            scene = InkScene.CALENDAR,
            subtitle = "月下有期，墨点为记",
            action = {
                InkSecondaryButton(
                    text = "回到今天",
                    onClick = viewModel::goToToday,
                    modifier = Modifier.testTag("calendar_today"),
                )
            },
        )
        InkGlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(modifier = Modifier.padding(InkTheme.spacing.sm)) {
                MonthHeader(state, viewModel::previousMonth, viewModel::nextMonth)
                WeekHeader()
                MonthGrid(state, viewModel::selectDate)
            }
        }
        SelectedDateTasks(state, onOpenTask)
        Spacer(Modifier.size(InkTheme.spacing.xxl))
    }
}

@Composable
private fun MonthHeader(
    state: CalendarMonthProjection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, modifier = Modifier.testTag("calendar_previous")) { Text("上一月") }
        Text(
            state.month.atDay(1).format(monthTitleFormatter),
            color = InkTheme.colors.indigo,
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onNext, modifier = Modifier.testTag("calendar_next")) { Text("下一月") }
    }
}

@Composable
private fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekLabels.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarMonthProjection, onSelect: (LocalDate) -> Unit) {
    state.cells.chunked(CalendarMonthBuilder.COLUMN_COUNT).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                DayCell(
                    date = date,
                    isToday = date == state.today,
                    isSelected = date == state.selectedDate,
                    hasTasks = date in state.taskDates,
                    onClick = { date?.let(onSelect) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .sizeIn(minHeight = InkTheme.spacing.touchTarget)
            .then(if (date != null) Modifier.testTag("calendar_day_$date") else Modifier)
            .semantics {
                if (date != null) {
                    contentDescription = buildString {
                        append(date.monthValue).append("月").append(date.dayOfMonth).append("日")
                        if (isToday) append("，今天")
                        if (hasTasks) append("，有待办")
                    }
                    selected = isSelected
                }
            }
            .clickable(enabled = date != null, onClick = onClick)
            .padding(InkTheme.spacing.xs),
        shape = if (isToday) MaterialTheme.shapes.small else CircleShape,
        color = when {
            isToday -> InkTheme.colors.cinnabar.copy(alpha = 0.14f)
            isSelected -> InkTheme.colors.jade.copy(alpha = 0.20f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        border = when {
            isToday -> BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.cinnabar)
            isSelected -> BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.jade)
            else -> null
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (date != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfMonth.toString(),
                        color = if (isToday) InkTheme.colors.cinnabar else InkTheme.colors.ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isToday || hasTasks) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isToday) {
                                Text(
                                    "今",
                                    color = InkTheme.colors.cinnabar,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (hasTasks) {
                                Box(
                                    Modifier
                                        .size(InkTheme.spacing.xs)
                                        .background(InkTheme.colors.indigo, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateTasks(state: CalendarMonthProjection, onOpenTask: (Long) -> Unit) {
    InkSectionLabel(
        text = state.selectedDate.format(selectedDateFormatter),
        modifier = Modifier.padding(top = InkTheme.spacing.md),
    )
    if (state.selectedTasks.isEmpty()) {
        Text("当日无未完成待办。", color = InkTheme.colors.mist, style = MaterialTheme.typography.bodyMedium)
    } else {
        state.selectedTasks.forEach { task -> CalendarTaskCard(task, onOpenTask) }
    }
}

@Composable
private fun CalendarTaskCard(task: TodoTask, onOpenTask: (Long) -> Unit) {
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_task_${task.id}"),
        onClick = { onOpenTask(task.id) },
    ) {
        Column(modifier = Modifier.padding(InkTheme.spacing.md)) {
            Text(task.title, color = InkTheme.colors.ink, style = MaterialTheme.typography.titleMedium)
            task.note?.let {
                Text(it, color = InkTheme.colors.mist, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}
