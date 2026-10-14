package com.inktodo.app.feature.todo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.theme.InkTheme

@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = InkTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        InkCompactButton(
            text = "返回",
            onClick = onBack,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(Modifier.weight(0.28f))
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = InkTheme.colors.moss,
            )
            state.task == null -> Text(
                text = "任务不存在或已删除。",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = InkTheme.colors.mist,
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> InkGlassCard(
                modifier = Modifier.fillMaxWidth(),
                emphasized = true,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = requireNotNull(state.task).title,
                        color = InkTheme.colors.ink,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = requireNotNull(state.task).note?.takeIf(String::isNotBlank) ?: "无备注",
                        color = InkTheme.colors.inkSoft,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Spacer(Modifier.weight(0.72f))
    }
}
