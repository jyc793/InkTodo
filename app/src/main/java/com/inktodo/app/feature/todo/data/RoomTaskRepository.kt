package com.inktodo.app.feature.todo.data

import com.inktodo.app.core.database.TaskDao
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.model.coversDate
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : TaskRepository {
    override fun observePending(): Flow<List<TodoTask>> =
        taskDao.observePending().map { tasks -> tasks.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<TodoTask>> =
        taskDao.observeCompleted().map { tasks -> tasks.map { it.toDomain() } }

    override fun observeForDate(date: LocalDate): Flow<List<TodoTask>> =
        observePending().map { tasks ->
            tasks.filter { task ->
                task.date == date ||
                    (!task.recurrence.isRepeating && task.coversDate(date, zoneId)) ||
                    (task.recurrence.isRepeating && task.date == date.minusDays(1) &&
                        (task.reminder as? com.inktodo.app.core.model.TaskReminder.TimeRange)
                            ?.endAt?.atZone(zoneId)?.toLocalDate() == date)
            }
        }

    override suspend fun findById(id: Long): TodoTask? = taskDao.findById(id)?.toDomain()

    override suspend fun save(task: TodoTask): Long {
        val persistence = task.toPersistenceModel()
        return taskDao.save(persistence.task, persistence.reminder)
    }

    override suspend fun delete(id: Long): Boolean = taskDao.delete(id)

    override suspend fun setCompleted(id: Long, completed: Boolean, changedAt: Instant): Boolean =
        taskDao.setCompleted(id, completed, changedAt.toEpochMilli())

    override suspend fun getSchedulableTasks(now: Instant): List<TodoTask> =
        taskDao.getSchedulableTasks(now.toEpochMilli()).map { it.toDomain() }

    override suspend fun findSeriesOccurrence(seriesId: String, date: LocalDate): TodoTask? =
        taskDao.findSeriesOccurrence(seriesId, date.toEpochDay())?.toDomain()

    override suspend fun getPendingSeriesTasks(seriesId: String): List<TodoTask> =
        taskDao.getPendingSeriesTasks(seriesId).map { it.toDomain() }

    override suspend fun getPendingRecurringTasks(): List<TodoTask> =
        taskDao.getPendingRecurringTasks().map { it.toDomain() }

    override suspend fun getExpiredRecurringTasks(before: LocalDate): List<TodoTask> =
        taskDao.getExpiredRecurringTasks(before.toEpochDay()).map { it.toDomain() }

    override suspend fun findSpecialOccurrence(
        specialDateId: Long,
        occurrenceDate: LocalDate,
        taskDate: LocalDate,
    ): TodoTask? = taskDao.findSpecialOccurrence(
        specialDateId,
        occurrenceDate.toEpochDay(),
        taskDate.toEpochDay(),
    )?.toDomain()

    override suspend fun getSpecialDateTasks(specialDateId: Long): List<TodoTask> =
        taskDao.getSpecialDateTasks(specialDateId).map { it.toDomain() }

    override suspend fun getExpiredSpecialDateTasks(before: LocalDate): List<TodoTask> =
        taskDao.getExpiredSpecialDateTasks(before.toEpochDay()).map { it.toDomain() }
}
