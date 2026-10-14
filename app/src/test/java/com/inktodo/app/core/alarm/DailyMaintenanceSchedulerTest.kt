package com.inktodo.app.core.alarm

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyMaintenanceSchedulerTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test fun beforeMaintenanceSchedulesToday() {
        assertEquals(
            Instant.parse("2026-10-09T16:05:00Z"),
            DailyMaintenanceScheduler.nextTriggerAt(
                Instant.parse("2026-10-09T16:01:00Z"), shanghai,
            ),
        )
    }

    @Test fun afterMaintenanceSchedulesTomorrow() {
        assertEquals(
            Instant.parse("2026-10-10T16:05:00Z"),
            DailyMaintenanceScheduler.nextTriggerAt(
                Instant.parse("2026-10-09T16:05:00Z"), shanghai,
            ),
        )
    }
}
