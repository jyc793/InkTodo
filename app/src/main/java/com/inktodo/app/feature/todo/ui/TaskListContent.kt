package com.inktodo.app.feature.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.accent

@Composable
internal fun TaskListContent(
    title: String,
    scene: InkScene,
    tasks: List<TodoTask>,
    isLoading: Boolean,
    emptyText: String,
    primaryActionLabel: String,
    onPrimaryAction: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onDeleteTask: (TodoTask) -> Unit,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
) {
    val spacing = InkTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        InkSceneHeader(
            title = title,
            scene = scene,
            subtitle = if (scene == InkScene.COMPLETED) "墨迹归雾，仍可恢复" else "今日之事，清晰有序",
            action = headerAction,
        )

        when {
            isLoading -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = InkTheme.colors.moss,
                )
                Spacer(Modifier.weight(1f))
            }
            tasks.isEmpty() -> {
                Spacer(Modifier.weight(1f))
                InkGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    muted = scene == InkScene.COMPLETED,
                ) {
                    Text(
                        text = emptyText,
                        modifier = Modifier.padding(spacing.lg),
                        color = InkTheme.colors.inkSoft,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(tasks, key = TodoTask::id) { task ->
                    TaskItem(
                        task = task,
                        scene = scene,
                        primaryActionLabel = primaryActionLabel,
                        onPrimaryAction = { onPrimaryAction(task.id) },
                        onOpen = { onOpenTask(task.id) },
                        onEdit = { onEditTask(task.id) },
                        onDelete = { onDeleteTask(task) },
                    )
                }
                item { Spacer(Modifier.height(spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: TodoTask,
    scene: InkScene,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = InkTheme.spacing
    val completed = scene == InkScene.COMPLETED
    val colors = InkTheme.colors
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    InkGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        muted = scene == InkScene.COMPLETED,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(task.id) {
                        detectTapGestures(
                            onTap = { onOpen() },
                            onDoubleTap = { onEdit() },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 32.dp)
                        .background(
                            if (completed) colors.mist.copy(alpha = 0.48f) else scene.accent(colors),
                            MaterialTheme.shapes.small,
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        color = if (completed) colors.inkSoft.copy(alpha = 0.78f) else colors.ink,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "${task.dateLabel()} · ${task.reminderLabel()}",
                        color = colors.mist.copy(alpha = if (completed) 0.70f else 1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    task.note?.let {
                        Text(
                            it,
                            color = colors.mist.copy(alpha = if (completed) 0.62f else 1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            InkCompactButton(
                text = primaryActionLabel,
                modifier = Modifier.semantics {
                    contentDescription = "$primaryActionLabel 任务：${task.title}"
                },
                onClick = onPrimaryAction,
            )
            Box(
                modifier = Modifier
                    .size(spacing.touchTarget)
                    .semantics { contentDescription = "更多操作：${task.title}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⋮",
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .clickable { menuExpanded = true }
                        .padding(top = 8.dp),
                    color = colors.inkSoft,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("删除", color = colors.cinnabar) },
                        modifier = Modifier.testTag("delete_task_${task.id}"),
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
