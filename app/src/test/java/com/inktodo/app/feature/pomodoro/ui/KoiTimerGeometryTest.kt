package com.inktodo.app.feature.pomodoro.ui

import com.inktodo.app.core.datastore.PomodoroRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoiTimerGeometryTest {
    @Test
    fun wakeEnvelopeStartsAndEndsAtStrictZeroWithoutTailFloor() {
        assertEquals(0f, koiWakeAlpha(0f), 0.0001f)
        assertEquals(1f, koiWakeAlpha(0.2f), 0.0001f)
        assertTrue(koiWakeAlpha(0.8f) in 0f..1f)
        assertEquals(0f, koiWakeAlpha(0.92f), 0.0001f)
        assertEquals(0f, koiWakeAlpha(0.99f), 0.0001f)
        assertEquals(0f, koiWakeAlpha(1f), 0.0001f)
    }

    @Test
    fun wakeTextureIsFlippedHalfTurnFromPreviousTangentOrientation() {
        assertEquals(270f, koiWakeTextureRotationDegrees(0f), 0.0001f)
        assertEquals(360f, koiWakeTextureRotationDegrees(90f), 0.0001f)
    }

    @Test
    fun `concentric ripple is transparent at both ends and peaks at fourteen percent`() {
        assertEquals(0f, koiRippleAlpha(0f), 0.0001f)
        assertEquals(0.14f, koiRippleAlpha(0.5f), 0.0001f)
        assertEquals(0f, koiRippleAlpha(1f), 0.0001f)
    }

    @Test
    fun `paused ripple freezes while running ripple advances`() {
        assertEquals(
            0.37f,
            ripplePhaseAfterElapsed(PomodoroRunState.PAUSED, 0.37f, 2_800L),
            0.0001f,
        )
        assertEquals(
            0.5f,
            ripplePhaseAfterElapsed(PomodoroRunState.RUNNING, 0f, 2_800L),
            0.0001f,
        )
    }

    @Test
    fun `immersive ring diameter follows all three responsive caps`() {
        assertEquals(336f, immersiveTimerDiameter(400f, 1_000f, 620f), 0.0001f)
        assertEquals(406f, immersiveTimerDiameter(1_200f, 700f, 620f), 0.0001f)
        assertEquals(620f, immersiveTimerDiameter(1_200f, 1_200f, 620f), 0.0001f)
    }
    @Test
    fun `body wave keeps head stable and gives tail visible motion`() {
        assertEquals(0f, koiBodyWaveOffset(0f, 0.25f, 100f), 0.0001f)
        val firstTailPosition = koiBodyWaveOffset(1f, 0f, 100f)
        val laterTailPosition = koiBodyWaveOffset(1f, 0.5f, 100f)
        assertTrue(kotlin.math.abs(firstTailPosition) > 5f)
        assertTrue(firstTailPosition * laterTailPosition < 0f)
    }

    @Test
    fun `mouth anchor lands exactly on ring through a full lap`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val width = 72f
            val height = 108f
            val radius = 160f
            val translation = koiLayerTranslationForMouthAnchor(progress, radius, width, height)
            val localMouthX = 0f
            val localMouthY = (0.062f - 0.5f) * height
            val rotation = Math.toRadians(koiSpriteRotationDegrees(progress).toDouble())
            val transformedMouthX = translation.x - kotlin.math.sin(rotation).toFloat() * localMouthY
            val transformedMouthY = translation.y + kotlin.math.cos(rotation).toFloat() * localMouthY
            val position = Math.toRadians(koiPositionAngleDegrees(progress).toDouble())
            assertEquals(kotlin.math.cos(position).toFloat() * radius, transformedMouthX, 0.001f)
            assertEquals(kotlin.math.sin(position).toFloat() * radius, transformedMouthY, 0.001f)
        }
    }

    @Test
    fun `koi completes exactly one clockwise lap`() {
        assertEquals(-90f, koiPositionAngleDegrees(0f))
        assertEquals(90f, koiPositionAngleDegrees(0.5f))
        assertEquals(270f, koiPositionAngleDegrees(1f))
    }

    @Test
    fun `upward sprite follows clockwise tangent`() {
        assertEquals(90f, koiSpriteRotationDegrees(0f))
        assertEquals(270f, koiSpriteRotationDegrees(0.5f))
    }

    @Test
    fun `elapsed progress starts at zero and follows remaining real time`() {
        val idle = PomodoroUiState(
            runState = PomodoroRunState.IDLE,
            focusMinutes = 10,
            remainingMillis = 5 * 60_000L,
        )
        val halfway = idle.copy(runState = PomodoroRunState.PAUSED)
        val finished = idle.copy(
            runState = PomodoroRunState.RUNNING,
            remainingMillis = 0L,
        )

        assertEquals(0f, idle.elapsedProgress)
        assertEquals(0.5f, halfway.elapsedProgress)
        assertEquals(1f, finished.elapsedProgress)
    }

    @Test
    fun `ceil display starts at forty five minutes then reaches forty four fifty nine`() {
        assertEquals("45:00", formatRemaining(45L * 60_000L))
        assertEquals("45:00", formatRemaining(45L * 60_000L - 1L))
        assertEquals("44:59", formatRemaining(45L * 60_000L - 1_000L))
    }

    @Test
    fun `running koi progress uses the exact deadline between text clock ticks`() {
        val start = 1_000_000L
        val duration = 60_000L

        assertEquals(
            0.125f,
            koiElapsedProgressAt(
                runState = PomodoroRunState.RUNNING,
                remainingMillis = 53_000L,
                durationMillis = duration,
                targetEndEpochMillis = start + duration,
                nowEpochMillis = start + 7_500L,
            ),
        )
        assertEquals(
            0.875f,
            koiElapsedProgressAt(
                runState = PomodoroRunState.RUNNING,
                remainingMillis = 8_000L,
                durationMillis = duration,
                targetEndEpochMillis = start + duration,
                nowEpochMillis = start + 52_500L,
            ),
        )
    }

    @Test
    fun `paused koi progress holds its persisted remaining time`() {
        assertEquals(
            0.5f,
            koiElapsedProgressAt(
                runState = PomodoroRunState.PAUSED,
                remainingMillis = 30_000L,
                durationMillis = 60_000L,
                targetEndEpochMillis = 0L,
                nowEpochMillis = 99_999L,
            ),
        )
    }

    @Test
    fun `full focus exit requires two encouragements then pin stage`() {
        val first = nextFullFocusExitStage(0)
        val second = nextFullFocusExitStage(first)
        val pin = nextFullFocusExitStage(second)

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(3, pin)
        assertEquals(3, nextFullFocusExitStage(pin))
    }
}
