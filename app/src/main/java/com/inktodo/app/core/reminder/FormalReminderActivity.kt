package com.inktodo.app.core.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.components.InkBackdrop
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormalReminderActivity : ComponentActivity() {
    private var payload by mutableStateOf<FormalReminderPayload?>(null)
    private var confirming by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReminderWindow()
        payload = intent.toPayload()
        if (payload == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        val container = (application as InkTodoApplication).appContainer
        setContent {
            val preferences by container.preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = preferences.themeMode.resolveDarkTheme(
                androidx.compose.foundation.isSystemInDarkTheme(),
            )
            InkTheme(darkTheme = darkTheme) {
                BackHandler(enabled = true) { /* Confirmation is the only dismissal path. */ }
                payload?.let { reminder ->
                    FormalReminderScreen(
                        title = reminder.title,
                        note = reminder.note,
                        confirming = confirming,
                        onConfirm = { confirm(reminder.taskId) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toPayload()?.let { payload = it }
    }

    private fun configureReminderWindow() {
        setFinishOnTouchOutside(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun confirm(taskId: Long) {
        if (confirming) return
        confirming = true
        val container = (application as InkTodoApplication).appContainer
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                container.reminderHealthStore.record(
                    taskId,
                    ReminderHealthState.ACKNOWLEDGED,
                    detail = "用户已确认正式提醒",
                )
            }
            runCatching { container.reminderSnapshotStore.remove(taskId) }
            container.notificationController.cancelFormal(taskId)
            withContext(Dispatchers.Main) {
                FormalReminderOverlayService.dismiss(this@FormalReminderActivity, taskId)
                finishAndRemoveTask()
            }
        }
    }

    private fun Intent.toPayload(): FormalReminderPayload? {
        val taskId = getLongExtra(EXTRA_TASK_ID, -1L)
        val title = getStringExtra(EXTRA_TASK_TITLE)?.trim().orEmpty()
        val note = getStringExtra(EXTRA_TASK_NOTE)?.trim()?.takeIf(String::isNotEmpty)
        return if (taskId > 0 && title.isNotEmpty()) FormalReminderPayload(taskId, title, note) else null
    }

    companion object {
        const val EXTRA_TASK_ID = "formalReminderTaskId"
        const val EXTRA_TASK_TITLE = "formalReminderTaskTitle"
        const val EXTRA_TASK_NOTE = "formalReminderTaskNote"

        fun createIntent(context: Context, taskId: Long, title: String, note: String? = null): Intent =
            Intent(context, FormalReminderActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_TITLE, title)
                .putExtra(EXTRA_TASK_NOTE, note)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private data class FormalReminderPayload(val taskId: Long, val title: String, val note: String?)

@androidx.compose.runtime.Composable
private fun FormalReminderScreen(
    title: String,
    note: String?,
    confirming: Boolean,
    onConfirm: () -> Unit,
) {
    InkBackdrop(scene = InkScene.REMINDER) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(InkTheme.spacing.lg),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                InkGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = false,
                ) {
                    Column(
                        modifier = Modifier.padding(InkTheme.spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.lg),
                    ) {
                        Text(
                            text = "待办提醒",
                            color = InkTheme.colors.cinnabar,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = title,
                            color = InkTheme.colors.ink,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        note?.let {
                            Text(
                                text = it,
                                color = InkTheme.colors.inkSoft,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        InkPrimaryButton(
                            text = if (confirming) "正在确认…" else "确认",
                            enabled = !confirming,
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
