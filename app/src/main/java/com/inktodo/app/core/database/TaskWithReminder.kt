package com.inktodo.app.core.database

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithReminder(
    @Embedded
    val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id",
    )
    val reminder: ReminderEntity?,
)
