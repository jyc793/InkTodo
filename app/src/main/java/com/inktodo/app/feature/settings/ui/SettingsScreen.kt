package com.inktodo.app.feature.settings.ui

import android.Manifest
import android.os.Build
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
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSecondaryButton
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
                .padding(horizontal = InkTheme.spacing.lg, vertical = InkTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.md),
        ) {
            InkSceneHeader(
                title = "提醒可靠性",
                scene = InkScene.SETTINGS,
                subtitle = "先看总状态，只处理未开启项",
                action = { InkSecondaryButton(text = "返回", onClick = onBack) },
            )

            InkGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reliability_summary"),
                emphasized = true,
            ) {
                Column(modifier = Modifier.padding(InkTheme.spacing.lg)) {
                    Text(
                        state.reliability.summary,
                        color = if (state.reliability.disabledCount > 0) {
                            InkTheme.colors.cinnabar
                        } else {
                            InkTheme.colors.moss
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(InkTheme.spacing.sm))
                    Text(
                        "通知总开关开启后，四个必要渠道会自动建立；已显示“已开启”的项目无需逐个进入。Android 特殊权限仍需由你逐项确认，返回后本页会自动刷新。",
                        color = InkTheme.colors.inkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.reliability.items.forEach { item ->
                ReliabilityCard(
                    item = item,
                    onAction = {
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
            }
            HabitPreferences(
                themeMode = state.preferences.themeMode,
                taskSortOrder = state.preferences.taskSortOrder,
                onThemeModeSelected = viewModel::setThemeMode,
                onTaskSortOrderSelected = viewModel::setTaskSortOrder,
            )
            Spacer(Modifier.height(InkTheme.spacing.md))
        }
    }
}

@Composable
private fun HabitPreferences(
    themeMode: ThemeMode,
    taskSortOrder: TaskSortOrder,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onTaskSortOrderSelected: (TaskSortOrder) -> Unit,
) {
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("habit_preferences"),
    ) {
        Column(
            modifier = Modifier.padding(InkTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
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
        }
    }
}

@Composable
private fun ReliabilityCard(item: ReliabilityItem, onAction: () -> Unit) {
    InkGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(InkTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.title, color = InkTheme.colors.ink, style = MaterialTheme.typography.titleMedium)
                InkStatusSeal(
                    text = item.statusText(),
                    scene = when (item.state) {
                        ReliabilityState.ENABLED -> InkScene.SETTINGS
                        ReliabilityState.DISABLED -> InkScene.REMINDER
                        ReliabilityState.SYSTEM_LIMITED -> InkScene.CALENDAR
                    },
                )
            }
            Text(item.description, color = InkTheme.colors.inkSoft, style = MaterialTheme.typography.bodyMedium)
            item.guidance?.let {
                Text(it, color = InkTheme.colors.indigo, style = MaterialTheme.typography.bodySmall)
            }
            if (item.action != ReliabilityAction.NONE) {
                InkPrimaryButton(
                    text = if (item.state == ReliabilityState.SYSTEM_LIMITED) "去确认" else "去开启",
                    onClick = onAction,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("reliability_action_${item.id}"),
                )
            }
        }
    }
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
