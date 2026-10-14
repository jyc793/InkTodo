package com.inktodo.app.feature.calendar.domain

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class SpecialDateCalendarType { GREGORIAN, LUNAR }
enum class SpecialDateRepeatMode { ONCE, ANNUAL_GREGORIAN, ANNUAL_LUNAR }

data class SpecialDate(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val calendarType: SpecialDateCalendarType,
    val repeatMode: SpecialDateRepeatMode,
    val gregorianDate: LocalDate? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarIsLeapMonth: Boolean = false,
    val reminderEnabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)

interface SpecialDateRepository {
    fun observeAll(): Flow<List<SpecialDate>>
    suspend fun getAll(): List<SpecialDate>
    suspend fun findById(id: Long): SpecialDate?
    suspend fun save(value: SpecialDate): Long
    suspend fun delete(id: Long): Boolean
}
