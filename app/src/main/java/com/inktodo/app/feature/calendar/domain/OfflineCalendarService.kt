package com.inktodo.app.feature.calendar.domain

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
)

enum class OfficialDayKind { HOLIDAY, WORKDAY }

data class CalendarDayDecoration(
    val lunarText: String,
    val fullLunarText: String,
    val shortLabel: String,
    val fullLabels: List<String>,
    val officialDayKind: OfficialDayKind? = null,
    val specialDateIds: List<Long> = emptyList(),
)

data class OfficialDayInfo(
    val kind: OfficialDayKind,
    val label: String,
    val scheduleYear: Int,
    val sourceTitle: String,
    val sourceUrl: String,
)

internal fun matchesLunarTarget(
    current: LunarDate,
    tomorrow: LunarDate,
    requestedMonth: Int,
    requestedDay: Int,
    requestedLeapMonth: Boolean,
    requestedLeapMonthExists: Boolean,
): Boolean {
    if (current.month != requestedMonth) return false
    if (requestedLeapMonth && requestedLeapMonthExists) {
        if (!current.isLeapMonth) return false
    } else if (current.isLeapMonth) {
        return false
    }
    if (current.day == requestedDay) return true
    return requestedDay == 30 && current.day == 29 &&
        (tomorrow.month != current.month || tomorrow.isLeapMonth != current.isLeapMonth)
}

