package com.inktodo.app.core.reminder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.inktodo.app.R
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.alarm.ReminderSnapshotKind
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import com.inktodo.app.core.model.ThemeMode
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FormalReminderOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val fogAnimators = mutableListOf<Animator>()
    private var activeTaskId: Long = -1L
    private var activeKind: ReminderSnapshotKind = ReminderSnapshotKind.AT_TIME
    private var activeSnapshot: ScheduledTaskSnapshot? = null
    private var confirmationCountdown: Runnable? = null
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                activeSnapshot?.let(::showOverlayWhenUnlocked)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(unlockReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val snapshot = intent?.toSnapshot() ?: run {
            stopCompletely()
            return START_NOT_STICKY
        }
        val previousTaskId = activeTaskId
        val container = (application as InkTodoApplication).appContainer
        if (previousTaskId > 0L && previousTaskId != snapshot.taskId) {
            container.notificationController.cancelFormal(previousTaskId)
        }
        removeOverlay()
        activeTaskId = snapshot.taskId
        displayedTaskId = snapshot.taskId
        activeKind = snapshot.kind
        activeSnapshot = snapshot
        container.notificationController.createChannels()
        val notification = container.notificationController.buildFormalNotification(snapshot)
        val notificationId = container.notificationController.formalNotificationId(snapshot.taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        showOverlayWhenUnlocked(snapshot)
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (displayedTaskId == activeTaskId) displayedTaskId = -1L
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlockReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showOverlay(snapshot: ScheduledTaskSnapshot, darkTheme: Boolean) {
        removeOverlay()
        val palette = formalOverlayPalette(darkTheme)
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.scrim)
            isClickable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }
        val panel = FrameLayout(this).apply {
            elevation = 18f * density
            background = roundedDrawable(
                radiusPx = 32f * density,
                color = Color.TRANSPARENT,
                strokeWidth = 1.dp(),
                strokeColor = palette.edge,
            )
            clipToOutline = true
        }
        panel.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.widget_mountain_cool_v1)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val upperFog = fogLayer(alpha = if (darkTheme) 0.22f else 0.30f)
        panel.addView(
            upperFog,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                138.dp(),
                Gravity.TOP,
            ).apply { topMargin = 72.dp() },
        )
        val lowerFog = fogLayer(alpha = if (darkTheme) 0.18f else 0.25f)
        panel.addView(
            lowerFog,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                150.dp(),
                Gravity.CENTER_VERTICAL,
            ).apply { topMargin = 38.dp() },
        )
        panel.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    palette.veil,
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        panel.addView(
            textView("待办提醒", 22f, palette.cinnabar, Typeface.BOLD).apply {
                setShadowLayer(6f, 0f, 1f, palette.titleShadow)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = 26.dp()
                topMargin = 24.dp()
            },
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
            background = roundedDrawable(
                radiusPx = 24f * density,
                color = palette.card,
                strokeWidth = 1.dp(),
                strokeColor = palette.cardEdge,
            )
        }
        content.addView(textView(snapshot.title, 22f, palette.ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, if (snapshot.note == null) 14.dp() else 0)
        })
        snapshot.note?.let { note ->
            content.addView(textView(note, 16f, palette.inkSoft, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 8.dp(), 0, 14.dp())
            })
        }
        content.addView(
            Button(this).apply {
                text = "确认"
                textSize = 19f
                setTextColor(Color.WHITE)
                isAllCaps = false
                minHeight = 52.dp()
                background = roundedDrawable(
                    radiusPx = 18f * density,
                    color = palette.button,
                )
                setOnClickListener { startConfirmationCountdown(snapshot.taskId, this) }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()),
        )
        panel.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = 14.dp()
                rightMargin = 14.dp()
                bottomMargin = 14.dp()
            },
        )
        panel.addView(
            View(this).apply {
                background = roundedDrawable(
                    radiusPx = 32f * density,
                    color = Color.TRANSPARENT,
                    strokeWidth = 1.dp(),
                    strokeColor = palette.highlightEdge,
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelWidth = if (isTablet) {
            (if (isLandscape) 720 else 640).dp()
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
        val panelHeight = when {
            isTablet && isLandscape -> 380.dp()
            isTablet -> 430.dp()
            else -> 390.dp()
        }
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidth,
                panelHeight,
                if (isTablet) Gravity.CENTER else Gravity.BOTTOM,
            ).apply {
                if (!isTablet) {
                    leftMargin = 14.dp()
                    rightMargin = 14.dp()
                    bottomMargin = 24.dp()
                }
            },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.FILL
        }
        runCatching {
            windowManager.addView(root, params)
            root.requestFocus()
            overlayView = root
            startFogMotion(upperFog, lowerFog, density)
        }.onFailure {
            removeOverlay()
        }
    }

    private fun showOverlayWhenUnlocked(snapshot: ScheduledTaskSnapshot) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!Settings.canDrawOverlays(this) || keyguard.isKeyguardLocked) return
        val container = (application as InkTodoApplication).appContainer
        serviceScope.launch {
            val themeMode = runCatching {
                container.preferencesRepository.preferences.first().themeMode
            }.getOrDefault(ThemeMode.SYSTEM)
            val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            val darkTheme = themeMode.resolveDarkTheme(systemDark)
            withContext(Dispatchers.Main) {
                if (activeTaskId == snapshot.taskId && overlayView == null) {
                    showOverlay(snapshot, darkTheme)
                }
            }
        }
    }

    private fun fogLayer(alpha: Float) = ImageView(this).apply {
        setImageResource(R.drawable.ink_cloud_ribbon_v1)
        scaleType = ImageView.ScaleType.FIT_XY
        this.alpha = alpha
        contentDescription = null
    }

    private fun startFogMotion(upperFog: View, lowerFog: View, density: Float) {
        fogAnimators += loopingTranslation(upperFog, -14f * density, 14f * density, 14_000L)
        fogAnimators += loopingTranslation(lowerFog, 16f * density, -16f * density, 18_000L)
    }

    private fun loopingTranslation(view: View, from: Float, to: Float, durationMs: Long): Animator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, from, to).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }

    private fun roundedDrawable(
        radiusPx: Float,
        color: Int,
        strokeWidth: Int = 0,
        strokeColor: Int = Color.TRANSPARENT,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

    private fun textView(text: String, sizeSp: Float, color: Int, style: Int) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        typeface = Typeface.create("sans", style)
    }

    private fun acknowledge(taskId: Long) {
        val container = (application as InkTodoApplication).appContainer
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.launch {
            runCatching {
                container.reminderHealthStore.record(
                    taskId,
                    ReminderHealthState.ACKNOWLEDGED,
                    detail = "用户已确认正式提醒",
                )
            }
            if (activeKind != ReminderSnapshotKind.TIME_RANGE) {
                runCatching { container.reminderSnapshotStore.remove(taskId) }
            }
            container.notificationController.cancelFormal(taskId)
            withContext(Dispatchers.Main) { stopCompletely() }
        }
    }

    private fun startConfirmationCountdown(taskId: Long, button: Button) {
        if (confirmationCountdown != null || taskId != activeTaskId) return
        button.isEnabled = false
        var remainingSeconds = FormalReminderActivity.CONFIRM_COUNTDOWN_SECONDS
        button.text = formalConfirmationButtonText(remainingSeconds, confirming = false)
        val countdown = object : Runnable {
            override fun run() {
                if (taskId != activeTaskId) {
                    cancelConfirmationCountdown()
                    return
                }
                remainingSeconds -= 1
                if (remainingSeconds <= 0) {
                    confirmationCountdown = null
                    acknowledge(taskId)
                } else {
                    button.text = formalConfirmationButtonText(remainingSeconds, confirming = false)
                    mainHandler.postDelayed(this, 1_000L)
                }
            }
        }
        confirmationCountdown = countdown
        mainHandler.postDelayed(countdown, 1_000L)
    }

    private fun cancelConfirmationCountdown() {
        confirmationCountdown?.let(mainHandler::removeCallbacks)
        confirmationCountdown = null
    }

    private fun removeOverlay() {
        cancelConfirmationCountdown()
        fogAnimators.forEach(Animator::cancel)
        fogAnimators.clear()
        overlayView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        overlayView = null
    }

    private fun stopCompletely() {
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun Intent.toSnapshot(): ScheduledTaskSnapshot? {
        val taskId = getLongExtra(EXTRA_TASK_ID, -1L)
        val title = getStringExtra(EXTRA_TASK_TITLE)?.trim().orEmpty()
        val note = getStringExtra(EXTRA_TASK_NOTE)?.trim()?.takeIf(String::isNotEmpty)
        val startAt = getLongExtra(EXTRA_START_AT, -1L)
        val kind = getStringExtra(EXTRA_KIND)?.let {
            runCatching { ReminderSnapshotKind.valueOf(it) }.getOrNull()
        } ?: ReminderSnapshotKind.AT_TIME
        return if (taskId > 0 && title.isNotEmpty() && startAt > 0) {
            ScheduledTaskSnapshot(
                taskId = taskId,
                title = title,
                kind = kind,
                startAt = Instant.ofEpochMilli(startAt),
                note = note,
            )
        } else {
            null
        }
    }

    companion object {
        private const val ACTION_SHOW = "com.inktodo.app.action.SHOW_FORMAL_OVERLAY"
        private const val EXTRA_TASK_ID = "taskId"
        private const val EXTRA_TASK_TITLE = "taskTitle"
        private const val EXTRA_TASK_NOTE = "taskNote"
        private const val EXTRA_START_AT = "startAt"
        private const val EXTRA_KIND = "reminderKind"
        @Volatile private var displayedTaskId: Long = -1L

        fun show(context: Context, snapshot: ScheduledTaskSnapshot): Boolean = runCatching {
            val intent = Intent(context, FormalReminderOverlayService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_TASK_ID, snapshot.taskId)
                .putExtra(EXTRA_TASK_TITLE, snapshot.title)
                .putExtra(EXTRA_TASK_NOTE, snapshot.note)
                .putExtra(EXTRA_START_AT, snapshot.startAt.toEpochMilli())
                .putExtra(EXTRA_KIND, snapshot.kind.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess

        fun dismiss(context: Context, taskId: Long) {
            if (displayedTaskId == taskId) {
                context.stopService(Intent(context, FormalReminderOverlayService::class.java))
            }
        }
    }
}

internal data class FormalOverlayPalette(
    val scrim: Int,
    val edge: Int,
    val veil: IntArray,
    val cinnabar: Int,
    val titleShadow: Int,
    val card: Int,
    val cardEdge: Int,
    val ink: Int,
    val inkSoft: Int,
    val button: Int,
    val highlightEdge: Int,
)

internal fun formalOverlayPalette(darkTheme: Boolean): FormalOverlayPalette = if (darkTheme) {
    FormalOverlayPalette(
        scrim = Color.argb(190, 4, 14, 20),
        edge = Color.rgb(100, 145, 160),
        veil = intArrayOf(
            Color.argb(62, 12, 30, 39),
            Color.argb(132, 14, 38, 49),
            Color.argb(218, 9, 26, 35),
        ),
        cinnabar = Color.rgb(235, 130, 116),
        titleShadow = Color.argb(190, 5, 20, 28),
        card = Color.argb(224, 15, 38, 48),
        cardEdge = Color.argb(175, 111, 165, 181),
        ink = Color.rgb(240, 247, 249),
        inkSoft = Color.rgb(193, 212, 219),
        button = Color.rgb(54, 105, 119),
        highlightEdge = Color.argb(205, 126, 182, 198),
    )
} else {
    FormalOverlayPalette(
        scrim = Color.argb(126, 8, 25, 33),
        edge = Color.rgb(94, 138, 153),
        veil = intArrayOf(
            Color.argb(26, 237, 246, 248),
            Color.argb(64, 213, 231, 237),
            Color.argb(184, 224, 238, 242),
        ),
        cinnabar = Color.rgb(163, 60, 50),
        titleShadow = Color.argb(170, 232, 244, 247),
        card = Color.argb(205, 228, 240, 244),
        cardEdge = Color.argb(125, 91, 139, 155),
        ink = Color.rgb(20, 38, 47),
        inkSoft = Color.rgb(54, 78, 88),
        button = Color.rgb(49, 95, 111),
        highlightEdge = Color.argb(195, 137, 179, 192),
    )
}
