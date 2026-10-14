package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observePending(): Flow<List<TodoTask>>
    fun observeCompleted(): Flow<List<TodoTask>>
    fun observeForDate(date: LocalDate): Flow<List<TodoTask>>
    suspend fun findById(id: Long): TodoTask?
    suspend fun save(task: TodoTask): Long
    suspend fun delete(id: Long): Boolean
    suspend fun setCompleted(id: Long, completed: Boolean, changedAt: Instant): Boolean
    suspend fun getSchedulableTasks(now: Instant): List<TodoTask>
    suspend fun findSeriesOccurrence(seriesId: String, date: LocalDate): TodoTask? = null
    suspend fun getPendingSeriesTasks(seriesId: String): List<TodoTask> = emptyList()
    suspend fun getPendingRecurringTasks(): List<TodoTask> = emptyList()
    suspend fun getExpiredRecurringTasks(before: LocalDate): List<TodoTask> = emptyList()
    suspend fun findSpecialOccurrence(
        specialDateId: Long,
        occurrenceDate: LocalDate,
        taskDate: LocalDate,
    ): TodoTask? = null
    suspend fun getSpecialDateTasks(specialDateId: Long): List<TodoTask> = emptyList()
    suspend fun getExpiredSpecialDateTasks(before: LocalDate): List<TodoTask> = emptyList()
}
