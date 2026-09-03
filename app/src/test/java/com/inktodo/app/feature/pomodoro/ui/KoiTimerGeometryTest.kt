package com.inktodo.app.feature.pomodoro.ui

import com.inktodo.app.core.datastore.PomodoroRunState
import org.junit.Assert.assertEquals
import org.junit.Test

class KoiTimerGeometryTest {
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
