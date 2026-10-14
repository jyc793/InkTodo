package com.inktodo.app.feature.calendar.domain

import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.feature.todo.domain.TaskCommandResult
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialDateTaskCoordinatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun reconcileAtDMinusFiveCreatesFiveUniqueDailyTasksAndIsIdempotent() = runBlocking {
        val definition = annualGregorianDefinition()
        val specialDates = FakeSpecialDateRepository(mutableListOf(definition))
        val tasks = FakeTaskRepository()
        val coordinator = coordinator(specialDates, tasks, LocalDate.of(2026, 6, 5))

        coordinator.reconcile()
        coordinator.reconcile()

        assertEquals(5, tasks.values.size)
        assertEquals(
            listOf(
                "距离妈妈生日还有4天",
                "距离妈妈生日还有3天",
                "距离妈妈生日还有2天",
                "明天是妈妈生日",
                "今天是妈妈生日",
            ),
            tasks.values.sortedBy(TodoTask::date).map(TodoTask::title),
        )
        tasks.values.forEach { task ->
            val reminder = task.reminder as TaskReminder.TimeRange
            assertEquals(7, reminder.startAt.atZone(zone).hour)
            assertEquals(task.date.plusDays(1), reminder.endAt.atZone(zone).toLocalDate())
            assertEquals(60, reminder.repeatIntervalMinutes)
        }
    }

    @Test
    fun nextDayMaintenanceDeletesYesterdayEvenIfItWasCompleted() = runBlocking {
        val specialDates = FakeSpecialDateRepository(mutableListOf(annualGregorianDefinition()))
        val tasks = FakeTaskRepository()
        coordinator(specialDates, tasks, LocalDate.of(2026, 6, 5)).reconcile()
        val yesterday = tasks.values.single { it.date == LocalDate.of(2026, 6, 6) }
        tasks.values[tasks.values.indexOf(yesterday)] = yesterday.copy(isCompleted = true)

        coordinator(specialDates, tasks, LocalDate.of(2026, 6, 7)).reconcile()

        assertTrue(tasks.values.none { it.date == LocalDate.of(2026, 6, 6) })
        assertEquals(4, tasks.values.size)
    }

    @Test
    fun disablingDefinitionClearsPreviouslyGeneratedTasks() = runBlocking {
        val definition = annualGregorianDefinition()
        val specialDates = FakeSpecialDateRepository(mutableListOf(definition))
        val tasks = FakeTaskRepository()
        val coordinator = coordinator(specialDates, tasks, LocalDate.of(2026, 6, 5))
        coordinator.reconcile()
        coordinator.save(definition.copy(reminderEnabled = false))
        assertTrue(tasks.values.isEmpty())
    }

    private fun coordinator(
        dates: FakeSpecialDateRepository,
        tasks: FakeTaskRepository,
        today: LocalDate,
    ): SpecialDateTaskCoordinator {
        val now = today.atStartOfDay(zone).plusHours(1).toInstant()
        return SpecialDateTaskCoordinator(
            specialDates = dates,
            tasks = tasks,
            commands = FakeCommands(tasks),
            calendar = OfflineCalendarService(zone),
            clock = Clock.fixed(now, zone),
            zoneId = zone,
        )
    }

    private fun annualGregorianDefinition() = SpecialDate(
        id = 1,
        title = "妈妈生日",
        calendarType = SpecialDateCalendarType.GREGORIAN,
        repeatMode = SpecialDateRepeatMode.ANNUAL_GREGORIAN,
        gregorianDate = LocalDate.of(2000, 6, 10),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private class FakeSpecialDateRepository(
        private val values: MutableList<SpecialDate>,
    ) : SpecialDateRepository {
        override fun observeAll(): Flow<List<SpecialDate>> = flowOf(values.toList())
        override suspend fun getAll(): List<SpecialDate> = values.toList()
        override suspend fun findById(id: Long): SpecialDate? = values.find { it.id == id }
        override suspend fun save(value: SpecialDate): Long {
            val id = value.id.takeIf { it > 0 } ?: ((values.maxOfOrNull(SpecialDate::id) ?: 0) + 1)
            values.removeAll { it.id == id }
            values += value.copy(id = id)
            return id
        }
        override suspend fun delete(id: Long): Boolean = values.removeAll { it.id == id }
    }

    private class FakeTaskRepository : TaskRepository {
        val values = mutableListOf<TodoTask>()
        private var nextId = 1L
        override fun observePending(): Flow<List<TodoTask>> = flowOf(values.filterNot(TodoTask::isCompleted))
        override fun observeCompleted(): Flow<List<TodoTask>> = flowOf(values.filter(TodoTask::isCompleted))
        override fun observeForDate(date: LocalDate): Flow<List<TodoTask>> = flowOf(values.filter { it.date == date })
        override suspend fun findById(id: Long): TodoTask? = values.find { it.id == id }
        override suspend fun save(task: TodoTask): Long {
            val id = task.id.takeIf { it > 0 } ?: nextId++
            values.removeAll { it.id == id }
            values += task.copy(id = id)
            return id
        }
        override suspend fun delete(id: Long): Boolean = values.removeAll { it.id == id }
        override suspend fun setCompleted(id: Long, completed: Boolean, changedAt: Instant): Boolean = false
        override suspend fun getSchedulableTasks(now: Instant): List<TodoTask> = values
        override suspend fun findSpecialOccurrence(
            specialDateId: Long,
            occurrenceDate: LocalDate,
            taskDate: LocalDate,
        ): TodoTask? = values.find {
            it.specialDateId == specialDateId && it.specialOccurrenceDate == occurrenceDate && it.date == taskDate
        }
        override suspend fun getSpecialDateTasks(specialDateId: Long): List<TodoTask> =
            values.filter { it.specialDateId == specialDateId }
        override suspend fun getExpiredSpecialDateTasks(before: LocalDate): List<TodoTask> =
            values.filter { it.specialDateId != null && it.date.isBefore(before) }
    }

    private class FakeCommands(private val tasks: FakeTaskRepository) : TaskCommandService {
        override suspend fun save(task: TodoTask): TaskCommandResult = TaskCommandResult(tasks.save(task))
        override suspend fun delete(taskId: Long): TaskCommandResult {
            tasks.delete(taskId)
            return TaskCommandResult(taskId)
        }
        override suspend fun setCompleted(taskId: Long, completed: Boolean, changedAt: Instant) =
            TaskCommandResult(taskId)
        override suspend fun stopOngoingReminder(taskId: Long, changedAt: Instant) = TaskCommandResult(taskId)
    }
}
