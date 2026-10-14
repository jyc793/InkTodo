package com.inktodo.app.feature.pomodoro.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroTransitionTest {
    @Test
    fun `entry fade and movement start concurrently`() {
        runBlocking {
            val fadeStarted = CompletableDeferred<Unit>()
            val moveStarted = CompletableDeferred<Unit>()

            withTimeout(1_000L) {
                runPomodoroPairedTransition(
                    fade = {
                        fadeStarted.complete(Unit)
                        moveStarted.await()
                    },
                    move = {
                        moveStarted.complete(Unit)
                        fadeStarted.await()
                    },
                )
            }
        }
    }

    @Test
    fun `exit fade and reverse movement start concurrently`() {
        runBlocking {
            val fadeStarted = CompletableDeferred<Unit>()
            val moveStarted = CompletableDeferred<Unit>()

            withTimeout(1_000L) {
                runPomodoroPairedTransition(
                    fade = {
                        fadeStarted.complete(Unit)
                        moveStarted.await()
                    },
                    move = {
                        moveStarted.complete(Unit)
                        fadeStarted.await()
                    },
                )
            }
        }
    }

    @Test
    fun `disappearing chrome follows the downward transition`() {
        assertEquals(0f, disappearingChromeTranslationY(1_000f, 0f), 0.0001f)
        assertEquals(60f, disappearingChromeTranslationY(1_000f, 0.5f), 0.0001f)
        assertEquals(120f, disappearingChromeTranslationY(1_000f, 1f), 0.0001f)
    }
}
