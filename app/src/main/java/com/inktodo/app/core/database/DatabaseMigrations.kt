package com.inktodo.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE reminders ADD COLUMN is_manually_stopped INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reminders_end_at_epoch_millis " +
                    "ON reminders(end_at_epoch_millis)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_mode TEXT NOT NULL DEFAULT 'NONE'")
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_weekdays_mask INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_series_id TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_recurrence_series_id_task_date_epoch_day " +
                    "ON tasks(recurrence_series_id, task_date_epoch_day)",
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN repeat_interval_minutes INTEGER")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN special_date_id INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN special_occurrence_epoch_day INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN special_offset_days INTEGER")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_tasks_special_date_id_special_occurrence_epoch_day_task_date_epoch_day " +
                    "ON tasks(special_date_id, special_occurrence_epoch_day, task_date_epoch_day)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS special_dates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    note TEXT,
                    calendar_type TEXT NOT NULL,
                    repeat_mode TEXT NOT NULL,
                    gregorian_epoch_day INTEGER,
                    lunar_month INTEGER,
                    lunar_day INTEGER,
                    lunar_is_leap_month INTEGER NOT NULL DEFAULT 0,
                    reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    created_at_epoch_millis INTEGER NOT NULL,
                    updated_at_epoch_millis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_special_dates_updated_at_epoch_millis " +
                    "ON special_dates(updated_at_epoch_millis)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