class OfflineCalendarService(
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    private val lunarCache = ConcurrentHashMap<LocalDate, LunarDate>()
    private val leapMonthCache = ConcurrentHashMap<Pair<Int, Int>, Boolean>()

    fun lunar(date: LocalDate): LunarDate = lunarCache.getOrPut(date) {
        val calendar = ChineseCalendar(
            TimeZone.getTimeZone(zoneId.id),
            Locale.CHINA,
        ).apply {
            timeInMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        LunarDate(
            year = calendar.get(Calendar.EXTENDED_YEAR) - CHINESE_YEAR_OFFSET,
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            isLeapMonth = calendar.get(Calendar.IS_LEAP_MONTH) == 1,
        )
    }

    fun decorate(date: LocalDate, specialDates: List<SpecialDate> = emptyList()): CalendarDayDecoration {
        val lunar = lunar(date)
        val specialMatches = specialDates.filter { matches(it, date) }
        val official = OfficialHolidayScheduleData.days[date]
        val labels = buildList {
            addAll(specialMatches.map(SpecialDate::title))
            official?.let { add(it.label) }
            traditionalFestival(date, lunar)?.let(::add)
            solarTerm(date)?.let(::add)
            gregorianFestivals[date.monthValue to date.dayOfMonth]?.let(::add)
        }.distinct()
        val lunarText = formatLunar(lunar)
        return CalendarDayDecoration(
            lunarText = lunarText,
            fullLunarText = fullLunarDescription(date),
            shortLabel = labels.firstOrNull() ?: lunarText,
            fullLabels = labels,
            officialDayKind = official?.kind,
            specialDateIds = specialMatches.map(SpecialDate::id),
        )
    }

    fun decorations(month: YearMonth, specialDates: List<SpecialDate>): Map<LocalDate, CalendarDayDecoration> =
        (1..month.lengthOfMonth()).associate { day ->
            val date = month.atDay(day)
            date to decorate(date, specialDates)
        }

    fun matches(value: SpecialDate, date: LocalDate): Boolean = when (value.repeatMode) {
        SpecialDateRepeatMode.ONCE -> value.gregorianDate == date
        SpecialDateRepeatMode.ANNUAL_GREGORIAN -> {
            val anchor = value.gregorianDate ?: return false
            val resolvedDay = anchor.dayOfMonth.coerceAtMost(YearMonth.from(date).lengthOfMonth())
            date.monthValue == anchor.monthValue && date.dayOfMonth == resolvedDay
        }
        SpecialDateRepeatMode.ANNUAL_LUNAR -> matchesAnnualLunar(value, date)
    }

    fun nextOccurrence(value: SpecialDate, from: LocalDate, maxDays: Long = 400): LocalDate? {
        if (value.repeatMode == SpecialDateRepeatMode.ONCE) {
            return value.gregorianDate?.takeUnless { it.isBefore(from) }
        }
        return generateSequence(from) { it.plusDays(1) }
            .take((maxDays + 1).toInt())
            .firstOrNull { matches(value, it) }
    }

    fun fullLunarDescription(date: LocalDate): String {
        val value = lunar(date)
        val stem = heavenlyStems[Math.floorMod(value.year - 4, heavenlyStems.size)]
        val branch = earthlyBranches[Math.floorMod(value.year - 4, earthlyBranches.size)]
        val zodiac = zodiacAnimals[Math.floorMod(value.year - 4, zodiacAnimals.size)]
        return "$stem$branch${zodiac}年 ${if (value.isLeapMonth) "闰" else ""}${monthNames[value.month - 1]}月${dayNames[value.day - 1]}"
    }

    private fun matchesAnnualLunar(value: SpecialDate, date: LocalDate): Boolean {
        val month = value.lunarMonth ?: return false
        val day = value.lunarDay ?: return false
        val current = lunar(date)
        return matchesLunarTarget(
            current = current,
            tomorrow = lunar(date.plusDays(1)),
            requestedMonth = month,
            requestedDay = day,
            requestedLeapMonth = value.lunarIsLeapMonth,
            requestedLeapMonthExists = value.lunarIsLeapMonth && hasLeapMonth(current.year, month),
        )
    }

    private fun hasLeapMonth(lunarYear: Int, month: Int): Boolean {
        return leapMonthCache.getOrPut(lunarYear to month) {
            val approximateStart = LocalDate.of(lunarYear - 1, 11, 1)
            generateSequence(approximateStart) { it.plusDays(1) }
                .take(520)
                .map(::lunar)
                .any { it.year == lunarYear && it.month == month && it.isLeapMonth }
        }
    }

    private fun traditionalFestival(date: LocalDate, value: LunarDate): String? {
        lunarFestivals[value.month to value.day]?.let { return it }
        val tomorrow = lunar(date.plusDays(1))
        return "除夕".takeIf {
            value.month == 12 && tomorrow.month == 1 && tomorrow.day == 1
        }
    }

    private fun solarTerm(date: LocalDate): String? {
        if (date.year !in 1901..2100) return null
        return solarTermNames.indices.firstNotNullOfOrNull { index ->
            solarTermDate(date.year, index).takeIf { it == date }?.let { solarTermNames[index] }
        }
    }

    private fun solarTermDate(year: Int, index: Int): LocalDate {
        val elapsed = ((year - 1900) * TROPICAL_YEAR_MILLIS + solarTermMinutes[index] * 60_000.0).roundToLong()
        return SOLAR_TERM_EPOCH.plusMillis(elapsed).atZone(zoneId).toLocalDate()
    }

    private fun formatLunar(value: LunarDate): String =
        if (value.day == 1) "${if (value.isLeapMonth) "闰" else ""}${monthNames[value.month - 1]}月"
        else dayNames[value.day - 1]

    companion object {
        val LAST_OFFICIAL_SCHEDULE_YEAR: Int = OfficialHolidayScheduleData.coveredYears.maxOrNull() ?: 0
        private const val CHINESE_YEAR_OFFSET = 2637
        private const val TROPICAL_YEAR_MILLIS = 31_556_925_974.7
        private val SOLAR_TERM_EPOCH = Instant.parse("1900-01-06T02:05:00Z")
        private val monthNames = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        private val dayNames = listOf(
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
        )
        private val heavenlyStems = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
        private val earthlyBranches = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
        private val zodiacAnimals = listOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
        private val lunarFestivals = mapOf(
            (1 to 1) to "春节", (1 to 15) to "元宵节", (2 to 2) to "龙抬头",
            (5 to 5) to "端午节", (7 to 7) to "七夕", (7 to 15) to "中元节",
            (8 to 15) to "中秋节", (9 to 9) to "重阳节", (12 to 8) to "腊八节",
        )
        private val gregorianFestivals = mapOf(
            (1 to 1) to "元旦", (2 to 14) to "情人节", (3 to 8) to "妇女节",
            (3 to 12) to "植树节", (5 to 1) to "劳动节", (5 to 4) to "青年节",
            (6 to 1) to "儿童节", (7 to 1) to "建党节", (8 to 1) to "建军节",
            (9 to 3) to "抗战胜利纪念日", (9 to 10) to "教师节", (9 to 30) to "烈士纪念日",
            (10 to 1) to "国庆节", (12 to 13) to "国家公祭日", (12 to 25) to "圣诞节",
        )
        private val solarTermNames = listOf(
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
            "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至",
        )
        private val solarTermMinutes = intArrayOf(
            0, 21208, 42467, 63836, 85337, 107014, 128867, 150921,
            173149, 195551, 218072, 240693, 263343, 285989, 308563, 331033,
            353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758,
        )
        val officialDays: Map<LocalDate, OfficialDayKind> =
            OfficialHolidayScheduleData.days.mapValues { it.value.kind }
    }
}
