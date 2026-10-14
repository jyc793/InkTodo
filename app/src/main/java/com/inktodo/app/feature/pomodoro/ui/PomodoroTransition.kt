package com.inktodo.app.feature.pomodoro.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Starts chrome opacity and immersive layout movement in the same frame, in either direction. */
internal suspend fun runPomodoroPairedTransition(
    fade: suspend () -> Unit,
    move: suspend () -> Unit,
) = coroutineScope {
    launch { fade() }
    launch { move() }
    Unit
}

internal fun disappearingChromeTranslationY(
    viewportHeight: Float,
    progress: Float,
): Float = viewportHeight.coerceAtLeast(0f) * 0.12f * progress.coerceIn(0f, 1f)
