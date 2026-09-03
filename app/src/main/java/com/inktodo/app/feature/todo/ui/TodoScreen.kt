package com.inktodo.app.feature.todo.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.components.InkSecondaryButton

@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onAddTask: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<TodoTask?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect(snackbar::showSnackbar) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_task")
                    .semantics { contentDescription = "新建待办" },
                containerColor = InkTheme.colors.moss,
                contentColor = InkTheme.colors.paper,
            ) { Text("+") }
        },
    ) { contentPadding ->
        TaskListContent(
            title = "待办",
            scene = InkScene.TODO,
            tasks = state.tasks,
            isLoading = state.isLoading,
            emptyText = "山静无事，点击 + 写下第一件待办。",
            primaryActionLabel = "完成",
            onPrimaryAction = viewModel::complete,
            onOpenTask = onOpenTask,
            onDeleteTask = { pendingDelete = it },
            modifier = Modifier.padding(contentPadding),
            headerAction = {
                InkSecondaryButton(text = "提醒可靠性", onClick = onOpenSettings)
            },
        )
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除待办？") },
            text = { Text("“${task.title}”将被永久删除。") },
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
