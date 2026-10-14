package com.inktodo.app.feature.settings.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.components.InkStatusSeal
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.ThemeMode
import com.inktodo.app.feature.settings.data.ReminderSettingsNavigator
import com.inktodo.app.feature.settings.domain.ReliabilityAction
import com.inktodo.app.feature.settings.domain.ReliabilityItem
import com.inktodo.app.feature.settings.domain.ReliabilityState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navigator: ReminderSettingsNavigator,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = InkTheme.spacing.screenHorizontal, vertical = InkTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
        ) {
            InkSceneHeader(
                title = "提醒可靠性",
                scene = InkScene.SETTINGS,
                subtitle = "先看总状态，只处理未开启项",
                action = { InkCompactButton(text = "返回", onClick = onBack) },
            )

            InkGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reliability_summary"),
                emphasized = true,
            ) {
                Column(modifier = Modifier.padding(InkTheme.spacing.sm)) {
                    Text(
                        state.reliability.summary,
                        color = if (state.reliability.disabledCount > 0) {
                            InkTheme.colors.cinnabar
                        } else {
                            InkTheme.colors.moss
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(InkTheme.spacing.xs))
                    Text(
                        "通知总开关开启后，四个必要渠道会自动建立；已显示“已开启”的项目无需逐个进入。Android 特殊权限仍需由你逐项确认，返回后本页会自动刷新。",
                        color = InkTheme.colors.inkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ReliabilityGroup(
                items = state.reliability.items,
                onAction = { item ->
                    if (item.action == ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val result = navigator.open(item.action, item.channelId)
                        result.message?.let { message ->
                            scope.launch { snackbar.showSnackbar(message) }
                        }
                    }
                },
            )
            HabitPreferences(
                themeMode = state.preferences.themeMode,
                taskSortOrder = state.preferences.taskSortOrder,
                highRefreshRateAnimations = state.preferences.highRefreshRateAnimations,
                onThemeModeSelected = viewModel::setThemeMode,
                onTaskSortOrderSelected = viewModel::setTaskSortOrder,
                onHighRefreshRateAnimationsChanged = viewModel::setHighRefreshRateAnimations,
            )
            Spacer(Modifier.height(InkTheme.spacing.md))
        }
    }
}

@Composable
private fun HabitPreferences(
    themeMode: ThemeMode,
    taskSortOrder: TaskSortOrder,
    highRefreshRateAnimations: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onTaskSortOrderSelected: (TaskSortOrder) -> Unit,
    onHighRefreshRateAnimationsChanged: (Boolean) -> Unit,
) {
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("habit_preferences"),
    ) {
        Column(
            modifier = Modifier.padding(InkTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
        ) {
            Text("使用习惯", color = InkTheme.colors.ink, style = MaterialTheme.typography.titleLarge)
            InkSectionLabel("主题")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                items(ThemeMode.entries) { value ->
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
                        selected = themeMode == value,
                        onClick = { onThemeModeSelected(value) },
                        label = {
                            Text(
                                when (value) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                },
                            )
                        },
                        colors = settingsChipColors(),
                    )
                }
            }
            InkSectionLabel("任务排序")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                items(TaskSortOrder.entries) { value ->
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
                        selected = taskSortOrder == value,
                        onClick = { onTaskSortOrderSelected(value) },
                        label = {
                            Text(
                                if (value == TaskSortOrder.TIME_ASCENDING) "按时间" else "最新创建",
                            )
                        },
                        colors = settingsChipColors(),
                    )
                }
            }
            InkSectionLabel("动画刷新率")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                item {
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
                        selected = !highRefreshRateAnimations,
                        onClick = { onHighRefreshRateAnimationsChanged(false) },
                        label = { Text("标准（省电）") },
                        colors = settingsChipColors(),
                    )
                }
                item {
                    FilterChip(
                        modifier = Modifier
                            .defaultMinSize(minHeight = InkTheme.spacing.touchTarget)
                            .testTag("high_refresh_rate_animations"),
                        selected = highRefreshRateAnimations,
                        onClick = { onHighRefreshRateAnimationsChanged(true) },
                        label = { Text("高刷新率") },
                        colors = settingsChipColors(),
                    )
                }
            }
            Text(
                text = "高刷新率会增加耗电；实际帧率由屏幕、系统省电状态和温度决定。",
                color = InkTheme.colors.inkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReliabilityGroup(
    items: List<ReliabilityItem>,
    onAction: (ReliabilityItem) -> Unit,
) {
    var expandedId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    InkGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs),
        ) {
            items.forEachIndexed { index, item ->
                val groupName = item.reliabilityGroupName()
                if (index > 0) {
                    HorizontalDivider(color = InkTheme.colors.glassEdge.copy(alpha = 0.7f))
                }
                if (index == 0 || items[index - 1].reliabilityGroupName() != groupName) {
                    InkSectionLabel(groupName)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        color = InkTheme.colors.ink,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    InkStatusSeal(
                        text = item.statusText(),
                        scene = when (item.state) {
                            ReliabilityState.ENABLED -> InkScene.SETTINGS
                            ReliabilityState.DISABLED -> InkScene.REMINDER
                            ReliabilityState.SYSTEM_LIMITED -> InkScene.CALENDAR
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.description,
                        modifier = Modifier.weight(1f),
                        color = InkTheme.colors.inkSoft,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (item.guidance != null) {
                        InkCompactButton(
                            text = if (expandedId == item.id) "收起" else "说明",
                            onClick = { expandedId = item.id.takeUnless { expandedId == it } },
                        )
                    }
                    if (item.action != ReliabilityAction.NONE) {
                        InkCompactButton(
                            text = if (item.state == ReliabilityState.SYSTEM_LIMITED) "确认" else "开启",
                            onClick = { onAction(item) },
                            emphasized = item.state == ReliabilityState.DISABLED,
                            modifier = Modifier.testTag("reliability_action_${item.id}"),
                        )
                    }
                }
                AnimatedVisibility(visible = expandedId == item.id) {
                    item.guidance?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(bottom = InkTheme.spacing.xs),
                            color = InkTheme.colors.indigo,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun ReliabilityItem.reliabilityGroupName(): String = when {
    id == "system_clock" -> "系统闹钟"
    id == "lock_screen" || id == "battery_unrestricted" || id.startsWith("hyperos_") -> "系统后台"
    else -> "提醒能力"
}

private fun ReliabilityItem.statusText(): String = when (state) {
    ReliabilityState.ENABLED -> "已开启 ✓"
    ReliabilityState.DISABLED -> "未开启"
    ReliabilityState.SYSTEM_LIMITED -> "受系统限制"
}

@Composable
private fun settingsChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = InkTheme.colors.glass.copy(alpha = InkTheme.glass.subtleCardAlpha),
    labelColor = InkTheme.colors.inkSoft,
    selectedContainerColor = InkTheme.colors.moss.copy(alpha = 0.18f),
    selectedLabelColor = InkTheme.colors.ink,
)
