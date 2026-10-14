package com.inktodo.app.feature.pomodoro.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.R
import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.pomodoro.floating.PomodoroFloatingTimerService
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val focusPresets = listOf(15, 25, 30, 45, 60)
private val breakPresets = listOf(5, 10, 15, 20)

@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    onBottomBarVisibilityChanged: (Boolean) -> Unit = {},
    onTransitionRunningChanged: (Boolean) -> Unit = {},
    normalBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customTarget by remember { mutableStateOf<PomodoroPhase?>(null) }
    var showFloatingPermissionDialog by remember { mutableStateOf(false) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            if (!PomodoroFloatingTimerService.show(context)) {
                Toast.makeText(context, "无法启动番茄钟悬浮球", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "未获得悬浮窗权限，悬浮球没有开启", Toast.LENGTH_LONG).show()
        }
    }
    val onFloatingToggle: () -> Unit = {
        if (state.floatingVisible) {
            PomodoroFloatingTimerService.hide(context)
        } else if (Settings.canDrawOverlays(context)) {
            if (!PomodoroFloatingTimerService.show(context)) {
                Toast.makeText(context, "无法启动番茄钟悬浮球", Toast.LENGTH_LONG).show()
            }
        } else {
            showFloatingPermissionDialog = true
        }
    }
    val motionEnabled = InkTheme.motion.enabled
    val chromeAlpha = remember { Animatable(if (state.ordinaryImmersive) 0f else 1f) }
    val immersiveProgress = remember { Animatable(if (state.ordinaryImmersive) 1f else 0f) }
    val moveEasing = remember { CubicBezierEasing(0.25f, 1f, 0.5f, 1f) }

    LaunchedEffect(state.ordinaryImmersive, motionEnabled) {
        val entryNeedsAnimation = state.ordinaryImmersive &&
            (chromeAlpha.value > 0f || immersiveProgress.value < 1f)
        val exitNeedsAnimation = !state.ordinaryImmersive &&
            (chromeAlpha.value < 1f || immersiveProgress.value > 0f)
        onTransitionRunningChanged(motionEnabled && (entryNeedsAnimation || exitNeedsAnimation))
        try {
            if (state.ordinaryImmersive) {
                onBottomBarVisibilityChanged(false)
                if (!motionEnabled) {
                    chromeAlpha.snapTo(0f)
                    immersiveProgress.snapTo(1f)
                } else {
                    val fadeDuration = (550 * chromeAlpha.value).roundToInt()
                    val moveDuration = (750 * (1f - immersiveProgress.value)).roundToInt()
                    runPomodoroPairedTransition(
                        fade = {
                            if (fadeDuration > 0) {
                                chromeAlpha.animateTo(0f, tween(fadeDuration, easing = moveEasing))
                            }
                        },
                        move = {
                            if (moveDuration > 0) {
                                immersiveProgress.animateTo(1f, tween(moveDuration, easing = moveEasing))
                            }
                        },
                    )
                }
            } else {
                if (!motionEnabled) {
                    immersiveProgress.snapTo(0f)
                    onBottomBarVisibilityChanged(true)
                    chromeAlpha.snapTo(1f)
                } else {
                    val moveDuration = (750 * immersiveProgress.value).roundToInt()
                    onBottomBarVisibilityChanged(true)
                    val fadeDuration = (550 * (1f - chromeAlpha.value)).roundToInt()
                    runPomodoroPairedTransition(
                        fade = {
                            if (fadeDuration > 0) {
                                chromeAlpha.animateTo(1f, tween(fadeDuration, easing = moveEasing))
                            }
                        },
                        move = {
                            if (moveDuration > 0) {
                                immersiveProgress.animateTo(0f, tween(moveDuration, easing = moveEasing))
                            }
                        },
                    )
                }
            }
        } finally {
            onTransitionRunningChanged(false)
        }
    }

    BackHandler(enabled = state.ordinaryImmersive) {
        viewModel.exitOrdinaryImmersive()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val tabletLandscape = maxWidth >= 720.dp && maxWidth > maxHeight
        val horizontalPadding = InkTheme.spacing.screenHorizontal
        val contentWidth = maxWidth - horizontalPadding * 2
        val compactTimerDiameter = if (tabletLandscape) {
            minOf(contentWidth * 0.43f, maxHeight * 0.52f, 520.dp)
        } else {
            minOf(contentWidth * 0.72f, maxHeight * 0.42f, 520.dp)
        }
        val controlWidth = if (tabletLandscape) {
            ((contentWidth - InkTheme.spacing.lg) * 0.54f).coerceAtMost(560.dp)
        } else {
            contentWidth.coerceAtMost(560.dp)
        }
        val targetDiameter = immersiveTimerDiameter(
            availableWidth = maxWidth.value,
            availableHeight = maxHeight.value,
            maxDiameter = 620f,
        ).dp
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val rootWidthPx = with(density) { viewportWidth.toPx() }
        val rootHeightPx = with(density) { viewportHeight.toPx() }
        var rootBounds by remember { mutableStateOf<Rect?>(null) }
        var timerAnchor by remember { mutableStateOf<Rect?>(null) }
        var controlsAnchor by remember { mutableStateOf<Rect?>(null) }
        val rootModifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }

        Box(modifier = rootModifier) {
            KoiRippleField(
                center = animatedTimerCenter(
                    rootBounds = rootBounds,
                    timerAnchor = timerAnchor,
                    progress = immersiveProgress.value,
                    fallbackWidth = rootWidthPx,
                    fallbackHeight = rootHeightPx,
                ),
                ringRadius = with(density) {
                    val compact = timerAnchor?.width ?: compactTimerDiameter.toPx()
                    lerpFloat(compact, targetDiameter.toPx(), immersiveProgress.value) * 0.44f
                },
                runState = state.runState,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        top = InkTheme.spacing.sm,
                        end = horizontalPadding,
                        bottom = InkTheme.spacing.sm + normalBottomPadding,
                    )
                    .graphicsLayer {
                        alpha = chromeAlpha.value
                        translationY = disappearingChromeTranslationY(
                            viewportHeight = rootHeightPx,
                            progress = immersiveProgress.value,
                        )
                    }
                    .then(
                        if (chromeAlpha.value <= 0.01f) Modifier.clearAndSetSemantics { }
                        else Modifier,
                    ),
            ) {
                PomodoroChromeLayout(
                    tabletLandscape = tabletLandscape,
                    compactTimerDiameter = compactTimerDiameter,
                    state = state,
                    viewModel = viewModel,
                    onCustom = { customTarget = it },
                    onTimerPlaced = { timerAnchor = relativeBounds(it, rootBounds) },
                    onControlsPlaced = { controlsAnchor = relativeBounds(it, rootBounds) },
                )
            }

            val targetDiameterPx = with(density) { targetDiameter.toPx() }
            val normalTimerCenter = timerAnchor?.center ?: Offset(rootWidthPx / 2f, rootHeightPx * 0.34f)
            val targetTimerCenter = Offset(rootWidthPx / 2f, rootHeightPx / 2f)
            val compactDiameterPx = timerAnchor?.width ?: with(density) { compactTimerDiameter.toPx() }
            val ringScale = lerpFloat(
                compactDiameterPx / targetDiameterPx.coerceAtLeast(1f),
                1f,
                immersiveProgress.value,
            )
            TimerMirror(
                state = state,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(targetDiameter)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        translationX = (normalTimerCenter.x - targetTimerCenter.x) *
                            (1f - immersiveProgress.value)
                        translationY = (normalTimerCenter.y - targetTimerCenter.y) *
                            (1f - immersiveProgress.value)
                    }
                    .clickable(
                        enabled = state.runState != PomodoroRunState.IDLE &&
                            !state.ordinaryImmersive,
                        onClick = viewModel::enterOrdinaryImmersive,
                    ),
            )

            val normalControlsCenter = controlsAnchor?.center
                ?: Offset(rootWidthPx / 2f, rootHeightPx * 0.78f)
            val targetControlsCenter = Offset(
                rootWidthPx / 2f,
                rootHeightPx - (controlsAnchor?.height ?: with(density) { 64.dp.toPx() }) / 2f,
            )
            TimerControls(
                state = state,
                viewModel = viewModel,
                onFloatingToggle = onFloatingToggle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(controlWidth)
                    .graphicsLayer {
                        translationX = (normalControlsCenter.x - targetControlsCenter.x) *
                            (1f - immersiveProgress.value)
                        translationY = (normalControlsCenter.y - targetControlsCenter.y) *
                            (1f - immersiveProgress.value)
                    },
            )
        }
    }

    customTarget?.let { phase ->
        CustomDurationDialog(
            title = if (phase == PomodoroPhase.FOCUS) "自定义专注时间" else "自定义休息时间",
            initialMinutes = if (phase == PomodoroPhase.FOCUS) state.focusMinutes else state.breakMinutes,
            onDismiss = { customTarget = null },
            onConfirm = { minutes ->
                if (phase == PomodoroPhase.FOCUS) {
                    viewModel.setFocusMinutes(minutes)
                } else {
                    viewModel.setBreakMinutes(minutes)
                }
                customTarget = null
            },
        )
    }

    if (showFloatingPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showFloatingPermissionDialog = false },
            title = { Text("开启番茄钟悬浮球") },
            text = {
                Text("需要允许墨待显示在其他应用上层。授权后，运行中的锦鲤环会缩成可拖动悬浮球；双击可暂停、结束或隐藏。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFloatingPermissionDialog = false
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                ) { Text("前往授权") }
            },
            dismissButton = {
                TextButton(onClick = { showFloatingPermissionDialog = false }) {
                    Text("暂不开启")
                }
            },
        )
    }
}

