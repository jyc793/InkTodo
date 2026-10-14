package com.inktodo.app.feature.pomodoro.ui

import com.inktodo.app.core.datastore.PomodoroRunState
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.min

internal fun koiPositionAngleDegrees(progress: Float): Float =
    -90f + progress.coerceIn(0f, 1f) * 360f

/**
 * The generated koi sprite faces upward. This rotation aligns it to the clockwise
 * tangent of the timer ring at every progress position.
 */
internal fun koiSpriteRotationDegrees(progress: Float): Float =
    koiPositionAngleDegrees(progress) + 180f

/** Rotates the retained wake texture so its dense edge leads and its sparse edge trails. */
internal fun koiWakeTextureRotationDegrees(trailAngle: Float): Float = trailAngle + 270f

/**
 * Continuous wake envelope: a short soft birth, a visible body, then a long fade to
 * strict zero. Both ends are zero, so a looping layer cannot flash at its reset.
 */
internal fun koiWakeAlpha(phase: Float): Float {
    val normalized = phase.coerceIn(0f, 1f)
    return when {
        normalized <= 0f || normalized >= 0.92f -> 0f
        normalized < 0.12f -> smoothStep(normalized / 0.12f)
        normalized <= 0.42f -> 1f
        else -> 1f - smoothStep((normalized - 0.42f) / 0.50f)
    }
}

internal fun koiRippleAlpha(phase: Float): Float {
    val normalized = phase.coerceIn(0f, 1f)
    if (normalized <= 0f || normalized >= 1f) return 0f
    return sin(normalized * Math.PI).toFloat().pow(1.35f) * 0.14f
}

internal fun immersiveTimerDiameter(
    availableWidth: Float,
    availableHeight: Float,
    maxDiameter: Float,
): Float = min(min(availableWidth * 0.84f, availableHeight * 0.58f), maxDiameter)

internal fun ripplePhaseAfterElapsed(
    runState: PomodoroRunState,
    currentPhase: Float,
    elapsedMillis: Long,
    cycleMillis: Long = 5_600L,
): Float = when (runState) {
    PomodoroRunState.IDLE -> 0f
    PomodoroRunState.PAUSED -> currentPhase.coerceIn(0f, 1f)
    PomodoroRunState.RUNNING ->
        ((currentPhase + elapsedMillis.coerceAtLeast(0L).toFloat() / cycleMillis) % 1f)
}

private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal data class KoiLayerTranslation(val x: Float, val y: Float)

/**
 * Places the sprite's mouth — rather than its image center — exactly on the timer ring.
 * The v2 sprite is centered horizontally and its mouth sits 6.2% down from the top edge.
 */
internal fun koiLayerTranslationForMouthAnchor(
    progress: Float,
    ringRadius: Float,
    fishWidth: Float,
    fishHeight: Float,
    mouthXFraction: Float = 0.5f,
    mouthYFraction: Float = 0.062f,
): KoiLayerTranslation {
    val positionRadians = Math.toRadians(koiPositionAngleDegrees(progress).toDouble())
    val targetX = kotlin.math.cos(positionRadians).toFloat() * ringRadius
    val targetY = kotlin.math.sin(positionRadians).toFloat() * ringRadius

    val localMouthX = (mouthXFraction - 0.5f) * fishWidth
    val localMouthY = (mouthYFraction - 0.5f) * fishHeight
    val rotationRadians = Math.toRadians(koiSpriteRotationDegrees(progress).toDouble())
    val rotatedMouthX = kotlin.math.cos(rotationRadians).toFloat() * localMouthX -
        kotlin.math.sin(rotationRadians).toFloat() * localMouthY
    val rotatedMouthY = kotlin.math.sin(rotationRadians).toFloat() * localMouthX +
        kotlin.math.cos(rotationRadians).toFloat() * localMouthY
    return KoiLayerTranslation(
        x = targetX - rotatedMouthX,
        y = targetY - rotatedMouthY,
    )
}

/** Keeps the head stable while a travelling wave grows smoothly toward the tail. */
internal fun koiBodyWaveOffset(
    bodyPosition: Float,
    swimPhase: Float,
    availableWidth: Float,
): Float {
    val normalizedBodyPosition = bodyPosition.coerceIn(0f, 1f)
    val tailInfluence = normalizedBodyPosition.pow(2.15f)
    return sin(
        swimPhase * (Math.PI * 2f).toFloat() - normalizedBodyPosition * 5.1f,
    ) * availableWidth * 0.12f * tailInfluence
}

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
