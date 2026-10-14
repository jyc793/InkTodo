package com.inktodo.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "reminders",
    primaryKeys = ["task_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["start_at_epoch_millis"]),
        Index(value = ["end_at_epoch_millis"]),
    ],
)
data class ReminderEntity(
    @ColumnInfo(name = "task_id")
    val taskId: Long,
    val kind: ReminderKind,
    @ColumnInfo(name = "start_at_epoch_millis")
    val startAtEpochMillis: Long,
    @ColumnInfo(name = "end_at_epoch_millis")
    val endAtEpochMillis: Long?,
    @ColumnInfo(name = "use_system_alarm")
    val useSystemAlarm: Boolean,
    @ColumnInfo(name = "is_manually_stopped", defaultValue = "0")
    val isManuallyStopped: Boolean = false,
    @ColumnInfo(name = "repeat_interval_minutes")
    val repeatIntervalMinutes: Int? = null,
)

enum class ReminderKind {
    AT_TIME,
    TIME_RANGE,
}
