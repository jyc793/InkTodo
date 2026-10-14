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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.inktodo.app.R
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormalReminderActivity : ComponentActivity() {
    private var payload by mutableStateOf<FormalReminderPayload?>(null)
    private var confirming by mutableStateOf(false)
    private var confirmCountdownSeconds by mutableStateOf<Int?>(null)
    private var confirmJob: Job? = null

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
                        confirmCountdownSeconds = confirmCountdownSeconds,
                        darkTheme = darkTheme,
                        onConfirm = { confirm(reminder.taskId, reminder.preserveSnapshot) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toPayload()?.let {
            confirmJob?.cancel()
            confirmJob = null
            confirming = false
            confirmCountdownSeconds = null
            payload = it
        }
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

    private fun confirm(taskId: Long, preserveSnapshot: Boolean) {
        if (confirming || confirmCountdownSeconds != null) return
        confirmJob = lifecycleScope.launch {
            for (seconds in CONFIRM_COUNTDOWN_SECONDS downTo 1) {
                confirmCountdownSeconds = seconds
                delay(1_000L)
            }
            confirmCountdownSeconds = null
            confirming = true
            completeConfirmation(taskId, preserveSnapshot)
        }
    }

    private suspend fun completeConfirmation(taskId: Long, preserveSnapshot: Boolean) {
        val container = (application as InkTodoApplication).appContainer
        withContext(Dispatchers.IO) {
            runCatching {
                container.reminderHealthStore.record(
                    taskId,
                    ReminderHealthState.ACKNOWLEDGED,
                    detail = "用户已确认正式提醒",
                )
            }
            if (!preserveSnapshot) runCatching { container.reminderSnapshotStore.remove(taskId) }
            container.notificationController.cancelFormal(taskId)
        }
        FormalReminderOverlayService.dismiss(this@FormalReminderActivity, taskId)
        finishAndRemoveTask()
    }

    private fun Intent.toPayload(): FormalReminderPayload? {
        val taskId = getLongExtra(EXTRA_TASK_ID, -1L)
        val title = getStringExtra(EXTRA_TASK_TITLE)?.trim().orEmpty()
        val note = getStringExtra(EXTRA_TASK_NOTE)?.trim()?.takeIf(String::isNotEmpty)
        val preserveSnapshot = getBooleanExtra(EXTRA_PRESERVE_SNAPSHOT, false)
        return if (taskId > 0 && title.isNotEmpty())
            FormalReminderPayload(taskId, title, note, preserveSnapshot) else null
    }

    companion object {
        const val EXTRA_TASK_ID = "formalReminderTaskId"
        const val EXTRA_TASK_TITLE = "formalReminderTaskTitle"
        const val EXTRA_TASK_NOTE = "formalReminderTaskNote"
        const val EXTRA_PRESERVE_SNAPSHOT = "preserveReminderSnapshot"
        internal const val CONFIRM_COUNTDOWN_SECONDS = 3

        fun createIntent(context: Context, taskId: Long, title: String, note: String? = null,
            preserveSnapshot: Boolean = false): Intent =
            Intent(context, FormalReminderActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_TITLE, title)
                .putExtra(EXTRA_TASK_NOTE, note)
                .putExtra(EXTRA_PRESERVE_SNAPSHOT, preserveSnapshot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private data class FormalReminderPayload(
    val taskId: Long,
    val title: String,
    val note: String?,
    val preserveSnapshot: Boolean,
)

@androidx.compose.runtime.Composable
private fun FormalReminderScreen(
    title: String,
    note: String?,
    confirming: Boolean,
    confirmCountdownSeconds: Int?,
    darkTheme: Boolean,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10242D)),
    ) {
        Image(
            painter = painterResource(R.drawable.widget_mountain_cool_v1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (darkTheme) {
                            listOf(
                                Color(0x990B2029),
                                Color(0xB30A1C25),
                                Color(0xE608161E),
                            )
                        } else {
                            listOf(
                                Color(0x1AEEF6F8),
                                Color(0x334E7482),
                                Color(0xB3122933),
                            )
                        },
                    ),
                ),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(InkTheme.spacing.screenHorizontal),
        ) {
            val isTablet = maxWidth >= 600.dp
            val cardAlignment = if (isTablet || maxWidth > maxHeight) Alignment.Center else Alignment.BottomCenter
            val cardMaxWidth = if (maxWidth >= 840.dp) 680.dp else 560.dp
            val noteMaxLines = if (isTablet) 8 else 5
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = cardAlignment,
            ) {
                InkGlassCard(
                    modifier = Modifier
                        .widthIn(max = cardMaxWidth)
                        .fillMaxWidth(),
                    emphasized = false,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (isTablet) InkTheme.spacing.lg else InkTheme.spacing.md,
                            vertical = InkTheme.spacing.md,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
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
                                maxLines = noteMaxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        InkPrimaryButton(
                            text = formalConfirmationButtonText(
                                countdownSeconds = confirmCountdownSeconds,
                                confirming = confirming,
                            ),
                            enabled = !confirming && confirmCountdownSeconds == null,
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

internal fun formalConfirmationButtonText(
    countdownSeconds: Int?,
    confirming: Boolean,
): String = when {
    countdownSeconds != null -> "$countdownSeconds 秒后确认"
    confirming -> "正在确认…"
    else -> "确认"
}
