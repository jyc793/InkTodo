package com.inktodo.app.core.widget

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WidgetTaskRow(
    val taskId: Long,
    val title: String,
    val timeLabel: String,
    val note: String?,
)

data class WidgetTaskContent(
    val totalPendingCount: Int,
    val rows: List<WidgetTaskRow>,
)

object WidgetTaskContentFactory {
    fun create(
        tasks: List<TodoTask>,
        spec: WidgetLayoutSpec,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): WidgetTaskContent {
        val pending = tasks
            .asSequence()
            .filterNot(TodoTask::isCompleted)
            .sortedWith(compareBy<TodoTask>({ it.reminder?.startAt ?: Instant.MAX }, TodoTask::createdAt))
            .toList()
        return WidgetTaskContent(
            totalPendingCount = pending.size,
            rows = pending.take(spec.maxTaskRows).map { task ->
                WidgetTaskRow(
                    taskId = task.id,
                    title = task.title,
                    timeLabel = task.reminder.toTimeLabel(zoneId),
                    note = task.note?.trim()?.takeIf(String::isNotEmpty),
                )
            },
        )
    }

    private fun TaskReminder?.toTimeLabel(zoneId: ZoneId): String = when (this) {
        null -> "全天"
        is TaskReminder.AtTime -> TIME_FORMAT.format(startAt.atZone(zoneId))
        is TaskReminder.TimeRange -> {
            val start = startAt.atZone(zoneId)
            val end = endAt.atZone(zoneId)
            if (start.toLocalDate() == end.toLocalDate()) {
                "${TIME_FORMAT.format(start)}–${TIME_FORMAT.format(end)}"
            } else {
                "${DATE_TIME_FORMAT.format(start)}–${DATE_TIME_FORMAT.format(end)}"
            }
        }
    }

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm")
}
