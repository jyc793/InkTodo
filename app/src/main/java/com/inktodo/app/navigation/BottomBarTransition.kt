package com.inktodo.app.navigation

internal fun reserveBottomBarContentSpace(
    showBottomBar: Boolean,
    isPomodoroRoute: Boolean,
): Boolean = showBottomBar && !isPomodoroRoute

internal fun bottomBarTranslationY(
    measuredHeight: Int,
    slideProgress: Float,
): Float = measuredHeight.coerceAtLeast(0) * slideProgress.coerceIn(0f, 1f)