@Composable
private fun PomodoroChromeLayout(
    tabletLandscape: Boolean,
    compactTimerDiameter: androidx.compose.ui.unit.Dp,
    state: PomodoroUiState,
    viewModel: PomodoroViewModel,
    onCustom: (PomodoroPhase) -> Unit,
    onTimerPlaced: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    onControlsPlaced: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
) {
    if (tabletLandscape) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier.weight(0.92f).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InkSceneHeader(
                    title = "番茄钟",
                    scene = InkScene.POMODORO,
                    subtitle = "水行有度，专注有时",
                )
                Spacer(Modifier.height(InkTheme.spacing.md))
                TimerAnchor(compactTimerDiameter, onTimerPlaced)
                Spacer(Modifier.height(InkTheme.spacing.xxl))
            }
            Column(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = InkTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PomodoroControls(state, viewModel, onCustom) {
                    ControlsAnchor(onControlsPlaced)
                }
                Spacer(Modifier.height(InkTheme.spacing.xxl))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InkSceneHeader(
                title = "番茄钟",
                scene = InkScene.POMODORO,
                subtitle = "水行有度，专注有时",
            )
            Spacer(Modifier.height(InkTheme.spacing.sm))
            TimerAnchor(compactTimerDiameter, onTimerPlaced)
            Spacer(Modifier.height(InkTheme.spacing.sm))
            PomodoroControls(state, viewModel, onCustom) {
                ControlsAnchor(onControlsPlaced)
            }
            Spacer(Modifier.height(InkTheme.spacing.xxl))
        }
    }
}

