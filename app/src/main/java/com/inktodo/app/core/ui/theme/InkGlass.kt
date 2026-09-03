package com.inktodo.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class InkGlass(
    val cardAlpha: Float = 0.90f,
    val subtleCardAlpha: Float = 0.80f,
    val strongCardAlpha: Float = 0.97f,
    val navigationAlpha: Float = 0.86f,
    val edgeWidth: Dp = 1.dp,
    val strongEdgeWidth: Dp = 2.dp,
    val tonalLift: Dp = 0.dp,
    val shadowElevation: Dp = 2.dp,
    val pressedAlpha: Float = 0.78f,
    val highlightAlpha: Float = 0.30f,
)
