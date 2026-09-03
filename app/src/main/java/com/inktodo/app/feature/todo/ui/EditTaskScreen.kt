package com.inktodo.app.feature.todo.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.ReminderType
import com.inktodo.app.core.ui.components.InkDestructiveButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSecondaryButton
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import java.time.LocalDate
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
                onDelete = { confirmDelete = true },
                onStopReminder = { confirmStopReminder = true },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("永久删除？") },
            text = { Text("删除后无法恢复。") },
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
}

@Composable
private fun TaskEditorForm(
    state: EditTaskUiState,
    viewModel: EditTaskViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onStopReminder: () -> Unit,
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
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        InkSceneHeader(
            title = if (state.isExistingTask) "编辑待办" else "新建待办",
            scene = InkScene.EDITOR,
            subtitle = "落纸成事，留白有序",
            action = { InkSecondaryButton(text = "返回", onClick = onBack) },
        )

        InkGlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(modifier = Modifier.padding(spacing.sm)) {
                TextField(
                    value = state.draft.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
                    label = { Text("任务名称") },
                    singleLine = true,
                    colors = textFieldColors,
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(spacing.sm))
                TextField(
                    value = state.draft.note,
                    onValueChange = viewModel::setNote,
                    modifier = Modifier.fillMaxWidth().testTag("task_note_input"),
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    colors = textFieldColors,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }

        if (state.draft.reminderType == ReminderType.AT_TIME) {
            Spacer(Modifier.height(spacing.md))
            InkGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        InkSectionLabel("日期")
                        Text(
                            state.draft.date.format(editorDateFormatter),
                            color = colors.ink,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    InkSecondaryButton(
                        text = "选择日期",
                        modifier = Modifier.testTag("at_time_date"),
                        onClick = { showDatePicker(context, state.draft.date, viewModel::setDate) },
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.md))
        InkGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(spacing.md)) {
                InkSectionLabel("提醒方式")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = spacing.touchTarget),
                        selected = state.draft.reminderType == ReminderType.AT_TIME,
                        onClick = { viewModel.setReminderType(ReminderType.AT_TIME) },
                        label = { Text("某个时刻") },
                    )
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = spacing.touchTarget),
                        selected = state.draft.reminderType == ReminderType.TIME_RANGE,
                        onClick = { viewModel.setReminderType(ReminderType.TIME_RANGE) },
                        label = { Text("持续时间段") },
                    )
                }

                if (state.draft.reminderType == ReminderType.AT_TIME) {
                    InkSectionLabel("提醒时间")
                    InkSecondaryButton(
                        text = state.draft.atTime.format(editorTimeFormatter),
                        onClick = { showTimePicker(context, state.draft.atTime, viewModel::setAtTime) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用系统闹钟", color = colors.ink, style = MaterialTheme.typography.titleMedium)
                            Text("正式时间交由手机时钟强提醒", color = colors.inkSoft, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = state.draft.useSystemAlarm,
                            onCheckedChange = viewModel::setUseSystemAlarm,
                            modifier = Modifier.semantics { contentDescription = "使用系统闹钟" },
                        )
                    }
                } else {
                    InkSectionLabel("持续时间 · 可跨日期")
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
                    Spacer(Modifier.height(spacing.sm))
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
                }
            }
        }

        state.validationMessage?.let {
            Spacer(Modifier.height(spacing.sm))
            Text(it, color = colors.cinnabar, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(spacing.xl))
        InkPrimaryButton(
            text = if (state.isSaving) "保存中…" else "保存",
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("save_task"),
        )
        if (state.isExistingTask) {
            if (state.canStopOngoingReminder) {
                InkDestructiveButton(
                    text = "结束持续提醒",
                    onClick = onStopReminder,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("stop_ongoing_reminder"),
                )
            }
            InkDestructiveButton(
                text = "删除此任务",
                onClick = onDelete,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("delete_task_from_editor"),
            )
        }
        Spacer(Modifier.height(spacing.xxl))
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
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            InkSecondaryButton(
                text = date.format(editorDateFormatter),
                modifier = Modifier.weight(1f).testTag(dateTag),
                onClick = onDateClick,
            )
            InkSecondaryButton(
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
