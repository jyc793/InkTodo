package com.inktodo.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, SpecialDateEntity::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class InkTodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun specialDateDao(): SpecialDateDao
}
