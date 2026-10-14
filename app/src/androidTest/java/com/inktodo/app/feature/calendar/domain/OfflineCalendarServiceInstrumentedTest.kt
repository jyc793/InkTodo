package com.inktodo.app.feature.calendar.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineCalendarServiceInstrumentedTest {
    private val service = OfflineCalendarService()

    @Test
    fun knownTraditionalFestivalsAndSolarTermsAreConvertedOffline() {
        assertTrue(service.decorate(LocalDate.of(2025, 1, 29)).fullLabels.contains("春节"))
        assertTrue(service.decorate(LocalDate.of(2025, 10, 6)).fullLabels.contains("中秋节"))
        assertTrue(service.decorate(LocalDate.of(2025, 10, 29)).fullLabels.contains("重阳节"))
        assertTrue(service.decorate(LocalDate.of(2025, 4, 4)).fullLabels.contains("清明"))
    }

    @Test
    fun customDateWinsDisplayPriorityOverOfficialAndGregorianLabels() {
        val custom = SpecialDate(
            id = 9,
            title = "相识纪念日",
            calendarType = SpecialDateCalendarType.GREGORIAN,
            repeatMode = SpecialDateRepeatMode.ANNUAL_GREGORIAN,
            gregorianDate = LocalDate.of(2020, 1, 1),
            createdAt = java.time.Instant.EPOCH,
            updatedAt = java.time.Instant.EPOCH,
        )
        val decoration = service.decorate(LocalDate.of(2026, 1, 1), listOf(custom))
        assertEquals("相识纪念日", decoration.shortLabel)
        assertEquals(OfficialDayKind.HOLIDAY, decoration.officialDayKind)
    }
}
