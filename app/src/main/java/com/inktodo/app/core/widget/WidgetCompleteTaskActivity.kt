package com.inktodo.app.core.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSecondaryButton
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import com.inktodo.app.feature.todo.domain.userMessage
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCompleteTaskActivity : ComponentActivity() {
    private var payload by mutableStateOf<Payload?>(null)
    private var confirming by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        payload = intent.toPayload()
        if (payload == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.42f }
        enableEdgeToEdge()
        val container = (application as InkTodoApplication).appContainer
        setContent {
            val preferences by container.preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = preferences.themeMode.resolveDarkTheme(
                androidx.compose.foundation.isSystemInDarkTheme(),
            )
            InkTheme(darkTheme = darkTheme) {
                WidgetCompleteConfirmation(
                    title = payload?.title.orEmpty(),
                    confirming = confirming,
                    errorMessage = errorMessage,
                    onCancel = ::finish,
                    onConfirm = ::completeTask,
                )
            }
        }
    }

    private fun completeTask() {
        val task = payload ?: return
        if (confirming) return
        confirming = true
        errorMessage = null
        val container = (application as InkTodoApplication).appContainer
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                container.taskCommandService.setCompleted(task.taskId, true, Instant.now())
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    result.issues.takeIf(Set<*>::isNotEmpty)?.let { issues ->
                        Toast.makeText(
                            this@WidgetCompleteTaskActivity,
                            issues.joinToString("\n") { it.userMessage() },
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    finishAndRemoveTask()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    confirming = false
                    errorMessage = error.message ?: "暂时无法完成任务，请打开墨待重试。"
                }
            }
        }
    }

    private fun Intent.toPayload(): Payload? {
        val taskId = getLongExtra(EXTRA_TASK_ID, -1L)
        val title = getStringExtra(EXTRA_TASK_TITLE)?.trim().orEmpty()
        return if (taskId > 0 && title.isNotEmpty()) Payload(taskId, title) else null
    }

    companion object {
        private const val EXTRA_TASK_ID = "widgetCompleteTaskId"
        private const val EXTRA_TASK_TITLE = "widgetCompleteTaskTitle"

        fun createIntent(context: Context, taskId: Long, title: String): Intent =
            Intent(context, WidgetCompleteTaskActivity::class.java)
                .setAction("com.inktodo.app.action.WIDGET_CONFIRM_COMPLETE")
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private data class Payload(val taskId: Long, val title: String)
}

@androidx.compose.runtime.Composable
private fun WidgetCompleteConfirmation(
    title: String,
    confirming: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            emphasized = true,
        ) {
            Column(
                modifier = Modifier.padding(InkTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.md),
            ) {
                Text(
                    text = "确认完成任务？",
                    color = InkTheme.colors.cinnabar,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = title,
                    color = InkTheme.colors.ink,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "确认后将进入“已完成”，相关提醒会同步取消。",
                    color = InkTheme.colors.inkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        color = InkTheme.colors.cinnabar,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
                ) {
                    InkSecondaryButton(
                        text = "取消",
                        onClick = onCancel,
                        enabled = !confirming,
                        modifier = Modifier.weight(1f),
                    )
                    InkPrimaryButton(
                        text = if (confirming) "正在完成…" else "确认完成",
                        onClick = onConfirm,
                        enabled = !confirming,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
