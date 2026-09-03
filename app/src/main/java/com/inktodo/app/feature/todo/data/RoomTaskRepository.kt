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
        observePending().map { tasks -> tasks.filter { it.coversDate(date, zoneId) } }

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
}
