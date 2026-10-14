package com.inktodo.app.feature.pomodoro.floating

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.MainActivity
import com.inktodo.app.R
import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.resolveDarkTheme
import com.inktodo.app.feature.pomodoro.domain.PomodoroSessionLogic
import com.inktodo.app.feature.pomodoro.ui.FloatingPomodoroVisual
import com.inktodo.app.feature.pomodoro.ui.PomodoroUiState
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PomodoroFloatingTimerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var ballView: FloatingTimerTouchView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var visualView: ComposeView? = null
    private var visualParams: WindowManager.LayoutParams? = null
    private var visualOwner: FloatingComposeWindowOwner? = null
    private var floatingUiState by mutableStateOf(PomodoroUiState())
    private var floatingDarkTheme by mutableStateOf(false)
    private var floatingBallDiameterDp by mutableStateOf(92)
    private var menuView: View? = null
    private var preferencesJob: Job? = null
    private var latestPreferences: UserPreferences? = null
    private var snapAnimator: ValueAnimator? = null
    private var finishRequested = false
    private var foregroundStarted = false

    private val ticker = object : Runnable {
        override fun run() {
            val preferences = latestPreferences
            if (preferences != null && shouldDisplay(preferences)) {
                updateTimer(preferences)
                mainHandler.postDelayed(this, TICK_MILLIS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()
        when (intent?.action) {
            ACTION_SHOW -> requestShow()
            ACTION_HIDE -> requestHide()
            else -> observePreferences()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dismissMenu()
        latestPreferences?.let(::restorePosition)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        snapAnimator?.cancel()
        dismissMenu()
        removeBall()
        serviceScope.cancel()
        foregroundStarted = false
        super.onDestroy()
    }

    private fun requestShow() {
        serviceScope.launch {
            val container = (application as InkTodoApplication).appContainer
            val preferences = withContext(Dispatchers.IO) {
                container.preferencesRepository.preferences.first()
            }
            val session = preferences.pomodoroSession
            if (
                Settings.canDrawOverlays(this@PomodoroFloatingTimerService) &&
                session.runState != PomodoroRunState.IDLE &&
                !preferences.fullFocus.enabled
            ) {
                withContext(Dispatchers.IO) {
                    container.pomodoroCoordinator.setFloatingVisible(true)
                }
                observePreferences()
            } else {
                stopCompletely()
            }
        }
    }

    private fun requestHide() {
        serviceScope.launch {
            val container = (application as InkTodoApplication).appContainer
            withContext(Dispatchers.IO) {
                container.pomodoroCoordinator.setFloatingVisible(false)
            }
            stopCompletely()
        }
    }

    private fun observePreferences() {
        if (preferencesJob != null) return
        val container = (application as InkTodoApplication).appContainer
        preferencesJob = serviceScope.launch {
            container.preferencesRepository.preferences.collectLatest { preferences ->
                latestPreferences = preferences
                if (shouldDisplay(preferences)) {
                    finishRequested = false
                    ensureBall(preferences)
                    updateTimer(preferences)
                    updateForegroundNotification(preferences)
                } else {
                    if (
                        preferences.pomodoroSession.floatingVisible &&
                        (preferences.pomodoroSession.runState == PomodoroRunState.IDLE ||
                            !Settings.canDrawOverlays(this@PomodoroFloatingTimerService) ||
                            preferences.fullFocus.enabled)
                    ) {
                        withContext(Dispatchers.IO) {
                            container.pomodoroCoordinator.setFloatingVisible(false)
                        }
                    }
                    stopCompletely()
                }
            }
        }
    }

    private fun shouldDisplay(preferences: UserPreferences): Boolean =
        preferences.pomodoroSession.floatingVisible &&
            preferences.pomodoroSession.runState != PomodoroRunState.IDLE &&
            !preferences.fullFocus.enabled &&
            Settings.canDrawOverlays(this)

    private fun ensureBall(preferences: UserPreferences) {
        if (ballView != null && visualView != null) return
        removeBall()
        val ballSize = floatingBallSizePx()
        val visualSize = floatingVisualSizePx()
        val bounds = floatingBounds(ballSize)
        val ballPosition = bounds.fromFractions(
            preferences.pomodoroFloatingXFraction,
            preferences.pomodoroFloatingYFraction,
        )
        val visualPosition = floatingVisualPosition(ballPosition, ballSize, visualSize)
        val owner = FloatingComposeWindowOwner()
        val visual = ComposeView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                InkTheme(darkTheme = floatingDarkTheme) {
                    FloatingPomodoroVisual(
                        state = floatingUiState,
                        ballDiameter = floatingBallDiameterDp.dp,
                    )
                }
            }
        }
        val visualWindowParams = WindowManager.LayoutParams(
            visualSize,
            visualSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = visualPosition.x
            y = visualPosition.y
            // Android 12+ only forwards touches through a non-touchable overlay below the
            // maximum obscuring opacity. The visual layer must never steal another app's input.
            alpha = VISUAL_WINDOW_ALPHA
        }
        val touchParams = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballPosition.x
            y = ballPosition.y
        }
        val touchView = FloatingTimerTouchView(this).apply {
            onDragStarted = ::beginDrag
            onDragged = ::moveBall
            onDragReleased = ::snapBallToEdge
            onMenuRequested = ::toggleMenu
        }
        floatingBallDiameterDp = floatingBallSizeDp()
        floatingDarkTheme = resolveDarkTheme(preferences)
        runCatching {
            windowManager.addView(visual, visualWindowParams)
            windowManager.addView(touchView, touchParams)
            touchView.syncWindowPosition(touchParams.x, touchParams.y)
            visualOwner = owner
            visualParams = visualWindowParams
            visualView = visual
            ballParams = touchParams
            ballView = touchView
            mainHandler.removeCallbacks(ticker)
            mainHandler.post(ticker)
        }.onFailure {
            runCatching { windowManager.removeViewImmediate(touchView) }
            runCatching { windowManager.removeViewImmediate(visual) }
            visual.disposeComposition()
            owner.destroy()
            ballParams = null
            ballView = null
            visualParams = null
            visualView = null
            visualOwner = null
            serviceScope.launch {
                withContext(Dispatchers.IO) {
                    (application as InkTodoApplication).appContainer.pomodoroCoordinator
                        .setFloatingVisible(false)
                }
                stopCompletely()
            }
        }
    }

    private fun updateTimer(preferences: UserPreferences) {
        val session = preferences.pomodoroSession
        val remaining = PomodoroSessionLogic.remainingMillis(
            session = session,
            preferences = preferences,
            nowEpochMillis = System.currentTimeMillis(),
        )
        if (
            session.runState == PomodoroRunState.RUNNING &&
            remaining <= 0L &&
            !finishRequested
        ) {
            finishRequested = true
            serviceScope.launch {
                withContext(Dispatchers.IO) {
                    (application as InkTodoApplication).appContainer.pomodoroCoordinator
                        .finishIfDue(showNotification = true)
                }
            }
        }
        floatingDarkTheme = resolveDarkTheme(preferences)
        floatingUiState = PomodoroUiState(
            phase = session.phase,
            runState = session.runState,
            focusMinutes = preferences.focusMinutes,
            breakMinutes = preferences.breakMinutes,
            remainingMillis = remaining,
            targetEndEpochMillis = session.targetEndEpochMillis,
            ordinaryImmersive = session.ordinaryImmersive,
            floatingVisible = session.floatingVisible,
            fullFocus = preferences.fullFocus,
        )
        ballView?.setTimerState(
            phase = session.phase,
            runState = session.runState,
            remainingMillis = remaining,
        )
    }

    private fun moveBall(requestedX: Int, requestedY: Int) {
        val params = ballParams ?: return
        val view = ballView ?: return
        val point = floatingBounds(params.width).clamp(requestedX, requestedY)
        params.x = point.x
        params.y = point.y
        view.syncWindowPosition(point.x, point.y)
        syncVisualWindow(point, params.width)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun syncVisualWindow(ballPosition: FloatingTimerPoint, ballSize: Int) {
        val params = visualParams ?: return
        val view = visualView ?: return
        val position = floatingVisualPosition(ballPosition, ballSize, params.width)
        params.x = position.x
        params.y = position.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun beginDrag() {
        dismissMenu()
        snapAnimator?.removeAllListeners()
        snapAnimator?.cancel()
        snapAnimator = null
    }

    private fun snapBallToEdge() {
        val params = ballParams ?: return
        val bounds = floatingBounds(params.width)
        val target = bounds.snapToHorizontalEdge(params.x, params.y)
        snapAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || params.x == target.x) {
            moveBall(target.x, target.y)
            persistPosition(target, bounds)
            return
        }
        val startX = params.x
        snapAnimator = ValueAnimator.ofInt(startX, target.x).apply {
            duration = SNAP_DURATION_MILLIS
            interpolator = PathInterpolator(0.25f, 1f, 0.5f, 1f)
            addUpdateListener { animation -> moveBall(animation.animatedValue as Int, target.y) }
            doOnEnd {
                snapAnimator = null
                persistPosition(target, bounds)
            }
            start()
        }
    }

    private fun persistPosition(point: FloatingTimerPoint, bounds: FloatingTimerBounds) {
        val fractions = bounds.fractions(point)
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                (application as InkTodoApplication).appContainer.preferencesRepository
                    .setPomodoroFloatingPosition(fractions.first, fractions.second)
            }
        }
    }

    private fun restorePosition(preferences: UserPreferences) {
        val params = ballParams ?: return
        val view = ballView ?: return
        val ballSize = floatingBallSizePx()
        params.width = ballSize
        params.height = ballSize
        val target = floatingBounds(params.width).fromFractions(
            preferences.pomodoroFloatingXFraction,
            preferences.pomodoroFloatingYFraction,
        )
        params.x = target.x
        params.y = target.y
        view.syncWindowPosition(target.x, target.y)
        floatingBallDiameterDp = floatingBallSizeDp()
        floatingDarkTheme = resolveDarkTheme(preferences)
        visualParams?.let { visualWindowParams ->
            val visualSize = floatingVisualSizePx()
            visualWindowParams.width = visualSize
            visualWindowParams.height = visualSize
        }
        syncVisualWindow(target, ballSize)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun toggleMenu() {
        if (menuView == null) showMenu() else dismissMenu()
    }

    private fun showMenu() {
        val params = ballParams ?: return
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        val paused = latestPreferences?.pomodoroSession?.runState == PomodoroRunState.PAUSED
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
            elevation = 18f * density
            background = GradientDrawable().apply {
                cornerRadius = 22f * density
                setColor(Color.argb(244, 239, 246, 243))
                setStroke(1.dp(), Color.argb(120, 61, 91, 91))
            }
        }
        menu.addView(menuButton("结束", Color.rgb(151, 57, 48)) { requestEnd() })
        menu.addView(menuButton(if (paused) "继续" else "暂停", Color.rgb(35, 69, 72)) {
            requestPauseOrResume()
        })
        menu.addView(menuButton("隐藏", Color.rgb(35, 69, 72)) { requestHide() })
        val menuWidth = 222.dp()
        val menuHeight = 62.dp()
        val displayWidth = displayWidthPx()
        val opensRight = params.x + params.width / 2 < displayWidth / 2
        val desiredX = if (opensRight) params.x + params.width + 6.dp() else params.x - menuWidth - 6.dp()
        val menuBounds = floatingBoundsForSize(menuWidth, menuHeight)
        val menuPosition = menuBounds.clamp(desiredX, params.y + (params.height - menuHeight) / 2)
        val menuParams = WindowManager.LayoutParams(
            menuWidth,
            menuHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuPosition.x
            y = menuPosition.y
        }
        menu.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) dismissMenu()
            false
        }
        runCatching {
            windowManager.addView(menu, menuParams)
            menuView = menu
            if (ValueAnimator.areAnimatorsEnabled()) {
                menu.pivotX = if (opensRight) 0f else menuWidth.toFloat()
                menu.pivotY = menuHeight / 2f
                AnimatorSet().apply {
                    duration = MENU_DURATION_MILLIS
                    interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                    playTogether(
                        ObjectAnimator.ofFloat(menu, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(menu, View.SCALE_X, 0.88f, 1f),
                        ObjectAnimator.ofFloat(menu, View.SCALE_Y, 0.88f, 1f),
                    )
                    start()
                }
            }
        }
    }

    private fun menuButton(label: String, textColor: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(textColor)
            isAllCaps = false
            contentDescription = "$label 番茄钟"
            minWidth = (64 * resources.displayMetrics.density).toInt()
            minHeight = (48 * resources.displayMetrics.density).toInt()
            background = GradientDrawable().apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener {
                dismissMenu()
                action()
            }
        }.also { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

    private fun requestPauseOrResume() {
        val runState = latestPreferences?.pomodoroSession?.runState ?: return
        serviceScope.launch {
            val coordinator = (application as InkTodoApplication).appContainer.pomodoroCoordinator
            withContext(Dispatchers.IO) {
                when (runState) {
                    PomodoroRunState.RUNNING -> coordinator.pause()
                    PomodoroRunState.PAUSED -> coordinator.resume()
                    PomodoroRunState.IDLE -> Unit
                }
            }
        }
    }

    private fun requestEnd() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                (application as InkTodoApplication).appContainer.pomodoroCoordinator.end()
            }
        }
    }

    private fun dismissMenu() {
        menuView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        menuView = null
    }

    private fun removeBall() {
        ballView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        visualView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
            view.disposeComposition()
        }
        visualOwner?.destroy()
        ballView = null
        ballParams = null
        visualView = null
        visualParams = null
        visualOwner = null
    }

    private fun stopCompletely() {
        mainHandler.removeCallbacks(ticker)
        dismissMenu()
        removeBall()
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        foregroundStarted = false
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "番茄钟悬浮球",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "跨应用显示番茄钟剩余时间"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) return
        val notification = buildForegroundNotification(latestPreferences)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateForegroundNotification(preferences: UserPreferences) {
        if (!foregroundStarted) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildForegroundNotification(preferences),
        )
    }

    private fun buildForegroundNotification(preferences: UserPreferences?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_POMODORO, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = preferences?.pomodoroSession?.runState
        val text = when (state) {
            PomodoroRunState.PAUSED -> "计时已暂停，双击悬浮球可继续"
            PomodoroRunState.RUNNING -> "双击悬浮球可暂停、结束或隐藏"
            else -> "正在准备番茄钟悬浮球"
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("番茄钟悬浮球")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun floatingBallSizeDp(): Int =
        if (resources.configuration.smallestScreenWidthDp >= 600) 104 else 92

    private fun floatingBallSizePx(): Int =
        (floatingBallSizeDp() * resources.displayMetrics.density).toInt()

    private fun floatingVisualSizePx(): Int {
        val visualDp = if (resources.configuration.smallestScreenWidthDp >= 600) 240 else 216
        return (visualDp * resources.displayMetrics.density).toInt()
    }

    private fun resolveDarkTheme(preferences: UserPreferences): Boolean {
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return preferences.themeMode.resolveDarkTheme(systemDark)
    }

    private fun floatingBounds(size: Int): FloatingTimerBounds = floatingBoundsForSize(size, size)

    private fun floatingBoundsForSize(width: Int, height: Int): FloatingTimerBounds {
        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val displayWidth = metrics.bounds.width()
            val displayHeight = metrics.bounds.height()
            val minX = insets.left + margin
            val maxX = (displayWidth - insets.right - width - margin).coerceAtLeast(minX)
            val minY = insets.top + margin
            val maxY = (displayHeight - insets.bottom - height - margin).coerceAtLeast(minY)
            return FloatingTimerBounds(minX, maxX, minY, maxY)
        }
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels
        return FloatingTimerBounds(
            minX = margin,
            maxX = (displayWidth - width - margin).coerceAtLeast(margin),
            minY = statusBarHeight() + margin,
            maxY = (displayHeight - navigationBarHeight() - height - margin)
                .coerceAtLeast(statusBarHeight() + margin),
        )
    }

    private fun displayWidthPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.width()
    } else {
        resources.displayMetrics.widthPixels
    }

    private fun statusBarHeight(): Int = systemDimension("status_bar_height")
    private fun navigationBarHeight(): Int = systemDimension("navigation_bar_height")

    private fun systemDimension(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id == 0) 0 else resources.getDimensionPixelSize(id)
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    companion object {
        private const val ACTION_SHOW = "com.inktodo.app.action.SHOW_POMODORO_FLOATING_TIMER"
        private const val ACTION_HIDE = "com.inktodo.app.action.HIDE_POMODORO_FLOATING_TIMER"
        private const val CHANNEL_ID = "pomodoro_floating_timer_v1"
        private const val NOTIFICATION_ID = 7_702
        private const val TICK_MILLIS = 250L
        private const val SNAP_DURATION_MILLIS = 220L
        private const val MENU_DURATION_MILLIS = 180L
        private const val VISUAL_WINDOW_ALPHA = 0.79f

        fun show(context: Context): Boolean = runCatching {
            val intent = Intent(context, PomodoroFloatingTimerService::class.java).setAction(ACTION_SHOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess

        fun hide(context: Context): Boolean = runCatching {
            context.startService(
                Intent(context, PomodoroFloatingTimerService::class.java).setAction(ACTION_HIDE),
            )
        }.isSuccess

        fun restore(context: Context): Boolean = runCatching {
            val intent = Intent(context, PomodoroFloatingTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess
    }
}

private class FloatingTimerTouchView(context: Context) : View(context) {
    var onDragStarted: () -> Unit = {}
    var onDragged: (Int, Int) -> Unit = { _, _ -> }
    var onDragReleased: () -> Unit = {}
    var onMenuRequested: () -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var dragged = false
    private var currentWindowX = 0
    private var currentWindowY = 0

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onDoubleTap(event: MotionEvent): Boolean {
            performClick()
            return true
        }
    })

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "番茄钟悬浮球，双击打开控制菜单"
    }

    fun setTimerState(
        phase: PomodoroPhase,
        runState: PomodoroRunState,
        remainingMillis: Long,
    ) {
        val safeRemaining = remainingMillis.coerceAtLeast(0L)
        contentDescription = buildString {
            append(if (phase == PomodoroPhase.FOCUS) "专注" else "休息")
            append("番茄钟，剩余")
            append(formatFloatingTime(safeRemaining))
            if (runState == PomodoroRunState.PAUSED) append("，已暂停")
            append("。双击打开控制菜单")
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downRawX = event.rawX
                downRawY = event.rawY
                startWindowX = currentWindowX
                startWindowY = currentWindowY
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragged && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    dragged = true
                    onDragStarted()
                }
                if (dragged) {
                    currentWindowX = startWindowX + dx.toInt()
                    currentWindowY = startWindowY + dy.toInt()
                    onDragged(currentWindowX, currentWindowY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged) onDragReleased()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                dragged = false
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    if (dragged) onDragReleased()
                    dragged = false
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun syncWindowPosition(x: Int, y: Int) {
        currentWindowX = x
        currentWindowY = y
    }

    override fun performClick(): Boolean {
        super.performClick()
        onMenuRequested()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.className = Button::class.java.name
    }
}

private class FloatingComposeWindowOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

private fun formatFloatingTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
