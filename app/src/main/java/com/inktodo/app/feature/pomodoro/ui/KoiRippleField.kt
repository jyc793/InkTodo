package com.inktodo.app.feature.pomodoro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.ui.theme.InkTheme
import kotlin.math.hypot
import kotlin.math.max

@Composable
internal fun KoiRippleField(
    center: Offset,
    ringRadius: Float,
    runState: PomodoroRunState,
    modifier: Modifier = Modifier,
) {
    val phase = rememberKoiRipplePhase(runState, InkTheme.motion.enabled)
    val color = InkTheme.colors.jade
    Canvas(modifier = modifier.fillMaxSize()) {
        if (ringRadius <= 0f) return@Canvas
        if (runState == PomodoroRunState.IDLE) {
            drawCircle(
                color = color.copy(alpha = 0.08f),
                radius = ringRadius * 1.08f,
                center = center,
                style = Stroke(width = 1.4f),
            )
            return@Canvas
        }

        val farthestX = max(center.x, size.width - center.x)
        val farthestY = max(center.y, size.height - center.y)
        val viewportRadius = hypot(farthestX, farthestY).coerceAtLeast(ringRadius)
        repeat(5) { index ->
            val localPhase = (phase + index / 5f) % 1f
            val alpha = koiRippleAlpha(localPhase)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = ringRadius + (viewportRadius - ringRadius) * localPhase,
                center = center,
                style = Stroke(width = 1.1f + alpha / 0.14f * 1.2f),
            )
        }
    }
}

@Composable
private fun rememberKoiRipplePhase(
    runState: PomodoroRunState,
    enabled: Boolean,
): Float {
    val phase = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(runState, enabled) {
        when {
            runState == PomodoroRunState.IDLE -> phase.snapTo(0f)
            runState == PomodoroRunState.RUNNING && enabled -> {
                while (true) {
                    val remaining = (1f - phase.value).coerceIn(0f, 1f)
                    phase.animateTo(
                        1f,
                        tween(
                            durationMillis = (5_600 * remaining).toInt().coerceAtLeast(1),
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
