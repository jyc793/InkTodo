package com.inktodo.app.core.widget

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetTaskContentFactoryTest {
    private val date = LocalDate.of(2026, 8, 25)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun onlyPendingTasksAreCountedSortedAndLimited() {
        val tasks = listOf(
            task(1, "无提醒", null, "2026-08-25T01:00:00Z"),
            task(2, "下午", TaskReminder.AtTime(Instant.parse("2026-08-25T06:00:00Z")), "2026-08-25T00:00:00Z"),
            task(3, "已完成", TaskReminder.AtTime(Instant.parse("2026-08-25T02:00:00Z")), "2026-08-25T00:00:00Z", completed = true),
            task(4, "上午", TaskReminder.AtTime(Instant.parse("2026-08-25T01:00:00Z")), "2026-08-25T00:00:00Z"),
        )

        val content = WidgetTaskContentFactory.create(tasks, WidgetLayoutSpec(2, false), zone)

        assertEquals(3, content.totalPendingCount)
        assertEquals(listOf("上午", "下午"), content.rows.map(WidgetTaskRow::title))
        assertEquals(listOf("09:00", "14:00"), content.rows.map(WidgetTaskRow::timeLabel))
    }

    @Test
    fun rangeAndAllDayLabelsAreHonestAndNotesAreTrimmed() {
        val content = WidgetTaskContentFactory.create(
            listOf(
                task(
                    1,
                    "时间段",
                    TaskReminder.TimeRange(
                        Instant.parse("2026-08-25T06:00:00Z"),
                        Instant.parse("2026-08-25T08:00:00Z"),
                    ),
                    "2026-08-25T00:00:00Z",
                    note = "  验证 Executor  ",
                ),
                task(2, "全天", null, "2026-08-25T01:00:00Z", note = "   "),
            ),
            WidgetLayoutSpec(5, true),
            zone,
        )

        assertEquals("14:00–16:00", content.rows[0].timeLabel)
        assertEquals("验证 Executor", content.rows[0].note)
        assertEquals("全天", content.rows[1].timeLabel)
        assertNull(content.rows[1].note)
    }

    @Test
    fun crossDateRangeLabelIncludesBothDates() {
        val content = WidgetTaskContentFactory.create(
            listOf(
                task(
                    8,
                    "跨日",
                    TaskReminder.TimeRange(
                        Instant.parse("2026-08-25T06:00:00Z"),
                        Instant.parse("2026-08-28T08:00:00Z"),
                    ),
                    "2026-08-25T00:00:00Z",
                ),
            ),
            WidgetLayoutSpec(2, false),
            zone,
        )

        assertEquals("8/25 14:00–8/28 16:00", content.rows.single().timeLabel)
    }

    private fun task(
        id: Long,
        title: String,
        reminder: TaskReminder?,
        createdAt: String,
        completed: Boolean = false,
        note: String? = null,
    ) = TodoTask(
        id = id,
        title = title,
        note = note,
        date = date,
        reminder = reminder,
        isCompleted = completed,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(createdAt),
    )
}
