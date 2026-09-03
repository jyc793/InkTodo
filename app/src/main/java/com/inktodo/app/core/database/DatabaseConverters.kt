package com.inktodo.app.core.database

import androidx.room.TypeConverter

class DatabaseConverters {
    @TypeConverter
    fun reminderKindToString(value: ReminderKind): String = value.name

    @TypeConverter
    fun stringToReminderKind(value: String): ReminderKind = ReminderKind.valueOf(value)
}