@Composable
private fun TimerAnchor(
    diameter: androidx.compose.ui.unit.Dp,
    onPlaced: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
) {
    Box(Modifier.size(diameter).onGloballyPositioned(onPlaced))
}

@Composable
private fun ControlsAnchor(
    onPlaced: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(64.dp).onGloballyPositioned(onPlaced))
}

@Composable
private fun PomodoroControls(
    state: PomodoroUiState,
    viewModel: PomodoroViewModel,
    onCustom: (PomodoroPhase) -> Unit,
    controlsAnchor: @Composable () -> Unit,
) {
    DurationSelector(
        title = "专注时间",
        values = focusPresets,
        selected = state.focusMinutes,
        enabled = state.runState == PomodoroRunState.IDLE,
        onSelected = viewModel::setFocusMinutes,
        onCustom = { onCustom(PomodoroPhase.FOCUS) },
    )
    DurationSelector(
        title = "休息时间",
        values = breakPresets,
        selected = state.breakMinutes,
        enabled = state.runState == PomodoroRunState.IDLE,
        onSelected = viewModel::setBreakMinutes,
        onCustom = { onCustom(PomodoroPhase.BREAK) },
    )
    Spacer(Modifier.height(InkTheme.spacing.md))
    controlsAnchor()
    Spacer(Modifier.height(InkTheme.spacing.sm))
    FullFocusEntryAndOverlay(state, viewModel)
}

