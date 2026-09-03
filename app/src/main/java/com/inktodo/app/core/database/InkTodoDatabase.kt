package com.inktodo.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, ReminderEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class InkTodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
