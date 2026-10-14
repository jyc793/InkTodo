package com.inktodo.app.feature.calendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.calendar.domain.CalendarDayDecoration
import com.inktodo.app.feature.calendar.domain.CalendarMonthBuilder
import com.inktodo.app.feature.calendar.domain.CalendarMonthProjection
import com.inktodo.app.feature.calendar.domain.OfficialDayKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年 M月")
private val selectedDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onManageSpecialDates: () -> Unit,
    onOpenHolidayInfo: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = InkTheme.spacing

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    ) {
        val tabletLandscape = maxWidth >= 720.dp && maxWidth > maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
            InkSceneHeader(
                title = "日历",
                scene = InkScene.CALENDAR,
                subtitle = "公历农历，节气有期",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        InkCompactButton(
                            text = "＋",
                            onClick = onManageSpecialDates,
                            modifier = Modifier.testTag("calendar_special_dates"),
                        )
                        InkCompactButton(
                            text = "今天",
                            onClick = viewModel::goToToday,
                            modifier = Modifier.testTag("calendar_today"),
                        )
                    }
                },
            )

            if (state.holidayDataUpdateNeeded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.xs)
                        .clickable(onClick = onOpenHolidayInfo)
                        .testTag("holiday_data_update_notice"),
                    shape = MaterialTheme.shapes.small,
                    color = InkTheme.colors.cinnabar.copy(alpha = 0.10f),
                    border = BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.cinnabar.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "下一年度节假日数据待更新 · 查看说明",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        color = InkTheme.colors.cinnabar,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (tabletLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.25f),
                    ) {
                        CalendarPanel(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDate)
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.75f),
                    ) {
                        SelectedDateTasks(
                            state,
                            onOpenTask,
                            onEditTask,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    CalendarPanel(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDate)
                    SelectedDateTasks(
                        state,
                        onOpenTask,
                        onEditTask,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPanel(
    state: CalendarMonthProjection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    InkGlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(modifier = Modifier.padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs)) {
            MonthHeader(state, onPrevious, onNext)
            WeekHeader()
            MonthGrid(state, onSelectDate)
        }
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
        TextButton(onClick = onPrevious, modifier = Modifier.testTag("calendar_previous")) { Text("上月") }
        Text(
            state.month.atDay(1).format(monthTitleFormatter),
            color = InkTheme.colors.indigo,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onNext, modifier = Modifier.testTag("calendar_next")) { Text("下月") }
    }
}

@Composable
private fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekLabels.forEach { label ->
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp),
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
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
                    decoration = date?.let(state.dayDecorations::get),
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
    decoration: CalendarDayDecoration?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(InkTheme.spacing.calendarCellHeight)
            .then(if (date != null) Modifier.testTag("calendar_day_$date") else Modifier)
            .semantics {
                if (date != null) {
                    contentDescription = buildString {
                        append(date.monthValue).append("月").append(date.dayOfMonth).append("日")
                        if (isToday) append("，今天")
                        decoration?.let { append("，").append(it.shortLabel) }
                    }
                    selected = isSelected
                }
            }
            .clickable(enabled = date != null, onClick = onClick)
            .padding(2.dp),
        shape = MaterialTheme.shapes.small,
        color = when {
            isToday -> InkTheme.colors.cinnabar.copy(alpha = 0.13f)
            isSelected -> InkTheme.colors.jade.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        border = when {
            isToday -> BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.cinnabar)
            isSelected -> BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.jade)
            else -> null
        },
    ) {
        if (date != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (isToday) InkTheme.colors.cinnabar else InkTheme.colors.ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    decoration?.officialDayKind?.let { kind ->
                        Text(
                            text = if (kind == OfficialDayKind.HOLIDAY) "休" else "班",
                            modifier = Modifier.padding(start = 1.dp),
                            color = if (kind == OfficialDayKind.HOLIDAY) InkTheme.colors.cinnabar else InkTheme.colors.indigo,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    text = decoration?.shortLabel.orEmpty(),
                    color = if (decoration?.fullLabels?.isNotEmpty() == true) InkTheme.colors.indigo else InkTheme.colors.mist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SelectedDateTasks(
    state: CalendarMonthProjection,
    onOpenTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        InkSectionLabel(
            text = state.selectedDate.format(selectedDateFormatter),
            modifier = Modifier.padding(top = InkTheme.spacing.xs),
        )
        state.selectedDecoration?.let { decoration ->
            Text(
                text = buildString {
                    append(decoration.fullLunarText)
                    if (decoration.fullLabels.isNotEmpty()) {
                        append(" · ").append(decoration.fullLabels.joinToString("、"))
                    }
                },
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.selectedTasks.isEmpty()) {
            Text(
                "当日无未完成待办。",
                modifier = Modifier.padding(vertical = InkTheme.spacing.sm),
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = InkTheme.spacing.xxl,
                ),
            ) {
                items(state.selectedTasks, key = { it.id }) { task ->
                    CalendarTaskCard(task, onOpenTask, onEditTask)
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskCard(
    task: TodoTask,
    onOpenTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = InkTheme.spacing.xs)
            .testTag("calendar_task_${task.id}")
            .pointerInput(task.id) {
                detectTapGestures(
                    onTap = { onOpenTask(task.id) },
                    onDoubleTap = { onEditTask(task.id) },
                )
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = InkTheme.spacing.sm, vertical = 7.dp)) {
            Text(
                task.title,
                color = InkTheme.colors.ink,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            task.note?.let {
                Text(
                    it,
                    color = InkTheme.colors.mist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
