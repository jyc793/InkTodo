package com.inktodo.app.core.ui.theme

import androidx.compose.ui.unit.dp

/** Slow, ambient motion tokens kept separate from routine interaction motion. */
object InkAtmosphere {
    const val FarMistMillis = 36_000
    const val MidMistMillis = 26_000
    const val NearMistMillis = 18_000
    const val CalendarMoonMillis = 12_000
    const val PomodoroWaterMillis = 5_600
    const val CompletedHazeMillis = 18_000
    const val CinnabarBreathMillis = 4_800

    val FarMistDrift = 42.dp
    val MidMistDrift = 62.dp
    val NearMistDrift = 84.dp
}
