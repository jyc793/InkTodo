package com.inktodo.app.feature.calendar.data

import com.inktodo.app.core.database.SpecialDateDao
import com.inktodo.app.core.database.SpecialDateEntity
import com.inktodo.app.feature.calendar.domain.SpecialDate
import com.inktodo.app.feature.calendar.domain.SpecialDateCalendarType
import com.inktodo.app.feature.calendar.domain.SpecialDateRepository
import com.inktodo.app.feature.calendar.domain.SpecialDateRepeatMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSpecialDateRepository(private val dao: SpecialDateDao) : SpecialDateRepository {
    override fun observeAll(): Flow<List<SpecialDate>> = dao.observeAll().map { values -> values.map { it.toDomain() } }
    override suspend fun getAll(): List<SpecialDate> = dao.getAll().map { it.toDomain() }
    override suspend fun findById(id: Long): SpecialDate? = dao.findById(id)?.toDomain()
    override suspend fun save(value: SpecialDate): Long {
        val result = dao.upsert(value.toEntity())
        return value.id.takeIf { it > 0 } ?: result
    }
    override suspend fun delete(id: Long): Boolean {
        val value = dao.findById(id) ?: return false
        dao.delete(value)
        return true
    }
}

private fun SpecialDate.toEntity() = SpecialDateEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    calendarType = calendarType.name,
    repeatMode = repeatMode.name,
    gregorianEpochDay = gregorianDate?.toEpochDay(),
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    lunarIsLeapMonth = lunarIsLeapMonth,
    reminderEnabled = reminderEnabled,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

private fun SpecialDateEntity.toDomain() = SpecialDate(
    id = id,
    title = title,
    note = note,
    calendarType = SpecialDateCalendarType.valueOf(calendarType),
    repeatMode = SpecialDateRepeatMode.valueOf(repeatMode),
    gregorianDate = gregorianEpochDay?.let(LocalDate::ofEpochDay),
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    lunarIsLeapMonth = lunarIsLeapMonth,
    reminderEnabled = reminderEnabled,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)