@Composable
internal fun TimerMirror(
    state: PomodoroUiState,
    modifier: Modifier = Modifier,
    minimal: Boolean = false,
) {
    val progress = rememberContinuousKoiProgress(
        state = state,
    )
    val wakePhase = rememberKoiWakePhase(
        runState = state.runState,
        enabled = InkTheme.motion.enabled,
    )
    val swimPhase = rememberKoiSwimPhase(
        runState = state.runState,
        enabled = InkTheme.motion.enabled,
    )
    val colors = InkTheme.colors
    BoxWithConstraints(
        modifier = modifier.testTag("pomodoro_timer"),
        contentAlignment = Alignment.Center,
    ) {
        val diameterPx = with(LocalDensity.current) { maxWidth.toPx() }
        val ringRadiusPx = diameterPx * 0.395f
        val positionAngle = koiPositionAngleDegrees(progress)
        val koiHeightPx = diameterPx * 0.24f
        val koiWidthPx = diameterPx * 0.30f
        val koiTranslation = koiLayerTranslationForMouthAnchor(
            progress = progress,
            ringRadius = ringRadiusPx,
            fishWidth = koiWidthPx,
            fishHeight = koiHeightPx,
        )
        val wakeTexture = ImageBitmap.imageResource(R.drawable.ink_koi_wake_v1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val ringRadius = diameter * 0.395f
            val ringWidth = diameter * 0.058f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.glassStrong.copy(alpha = 0.34f),
                        colors.jade.copy(alpha = 0.08f),
                        colors.indigo.copy(alpha = 0.025f),
                        colors.glass.copy(alpha = 0.03f),
                    ),
                    center = center.copy(
                        x = center.x - diameter * 0.08f,
                        y = center.y - diameter * 0.10f,
                    ),
                    radius = diameter * 0.46f,
                ),
                radius = diameter * 0.44f,
            )

            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        colors.surfaceStrong.copy(alpha = 0.64f),
                        colors.jade.copy(alpha = 0.24f),
                        colors.glassStrong.copy(alpha = 0.72f),
                        colors.surfaceStrong.copy(alpha = 0.62f),
                    ),
                ),
                radius = ringRadius,
                style = Stroke(width = ringWidth),
            )

            drawCircle(
                color = colors.surfaceStrong.copy(alpha = 0.62f),
                radius = ringRadius - ringWidth * 0.47f,
                style = Stroke(width = diameter * 0.005f),
            )
            drawCircle(
                color = colors.glassEdge.copy(alpha = 0.52f),
                radius = ringRadius + ringWidth * 0.47f,
                style = Stroke(width = diameter * 0.004f),
            )

            if (state.runState != PomodoroRunState.IDLE) {
                repeat(5) { wakeIndex ->
                    val localPhase = (wakePhase + wakeIndex / 5f) % 1f
                    val trailAngle = positionAngle - 8f - localPhase * 48f
                    val trailRadians = Math.toRadians(trailAngle.toDouble())
                    val rippleCenter = Offset(
                        x = center.x + cos(trailRadians).toFloat() * ringRadius,
                        y = center.y + sin(trailRadians).toFloat() * ringRadius,
                    )
                    val rippleWidth = diameter * (0.16f + localPhase * 0.10f)
                    val rippleHeight = diameter * (0.070f + localPhase * 0.045f)
                    rotate(degrees = koiWakeTextureRotationDegrees(trailAngle), pivot = rippleCenter) {
                        drawImage(
                            image = wakeTexture,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(wakeTexture.width, wakeTexture.height),
                            dstOffset = IntOffset(
                                (rippleCenter.x - rippleWidth / 2f).toInt(),
                                (rippleCenter.y - rippleHeight / 2f).toInt(),
                            ),
                            dstSize = IntSize(
                                rippleWidth.toInt().coerceAtLeast(1),
                                rippleHeight.toInt().coerceAtLeast(1),
                            ),
                            alpha = 0.62f * koiWakeAlpha(localPhase) *
                                if (state.runState == PomodoroRunState.PAUSED) 0.62f else 1f,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }

        SwimmingKoi(
            swimPhase = swimPhase,
            modifier = Modifier
                .align(Alignment.Center)
                .height(maxWidth * 0.24f)
                .width(maxWidth * 0.30f)
                .graphicsLayer {
                    translationX = koiTranslation.x
                    translationY = koiTranslation.y
                    rotationZ = koiSpriteRotationDegrees(progress)
                },
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!minimal) {
                Text(
                    if (state.phase == PomodoroPhase.FOCUS) "专注" else "休息",
                    color = colors.indigo,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                formatRemaining(state.remainingMillis),
                color = colors.ink,
                style = if (minimal) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.displayMedium
                },
            )
            if (!minimal) {
                Text(
                    when (state.runState) {
                        PomodoroRunState.IDLE -> "水静待启"
                        PomodoroRunState.RUNNING -> "水行不息"
                        PomodoroRunState.PAUSED -> "水止心定"
                    },
                    color = colors.inkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SwimmingKoi(
    swimPhase: Float,
    modifier: Modifier = Modifier,
) {
    val koi = ImageBitmap.imageResource(R.drawable.ink_koi_timer_v2)
    val bitmap = remember(koi) { koi.asAndroidBitmap() }
    val bitmapPaint = remember {
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
        )
    }
    Canvas(modifier = modifier) {
        val meshWidth = 4
        val meshHeight = 28
        val renderedWidth = size.width * 0.42f
        val vertices = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)
        var vertexIndex = 0
        for (row in 0..meshHeight) {
            val bodyPosition = row.toFloat() / meshHeight
            val bendProgress = ((bodyPosition - 0.12f) / 0.88f).coerceIn(0f, 1f)
            val ringCurve = size.width * 0.225f * bendProgress.pow(2.05f)
            val swimWave = koiBodyWaveOffset(
                bodyPosition = bendProgress,
                swimPhase = swimPhase,
                availableWidth = size.width * 0.72f,
            )
            for (column in 0..meshWidth) {
                val acrossBody = column.toFloat() / meshWidth
                vertices[vertexIndex++] =
                    size.width / 2f + (acrossBody - 0.5f) * renderedWidth + ringCurve + swimWave
                vertices[vertexIndex++] = bodyPosition * size.height
            }
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                bitmap,
                meshWidth,
                meshHeight,
                vertices,
                0,
                null,
                0,
                bitmapPaint,
            )
        }
    }
}

@Composable
private fun rememberContinuousKoiProgress(
    state: PomodoroUiState,
): Float {
    var progress by remember { mutableFloatStateOf(state.elapsedProgress) }
    LaunchedEffect(
        state.runState,
        state.targetEndEpochMillis,
        state.durationMillis,
    ) {
        if (
            state.runState != PomodoroRunState.RUNNING ||
            state.targetEndEpochMillis <= 0L
        ) {
            progress = state.elapsedProgress
            return@LaunchedEffect
        }
        while (true) {
            progress = koiElapsedProgressAt(
                runState = state.runState,
                remainingMillis = state.remainingMillis,
                durationMillis = state.durationMillis,
                targetEndEpochMillis = state.targetEndEpochMillis,
                nowEpochMillis = System.currentTimeMillis(),
            )
            withFrameNanos { }
        }
    }
    return progress
}

@Composable
private fun rememberKoiWakePhase(
    runState: PomodoroRunState,
    enabled: Boolean,
): Float {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(runState, enabled) {
        when {
            runState == PomodoroRunState.IDLE -> phase.snapTo(0f)
            runState == PomodoroRunState.RUNNING && enabled -> {
                while (true) {
                    val remainingFraction = (1f - phase.value).coerceIn(0f, 1f)
                    phase.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (2_600 * remainingFraction).toInt().coerceAtLeast(1),
                            easing = LinearEasing,
                        ),
                    )
                    phase.snapTo(0f)
                }
            }
        }
    }
    return phase.value
}

