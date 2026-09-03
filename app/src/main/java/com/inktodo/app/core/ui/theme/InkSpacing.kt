package com.inktodo.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class InkSpacing(
    val hairline: Dp = 1.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val section: Dp = 28.dp,
    val touchTarget: Dp = 48.dp,
    val controlHeight: Dp = 52.dp,
    val bottomBarHeight: Dp = 72.dp,
)
