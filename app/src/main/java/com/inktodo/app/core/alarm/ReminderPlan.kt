package com.inktodo.app.core.alarm

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Duration
import java.time.Instant

enum class ReminderAlarmKind(val code: Int) {
    PRE_REMINDER(1),
    FORMAL_REMINDER(2),
    RANGE_START(3),
    RANGE_END(4),
    RANGE_RENEW(5),
}

enum class ReminderSnapshotKind {
    AT_TIME,
    TIME_RANGE,
}

data class ScheduledTaskSnapshot(
    val taskId: Long,
    val title: String,
    val kind: ReminderSnapshotKind,
    val startAt: Instant,
    val endAt: Instant? = null,
    val useSystemAlarm: Boolean = false,
    val note: String? = null,
)

data class PlannedReminderAlarm(
    val kind: ReminderAlarmKind,
    val triggerAt: Instant,
)

data class ReminderPlan(
    val alarms: List<PlannedReminderAlarm>,
    val startRangeImmediately: Boolean = false,
)

object ReminderPlanner {
    val PRE_REMINDER_OFFSET: Duration = Duration.ofMinutes(15)

    fun plan(snapshot: ScheduledTaskSnapshot, now: Instant): ReminderPlan = when (snapshot.kind) {
        ReminderSnapshotKind.AT_TIME -> planAtTime(snapshot, now)
        ReminderSnapshotKind.TIME_RANGE -> planTimeRange(snapshot, now)
    }

    private fun planAtTime(snapshot: ScheduledTaskSnapshot, now: Instant): ReminderPlan {
        if (!snapshot.startAt.isAfter(now)) return ReminderPlan(emptyList())
        val preReminderAt = snapshot.startAt.minus(PRE_REMINDER_OFFSET)
        return ReminderPlan(
            alarms = buildList {
                if (preReminderAt.isAfter(now)) {
                    add(PlannedReminderAlarm(ReminderAlarmKind.PRE_REMINDER, preReminderAt))
                }
                if (!snapshot.useSystemAlarm) {
                    add(PlannedReminderAlarm(ReminderAlarmKind.FORMAL_REMINDER, snapshot.startAt))
                }
            },
        )
    }

    private fun planTimeRange(snapshot: ScheduledTaskSnapshot, now: Instant): ReminderPlan {
        val endAt = requireNotNull(snapshot.endAt) { "Time range reminder requires an end time" }
        if (!endAt.isAfter(now) || !endAt.isAfter(snapshot.startAt)) return ReminderPlan(emptyList())
        if (!snapshot.startAt.isAfter(now)) {
            return ReminderPlan(
                alarms = buildList {
                    add(PlannedReminderAlarm(ReminderAlarmKind.RANGE_END, endAt))
                    renewalAfter(now, endAt)?.let(::add)
                },
                startRangeImmediately = true,
            )
        }
        return ReminderPlan(
            alarms = buildList {
                add(PlannedReminderAlarm(ReminderAlarmKind.RANGE_START, snapshot.startAt))
                add(PlannedReminderAlarm(ReminderAlarmKind.RANGE_END, endAt))
                renewalAfter(snapshot.startAt, endAt)?.let(::add)
            },
        )
    }

    private fun renewalAfter(anchor: Instant, endAt: Instant): PlannedReminderAlarm? =
        anchor.plus(RANGE_RENEWAL_INTERVAL)
            .takeIf(endAt::isAfter)
            ?.let { PlannedReminderAlarm(ReminderAlarmKind.RANGE_RENEW, it) }

    val RANGE_RENEWAL_INTERVAL: Duration = Duration.ofHours(48)
}

fun TodoTask.toScheduledSnapshot(): ScheduledTaskSnapshot? {
    if (isCompleted) return null
    return when (val configuredReminder = reminder) {
        null -> null
        is TaskReminder.AtTime -> ScheduledTaskSnapshot(
            taskId = id,
            title = title,
            kind = ReminderSnapshotKind.AT_TIME,
            startAt = configuredReminder.startAt,
            useSystemAlarm = configuredReminder.useSystemAlarm,
            note = note?.trim()?.takeIf(String::isNotEmpty),
        )
        is TaskReminder.TimeRange -> if (configuredReminder.isManuallyStopped) null else ScheduledTaskSnapshot(
            taskId = id,
            title = title,
            kind = ReminderSnapshotKind.TIME_RANGE,
            startAt = configuredReminder.startAt,
            endAt = configuredReminder.endAt,
            note = note?.trim()?.takeIf(String::isNotEmpty),
        )
    }
}
