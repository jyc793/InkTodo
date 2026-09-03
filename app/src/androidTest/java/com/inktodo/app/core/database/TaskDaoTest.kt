package com.inktodo.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.feature.todo.data.RoomTaskRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    private lateinit var database: InkTodoDatabase
    private lateinit var dao: TaskDao
    private lateinit var repository: RoomTaskRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InkTodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.taskDao()
        repository = RoomTaskRepository(dao)
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() = database.close()

    @Test
    fun saveObserveCompleteRestoreAndDeleteAreConsistent() = runBlocking {
        val taskId = dao.save(task(title = "上课"), atTimeReminder())
        assertEquals(listOf("上课"), dao.observePending().first().map { it.task.title })

        assertTrue(dao.setCompleted(taskId, true, 2_000L))
        assertTrue(dao.observePending().first().isEmpty())
        assertEquals(taskId, dao.observeCompleted().first().single().task.id)

        assertTrue(dao.setCompleted(taskId, false, 3_000L))
        val restored = requireNotNull(dao.findById(taskId))
        assertFalse(restored.task.isCompleted)
        assertNull(restored.task.completedAtEpochMillis)
        assertEquals(ReminderKind.AT_TIME, restored.reminder?.kind)

        assertTrue(dao.delete(taskId))
        assertNull(dao.findById(taskId))
        assertFalse(dao.delete(taskId))
    }

    @Test
    fun replacingReminderAndDateQueryUseOnlyCurrentValues() = runBlocking {
        val taskId = dao.save(task(title = "验证"), atTimeReminder())
        dao.save(
            task(id = taskId, title = "验证", date = 21L, updatedAt = 2_000L),
            ReminderEntity(taskId, ReminderKind.TIME_RANGE, 4_000L, 8_000L, false),
        )

        assertTrue(dao.observeForDate(20L).first().isEmpty())
        val saved = dao.observeForDate(21L).first().single()
        assertEquals(ReminderKind.TIME_RANGE, saved.reminder?.kind)
        assertEquals(8_000L, saved.reminder?.endAtEpochMillis)

        dao.save(saved.task, null)
        assertNull(dao.findById(taskId)?.reminder)
    }

    @Test
    fun schedulableQueryIncludesFutureAndActiveRangesOnly() = runBlocking {
        dao.save(task(title = "future"), ReminderEntity(0, ReminderKind.AT_TIME, 20_000L, null, false))
        dao.save(task(title = "active"), ReminderEntity(0, ReminderKind.TIME_RANGE, 5_000L, 15_000L, false))
        dao.save(
            task(title = "stopped"),
            ReminderEntity(0, ReminderKind.TIME_RANGE, 5_000L, 15_000L, false, isManuallyStopped = true),
        )
        dao.save(task(title = "ended"), ReminderEntity(0, ReminderKind.TIME_RANGE, 1_000L, 9_000L, false))
        val completedId = dao.save(task(title = "done"), ReminderEntity(0, ReminderKind.AT_TIME, 30_000L, null, false))
        dao.setCompleted(completedId, true, 8_000L)

        assertEquals(
            setOf("future", "active"),
            dao.getSchedulableTasks(10_000L).map { it.task.title }.toSet(),
        )
    }

    @Test
    fun repositoryPersistsAndMapsDomainTask() = runBlocking {
        val instant = Instant.ofEpochMilli(40_000L)
        val taskId = repository.save(
            TodoTask(
                title = "Repository 验证",
                date = LocalDate.of(2026, 8, 25),
                reminder = TaskReminder.AtTime(instant, useSystemAlarm = true),
                createdAt = Instant.ofEpochMilli(1_000L),
                updatedAt = Instant.ofEpochMilli(1_000L),
            ),
        )

        val restored = requireNotNull(repository.findById(taskId))
        assertEquals("Repository 验证", restored.title)
        assertEquals(TaskReminder.AtTime(instant, useSystemAlarm = true), restored.reminder)
        assertEquals(taskId, repository.observePending().first().single().id)

        assertTrue(repository.setCompleted(taskId, true, Instant.ofEpochMilli(2_000L)))
        assertEquals(taskId, repository.observeCompleted().first().single().id)
    }

    private fun task(
        id: Long = 0,
        title: String,
        date: Long = 20L,
        updatedAt: Long = 1_000L,
    ) = TaskEntity(
        id = id,
        title = title,
        note = null,
        taskDateEpochDay = date,
        isCompleted = false,
        completedAtEpochMillis = null,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = updatedAt,
    )

    private fun atTimeReminder() = ReminderEntity(
        taskId = 0,
        kind = ReminderKind.AT_TIME,
        startAtEpochMillis = 10_000L,
        endAtEpochMillis = null,
        useSystemAlarm = true,
    )
}
