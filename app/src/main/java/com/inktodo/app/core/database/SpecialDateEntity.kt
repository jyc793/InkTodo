package com.inktodo.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "special_dates",
    indices = [Index(value = ["updated_at_epoch_millis"])],
)
data class SpecialDateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String?,
    @ColumnInfo(name = "calendar_type")
    val calendarType: String,
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String,
    @ColumnInfo(name = "gregorian_epoch_day")
    val gregorianEpochDay: Long?,
    @ColumnInfo(name = "lunar_month")
    val lunarMonth: Int?,
    @ColumnInfo(name = "lunar_day")
    val lunarDay: Int?,
    @ColumnInfo(name = "lunar_is_leap_month", defaultValue = "0")
    val lunarIsLeapMonth: Boolean = false,
    @ColumnInfo(name = "reminder_enabled", defaultValue = "1")
    val reminderEnabled: Boolean = true,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
