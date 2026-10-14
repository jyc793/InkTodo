package com.inktodo.app.core.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class FormalReminderConfirmationTest {
    @Test
    fun `confirmation button exposes all three countdown seconds before confirming`() {
        assertEquals(3, FormalReminderActivity.CONFIRM_COUNTDOWN_SECONDS)
        assertEquals("3 秒后确认", formalConfirmationButtonText(3, confirming = false))
        assertEquals("2 秒后确认", formalConfirmationButtonText(2, confirming = false))
        assertEquals("1 秒后确认", formalConfirmationButtonText(1, confirming = false))
        assertEquals("正在确认…", formalConfirmationButtonText(null, confirming = true))
        assertEquals("确认", formalConfirmationButtonText(null, confirming = false))
    }
}
