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

    private companion object {
        const val TEST_DATABASE = "migration-stage4-test"
        const val TEST_DATABASE_V3 = "migration-stage12-test"
    }
}
