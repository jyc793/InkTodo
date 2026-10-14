package com.inktodo.app.feature.calendar.domain

import java.time.LocalDate

/**
 * Versioned, offline-only State Council holiday schedule data.
 * Future years are intentionally absent until an official notice is published.
 */
object OfficialHolidayScheduleData {
    data class Source(
        val year: Int,
        val title: String,
        val url: String,
    )

    val sources = listOf(
        Source(
            2025,
            "国务院办公厅关于2025年部分节假日安排的通知（国办发明电〔2024〕12号）",
            "https://www.gov.cn/zhengce/zhengceku/202411/content_6986383.htm",
        ),
        Source(
            2026,
            "国务院办公厅关于2026年部分节假日安排的通知（国办发明电〔2025〕7号）",
            "https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm",
        ),
    )
    val coveredYears: Set<Int> = sources.mapTo(linkedSetOf(), Source::year)

    val days: Map<LocalDate, OfficialDayInfo> = buildMap {
        fun source(year: Int) = sources.first { it.year == year }
        fun range(year: Int, start: String, end: String, label: String) {
            val metadata = source(year)
            var cursor = LocalDate.parse(start)
            val last = LocalDate.parse(end)
            while (!cursor.isAfter(last)) {
                put(cursor, OfficialDayInfo(OfficialDayKind.HOLIDAY, label, year, metadata.title, metadata.url))
                cursor = cursor.plusDays(1)
            }
        }
        fun work(year: Int, vararg values: String) {
            val metadata = source(year)
            values.forEach { value ->
                put(
                    LocalDate.parse(value),
                    OfficialDayInfo(OfficialDayKind.WORKDAY, "调休上班", year, metadata.title, metadata.url),
                )
            }
        }

        range(2025, "2025-01-01", "2025-01-01", "元旦假期")
        range(2025, "2025-01-28", "2025-02-04", "春节假期")
        work(2025, "2025-01-26", "2025-02-08")
        range(2025, "2025-04-04", "2025-04-06", "清明假期")
        range(2025, "2025-05-01", "2025-05-05", "劳动节假期")
        work(2025, "2025-04-27")
        range(2025, "2025-05-31", "2025-06-02", "端午假期")
        range(2025, "2025-10-01", "2025-10-08", "国庆中秋假期")
        work(2025, "2025-09-28", "2025-10-11")

        range(2026, "2026-01-01", "2026-01-03", "元旦假期")
        work(2026, "2026-01-04")
        range(2026, "2026-02-15", "2026-02-23", "春节假期")
        work(2026, "2026-02-14", "2026-02-28")
        range(2026, "2026-04-04", "2026-04-06", "清明假期")
        range(2026, "2026-05-01", "2026-05-05", "劳动节假期")
        work(2026, "2026-05-09")
        range(2026, "2026-06-19", "2026-06-21", "端午假期")
        range(2026, "2026-09-25", "2026-09-27", "中秋假期")
        range(2026, "2026-10-01", "2026-10-07", "国庆假期")
        work(2026, "2026-09-20", "2026-10-10")
    }

    fun shouldShowUpdateNotice(today: LocalDate): Boolean =
        today.year == coveredYears.maxOrNull() && today.plusYears(1).year !in coveredYears
}
