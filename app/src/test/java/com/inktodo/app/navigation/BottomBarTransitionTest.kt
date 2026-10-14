package com.inktodo.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarTransitionTest {
    @Test
    fun `pomodoro viewport never reserves animated bottom bar space`() {
        assertFalse(
            reserveBottomBarContentSpace(
                showBottomBar = true,
                isPomodoroRoute = true,
            ),
        )
        assertTrue(
            reserveBottomBarContentSpace(
                showBottomBar = true,
                isPomodoroRoute = false,
            ),
        )
    }

    @Test
    fun `bottom bar translation is continuous and clamped`() {
        assertEquals(0f, bottomBarTranslationY(100, -1f), 0.0001f)
        assertEquals(50f, bottomBarTranslationY(100, 0.5f), 0.0001f)
        assertEquals(100f, bottomBarTranslationY(100, 2f), 0.0001f)
    }
}