@Composable
private fun rememberKoiSwimPhase(
    runState: PomodoroRunState,
    enabled: Boolean,
): Float {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(runState, enabled) {
        when {
            runState == PomodoroRunState.IDLE -> phase.snapTo(0f)
            runState == PomodoroRunState.RUNNING && enabled -> {
                while (true) {
                    val remainingFraction = (1f - phase.value).coerceIn(0f, 1f)
                    phase.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (1_150 * remainingFraction).toInt().coerceAtLeast(1),
                            easing = LinearEasing,
                        ),
                    )
                    phase.snapTo(0f)
                }
            }
        }
    }
    return phase.value
}

@Composable
private fun DurationSelector(
    title: String,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InkSectionLabel("$title · 分钟")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
            ) {
                items(values) { minutes ->
                    FilterChip(
                        modifier = Modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
                        selected = selected == minutes,
                        enabled = enabled,
                        onClick = { onSelected(minutes) },
                        label = { Text(minutes.toString()) },
                        colors = inkFilterChipColors(),
                    )
                }
            }
            FilterChip(
                modifier = Modifier
                    .padding(start = InkTheme.spacing.sm)
                    .defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
                selected = selected !in values,
                enabled = enabled,
                onClick = onCustom,
                label = { Text("自定义") },
                colors = inkFilterChipColors(),
            )
        }
    }
}

