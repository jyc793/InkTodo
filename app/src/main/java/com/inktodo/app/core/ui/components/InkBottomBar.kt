package com.inktodo.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.navigation.AppDestination
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun InkBottomBar(
    currentRoute: String?,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val colors = InkTheme.colors
    Surface(
        color = colors.wash.copy(alpha = InkTheme.glass.navigationAlpha),
        border = BorderStroke(InkTheme.glass.edgeWidth, colors.glassEdge),
        shadowElevation = InkTheme.glass.shadowElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(InkTheme.spacing.xs),
        ) {
            AppDestination.bottomDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val selection by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (InkTheme.motion.enabled) InkTheme.motion.standardMillis else 0,
                        easing = InkTheme.motion.enterEasing,
                    ),
                    label = "${destination.route} selection",
                )
                val accent = destination.navigationAccent(colors)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = InkTheme.spacing.bottomBarHeight)
                        .testTag("nav_${destination.route}")
                        .semantics { contentDescription = destination.label }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onDestinationSelected(destination) },
                    ),
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = 0.02f + selection * 0.16f),
                    border = BorderStroke(
                        InkTheme.glass.edgeWidth,
                        accent.copy(alpha = 0.05f + selection * 0.38f),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        InkDestinationMark(
                            destination = destination,
                            selected = selected,
                            selection = selection,
                        )
                        Text(
                            text = destination.label,
                            color = lerp(colors.mist, colors.ink, selection),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InkDestinationMark(
    destination: AppDestination,
    selected: Boolean,
    selection: Float,
) {
    val colors = InkTheme.colors
    val accent = destination.navigationAccent(colors)
    val phase = bottomMarkPhase(
        enabled = selected && InkTheme.motion.enabled,
        label = "${destination.route} mark",
    )
    Box(
        modifier = Modifier.size(InkTheme.spacing.touchTarget * 0.72f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.045f
            when (destination) {
                AppDestination.Todo -> drawMountainMark(accent, selection, phase, stroke)
                AppDestination.Calendar -> drawMoonMark(accent, selection, phase)
                AppDestination.Pomodoro -> drawWaterMark(accent, selection, phase, stroke)
                AppDestination.Completed -> drawMistMark(accent, selection, phase, stroke)
                else -> Unit
            }
        }
        Text(
            text = destination.mark,
            color = lerp(colors.mist, accent, 0.48f + selection * 0.52f),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMountainMark(
    accent: Color,
    selection: Float,
    phase: Float,
    stroke: Float,
) {
    val ridge = Path().apply {
        moveTo(size.width * 0.06f, size.height * 0.72f)
        lineTo(size.width * 0.28f, size.height * 0.48f)
        lineTo(size.width * 0.43f, size.height * 0.63f)
        lineTo(size.width * 0.66f, size.height * 0.34f)
        lineTo(size.width * 0.94f, size.height * 0.72f)
    }
    drawPath(
        ridge,
        color = accent.copy(alpha = 0.12f + selection * 0.30f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    val mistShift = sin(phase * PI * 2.0).toFloat() * size.width * 0.025f
    drawLine(
        color = accent.copy(alpha = 0.10f + selection * 0.20f),
        start = Offset(size.width * 0.16f + mistShift, size.height * 0.78f),
        end = Offset(size.width * 0.84f + mistShift, size.height * 0.78f),
        strokeWidth = stroke * 0.72f,
        cap = StrokeCap.Round,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoonMark(
    accent: Color,
    selection: Float,
    phase: Float,
) {
    val pulse = 0.94f + sin(phase * PI * 2.0).toFloat() * 0.06f
    val center = Offset(size.width * 0.50f, size.height * 0.50f)
    fun moonPoint(x: Float, y: Float): Offset = Offset(
        x = center.x + (size.width * x - center.x) * pulse,
        y = center.y + (size.height * y - center.y) * pulse,
    )
    val crescent = Path().apply {
        val top = moonPoint(0.64f, 0.10f)
        moveTo(top.x, top.y)
        val outerControlTop = moonPoint(0.18f, 0.17f)
        val outerControlBottom = moonPoint(0.18f, 0.83f)
        val bottom = moonPoint(0.64f, 0.90f)
        cubicTo(
            outerControlTop.x, outerControlTop.y,
            outerControlBottom.x, outerControlBottom.y,
            bottom.x, bottom.y,
        )
        val innerControlBottom = moonPoint(0.43f, 0.70f)
        val innerControlTop = moonPoint(0.43f, 0.30f)
        cubicTo(
            innerControlBottom.x, innerControlBottom.y,
            innerControlTop.x, innerControlTop.y,
            top.x, top.y,
        )
        close()
    }
    drawPath(
        path = crescent,
        color = accent.copy(alpha = 0.12f + selection * 0.30f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaterMark(
    accent: Color,
    selection: Float,
    phase: Float,
    stroke: Float,
) {
    repeat(3) { row ->
        val path = Path()
        repeat(13) { point ->
            val fraction = point / 12f
            val x = size.width * (0.08f + fraction * 0.84f)
            val angle = fraction * PI * 2.0 + phase * PI * 2.0 + row * 0.72
            val y = size.height * (0.38f + row * 0.16f) +
                sin(angle).toFloat() * size.height * (0.030f + selection * 0.016f)
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = accent.copy(alpha = 0.135f + selection * (0.335f - row * 0.056f)),
            style = Stroke(width = stroke * (1f - row * 0.12f), cap = StrokeCap.Round),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMistMark(
    accent: Color,
    selection: Float,
    phase: Float,
    stroke: Float,
) {
    val drift = sin(phase * PI * 2.0).toFloat() * size.width * 0.035f
    repeat(3) { index ->
        val width = size.width * (0.84f - index * 0.08f)
        val height = size.height * (0.22f - index * 0.02f)
        val left = size.width * (0.08f + index * 0.04f) + drift * if (index % 2 == 0) 1f else -1f
        val top = size.height * (0.24f + index * 0.18f)
        drawOval(
            color = accent.copy(alpha = 0.07f + selection * (0.15f - index * 0.025f)),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
        drawLine(
            color = accent.copy(alpha = 0.10f + selection * 0.18f),
            start = Offset(left + width * 0.08f, top + height * 0.56f),
            end = Offset(left + width * 0.92f, top + height * 0.56f),
            strokeWidth = stroke * 0.58f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun bottomMarkPhase(enabled: Boolean, label: String): Float {
    if (!enabled) return 0.25f
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "$label phase",
    )
    return phase
}

private fun AppDestination.navigationAccent(colors: com.inktodo.app.core.ui.theme.InkPalette): Color =
    when (this) {
        AppDestination.Todo -> colors.moss
        AppDestination.Calendar -> colors.indigo
        AppDestination.Pomodoro -> colors.jade
        AppDestination.Completed -> colors.mist
        else -> colors.inkSoft
    }
