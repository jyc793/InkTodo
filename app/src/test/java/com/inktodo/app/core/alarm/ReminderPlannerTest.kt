package com.inktodo.app.core.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val now = Instant.parse("2026-08-25T09:00:00Z")

    @Test
    fun atTimeMoreThanFifteenMinutesAwayCreatesPreAndFormalAlarms() {
        val plan = ReminderPlanner.plan(atTime("2026-08-25T10:00:00Z"), now)

        assertEquals(
            listOf(
                PlannedReminderAlarm(ReminderAlarmKind.PRE_REMINDER, Instant.parse("2026-08-25T09:45:00Z")),
                PlannedReminderAlarm(ReminderAlarmKind.FORMAL_REMINDER, Instant.parse("2026-08-25T10:00:00Z")),
            ),
            plan.alarms,
        )
        assertFalse(plan.startRangeImmediately)
    }

    @Test
    fun atTimeLessThanFifteenMinutesAwayCreatesOnlyFormalAlarm() {
        val plan = ReminderPlanner.plan(atTime("2026-08-25T09:05:00Z"), now)

        assertEquals(
            listOf(PlannedReminderAlarm(ReminderAlarmKind.FORMAL_REMINDER, Instant.parse("2026-08-25T09:05:00Z"))),
            plan.alarms,
        )
    }

    @Test
    fun pastAtTimeCreatesNoAlarm() {
        assertTrue(ReminderPlanner.plan(atTime("2026-08-25T08:59:59Z"), now).alarms.isEmpty())
    }

    @Test
    fun futureRangeCreatesStartAndEndAlarms() {
        val plan = ReminderPlanner.plan(
            range("2026-08-25T10:00:00Z", "2026-08-25T12:00:00Z"),
            now,
        )

        assertEquals(
            listOf(ReminderAlarmKind.RANGE_START, ReminderAlarmKind.RANGE_END),
            plan.alarms.map(PlannedReminderAlarm::kind),
        )
        assertFalse(plan.startRangeImmediately)
    }

    @Test
    fun activeRangeStartsImmediatelyAndKeepsOnlyEndAlarm() {
        val plan = ReminderPlanner.plan(
            range("2026-08-25T08:30:00Z", "2026-08-25T10:00:00Z"),
            now,
        )

        assertTrue(plan.startRangeImmediately)
        assertEquals(
            listOf(PlannedReminderAlarm(ReminderAlarmKind.RANGE_END, Instant.parse("2026-08-25T10:00:00Z"))),
            plan.alarms,
        )
    }

    @Test
    fun endedOrInvalidRangeCreatesNoAlarm() {
        assertTrue(
            ReminderPlanner.plan(
                range("2026-08-25T08:00:00Z", "2026-08-25T09:00:00Z"),
                now,
            ).alarms.isEmpty(),
        )
        assertTrue(
            ReminderPlanner.plan(
                range("2026-08-25T10:00:00Z", "2026-08-25T09:30:00Z"),
                now,
            ).alarms.isEmpty(),
        )
    }

    @Test
    fun crossWeekRangeUsesItsExactStartAndEndInstants() {
        val plan = ReminderPlanner.plan(
            range("2026-08-27T10:00:00Z", "2026-09-03T16:30:00Z"),
            now,
        )

        assertEquals(
            listOf(
                PlannedReminderAlarm(ReminderAlarmKind.RANGE_START, Instant.parse("2026-08-27T10:00:00Z")),
                PlannedReminderAlarm(ReminderAlarmKind.RANGE_END, Instant.parse("2026-09-03T16:30:00Z")),
                PlannedReminderAlarm(ReminderAlarmKind.RANGE_RENEW, Instant.parse("2026-08-27T16:00:00Z")),
            ),
            plan.alarms,
        )
    }

    @Test
    fun systemClockTaskKeepsPreReminderButDoesNotDuplicateFormalAlarm() {
        val snapshot = atTime("2026-08-25T10:00:00Z").copy(useSystemAlarm = true)

        val plan = ReminderPlanner.plan(snapshot, now)

        assertEquals(
            listOf(PlannedReminderAlarm(ReminderAlarmKind.PRE_REMINDER, Instant.parse("2026-08-25T09:45:00Z"))),
            plan.alarms,
        )
    }

    @Test
    fun repeatingSystemClockTaskAdvancesAtFormalTime() {
        val snapshot = atTime("2026-08-25T10:00:00Z")
            .copy(useSystemAlarm = true, isRepeating = true)

        assertEquals(
            listOf(ReminderAlarmKind.PRE_REMINDER, ReminderAlarmKind.REPEAT_ADVANCE),
            ReminderPlanner.plan(snapshot, now).alarms.map(PlannedReminderAlarm::kind),
        )
    }

    @Test
    fun recurringRangeSchedulesOnlyNextIntervalAndNeverAtEnd() {
        val snapshot = range("2026-08-25T10:00:00Z", "2026-08-25T12:00:00Z")
            .copy(isRepeating = true, repeatIntervalMinutes = 30)
        val initial = ReminderPlanner.plan(snapshot, now)
        assertEquals(Instant.parse("2026-08-25T10:30:00Z"),
            initial.alarms.single { it.kind == ReminderAlarmKind.RANGE_INTERVAL }.triggerAt)
        val midRange = ReminderPlanner.plan(snapshot, Instant.parse("2026-08-25T10:34:00Z"))
        assertEquals(Instant.parse("2026-08-25T11:00:00Z"),
            midRange.alarms.single { it.kind == ReminderAlarmKind.RANGE_INTERVAL }.triggerAt)
        assertTrue(ReminderPlanner.plan(snapshot, Instant.parse("2026-08-25T11:45:00Z"))
            .alarms.none { it.kind == ReminderAlarmKind.RANGE_INTERVAL })
        assertTrue(ReminderPlanner.isIntervalTick(snapshot, Instant.parse("2026-08-25T11:00:00Z")))
        assertFalse(ReminderPlanner.isIntervalTick(snapshot, Instant.parse("2026-08-25T11:01:00Z")))
    }

    @Test
    fun specialDateRangeConfirmsAtSevenThenHourlyThroughTwentyThreeOnly() {
        val snapshot = range("2026-06-06T07:00:00Z", "2026-06-07T00:00:00Z")
            .copy(repeatIntervalMinutes = 60, confirmAtRangeStart = true)
        val initial = ReminderPlanner.plan(snapshot, Instant.parse("2026-06-06T06:00:00Z"))
        assertTrue(initial.alarms.any { it.kind == ReminderAlarmKind.RANGE_START && it.triggerAt == snapshot.startAt })
        assertEquals(
            Instant.parse("2026-06-06T08:00:00Z"),
            initial.alarms.single { it.kind == ReminderAlarmKind.RANGE_INTERVAL }.triggerAt,
        )
        val last = ReminderPlanner.intervalAfter(snapshot, Instant.parse("2026-06-06T22:30:00Z"))
        assertEquals(Instant.parse("2026-06-06T23:00:00Z"), last?.triggerAt)
        assertEquals(null, ReminderPlanner.intervalAfter(snapshot, Instant.parse("2026-06-06T23:00:00Z")))
    }

    private fun atTime(start: String) = ScheduledTaskSnapshot(
        taskId = 1,
        title = "上课",
        kind = ReminderSnapshotKind.AT_TIME,
        startAt = Instant.parse(start),
    )

    private fun range(start: String, end: String) = ScheduledTaskSnapshot(
        taskId = 2,
        title = "Executor 验证",
        kind = ReminderSnapshotKind.TIME_RANGE,
        startAt = Instant.parse(start),
        endAt = Instant.parse(end),
    )
}
