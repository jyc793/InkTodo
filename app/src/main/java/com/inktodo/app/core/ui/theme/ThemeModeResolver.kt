package com.inktodo.app.core.ui.theme

import com.inktodo.app.core.model.ThemeMode

fun ThemeMode.resolveDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
