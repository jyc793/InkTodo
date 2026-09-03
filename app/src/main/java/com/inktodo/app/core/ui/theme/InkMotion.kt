package com.inktodo.app.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

@Immutable
data class InkMotionSpec(
    val enabled: Boolean,
    val quickMillis: Int = InkMotion.QuickMillis,
    val standardMillis: Int = InkMotion.StandardMillis,
    val slowMillis: Int = InkMotion.SlowMillis,
    val enterEasing: Easing = InkMotion.EnterEasing,
    val moveEasing: Easing = InkMotion.MoveEasing,
)

object InkMotion {
    const val PressMillis = 120
    const val QuickMillis = 160
    const val StandardMillis = 260
    const val SlowMillis = 480

    val EnterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val MoveEasing = CubicBezierEasing(0.25f, 1f, 0.50f, 1f)
}
