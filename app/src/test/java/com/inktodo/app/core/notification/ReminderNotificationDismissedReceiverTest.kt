package com.inktodo.app.core.notification

import com.inktodo.app.core.alarm.ReminderSnapshotKind
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationDismissedReceiverTest {
    private val now = Instant.parse("2026-08-25T09:00:00Z")

    @Test
    fun onlyAnActiveRangeIsRestoredAfterUserDismissal() {
        val active = snapshot(
            start = "2026-08-25T08:00:00Z",
            end = "2026-08-25T10:00:00Z",
        )
        val future = snapshot(
            start = "2026-08-25T10:00:00Z",
            end = "2026-08-25T11:00:00Z",
        )
        val ended = snapshot(
            start = "2026-08-25T07:00:00Z",
            end = "2026-08-25T09:00:00Z",
        )

        assertTrue(active.isActiveRangeAt(now))
        assertFalse(future.isActiveRangeAt(now))
        assertFalse(ended.isActiveRangeAt(now))
        assertFalse(active.copy(kind = ReminderSnapshotKind.AT_TIME).isActiveRangeAt(now))
    }

    private fun snapshot(start: String, end: String) = ScheduledTaskSnapshot(
        taskId = 1,
        title = "持续验证",
        kind = ReminderSnapshotKind.TIME_RANGE,
        startAt = Instant.parse(start),
        endAt = Instant.parse(end),
    )
}
