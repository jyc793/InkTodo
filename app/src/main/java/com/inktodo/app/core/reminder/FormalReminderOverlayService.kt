package com.inktodo.app.core.reminder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
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
    private var ringtone: Ringtone? = null
    private var activeTaskId: Long = -1L
    private var stopAfterTone = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            if (activeTaskId <= 0 || taskId <= 0 || taskId == activeTaskId) stopCompletely()
            return START_NOT_STICKY
        }
        val snapshot = intent?.toSnapshot() ?: run {
            stopCompletely()
            return START_NOT_STICKY
        }
        activeTaskId = snapshot.taskId
        val container = (application as InkTodoApplication).appContainer
        container.notificationController.createChannels()
        val notification = container.notificationController.buildFormalNotification(snapshot)
        val notificationId = container.notificationController.formalNotificationId(snapshot.taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        playSystemNotificationTone()

        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Settings.canDrawOverlays(this) && !keyguard.isKeyguardLocked) {
            stopAfterTone = false
            serviceScope.launch {
                val themeMode = runCatching {
                    container.preferencesRepository.preferences.first().themeMode
                }.getOrDefault(ThemeMode.SYSTEM)
                val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                val darkTheme = themeMode.resolveDarkTheme(systemDark)
                withContext(Dispatchers.Main) {
                    if (activeTaskId == snapshot.taskId) showOverlay(snapshot, darkTheme)
                }
            }
        } else {
            stopAfterTone = true
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        stopTone()
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
                setImageResource(R.drawable.ink_mountain_master_v1)
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
            setPadding(22.dp(), 20.dp(), 22.dp(), 20.dp())
            background = roundedDrawable(
                radiusPx = 24f * density,
                color = palette.card,
                strokeWidth = 1.dp(),
                strokeColor = palette.cardEdge,
            )
        }
        content.addView(textView(snapshot.title, 28f, palette.ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, if (snapshot.note == null) 20.dp() else 0)
        })
        snapshot.note?.let { note ->
            content.addView(textView(note, 16f, palette.inkSoft, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 10.dp(), 0, 20.dp())
            })
        }
        content.addView(
            Button(this).apply {
                text = "确认"
                textSize = 19f
                setTextColor(Color.WHITE)
                isAllCaps = false
                minHeight = 56.dp()
                background = roundedDrawable(
                    radiusPx = 18f * density,
                    color = palette.button,
                )
                setOnClickListener { acknowledge(snapshot.taskId) }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp()),
        )
        panel.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = 18.dp()
                rightMargin = 18.dp()
                bottomMargin = 18.dp()
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

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                430.dp(),
                Gravity.BOTTOM,
            ).apply {
                leftMargin = 22.dp()
                rightMargin = 22.dp()
                bottomMargin = 42.dp()
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
            stopAfterTone = true
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
        stopTone()
        serviceScope.launch {
            runCatching {
                container.reminderHealthStore.record(
                    taskId,
                    ReminderHealthState.ACKNOWLEDGED,
                    detail = "用户已确认正式提醒",
                )
            }
            runCatching { container.reminderSnapshotStore.remove(taskId) }
            container.notificationController.cancelFormal(taskId)
            withContext(Dispatchers.Main) { stopCompletely() }
        }
    }

    private fun playSystemNotificationTone() {
        stopTone()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = false
            play()
        }
        mainHandler.postDelayed({
            stopTone()
            if (stopAfterTone) detachAndStop()
        }, MAX_TONE_DURATION_MS)
    }

    private fun stopTone() {
        runCatching { ringtone?.stop() }
        ringtone = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun removeOverlay() {
        fogAnimators.forEach(Animator::cancel)
        fogAnimators.clear()
        overlayView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        overlayView = null
    }

    private fun detachAndStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    private fun stopCompletely() {
        removeOverlay()
        stopTone()
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
        return if (taskId > 0 && title.isNotEmpty() && startAt > 0) {
            ScheduledTaskSnapshot(
                taskId = taskId,
                title = title,
                kind = ReminderSnapshotKind.AT_TIME,
                startAt = Instant.ofEpochMilli(startAt),
                note = note,
            )
        } else {
            null
        }
    }

    companion object {
        private const val ACTION_SHOW = "com.inktodo.app.action.SHOW_FORMAL_OVERLAY"
        private const val ACTION_DISMISS = "com.inktodo.app.action.DISMISS_FORMAL_OVERLAY"
        private const val EXTRA_TASK_ID = "taskId"
        private const val EXTRA_TASK_TITLE = "taskTitle"
        private const val EXTRA_TASK_NOTE = "taskNote"
        private const val EXTRA_START_AT = "startAt"
        private const val MAX_TONE_DURATION_MS = 8_000L

        fun show(context: Context, snapshot: ScheduledTaskSnapshot): Boolean = runCatching {
            val intent = Intent(context, FormalReminderOverlayService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_TASK_ID, snapshot.taskId)
                .putExtra(EXTRA_TASK_TITLE, snapshot.title)
                .putExtra(EXTRA_TASK_NOTE, snapshot.note)
                .putExtra(EXTRA_START_AT, snapshot.startAt.toEpochMilli())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess

        fun dismiss(context: Context, taskId: Long) {
            context.startService(
                Intent(context, FormalReminderOverlayService::class.java)
                    .setAction(ACTION_DISMISS)
                    .putExtra(EXTRA_TASK_ID, taskId),
            )
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
        scrim = Color.argb(184, 3, 10, 8),
        edge = Color.rgb(91, 133, 119),
        veil = intArrayOf(
            Color.argb(70, 9, 19, 16),
            Color.argb(136, 16, 30, 26),
            Color.argb(218, 12, 23, 20),
        ),
        cinnabar = Color.rgb(235, 130, 116),
        titleShadow = Color.argb(190, 8, 17, 14),
        card = Color.argb(218, 18, 31, 27),
        cardEdge = Color.argb(170, 112, 163, 145),
        ink = Color.rgb(242, 246, 241),
        inkSoft = Color.rgb(199, 213, 206),
        button = Color.rgb(67, 112, 95),
        highlightEdge = Color.argb(205, 124, 178, 159),
    )
} else {
    FormalOverlayPalette(
        scrim = Color.argb(126, 7, 16, 13),
        edge = Color.rgb(111, 151, 138),
        veil = intArrayOf(
            Color.argb(34, 244, 241, 232),
            Color.argb(76, 232, 239, 234),
            Color.argb(190, 238, 241, 234),
        ),
        cinnabar = Color.rgb(163, 60, 50),
        titleShadow = Color.argb(170, 244, 241, 232),
        card = Color.argb(188, 241, 244, 237),
        cardEdge = Color.argb(105, 111, 151, 138),
        ink = Color.rgb(23, 32, 29),
        inkSoft = Color.rgb(45, 58, 53),
        button = Color.rgb(52, 88, 75),
        highlightEdge = Color.argb(190, 150, 190, 177),
    )
}
