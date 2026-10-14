package com.inktodo.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InkTodoDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesTasksAndDefaultsManualStopToFalse() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """INSERT INTO tasks
                    (id, title, note, task_date_epoch_day, is_completed,
                     completed_at_epoch_millis, created_at_epoch_millis, updated_at_epoch_millis)
                    VALUES (7, '旧任务', '迁移保留', 20690, 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO reminders
                    (task_id, kind, start_at_epoch_millis, end_at_epoch_millis, use_system_alarm)
                    VALUES (7, 'TIME_RANGE', 2000, 4000, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            DatabaseMigrations.MIGRATION_1_2,
        ).apply {
            query(
                """SELECT tasks.title, tasks.note, reminders.is_manually_stopped
                    FROM tasks JOIN reminders ON reminders.task_id = tasks.id
                    WHERE tasks.id = 7
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("旧任务", cursor.getString(0))
                assertEquals("迁移保留", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
            close()
        }
    }

    @Test
    fun migration2To3PreservesCrossDateRangeAndAddsEndIndex() {
        val startAt = 1_777_302_000_000L
        val endAt = 1_782_660_600_000L
        helper.createDatabase(TEST_DATABASE_V3, 2).apply {
            execSQL(
                """INSERT INTO tasks
                    (id, title, note, task_date_epoch_day, is_completed,
                     completed_at_epoch_millis, created_at_epoch_millis, updated_at_epoch_millis)
                    VALUES (9, '跨学期任务', NULL, 20500, 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO reminders
                    (task_id, kind, start_at_epoch_millis, end_at_epoch_millis,
                     use_system_alarm, is_manually_stopped)
                    VALUES (9, 'TIME_RANGE', $startAt, $endAt, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V3,
            3,
            true,
            DatabaseMigrations.MIGRATION_2_3,
        ).apply {
            query(
                "SELECT start_at_epoch_millis, end_at_epoch_millis FROM reminders WHERE task_id = 9",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(startAt, cursor.getLong(0))
                assertEquals(endAt, cursor.getLong(1))
            }
            query("PRAGMA index_list('reminders')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == "index_reminders_end_at_epoch_millis") found = true
                }
                assertEquals(true, found)
            }
            close()
        }
    }

    @Test
    fun migration3To4PreservesExistingTasksAsNonRepeating() {
        helper.createDatabase(TEST_DATABASE_V4, 3).apply {
            execSQL(
                """INSERT INTO tasks
                    (id, title, note, task_date_epoch_day, is_completed,
                     completed_at_epoch_millis, created_at_epoch_millis, updated_at_epoch_millis)
                    VALUES (11, '保留任务', '原备注', 20735, 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO reminders
                    (task_id, kind, start_at_epoch_millis, end_at_epoch_millis,
                     use_system_alarm, is_manually_stopped)
                    VALUES (11, 'AT_TIME', 2000000000000, NULL, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V4, 4, true, DatabaseMigrations.MIGRATION_3_4,
        ).apply {
            query(
                """SELECT title, note, repeat_mode, repeat_weekdays_mask, recurrence_series_id
                   FROM tasks WHERE id = 11""",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("保留任务", cursor.getString(0))
                assertEquals("原备注", cursor.getString(1))
                assertEquals("NONE", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(true, cursor.isNull(4))
            }
            close()
        }
    }

    @Test
    fun migration4To5PreservesReminderAndDefaultsIntervalToOff() {
        helper.createDatabase(TEST_DATABASE_V5, 4).apply {
            execSQL(
                """INSERT INTO tasks
                    (id, title, note, task_date_epoch_day, is_completed,
                     completed_at_epoch_millis, created_at_epoch_millis, updated_at_epoch_millis,
                     repeat_mode, repeat_weekdays_mask, recurrence_series_id)
                    VALUES (12, '每日任务', NULL, 20735, 0, NULL, 1000, 1000, 'DAILY', 0, 'series-12')
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO reminders
                    (task_id, kind, start_at_epoch_millis, end_at_epoch_millis,
                     use_system_alarm, is_manually_stopped)
                    VALUES (12, 'TIME_RANGE', 2000000000000, 2000003600000, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V5, 5, true, DatabaseMigrations.MIGRATION_4_5,
        ).apply {
            query("SELECT repeat_interval_minutes FROM reminders WHERE task_id = 12").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            query("SELECT title FROM tasks WHERE id = 12").use { cursor ->
                cursor.moveToFirst()
                assertEquals("每日任务", cursor.getString(0))
            }
            close()
        }
    }

    @Test
    fun migration5To6PreservesExistingTasksAndAddsSpecialDateSchema() {
        helper.createDatabase(TEST_DATABASE_V6, 5).apply {
            execSQL(
                """INSERT INTO tasks
                    (id, title, note, task_date_epoch_day, is_completed,
                     completed_at_epoch_millis, created_at_epoch_millis, updated_at_epoch_millis,
                     repeat_mode, repeat_weekdays_mask, recurrence_series_id)
                    VALUES (13, '迁移前待办', '必须保留', 20740, 0, NULL, 1000, 2000, 'NONE', 0, NULL)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO reminders
                    (task_id, kind, start_at_epoch_millis, end_at_epoch_millis,
                     use_system_alarm, is_manually_stopped, repeat_interval_minutes)
                    VALUES (13, 'TIME_RANGE', 2000000000000, 2000003600000, 0, 0, 60)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V6, 6, true, DatabaseMigrations.MIGRATION_5_6,
        ).apply {
            query(
                """SELECT title, note, special_date_id, special_occurrence_epoch_day, special_offset_days
                    FROM tasks WHERE id = 13""",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("迁移前待办", cursor.getString(0))
                assertEquals("必须保留", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(true, cursor.isNull(4))
            }
            query("SELECT repeat_interval_minutes FROM reminders WHERE task_id = 13").use { cursor ->
                cursor.moveToFirst()
                assertEquals(60, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM special_dates").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-stage4-test"
        const val TEST_DATABASE_V3 = "migration-stage12-test"
        const val TEST_DATABASE_V4 = "migration-repeat-v4-test"
        const val TEST_DATABASE_V5 = "migration-interval-v5-test"
        const val TEST_DATABASE_V6 = "migration-special-date-v6-test"
    }
}
