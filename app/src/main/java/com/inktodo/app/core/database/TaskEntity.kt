package com.inktodo.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["is_completed", "task_date_epoch_day"]),
        Index(value = ["updated_at_epoch_millis"]),
        Index(value = ["recurrence_series_id", "task_date_epoch_day"], unique = true),
        Index(
            value = ["special_date_id", "special_occurrence_epoch_day", "task_date_epoch_day"],
            unique = true,
        ),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String?,
    @ColumnInfo(name = "task_date_epoch_day")
    val taskDateEpochDay: Long,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "repeat_mode", defaultValue = "'NONE'")
    val repeatMode: String = "NONE",
    @ColumnInfo(name = "repeat_weekdays_mask", defaultValue = "0")
    val repeatWeekdaysMask: Int = 0,
    @ColumnInfo(name = "recurrence_series_id")
    val recurrenceSeriesId: String? = null,
    @ColumnInfo(name = "special_date_id")
    val specialDateId: Long? = null,
    @ColumnInfo(name = "special_occurrence_epoch_day")
    val specialOccurrenceEpochDay: Long? = null,
    @ColumnInfo(name = "special_offset_days")
    val specialOffsetDays: Int? = null,
)