@Composable
private fun TimerControls(
    state: PomodoroUiState,
    viewModel: PomodoroViewModel,
    onFloatingToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkGlassCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkCompactButton(
                text = "重置",
                onClick = viewModel::reset,
                modifier = Modifier.testTag("pomodoro_reset"),
            )
            val primaryText: String
            val primaryAction: () -> Unit
            val primaryTag: String
            when (state.runState) {
                PomodoroRunState.IDLE -> {
                    primaryText = "开始"
                    primaryAction = viewModel::start
                    primaryTag = "pomodoro_start"
                }
                PomodoroRunState.RUNNING -> {
                    primaryText = "暂停"
                    primaryAction = viewModel::pause
                    primaryTag = "pomodoro_pause"
                }
                PomodoroRunState.PAUSED -> {
                    primaryText = "继续"
                    primaryAction = viewModel::resume
                    primaryTag = "pomodoro_resume"
                }
            }
            InkPrimaryButton(
                text = primaryText,
                onClick = primaryAction,
                modifier = Modifier.weight(1f).testTag(primaryTag),
            )
            InkCompactButton(
                text = "结束",
                onClick = viewModel::end,
                destructive = true,
                modifier = Modifier.testTag("pomodoro_end"),
            )
            InkCompactButton(
                text = if (state.floatingVisible) "隐藏" else "悬浮",
                onClick = onFloatingToggle,
                enabled = state.runState != PomodoroRunState.IDLE,
                emphasized = state.floatingVisible,
                modifier = Modifier.testTag("pomodoro_floating_timer"),
            )
        }
    }
}

private fun relativeBounds(
    coordinates: androidx.compose.ui.layout.LayoutCoordinates,
    rootBounds: Rect?,
): Rect {
    val bounds = coordinates.boundsInRoot()
    val root = rootBounds ?: return bounds
    return Rect(
        left = bounds.left - root.left,
        top = bounds.top - root.top,
        right = bounds.right - root.left,
        bottom = bounds.bottom - root.top,
    )
}

private fun animatedTimerCenter(
    rootBounds: Rect?,
    timerAnchor: Rect?,
    progress: Float,
    fallbackWidth: Float,
    fallbackHeight: Float,
): Offset {
    val width = rootBounds?.width ?: fallbackWidth
    val height = rootBounds?.height ?: fallbackHeight
    val compact = timerAnchor?.center ?: Offset(width / 2f, height * 0.34f)
    val target = Offset(width / 2f, height / 2f)
    return Offset(
        x = lerpFloat(compact.x, target.x, progress),
        y = lerpFloat(compact.y, target.y, progress),
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

@Composable
private fun inkFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = InkTheme.colors.glass.copy(alpha = InkTheme.glass.subtleCardAlpha),
    labelColor = InkTheme.colors.inkSoft,
    selectedContainerColor = InkTheme.colors.jade.copy(alpha = 0.20f),
    selectedLabelColor = InkTheme.colors.ink,
)

@Composable
private fun CustomDurationDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialMinutes) { mutableStateOf(initialMinutes.toString()) }
    val minutes = value.toIntOrNull()
    val valid = minutes != null && minutes in
        UserPreferences.MIN_TIMER_MINUTES..UserPreferences.MAX_TIMER_MINUTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                label = { Text("分钟（1 - 1440）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(requireNotNull(minutes)) }, enabled = valid) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun formatRemaining(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
