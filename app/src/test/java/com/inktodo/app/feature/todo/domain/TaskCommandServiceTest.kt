package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.alarm.ReminderPlanner
import com.inktodo.app.core.alarm.ReminderScheduleIssue
import com.inktodo.app.core.alarm.ReminderScheduleResult
import com.inktodo.app.core.alarm.ReminderScheduler
import com.inktodo.app.core.alarm.ReminderSnapshotStore
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import com.inktodo.app.core.alarm.toScheduledSnapshot
import com.inktodo.app.core.alarm.ReminderHealthRecord
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.alarm.ReminderHealthStore
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCommandServiceTest {
    private val now = Instant.parse("2026-08-25T09:00:00Z")

    @Test
    fun updatingCancelsOldAlarmBeforeSchedulingNewAndRequestsClockConfirmation() = runBlocking {
        val old = task(1, "旧标题", "2026-08-25T10:00:00Z", systemAlarm = true)
        val updated = task(1, "新标题", "2026-08-25T11:00:00Z", systemAlarm = true)
        val fixture = fixture(old)

        val result = fixture.commands.save(updated)

        assertEquals(listOf(1L), fixture.scheduler.cancelled)
        assertEquals(Instant.parse("2026-08-25T11:00:00Z"), fixture.scheduler.scheduled.single().startAt)
        assertEquals("新标题", fixture.store.get(1)?.title)
        assertNotNull(result.systemAlarmRequest)
        assertTrue(TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW in result.issues)
    }

    @Test
    fun reminderSnapshotCarriesTrimmedTaskNoteForConfirmationUi() = runBlocking {
        val fixture = fixture()

        val result = fixture.commands.save(
            task(0, "宿舍", "2026-08-25T10:00:00Z").copy(note = "  带钥匙和门禁卡  "),
        )

        assertEquals("带钥匙和门禁卡", fixture.store.get(result.taskId)?.note)
    }

    @Test
    fun completingCancelsAndRemovesReminderSnapshot() = runBlocking {
        val fixture = fixture(task(2, "上课", "2026-08-25T10:00:00Z"))

        fixture.commands.setCompleted(2, true, now)

        assertEquals(listOf(2L), fixture.scheduler.cancelled)
        assertTrue(fixture.store.getAll().isEmpty())
        assertTrue(checkNotNull(fixture.repository.findById(2)).isCompleted)
    }

    @Test
    fun restoringFutureSystemAlarmSchedulesAppAlarmAndRequestsSystemClock() = runBlocking {
        val completed = task(3, "会议", "2026-08-25T10:00:00Z", systemAlarm = true)
            .copy(isCompleted = true, completedAt = now)
        val fixture = fixture(completed)

        val result = fixture.commands.setCompleted(3, false, now)

        assertEquals(Instant.parse("2026-08-25T10:00:00Z"), fixture.scheduler.scheduled.single().startAt)
        assertNotNull(result.systemAlarmRequest)
    }

    @Test
    fun schedulingCapabilityIssuesAreReturnedWithoutRollingBackSavedTask() = runBlocking {
        val fixture = fixture()
        fixture.scheduler.nextIssues = setOf(
            ReminderScheduleIssue.EXACT_ALARM_UNAVAILABLE,
            ReminderScheduleIssue.NOTIFICATION_PERMISSION_UNAVAILABLE,
            ReminderScheduleIssue.OVERLAY_PERMISSION_UNAVAILABLE,
        )

        val result = fixture.commands.save(task(0, "复习", "2026-08-25T10:00:00Z"))

        assertTrue(TaskCommandIssue.EXACT_ALARM_UNAVAILABLE in result.issues)
        assertTrue(TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE in result.issues)
        assertTrue(TaskCommandIssue.OVERLAY_PERMISSION_UNAVAILABLE in result.issues)
        assertNotNull(fixture.repository.findById(result.taskId))
    }

    @Test
    fun snapshotFailureDoesNotRegisterUnsafeAlarmAndPersistsHealthFailure() = runBlocking {
        val fixture = fixture()
        fixture.store.failNextPut = true

        val result = fixture.commands.save(task(0, "写入失败", "2026-08-25T10:00:00Z"))

        assertTrue(TaskCommandIssue.REMINDER_SYNC_FAILED in result.issues)
        assertTrue(fixture.scheduler.scheduled.isEmpty())
        assertEquals(ReminderHealthState.FAILED, fixture.health.get(result.taskId)?.state)
    }

    @Test
    fun stoppingActiveRangeCancelsNotificationAndPersistsStoppedState() = runBlocking {
        val rangeTask = TodoTask(
            id = 4,
            title = "持续验证",
            date = LocalDate.of(2026, 8, 25),
            reminder = TaskReminder.TimeRange(
                startAt = Instant.parse("2026-08-25T08:00:00Z"),
                endAt = Instant.parse("2026-08-25T10:00:00Z"),
            ),
            createdAt = now,
            updatedAt = now,
        )
        val fixture = fixture(rangeTask)

        fixture.commands.stopOngoingReminder(4, now)

        assertEquals(listOf(4L), fixture.scheduler.cancelled)
        assertTrue(fixture.store.getAll().isEmpty())
        val stopped = checkNotNull(fixture.repository.findById(4)).reminder as TaskReminder.TimeRange
        assertTrue(stopped.isManuallyStopped)
        assertTrue(fixture.scheduler.scheduled.isEmpty())
    }

    @Test
    fun everySuccessfulTaskMutationRequestsWidgetRefresh() = runBlocking {
        val fixture = fixture(task(5, "刷新", "2026-08-25T10:00:00Z"))

        fixture.commands.save(task(5, "已修改", "2026-08-25T10:00:00Z"))
        fixture.commands.setCompleted(5, true, now)
        fixture.commands.setCompleted(5, false, now)
        fixture.commands.delete(5)

        assertEquals(4, fixture.widgetUpdater.updateCount)
    }

    @Test
    fun crossDateRangeStaysSynchronizedAcrossEditCompleteRestoreAndDelete() = runBlocking {
        val original = rangeTask(
            id = 6,
            start = "2026-08-25T10:00:00Z",
            end = "2026-08-27T10:00:00Z",
        )
        val updated = rangeTask(
            id = 6,
            start = "2026-09-01T01:00:00Z",
            end = "2026-12-31T09:00:00Z",
        )
        val fixture = fixture(original)
        val updatedRange = checkNotNull(updated.reminder) as TaskReminder.TimeRange

        fixture.commands.save(updated)

        assertEquals(listOf(6L), fixture.scheduler.cancelled)
        assertEquals(updatedRange.startAt, fixture.scheduler.scheduled.last().startAt)
        assertEquals(updatedRange.endAt, fixture.scheduler.scheduled.last().endAt)

        fixture.commands.setCompleted(6, true, now)
        assertEquals(listOf(6L, 6L), fixture.scheduler.cancelled)
        assertTrue(fixture.store.getAll().isEmpty())

        fixture.commands.setCompleted(6, false, now)
        assertEquals(updatedRange.endAt, fixture.scheduler.scheduled.last().endAt)
        assertEquals(updatedRange.endAt, fixture.store.get(6)?.endAt)

        fixture.commands.delete(6)
        assertEquals(listOf(6L, 6L, 6L, 6L), fixture.scheduler.cancelled)
        assertTrue(fixture.store.getAll().isEmpty())
        assertEquals(null, fixture.repository.findById(6))
    }

    private fun fixture(vararg initial: TodoTask): Fixture {
        val repository = FakeRepository(initial.toList())
        val scheduler = FakeScheduler(now)
        val store = FakeStore()
        initial.mapNotNull { it.toScheduledSnapshot() }.forEach(store::put)
        val widgetUpdater = FakeWidgetUpdater()
        val health = FakeHealthStore()
        return Fixture(
            repository,
            scheduler,
            store,
            widgetUpdater,
            health,
            DefaultTaskCommandService(repository, scheduler, store, widgetUpdater, health),
        )
    }

    private fun task(id: Long, title: String, start: String, systemAlarm: Boolean = false) = TodoTask(
        id = id,
        title = title,
        date = LocalDate.of(2026, 8, 25),
        reminder = TaskReminder.AtTime(Instant.parse(start), systemAlarm),
        createdAt = now,
        updatedAt = now,
    )

    private fun rangeTask(id: Long, start: String, end: String) = TodoTask(
        id = id,
        title = "跨日期持续任务",
        date = LocalDate.parse(start.substringBefore('T')),
        reminder = TaskReminder.TimeRange(
            startAt = Instant.parse(start),
            endAt = Instant.parse(end),
        ),
        createdAt = now,
        updatedAt = now,
    )

    private data class Fixture(
        val repository: FakeRepository,
        val scheduler: FakeScheduler,
        val store: FakeStore,
        val widgetUpdater: FakeWidgetUpdater,
        val health: FakeHealthStore,
        val commands: DefaultTaskCommandService,
    )

    private class FakeWidgetUpdater : WidgetUpdater {
        var updateCount = 0
        override fun requestUpdate() {
            updateCount += 1
        }
    }

    private class FakeScheduler(private val now: Instant) : ReminderScheduler {
        val cancelled = mutableListOf<Long>()
        val scheduled = mutableListOf<ScheduledTaskSnapshot>()
        var nextIssues: Set<ReminderScheduleIssue> = emptySet()

        override fun schedule(
            snapshot: ScheduledTaskSnapshot,
            alertIfRangeAlreadyActive: Boolean,
        ): ReminderScheduleResult {
            scheduled += snapshot
            return ReminderScheduleResult(ReminderPlanner.plan(snapshot, now), nextIssues)
        }

        override fun cancel(taskId: Long) {
            cancelled += taskId
        }
    }

    private class FakeStore : ReminderSnapshotStore {
        private val values = linkedMapOf<Long, ScheduledTaskSnapshot>()
        var failNextPut = false
        override fun get(taskId: Long) = values[taskId]
        override fun getAll() = values.values.toList()
        override fun put(snapshot: ScheduledTaskSnapshot) {
            if (failNextPut) {
                failNextPut = false
                error("simulated snapshot failure")
            }
            values[snapshot.taskId] = snapshot
        }
        override fun remove(taskId: Long) { values.remove(taskId) }
        override fun replaceAll(snapshots: Collection<ScheduledTaskSnapshot>) {
            values.clear()
            snapshots.forEach(::put)
        }
    }

    private class FakeHealthStore : ReminderHealthStore {
        private val values = linkedMapOf<Long, ReminderHealthRecord>()
        override fun get(taskId: Long) = values[taskId]
        override fun getAll() = values.values.toList()
        override fun record(
            taskId: Long,
            state: ReminderHealthState,
            nextTriggerAt: Instant?,
            detail: String?,
        ) {
            values[taskId] = ReminderHealthRecord(
                taskId,
                state,
                Instant.parse("2026-08-25T09:00:00Z"),
                nextTriggerAt,
                detail,
            )
        }
        override fun remove(taskId: Long) { values.remove(taskId) }
    }

    private class FakeRepository(initial: List<TodoTask>) : TaskRepository {
        private val tasks = MutableStateFlow(initial.associateBy(TodoTask::id))
        private var nextId = (initial.maxOfOrNull(TodoTask::id) ?: 0) + 1
        override fun observePending(): Flow<List<TodoTask>> = tasks.map { it.values.filterNot(TodoTask::isCompleted) }
        override fun observeCompleted(): Flow<List<TodoTask>> = tasks.map { it.values.filter(TodoTask::isCompleted) }
        override fun observeForDate(date: LocalDate): Flow<List<TodoTask>> = tasks.map { state -> state.values.filter { it.date == date } }
        override suspend fun findById(id: Long) = tasks.value[id]
        override suspend fun save(task: TodoTask): Long {
            val id = task.id.takeIf { it > 0 } ?: nextId++
            tasks.value = tasks.value + (id to task.copy(id = id))
            return id
        }
        override suspend fun delete(id: Long): Boolean {
            val existed = id in tasks.value
            tasks.value = tasks.value - id
            return existed
        }
        override suspend fun setCompleted(id: Long, completed: Boolean, changedAt: Instant): Boolean {
            val task = tasks.value[id] ?: return false
            tasks.value = tasks.value + (id to task.copy(
                isCompleted = completed,
                completedAt = changedAt.takeIf { completed },
                updatedAt = changedAt,
            ))
            return true
        }
        override suspend fun getSchedulableTasks(now: Instant) = tasks.value.values.filterNot(TodoTask::isCompleted)
    }
}
