package com.inktodo.app.feature.completed.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.feature.todo.ui.TaskListContent
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest

@Composable
fun CompletedScreen(
    viewModel: CompletedViewModel,
    onOpenTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onSystemAlarmRequest: (SystemAlarmRequest) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<TodoTask?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect(snackbar::showSnackbar) }
    LaunchedEffect(viewModel) { viewModel.systemAlarmRequests.collect(onSystemAlarmRequest) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        TaskListContent(
            title = "已完成",
            scene = InkScene.COMPLETED,
            tasks = state.tasks,
            isLoading = state.isLoading,
            emptyText = "完成的任务会像墨迹入雾，沉静地留在这里。",
            primaryActionLabel = "恢复",
            onPrimaryAction = viewModel::restore,
            onOpenTask = onOpenTask,
            onEditTask = onEditTask,
            onDeleteTask = { pendingDelete = it },
            modifier = Modifier.padding(contentPadding),
        )
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("永久删除？") },
            text = { Text("“${task.title}”删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    modifier = Modifier
                        .testTag("confirm_delete_task")
                        .semantics { contentDescription = "确认删除任务" },
                    onClick = {
                        viewModel.delete(task.id)
                        pendingDelete = null
                    },
                ) { Text("删除", color = InkTheme.colors.cinnabar) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}
