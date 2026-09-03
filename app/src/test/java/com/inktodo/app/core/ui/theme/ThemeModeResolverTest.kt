package com.inktodo.app.core.ui.theme

import com.inktodo.app.core.model.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeResolverTest {
    @Test
    fun explicitModesOverrideSystemAndSystemModeFollowsIt() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemDarkTheme = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemDarkTheme = false))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemDarkTheme = false))
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemDarkTheme = true))
    }
}
