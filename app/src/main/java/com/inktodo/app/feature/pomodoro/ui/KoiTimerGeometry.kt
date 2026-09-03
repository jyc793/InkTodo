package com.inktodo.app.feature.pomodoro.ui

import com.inktodo.app.core.datastore.PomodoroRunState

internal fun koiPositionAngleDegrees(progress: Float): Float =
    -90f + progress.coerceIn(0f, 1f) * 360f

/**
 * The generated koi sprite faces upward. This rotation aligns it to the clockwise
 * tangent of the timer ring at every progress position.
 */
internal fun koiSpriteRotationDegrees(progress: Float): Float =
    koiPositionAngleDegrees(progress) + 180f

/**
 * Resolves the koi position from the persisted deadline, not from the once-per-second
 * clock text refresh. This keeps its progress continuous at the display frame rate.
 */
internal fun koiElapsedProgressAt(
    runState: PomodoroRunState,
    remainingMillis: Long,
    durationMillis: Long,
    targetEndEpochMillis: Long,
    nowEpochMillis: Long,
): Float {
    if (runState == PomodoroRunState.IDLE || durationMillis <= 0L) return 0f
    val resolvedRemaining = if (runState == PomodoroRunState.RUNNING && targetEndEpochMillis > 0L) {
        (targetEndEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    } else {
        remainingMillis.coerceAtLeast(0L)
    }
    return (1f - resolvedRemaining.toFloat() / durationMillis).coerceIn(0f, 1f)
}
