package com.inktodo.app.feature.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCommandIssueNoticeGateTest {
    @Test
    fun everyRealIssueIsReturnedSoLaterReminderFailuresAreNotHidden() {
        val gate = TaskCommandIssueNoticeGate()
        val exact = setOf(TaskCommandIssue.EXACT_ALARM_UNAVAILABLE)

        assertEquals(exact, gate.takeUnseen(exact))
        assertEquals(exact, gate.takeUnseen(exact))
        assertEquals(
            setOf(
                TaskCommandIssue.EXACT_ALARM_UNAVAILABLE,
                TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE,
            ),
            gate.takeUnseen(
                setOf(
                    TaskCommandIssue.EXACT_ALARM_UNAVAILABLE,
                    TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE,
                ),
            ),
        )
    }
}
