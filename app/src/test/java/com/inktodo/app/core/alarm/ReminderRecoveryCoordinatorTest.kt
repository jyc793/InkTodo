package com.inktodo.app.core.alarm

import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRecoveryCoordinatorTest {
    private val now = Instant.parse("2026-08-25T09:00:00Z")

    @Test
    fun lockedBootUsesOnlyDeviceProtectedMirrorAndRestoresSilently() = runBlocking {
        val mirrored = snapshot(21, "锁屏恢复", "2026-08-25T10:00:00Z")
        val store = FakeStore(listOf(mirrored))
        val scheduler = FakeScheduler()
        val coordinator = ReminderRecoveryCoordinator(
            repository = { error("Locked boot must not resolve credential-protected Room") },
            snapshotStore = store,
            scheduler = scheduler,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        coordinator.recover(userUnlocked = false)

        assertEquals(listOf(mirrored), scheduler.scheduled.map(Pair<ScheduledTaskSnapshot, Boolean>::first))
        assertEquals(listOf(false), scheduler.scheduled.map(Pair<ScheduledTaskSnapshot, Boolean>::second))
    }

    @Test
    fun unlockedBootReconcilesRoomCancelsStaleAndReplacesMirror() = runBlocking {
        val stale = snapshot(31, "已删除", "2026-08-25T10:00:00Z")
        val currentTask = task(32, "未来任务", "2026-08-25T11:00:00Z")
        val repository = FakeRepository(listOf(currentTask))
        val store = FakeStore(listOf(stale))
        val scheduler = FakeScheduler()
        val coordinator = coordinator(repository, store, scheduler)

        coordinator.recover(userUnlocked = true)

        assertTrue(repository.wasQueriedForRecovery)
        assertEquals(listOf(31L), scheduler.cancelled)
        assertEquals(listOf(32L), scheduler.scheduled.map { it.first.taskId })
        assertEquals(listOf(32L), store.getAll().map(ScheduledTaskSnapshot::taskId))
    }

    @Test
    fun unlockedBootRestoresFullCrossDateRange() = runBlocking {
        val task = TodoTask(
            id = 33,
            title = "跨学期持续任务",
            date = LocalDate.of(2026, 9, 1),
            reminder = TaskReminder.TimeRange(
                startAt = Instant.parse("2026-09-01T01:00:00Z"),
                endAt = Instant.parse("2026-12-31T09:00:00Z"),
            ),
            createdAt = now,
            updatedAt = now,
        )
        val repository = FakeRepository(listOf(task))
        val store = FakeStore(emptyList())
        val scheduler = FakeScheduler()
        val range = checkNotNull(task.reminder) as TaskReminder.TimeRange

        coordinator(repository, store, scheduler).recover(userUnlocked = true)

        val restored = scheduler.scheduled.single().first
        assertEquals(range.startAt, restored.startAt)
        assertEquals(range.endAt, restored.endAt)
        assertEquals(range.endAt, store.get(33)?.endAt)
    }

    @Test
    fun unacknowledgedPastFormalReminderIsRestoredSilentlyAndKeptInMirror() = runBlocking {
        val awaiting = snapshot(34, "等待确认", "2026-08-25T08:55:00Z")
        val repository = FakeRepository(emptyList())
        val store = FakeStore(listOf(awaiting))
        val scheduler = FakeScheduler()
        val health = FakeHealthStore(
            ReminderHealthRecord(
                taskId = 34,
                state = ReminderHealthState.AWAITING_CONFIRMATION,
                updatedAt = now,
                detail = "等待用户确认",
            ),
        )
        val coordinator = ReminderRecoveryCoordinator(
            repository = { repository },
            snapshotStore = store,
            scheduler = scheduler,
            healthStore = health,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        coordinator.recover(userUnlocked = true)

        assertEquals(listOf(34L), scheduler.restored.map(ScheduledTaskSnapshot::taskId))
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(listOf(34L), store.getAll().map(ScheduledTaskSnapshot::taskId))
    }

    private fun coordinator(
        repository: FakeRepository,
        store: FakeStore,
        scheduler: FakeScheduler,
    ) = ReminderRecoveryCoordinator(
        repository,
        store,
        scheduler,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun task(id: Long, title: String, start: String) = TodoTask(
        id = id,
        title = title,
        date = LocalDate.of(2026, 8, 25),
        reminder = TaskReminder.AtTime(Instant.parse(start)),
        createdAt = now,
        updatedAt = now,
    )

    private fun snapshot(id: Long, title: String, start: String) =
        task(id, title, start).toScheduledSnapshot()!!

    private class FakeStore(initial: List<ScheduledTaskSnapshot>) : ReminderSnapshotStore {
        private val values = initial.associateByTo(linkedMapOf(), ScheduledTaskSnapshot::taskId)
        override fun get(taskId: Long) = values[taskId]
        override fun getAll() = values.values.toList()
        override fun put(snapshot: ScheduledTaskSnapshot) { values[snapshot.taskId] = snapshot }
        override fun remove(taskId: Long) { values.remove(taskId) }
        override fun replaceAll(snapshots: Collection<ScheduledTaskSnapshot>) {
            values.clear()
            snapshots.forEach(::put)
        }
    }

    private class FakeScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Pair<ScheduledTaskSnapshot, Boolean>>()
        val cancelled = mutableListOf<Long>()
        val restored = mutableListOf<ScheduledTaskSnapshot>()
        override fun schedule(
            snapshot: ScheduledTaskSnapshot,
            alertIfRangeAlreadyActive: Boolean,
        ): ReminderScheduleResult {
            scheduled += snapshot to alertIfRangeAlreadyActive
            return ReminderScheduleResult(ReminderPlan(emptyList()))
        }
        override fun cancel(taskId: Long) { cancelled += taskId }
        override fun restoreAwaitingConfirmation(snapshot: ScheduledTaskSnapshot) {
            restored += snapshot
        }
    }

    private class FakeHealthStore(record: ReminderHealthRecord) : ReminderHealthStore {
        private val values = mutableMapOf(record.taskId to record)
        override fun get(taskId: Long) = values[taskId]
        override fun getAll() = values.values.toList()
        override fun record(
            taskId: Long,
            state: ReminderHealthState,
            nextTriggerAt: Instant?,
            detail: String?,
        ) {
            values[taskId] = ReminderHealthRecord(taskId, state, now, nextTriggerAt, detail)
        }
        override fun remove(taskId: Long) { values.remove(taskId) }

        private companion object {
            val now: Instant = Instant.parse("2026-08-25T09:00:00Z")
        }
    }

    private class FakeRepository(private val recoveryTasks: List<TodoTask>) : TaskRepository {
        var wasQueriedForRecovery = false
        override fun observePending(): Flow<List<TodoTask>> = emptyFlow()
        override fun observeCompleted(): Flow<List<TodoTask>> = emptyFlow()
        override fun observeForDate(date: LocalDate): Flow<List<TodoTask>> = emptyFlow()
        override suspend fun findById(id: Long): TodoTask? = null
        override suspend fun save(task: TodoTask): Long = error("not used")
        override suspend fun delete(id: Long): Boolean = error("not used")
        override suspend fun setCompleted(id: Long, completed: Boolean, changedAt: Instant): Boolean =
            error("not used")
        override suspend fun getSchedulableTasks(now: Instant): List<TodoTask> {
            wasQueriedForRecovery = true
            return recoveryTasks
        }
    }
}
