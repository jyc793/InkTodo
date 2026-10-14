package com.inktodo.app.feature.pomodoro.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * The floating timer deliberately composes the same timer and ripple layers as the in-app timer.
 * Its hosting overlay is non-touchable; a separate transparent window owns drag and double-tap.
 */
@Composable
internal fun FloatingPomodoroVisual(
    state: PomodoroUiState,
    ballDiameter: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val center = Offset(
            x = constraints.maxWidth / 2f,
            y = constraints.maxHeight / 2f,
        )
        val ringRadius = with(density) { ballDiameter.toPx() * 0.395f }
        KoiRippleField(
            center = center,
            ringRadius = ringRadius,
            runState = state.runState,
            modifier = Modifier.fillMaxSize(),
        )
        TimerMirror(
            state = state,
            minimal = true,
            modifier = Modifier.size(ballDiameter),
        )
    }
}
