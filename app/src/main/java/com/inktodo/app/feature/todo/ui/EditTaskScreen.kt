package com.inktodo.app.feature.todo.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.model.RepeatMode
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val editorDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val editorTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EditTaskScreen(
    viewModel: EditTaskViewModel,
    onBack: () -> Unit,
    onFinished: (TaskCommandResult) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStopReminder by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditTaskEvent.Saved -> onFinished(event.result)
                is EditTaskEvent.Deleted -> onFinished(event.result)
                EditTaskEvent.ReminderStopped -> snackbar.showSnackbar("持续提醒已安全结束。")
                is EditTaskEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading) {
                EditorActionBar(
                    state = state,
                    onSave = viewModel::save,
                    onStopReminder = { confirmStopReminder = true },
                    onDelete = { confirmDelete = true },
                )
            }
        },
    ) { contentPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = InkTheme.colors.moss)
            }
        } else {
            TaskEditorForm(
                state = state,
                viewModel = viewModel,
                onBack = onBack,
                onCustomInterval = { showIntervalDialog = true },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("永久删除？") },
            text = { Text(if (state.draft.repeatMode == RepeatMode.NONE) "删除后无法恢复。"
                else "将删除此重复系列中尚未完成的后续任务；过期的已完成重复记录也会自动清理。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }, modifier = Modifier
                    .testTag("confirm_delete_task")
                    .semantics { contentDescription = "确认删除任务" }) {
                    Text("删除", color = InkTheme.colors.cinnabar)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    if (confirmStopReminder) {
        AlertDialog(
            onDismissRequest = { confirmStopReminder = false },
            title = { Text("结束持续提醒？") },
            text = { Text("状态栏中的常驻提醒会立即结束，任务本身仍会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStopReminder = false
                        viewModel.stopOngoingReminder()
                    },
                    modifier = Modifier
                        .testTag("confirm_stop_ongoing_reminder")
                        .semantics { contentDescription = "确认结束持续提醒" },
                ) { Text("结束提醒", color = InkTheme.colors.cinnabar) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStopReminder = false }) { Text("继续提醒") }
            },
        )
    }

    if (showIntervalDialog) {
        var minutes by remember { mutableStateOf(state.draft.repeatIntervalMinutes?.toString().orEmpty()) }
        val value = minutes.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("自定义确认间隔") },
            text = {
                Column {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("分钟（10–720）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Text("须短于每次持续时间；到点需确认后才会撤下弹窗。",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value in 10..720, onClick = {
                    viewModel.setRepeatIntervalMinutes(value)
                    showIntervalDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showIntervalDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskEditorForm(
    state: EditTaskUiState,
    viewModel: EditTaskViewModel,
    onBack: () -> Unit,
    onCustomInterval: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = InkTheme.spacing
    val colors = InkTheme.colors
    val context = LocalContext.current
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceStrong,
        unfocusedContainerColor = colors.glass.copy(alpha = InkTheme.glass.cardAlpha),
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedIndicatorColor = colors.moss,
        unfocusedIndicatorColor = colors.glassEdge,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        InkGlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(
                modifier = Modifier.padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    TextField(
                        value = state.draft.title,
                        onValueChange = viewModel::setTitle,
                        modifier = Modifier.weight(0.58f).testTag("task_title_input"),
                        placeholder = { Text("任务") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = MaterialTheme.shapes.medium,
                        textStyle = MaterialTheme.typography.titleLarge,
                    )
                    TextField(
                        value = state.draft.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.weight(0.42f).testTag("task_note_input"),
                        placeholder = { Text("备注") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        RepeatMode.NONE to "不重复",
                        RepeatMode.DAILY to "每天",
                        RepeatMode.WEEKLY to "每周",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = state.draft.repeatMode == mode,
                            enabled = !state.recurrenceRuleLocked,
                            onClick = { viewModel.setRepeatMode(mode) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                    InkCompactButton(
                        text = "返回",
                        onClick = onBack,
                        modifier = Modifier.weight(0.86f),
                    )
                }
                if (state.draft.repeatMode == RepeatMode.WEEKLY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = day in state.draft.repeatWeekdays,
                                enabled = !state.recurrenceRuleLocked,
                                onClick = { viewModel.toggleRepeatWeekday(day) },
                                label = { Text("一二三四五六日"[day.value - 1].toString()) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.sm))
        InkGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Column(
                    modifier = Modifier.weight(0.32f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilterChip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = spacing.touchTarget),
                        selected = state.draft.reminderType == ReminderType.AT_TIME,
                        onClick = { viewModel.setReminderType(ReminderType.AT_TIME) },
                        label = { Text("某个时刻", maxLines = 1) },
                    )
                    FilterChip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = spacing.touchTarget),
                        selected = state.draft.reminderType == ReminderType.TIME_RANGE,
                        onClick = { viewModel.setReminderType(ReminderType.TIME_RANGE) },
                        label = { Text("持续时间段", maxLines = 1) },
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .heightIn(min = 112.dp)
                        .background(colors.glassEdge),
                )
                Column(
                    modifier = Modifier.weight(0.68f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    if (state.draft.reminderType == ReminderType.AT_TIME) {
                        RangeDateTimeRow(
                            label = "时间",
                            date = state.draft.date,
                            time = state.draft.atTime,
                            dateTag = "at_time_date",
                            timeTag = "at_time_clock",
                            onDateClick = {
                                showDatePicker(context, state.draft.date, viewModel::setDate)
                            },
                            onTimeClick = {
                                showTimePicker(context, state.draft.atTime, viewModel::setAtTime)
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "系统闹钟",
                                modifier = Modifier.weight(1f),
                                color = colors.ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = state.draft.useSystemAlarm,
                                onCheckedChange = viewModel::setUseSystemAlarm,
                                modifier = Modifier.semantics { contentDescription = "使用系统闹钟" },
                            )
                        }
                    } else {
                    RangeDateTimeRow(
                        label = "开始",
                        date = state.draft.rangeStartDate,
                        time = state.draft.rangeStartTime,
                        dateTag = "range_start_date",
                        timeTag = "range_start_time",
                        onDateClick = {
                            showDatePicker(context, state.draft.rangeStartDate, viewModel::setRangeStartDate)
                        },
                        onTimeClick = {
                            showTimePicker(context, state.draft.rangeStartTime, viewModel::setRangeStartTime)
                        },
                    )
                    RangeDateTimeRow(
                        label = "结束",
                        date = state.draft.rangeEndDate,
                        time = state.draft.rangeEndTime,
                        dateTag = "range_end_date",
                        timeTag = "range_end_time",
                        onDateClick = {
                            showDatePicker(context, state.draft.rangeEndDate, viewModel::setRangeEndDate)
                        },
                        onTimeClick = {
                            showTimePicker(context, state.draft.rangeEndTime, viewModel::setRangeEndTime)
                        },
                    )
                    if (state.draft.repeatMode != RepeatMode.NONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("定时确认", modifier = Modifier.weight(1f), color = colors.ink,
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = state.draft.repeatIntervalMinutes != null,
                                onCheckedChange = { checked ->
                                    viewModel.setRepeatIntervalMinutes(if (checked) 30 else null)
                                },
                                modifier = Modifier.semantics { contentDescription = "期间定时确认" },
                            )
                        }
                        if (state.draft.repeatIntervalMinutes != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                listOf(30, 60).forEach { minutes ->
                                    FilterChip(
                                        modifier = Modifier.weight(1f),
                                        selected = state.draft.repeatIntervalMinutes == minutes,
                                        onClick = { viewModel.setRepeatIntervalMinutes(minutes) },
                                        label = { Text("${minutes}分") },
                                    )
                                }
                                InkCompactButton(
                                    text = "自定义",
                                    onClick = onCustomInterval,
                                    modifier = Modifier.weight(1.2f),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        state.validationMessage?.let {
            Spacer(Modifier.height(spacing.sm))
            Text(it, color = colors.cinnabar, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(spacing.md))
    }
}

@Composable
private fun EditorActionBar(
    state: EditTaskUiState,
    onSave: () -> Unit,
    onStopReminder: () -> Unit,
    onDelete: () -> Unit,
) {
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = InkTheme.spacing.screenHorizontal, vertical = InkTheme.spacing.xs),
        emphasized = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isExistingTask && state.canStopOngoingReminder) {
                InkCompactButton(
                    text = "结束提醒",
                    onClick = onStopReminder,
                    enabled = !state.isSaving,
                    destructive = true,
                    modifier = Modifier.testTag("stop_ongoing_reminder"),
                )
            }
            if (state.isExistingTask) {
                InkCompactButton(
                    text = "删除",
                    onClick = onDelete,
                    enabled = !state.isSaving,
                    destructive = true,
                    modifier = Modifier.testTag("delete_task_from_editor"),
                )
            }
            InkPrimaryButton(
                text = if (state.isSaving) "保存中…" else "保存",
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f).testTag("save_task"),
            )
        }
    }
}

@Composable
private fun RangeDateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    dateTag: String,
    timeTag: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    val spacing = InkTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = InkTheme.colors.inkSoft,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            InkCompactButton(
                text = date.format(editorDateFormatter),
                modifier = Modifier.weight(1f).testTag(dateTag),
                onClick = onDateClick,
            )
            InkCompactButton(
                text = time.format(editorTimeFormatter),
                modifier = Modifier.weight(0.72f).testTag(timeTag),
                onClick = onTimeClick,
            )
        }
    }
}

private fun showDatePicker(context: Context, date: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth,
    ).show()
}

private fun showTimePicker(context: Context, time: LocalTime, onSelected: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(LocalTime.of(hour, minute)) },
        time.hour,
        time.minute,
        true,
    ).show()
}
