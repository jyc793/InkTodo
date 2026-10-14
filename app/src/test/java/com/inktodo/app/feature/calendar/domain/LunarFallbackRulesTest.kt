package com.inktodo.app.feature.calendar.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarFallbackRulesTest {
    @Test
    fun holidayUpdateNoticeOnlyAppliesToLastCoveredYear() {
        assertTrue(OfficialHolidayScheduleData.shouldShowUpdateNotice(LocalDate.of(2026, 10, 12)))
        assertFalse(OfficialHolidayScheduleData.shouldShowUpdateNotice(LocalDate.of(2025, 12, 1)))
    }

    @Test
    fun requestedDayThirtyFallsBackToLastDayOfShortMonth() {
        assertTrue(
            matchesLunarTarget(
                current = LunarDate(2026, 2, 29, false),
                tomorrow = LunarDate(2026, 3, 1, false),
                requestedMonth = 2,
                requestedDay = 30,
                requestedLeapMonth = false,
                requestedLeapMonthExists = false,
            ),
        )
    }

    @Test
    fun regularMonthEndIsRecognizedWhenLeapVersionOfSameMonthStartsNext() {
        assertTrue(
            matchesLunarTarget(
                current = LunarDate(2025, 6, 29, false),
                tomorrow = LunarDate(2025, 6, 1, true),
                requestedMonth = 6,
                requestedDay = 30,
                requestedLeapMonth = false,
                requestedLeapMonthExists = false,
            ),
        )
    }

    @Test
    fun requestedLeapMonthUsesLeapMonthWhenPresentAndRegularMonthWhenAbsent() {
        assertFalse(
            matchesLunarTarget(
                current = LunarDate(2025, 6, 15, false),
                tomorrow = LunarDate(2025, 6, 16, false),
                requestedMonth = 6,
                requestedDay = 15,
                requestedLeapMonth = true,
                requestedLeapMonthExists = true,
            ),
        )
        assertTrue(
            matchesLunarTarget(
                current = LunarDate(2026, 6, 15, false),
                tomorrow = LunarDate(2026, 6, 16, false),
                requestedMonth = 6,
                requestedDay = 15,
                requestedLeapMonth = true,
                requestedLeapMonthExists = false,
            ),
        )
    }
}
