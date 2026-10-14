package com.inktodo.app.feature.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.calendar.domain.SpecialDate
import com.inktodo.app.feature.calendar.domain.SpecialDateCalendarType
import com.inktodo.app.feature.calendar.domain.SpecialDateRepeatMode
import com.inktodo.app.feature.calendar.domain.SpecialDateRepository
import com.inktodo.app.feature.calendar.domain.SpecialDateTaskCoordinator
import com.inktodo.app.feature.todo.ui.simpleViewModelFactory
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpecialDateListViewModel(repository: SpecialDateRepository) : ViewModel() {
    val items: StateFlow<List<SpecialDate>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: SpecialDateRepository): ViewModelProvider.Factory =
            simpleViewModelFactory { SpecialDateListViewModel(repository) }
    }
}

@Composable
fun SpecialDateListScreen(
    viewModel: SpecialDateListViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val spacing = InkTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    ) {
        InkSceneHeader(
            title = "特殊日期",
            scene = InkScene.CALENDAR,
            subtitle = "生日与纪念日",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    InkCompactButton("返回", onBack)
                    InkCompactButton("新增", onAdd, Modifier.testTag("special_date_add"))
                }
            },
        )
        if (items.isEmpty()) {
            Text(
                "尚未添加生日或纪念日。",
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(items, key = SpecialDate::id) { item ->
                    InkGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item.id) }
                            .testTag("special_date_${item.id}"),
                    ) {
                        Column(Modifier.padding(spacing.sm)) {
                            Text(item.title, color = InkTheme.colors.ink, style = MaterialTheme.typography.titleMedium)
                            Text(
                                specialDateSummary(item),
                                color = InkTheme.colors.mist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SpecialDateEditState(
    val loading: Boolean = true,
    val title: String = "",
    val note: String = "",
    val calendarType: SpecialDateCalendarType = SpecialDateCalendarType.GREGORIAN,
    val repeatMode: SpecialDateRepeatMode = SpecialDateRepeatMode.ANNUAL_GREGORIAN,
    val gregorianDateText: String = LocalDate.now().toString(),
    val lunarMonthText: String = "1",
    val lunarDayText: String = "1",
    val lunarLeapMonth: Boolean = false,
    val reminderEnabled: Boolean = true,
    val error: String? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val canDelete: Boolean = false,
)

class SpecialDateEditViewModel(
    private val id: Long,
    private val repository: SpecialDateRepository,
    private val coordinator: SpecialDateTaskCoordinator,
) : ViewModel() {
    val state = MutableStateFlow(SpecialDateEditState())
    private var original: SpecialDate? = null

    init {
        viewModelScope.launch {
            original = id.takeIf { it > 0 }?.let { repository.findById(it) }
            val value = original
            state.update {
                if (value == null) it.copy(loading = false)
                else it.copy(
                    loading = false,
                    title = value.title,
                    note = value.note.orEmpty(),
                    calendarType = value.calendarType,
                    repeatMode = value.repeatMode,
                    gregorianDateText = value.gregorianDate?.toString() ?: LocalDate.now().toString(),
                    lunarMonthText = value.lunarMonth?.toString() ?: "1",
                    lunarDayText = value.lunarDay?.toString() ?: "1",
                    lunarLeapMonth = value.lunarIsLeapMonth,
                    reminderEnabled = value.reminderEnabled,
                    canDelete = true,
                )
            }
        }
    }

    fun updateTitle(value: String) = state.update { it.copy(title = value, error = null) }
    fun updateNote(value: String) = state.update { it.copy(note = value, error = null) }
    fun updateCalendarType(value: SpecialDateCalendarType) = state.update {
        it.copy(
            calendarType = value,
            repeatMode = when {
                value == SpecialDateCalendarType.LUNAR -> SpecialDateRepeatMode.ANNUAL_LUNAR
                it.repeatMode == SpecialDateRepeatMode.ANNUAL_LUNAR -> SpecialDateRepeatMode.ANNUAL_GREGORIAN
                else -> it.repeatMode
            },
            error = null,
        )
    }
    fun updateRepeatMode(value: SpecialDateRepeatMode) = state.update {
        it.copy(
            repeatMode = value,
            calendarType = if (value == SpecialDateRepeatMode.ANNUAL_LUNAR) {
                SpecialDateCalendarType.LUNAR
            } else SpecialDateCalendarType.GREGORIAN,
            error = null,
        )
    }
    fun updateGregorianDate(value: String) = state.update { it.copy(gregorianDateText = value, error = null) }
    fun updateLunarMonth(value: String) = state.update { it.copy(lunarMonthText = value.filter(Char::isDigit), error = null) }
    fun updateLunarDay(value: String) = state.update { it.copy(lunarDayText = value.filter(Char::isDigit), error = null) }
    fun updateLeapMonth(value: Boolean) = state.update { it.copy(lunarLeapMonth = value, error = null) }
    fun updateReminder(value: Boolean) = state.update { it.copy(reminderEnabled = value, error = null) }
    fun requestDelete() = state.update { it.copy(showDeleteConfirmation = true) }
    fun dismissDelete() = state.update { it.copy(showDeleteConfirmation = false) }

    fun save() {
        if (state.value.saving) return
        viewModelScope.launch {
            state.update { it.copy(saving = true, error = null) }
            runCatching {
                val current = state.value
                val gregorian = if (current.repeatMode == SpecialDateRepeatMode.ANNUAL_LUNAR) null
                else LocalDate.parse(current.gregorianDateText.trim())
                val now = Instant.now()
                coordinator.save(
                    SpecialDate(
                        id = id,
                        title = current.title,
                        note = current.note,
                        calendarType = current.calendarType,
                        repeatMode = current.repeatMode,
                        gregorianDate = gregorian,
                        lunarMonth = current.lunarMonthText.toIntOrNull(),
                        lunarDay = current.lunarDayText.toIntOrNull(),
                        lunarIsLeapMonth = current.lunarLeapMonth,
                        reminderEnabled = current.reminderEnabled,
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }.onSuccess {
                state.update { it.copy(saving = false, finished = true) }
            }.onFailure { error ->
                state.update { it.copy(saving = false, error = error.message ?: "保存失败") }
            }
        }
    }

    fun delete() {
        if (id <= 0 || state.value.saving) return
        viewModelScope.launch {
            state.update { it.copy(saving = true, showDeleteConfirmation = false) }
            runCatching { coordinator.delete(id) }
                .onSuccess { state.update { it.copy(saving = false, finished = true) } }
                .onFailure { error -> state.update { it.copy(saving = false, error = error.message ?: "删除失败") } }
        }
    }

    companion object {
        fun factory(
            id: Long,
            repository: SpecialDateRepository,
            coordinator: SpecialDateTaskCoordinator,
        ): ViewModelProvider.Factory = simpleViewModelFactory {
            SpecialDateEditViewModel(id, repository, coordinator)
        }
    }
}

@Composable
fun SpecialDateEditScreen(
    viewModel: SpecialDateEditViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = InkTheme.spacing
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        InkSceneHeader(
            title = if (state.loading) "特殊日期" else if (state.title.isBlank()) "新增特殊日期" else "编辑特殊日期",
            scene = InkScene.CALENDAR,
            action = { InkCompactButton("返回", onBack) },
        )
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::updateTitle,
            label = { Text("名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("special_date_title"),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::updateNote,
            label = { Text("备注") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        InkSectionLabel("日期类型")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FilterChip(
                selected = state.calendarType == SpecialDateCalendarType.GREGORIAN,
                onClick = { viewModel.updateCalendarType(SpecialDateCalendarType.GREGORIAN) },
                label = { Text("公历") },
            )
            FilterChip(
                selected = state.calendarType == SpecialDateCalendarType.LUNAR,
                onClick = { viewModel.updateCalendarType(SpecialDateCalendarType.LUNAR) },
                label = { Text("农历") },
            )
        }
        InkSectionLabel("重复规则")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            RepeatChip("仅一次", SpecialDateRepeatMode.ONCE, state.repeatMode, viewModel::updateRepeatMode)
            RepeatChip("每年公历", SpecialDateRepeatMode.ANNUAL_GREGORIAN, state.repeatMode, viewModel::updateRepeatMode)
            RepeatChip("每年农历", SpecialDateRepeatMode.ANNUAL_LUNAR, state.repeatMode, viewModel::updateRepeatMode)
        }
        if (state.repeatMode == SpecialDateRepeatMode.ANNUAL_LUNAR) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = state.lunarMonthText,
                    onValueChange = viewModel::updateLunarMonth,
                    label = { Text("农历月") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.lunarDayText,
                    onValueChange = viewModel::updateLunarDay,
                    label = { Text("农历日") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SettingSwitch("闰月日期", state.lunarLeapMonth, viewModel::updateLeapMonth)
            Text("当年无对应闰月时回退到同月同日；月天数不足时取该月最后一天。", style = MaterialTheme.typography.bodySmall, color = InkTheme.colors.mist)
        } else {
            OutlinedTextField(
                value = state.gregorianDateText,
                onValueChange = viewModel::updateGregorianDate,
                label = { Text("公历日期（yyyy-MM-dd）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingSwitch("提前五天自动提醒", state.reminderEnabled, viewModel::updateReminder)
        state.error?.let { Text(it, color = InkTheme.colors.cinnabar, style = MaterialTheme.typography.bodySmall) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
        ) {
            if (state.canDelete) {
                TextButton(onClick = viewModel::requestDelete, enabled = !state.saving) { Text("删除") }
            }
            Button(onClick = viewModel::save, enabled = !state.loading && !state.saving) {
                Text(if (state.saving) "处理中…" else "保存")
            }
        }
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除特殊日期？") },
            text = { Text("关联的自动待办、通知和确认浮层会一并清除。") },
            confirmButton = { TextButton(onClick = viewModel::delete) { Text("删除") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("取消") } },
        )
    }
}

@Composable
private fun RepeatChip(
    label: String,
    value: SpecialDateRepeatMode,
    selected: SpecialDateRepeatMode,
    onSelected: (SpecialDateRepeatMode) -> Unit,
) {
    FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label) })
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = InkTheme.colors.ink, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun specialDateSummary(value: SpecialDate): String {
    val date = when (value.repeatMode) {
        SpecialDateRepeatMode.ONCE -> "仅一次 · ${value.gregorianDate}"
        SpecialDateRepeatMode.ANNUAL_GREGORIAN -> "每年公历 · ${value.gregorianDate?.monthValue}月${value.gregorianDate?.dayOfMonth}日"
        SpecialDateRepeatMode.ANNUAL_LUNAR -> "每年农历 · ${if (value.lunarIsLeapMonth) "闰" else ""}${value.lunarMonth}月${value.lunarDay}日"
    }
    return "$date · ${if (value.reminderEnabled) "五天提醒已开" else "五天提醒已关"}"
}
