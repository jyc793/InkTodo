package com.inktodo.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY task_date_epoch_day, created_at_epoch_millis")
    abstract fun observePending(): Flow<List<TaskWithReminder>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY completed_at_epoch_millis DESC, updated_at_epoch_millis DESC")
    abstract fun observeCompleted(): Flow<List<TaskWithReminder>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE task_date_epoch_day = :epochDay ORDER BY is_completed, created_at_epoch_millis")
    abstract fun observeForDate(epochDay: Long): Flow<List<TaskWithReminder>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun findById(id: Long): TaskWithReminder?

    @Transaction
    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN reminders ON reminders.task_id = tasks.id
        WHERE tasks.is_completed = 0
          AND reminders.is_manually_stopped = 0
          AND (
            reminders.start_at_epoch_millis > :nowEpochMillis
            OR (reminders.kind = 'TIME_RANGE' AND reminders.end_at_epoch_millis > :nowEpochMillis)
          )
        ORDER BY reminders.start_at_epoch_millis
        """,
    )
    abstract suspend fun getSchedulableTasks(nowEpochMillis: Long): List<TaskWithReminder>

    @Transaction
    @Query("SELECT * FROM tasks WHERE recurrence_series_id = :seriesId AND task_date_epoch_day = :epochDay LIMIT 1")
    abstract suspend fun findSeriesOccurrence(seriesId: String, epochDay: Long): TaskWithReminder?

    @Transaction
    @Query("SELECT * FROM tasks WHERE recurrence_series_id = :seriesId AND is_completed = 0 ORDER BY task_date_epoch_day")
    abstract suspend fun getPendingSeriesTasks(seriesId: String): List<TaskWithReminder>

    @Transaction
    @Query("SELECT * FROM tasks WHERE recurrence_series_id IS NOT NULL AND is_completed = 0 ORDER BY task_date_epoch_day")
    abstract suspend fun getPendingRecurringTasks(): List<TaskWithReminder>

    @Transaction
    @Query("SELECT * FROM tasks WHERE recurrence_series_id IS NOT NULL AND task_date_epoch_day < :beforeEpochDay ORDER BY task_date_epoch_day")
    abstract suspend fun getExpiredRecurringTasks(beforeEpochDay: Long): List<TaskWithReminder>

    @Transaction
    @Query(
        "SELECT * FROM tasks WHERE special_date_id = :specialDateId " +
            "AND special_occurrence_epoch_day = :occurrenceEpochDay " +
            "AND task_date_epoch_day = :taskEpochDay LIMIT 1",
    )
    abstract suspend fun findSpecialOccurrence(
        specialDateId: Long,
        occurrenceEpochDay: Long,
        taskEpochDay: Long,
    ): TaskWithReminder?

    @Transaction
    @Query("SELECT * FROM tasks WHERE special_date_id = :specialDateId ORDER BY task_date_epoch_day")
    abstract suspend fun getSpecialDateTasks(specialDateId: Long): List<TaskWithReminder>

    @Transaction
    @Query("SELECT * FROM tasks WHERE special_date_id IS NOT NULL AND task_date_epoch_day < :beforeEpochDay")
    abstract suspend fun getExpiredSpecialDateTasks(beforeEpochDay: Long): List<TaskWithReminder>

    @Insert
    protected abstract suspend fun insertTask(task: TaskEntity): Long

    @Update
    protected abstract suspend fun updateTask(task: TaskEntity): Int

    @Upsert
    protected abstract suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    protected abstract suspend fun deleteReminder(taskId: Long)

    @Delete
    protected abstract suspend fun deleteTaskEntity(task: TaskEntity)

    @Query(
        """
        UPDATE tasks SET
            is_completed = :isCompleted,
            completed_at_epoch_millis = :completedAtEpochMillis,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :taskId
        """,
    )
    protected abstract suspend fun updateCompletion(
        taskId: Long,
        isCompleted: Boolean,
        completedAtEpochMillis: Long?,
        updatedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun save(task: TaskEntity, reminder: ReminderEntity?): Long {
        val taskId = if (task.id == 0L) {
            insertTask(task)
        } else {
            check(updateTask(task) == 1) { "Cannot update missing task ${task.id}" }
            task.id
        }
        if (reminder == null) {
            deleteReminder(taskId)
        } else {
            upsertReminder(reminder.copy(taskId = taskId))
        }
        return taskId
    }

    @Transaction
    open suspend fun delete(taskId: Long): Boolean {
        val task = findById(taskId)?.task ?: return false
        deleteTaskEntity(task)
        return true
    }

    @Transaction
    open suspend fun setCompleted(
        taskId: Long,
        completed: Boolean,
        changedAtEpochMillis: Long,
    ): Boolean = updateCompletion(
        taskId = taskId,
        isCompleted = completed,
        completedAtEpochMillis = changedAtEpochMillis.takeIf { completed },
        updatedAtEpochMillis = changedAtEpochMillis,
    ) == 1
}
