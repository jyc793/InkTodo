package com.inktodo.app.feature.pomodoro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktodo.app.R
import com.inktodo.app.core.datastore.PomodoroPhase
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.ui.components.InkDestructiveButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.components.InkSecondaryButton
import com.inktodo.app.core.ui.components.InkSectionLabel
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import kotlin.math.cos
import kotlin.math.sin

private val focusPresets = listOf(15, 25, 30, 45, 60)
private val breakPresets = listOf(5, 10, 15, 20)

@Composable
fun PomodoroScreen(viewModel: PomodoroViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customTarget by remember { mutableStateOf<PomodoroPhase?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = InkTheme.spacing.lg, vertical = InkTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkSceneHeader(
            title = "番茄钟",
            scene = InkScene.POMODORO,
            subtitle = "水行有度，专注有时",
        )
        Spacer(Modifier.height(InkTheme.spacing.lg))
        TimerMirror(
            state = state,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(InkTheme.spacing.lg))

        DurationSelector(
            title = "专注时间",
            values = focusPresets,
            selected = state.focusMinutes,
            enabled = state.runState == PomodoroRunState.IDLE,
            onSelected = viewModel::setFocusMinutes,
            onCustom = { customTarget = PomodoroPhase.FOCUS },
        )
        DurationSelector(
            title = "休息时间",
            values = breakPresets,
            selected = state.breakMinutes,
            enabled = state.runState == PomodoroRunState.IDLE,
            onSelected = viewModel::setBreakMinutes,
            onCustom = { customTarget = PomodoroPhase.BREAK },
        )
        Spacer(Modifier.height(InkTheme.spacing.md))
        TimerControls(state, viewModel)
        Spacer(Modifier.height(InkTheme.spacing.sm))
        FullFocusEntryAndOverlay(state, viewModel)
        Spacer(Modifier.height(InkTheme.spacing.xxl))
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
    val colors = InkTheme.colors
    BoxWithConstraints(
        modifier = modifier.testTag("pomodoro_timer"),
        contentAlignment = Alignment.Center,
    ) {
        val diameterPx = with(LocalDensity.current) { maxWidth.toPx() }
        val ringRadiusPx = diameterPx * 0.395f
        val positionAngle = koiPositionAngleDegrees(progress)
        val positionRadians = Math.toRadians(positionAngle.toDouble())
        val koiTranslationX = cos(positionRadians).toFloat() * ringRadiusPx
        val koiTranslationY = sin(positionRadians).toFloat() * ringRadiusPx

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
                repeat(7) { wakeIndex ->
                    val localPhase = (wakePhase + wakeIndex / 7f) % 1f
                    val fade = 1f - localPhase
                    val gapFromKoi = 7f + localPhase * 69f
                    val sweep = 5.2f + localPhase * 4.2f
                    val alpha = (0.06f + 0.38f * fade * fade) *
                        if (state.runState == PomodoroRunState.PAUSED) 0.72f else 1f
                    repeat(3) { rippleIndex ->
                        val radialOffset = (rippleIndex - 1) * diameter * 0.016f
                        drawArc(
                            color = colors.jade.copy(alpha = alpha * (1f - rippleIndex * 0.16f)),
                            startAngle = positionAngle - gapFromKoi - sweep,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                center.x - ringRadius - radialOffset,
                                center.y - ringRadius - radialOffset,
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                (ringRadius + radialOffset) * 2f,
                                (ringRadius + radialOffset) * 2f,
                            ),
                            style = Stroke(
                                width = diameter * (0.006f + fade * 0.005f),
                            ),
                        )
                    }
                }
                repeat(3) { highlightIndex ->
                    val behind = 4f + highlightIndex * 5f
                    drawArc(
                        color = colors.surfaceStrong.copy(alpha = 0.34f - highlightIndex * 0.08f),
                        startAngle = positionAngle - behind - 3.2f,
                        sweepAngle = 3.2f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - ringRadius,
                            center.y - ringRadius,
                        ),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = diameter * (0.010f - highlightIndex * 0.002f)),
                    )
                }
            } else {
                drawCircle(
                    color = colors.jade.copy(alpha = 0.08f),
                    radius = diameter * 0.18f,
                    style = Stroke(width = diameter * 0.004f),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ink_koi_timer_v1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .height(maxWidth * 0.275f)
                .graphicsLayer {
                    translationX = koiTranslationX
                    translationY = koiTranslationY
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
                style = MaterialTheme.typography.displayMedium,
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
private fun DurationSelector(
    title: String,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InkSectionLabel(title)
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
                        label = { Text("$minutes 分钟") },
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
private fun TimerControls(state: PomodoroUiState, viewModel: PomodoroViewModel) {
    InkGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(InkTheme.spacing.md)) {
            when (state.runState) {
                PomodoroRunState.IDLE -> InkPrimaryButton(
                    text = "开始",
                    onClick = viewModel::start,
                    modifier = Modifier.fillMaxWidth().testTag("pomodoro_start"),
                )
                PomodoroRunState.RUNNING -> InkPrimaryButton(
                    text = "暂停",
                    onClick = viewModel::pause,
                    modifier = Modifier.fillMaxWidth().testTag("pomodoro_pause"),
                )
                PomodoroRunState.PAUSED -> InkPrimaryButton(
                    text = "继续",
                    onClick = viewModel::resume,
                    modifier = Modifier.fillMaxWidth().testTag("pomodoro_resume"),
                )
            }
            Spacer(Modifier.height(InkTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
            ) {
                InkSecondaryButton(
                    text = "重置",
                    onClick = viewModel::reset,
                    modifier = Modifier.weight(1f).testTag("pomodoro_reset"),
                )
                InkDestructiveButton(
                    text = "结束",
                    onClick = viewModel::end,
                    modifier = Modifier.weight(1f).testTag("pomodoro_end"),
                )
            }
        }
    }
}

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

private fun formatRemaining(milliseconds: Long): String {
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
