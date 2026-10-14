package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.alarm.ReminderScheduleIssue
import com.inktodo.app.core.alarm.ReminderHealthState
import com.inktodo.app.core.alarm.ReminderHealthStore
import com.inktodo.app.core.alarm.NoOpReminderHealthStore
import com.inktodo.app.core.alarm.ReminderScheduler
import com.inktodo.app.core.alarm.ReminderSnapshotStore
import com.inktodo.app.core.alarm.ScheduledTaskSnapshot
import com.inktodo.app.core.alarm.toScheduledSnapshot
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.core.model.nextOccurrenceAfter
import com.inktodo.app.core.widget.NoOpWidgetUpdater
import com.inktodo.app.core.widget.WidgetUpdater
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId

data class SystemAlarmRequest(
    val title: String,
    val triggerAt: Instant,
    val taskId: Long = 0L,
    val repeatDays: Set<DayOfWeek> = emptySet(),
)

enum class TaskCommandIssue {
    EXACT_ALARM_UNAVAILABLE,
    NOTIFICATION_PERMISSION_UNAVAILABLE,
    OVERLAY_PERMISSION_UNAVAILABLE,
    REMINDER_SYNC_FAILED,
    SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW,
}

data class TaskCommandResult(
    val taskId: Long,
    val issues: Set<TaskCommandIssue> = emptySet(),
    val systemAlarmRequest: SystemAlarmRequest? = null,
)

interface TaskCommandService {
    suspend fun save(task: TodoTask): TaskCommandResult
    suspend fun delete(taskId: Long): TaskCommandResult
    suspend fun setCompleted(taskId: Long, completed: Boolean, changedAt: Instant): TaskCommandResult
    suspend fun stopOngoingReminder(taskId: Long, changedAt: Instant): TaskCommandResult
    suspend fun advanceRecurrence(taskId: Long, after: Instant): TaskCommandResult? = null
    suspend fun pruneExpiredRecurringTasks(now: Instant): Int = 0
}

class DefaultTaskCommandService(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val snapshotStore: ReminderSnapshotStore,
    private val widgetUpdater: WidgetUpdater = NoOpWidgetUpdater,
    private val healthStore: ReminderHealthStore = NoOpReminderHealthStore,
) : TaskCommandService {
    override suspend fun save(task: TodoTask): TaskCommandResult {
        val oldTask = task.id.takeIf { it > 0 }?.let { repository.findById(it) }
        task.recurrenceSeriesId?.let { seriesId ->
            val existingOnDate = repository.findSeriesOccurrence(seriesId, task.date)
            require(existingOnDate == null || existingOnDate.id == task.id) {
                "这一天已有同系列的重复任务，请选择其他日期。"
            }
        }
        oldTask?.let(::cancelBeforeMutation)
        val savedId = try {
            repository.save(task)
        } catch (error: Throwable) {
            oldTask?.let(::restoreReminderBestEffort)
            throw error
        }
        val savedTask = checkNotNull(repository.findById(savedId)) { "保存后的任务不存在" }
        val issues = syncReminder(savedTask).toMutableSet()
        if (savedTask.recurrence.isRepeating &&
            (savedTask.reminder as? TaskReminder.TimeRange)?.startAt?.isBefore(Instant.now()) == true) {
            advanceRecurrence(savedId, Instant.now())?.let { issues += it.issues }
        }
        refreshWidgetBestEffort()
        if (oldTask?.requiresSystemAlarmReviewComparedWith(savedTask) == true) {
            issues += TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW
        }
        return TaskCommandResult(
            taskId = savedId,
            issues = issues,
            systemAlarmRequest = savedTask.systemAlarmRequestIfNeeded(oldTask),
        )
    }

    override suspend fun delete(taskId: Long): TaskCommandResult {
        val oldTask = checkNotNull(repository.findById(taskId)) { "任务不存在" }
        val targets = if (!oldTask.isCompleted && oldTask.recurrenceSeriesId != null) {
            repository.getPendingSeriesTasks(oldTask.recurrenceSeriesId)
                .ifEmpty { listOf(oldTask) }
        } else listOf(oldTask)
        targets.forEach { target ->
            cancelBeforeMutation(target)
            val deleted = try {
                repository.delete(target.id)
            } catch (error: Throwable) {
                restoreReminderBestEffort(target)
                throw error
            }
            if (!deleted) error("任务不存在")
        }
        refreshWidgetBestEffort()
        return TaskCommandResult(
            taskId = taskId,
            issues = buildSet {
                if (targets.any(TodoTask::hasSystemAlarm)) add(TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW)
            },
        )
    }

    override suspend fun setCompleted(
        taskId: Long,
        completed: Boolean,
        changedAt: Instant,
    ): TaskCommandResult {
        val oldTask = checkNotNull(repository.findById(taskId)) { "任务不存在" }
        val advanceIssues = if (completed && oldTask.recurrence.isRepeating) {
            advanceRecurrence(taskId, changedAt)?.issues.orEmpty()
        } else emptySet()
        cancelBeforeMutation(oldTask)
        val changed = try {
            repository.setCompleted(taskId, completed, changedAt)
        } catch (error: Throwable) {
            restoreReminderBestEffort(oldTask)
            throw error
        }
        if (!changed) {
            restoreReminderBestEffort(oldTask)
            error("任务不存在")
        }
        val updatedTask = checkNotNull(repository.findById(taskId)) { "任务不存在" }
        val issues = if (completed) emptySet() else syncReminder(updatedTask)
        refreshWidgetBestEffort()
        return TaskCommandResult(
            taskId = taskId,
            issues = buildSet {
                addAll(issues)
                addAll(advanceIssues)
                if (completed && oldTask.hasSystemAlarm() && !oldTask.recurrence.isRepeating) {
                    add(TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW)
                }
            },
            systemAlarmRequest = if (completed || updatedTask.recurrence.isRepeating ||
                !updatedTask.reminderStartIsAfter(changedAt)) {
                null
            } else {
                updatedTask.systemAlarmRequestIfNeeded(null)
            },
        )
    }

    override suspend fun stopOngoingReminder(taskId: Long, changedAt: Instant): TaskCommandResult {
        val oldTask = checkNotNull(repository.findById(taskId)) { "任务不存在" }
        val range = oldTask.reminder as? TaskReminder.TimeRange
            ?: error("此任务不是持续提醒")
        check(!range.isManuallyStopped) { "持续提醒已经结束" }
        check(!changedAt.isBefore(range.startAt) && changedAt.isBefore(range.endAt)) {
            "当前不在持续提醒时间段内"
        }
        cancelBeforeMutation(oldTask)
        val stoppedTask = oldTask.copy(
            reminder = range.copy(isManuallyStopped = true),
            updatedAt = changedAt,
        )
        try {
            repository.save(stoppedTask)
        } catch (error: Throwable) {
            restoreReminderBestEffort(oldTask)
            throw error
        }
        refreshWidgetBestEffort()
        return TaskCommandResult(taskId = taskId)
    }

    override suspend fun advanceRecurrence(taskId: Long, after: Instant): TaskCommandResult? {
        val source = repository.findById(taskId) ?: return null
        val seriesId = source.recurrenceSeriesId ?: return null
        // Recovery may need to reconstruct a future occurrence from a completed old row.
        var next = source.copy(isCompleted = false).nextOccurrenceAfter(
            after, ZoneId.systemDefault(), Instant.now(),
        ) ?: return null
        var skippedCompleted = 0
        while (repository.findSeriesOccurrence(seriesId, next.date)?.isCompleted == true) {
            if (++skippedCompleted > 366) error("Too many completed recurrence dates")
            next = next.nextOccurrenceAfter(next.reminder!!.startAt, ZoneId.systemDefault(), Instant.now())
                ?: return null
        }
        if (repository.findSeriesOccurrence(seriesId, next.date) != null) return null
        val savedId = try {
            repository.save(next)
        } catch (error: Throwable) {
            if (repository.findSeriesOccurrence(seriesId, next.date) != null) return null
            throw error
        }
        val saved = checkNotNull(repository.findById(savedId))
        val issues = syncReminder(saved)
        refreshWidgetBestEffort()
        return TaskCommandResult(savedId, issues)
    }

    override suspend fun pruneExpiredRecurringTasks(now: Instant): Int {
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        var removed = 0
        repository.getExpiredRecurringTasks(today).forEach { task ->
            if (!task.isCompleted && !task.hasEndedBy(now)) return@forEach
            val seriesId = task.recurrenceSeriesId ?: return@forEach
            // A future row must exist before removing the last rule carrier.
            if (repository.getPendingSeriesTasks(seriesId).none { !it.date.isBefore(today) }) {
                advanceRecurrence(task.id, now)
            }
            val hasNext = repository.getPendingSeriesTasks(seriesId)
                .any { it.id != task.id && !it.date.isBefore(today) }
            if (!hasNext) return@forEach
            cancelBeforeMutation(task)
            if (repository.delete(task.id)) removed++
        }
        if (removed > 0) refreshWidgetBestEffort()
        return removed
    }

    private fun syncReminder(task: TodoTask): Set<TaskCommandIssue> {
        val snapshot = task.toScheduledSnapshot()
        if (snapshot == null) {
            cancelAndRemove(task.id)
            return emptySet()
        }
        val issues = mutableSetOf<TaskCommandIssue>()
        val snapshotSaved = runCatching { snapshotStore.put(snapshot) }
            .onFailure { error ->
                issues += TaskCommandIssue.REMINDER_SYNC_FAILED
                recordHealthBestEffort(task.id, ReminderHealthState.FAILED, detail = "快照写入失败：${error.message}")
            }
            .isSuccess
        if (!snapshotSaved) return issues
        runCatching { scheduler.schedule(snapshot) }
            .onSuccess { result ->
                issues += result.issues.map(ReminderScheduleIssue::toTaskCommandIssue)
                val nextTrigger = result.plan.alarms.minByOrNull { it.triggerAt }?.triggerAt
                recordHealthBestEffort(
                    task.id,
                    if (result.issues.isEmpty()) {
                        ReminderHealthState.SCHEDULED
                    } else {
                        ReminderHealthState.SCHEDULED_WITH_WARNING
                    },
                    nextTrigger,
                    result.issues.joinToString().ifBlank {
                        "提醒已注册${if (snapshot.useSystemAlarm) "；正式时间由系统时钟负责" else ""}"
                    },
                )
                if (result.plan.alarms.isEmpty() && !result.plan.startRangeImmediately) {
                    runCatching { snapshotStore.remove(task.id) }
                        .onFailure { issues += TaskCommandIssue.REMINDER_SYNC_FAILED }
                }
            }
            .onFailure { error ->
                issues += TaskCommandIssue.REMINDER_SYNC_FAILED
                recordHealthBestEffort(task.id, ReminderHealthState.FAILED, detail = "Alarm 注册失败：${error.message}")
            }
        return issues
    }

    private fun cancelAndRemove(taskId: Long) {
        scheduler.cancel(taskId)
        snapshotStore.remove(taskId)
        recordHealthBestEffort(taskId, ReminderHealthState.CANCELLED, detail = "提醒已取消")
    }

    private fun cancelBeforeMutation(oldTask: TodoTask) {
        try {
            cancelAndRemove(oldTask.id)
        } catch (error: Throwable) {
            restoreReminderBestEffort(oldTask)
            throw error
        }
    }

    private fun restoreReminderBestEffort(task: TodoTask) {
        task.toScheduledSnapshot()?.let { snapshot ->
            runCatching { snapshotStore.put(snapshot) }
            runCatching { scheduler.schedule(snapshot, alertIfRangeAlreadyActive = false) }
        }
    }

    private fun refreshWidgetBestEffort() {
        runCatching(widgetUpdater::requestUpdate)
    }

    private fun recordHealthBestEffort(
        taskId: Long,
        state: ReminderHealthState,
        nextTriggerAt: Instant? = null,
        detail: String? = null,
    ) {
        runCatching { healthStore.record(taskId, state, nextTriggerAt, detail) }
    }
}

fun TaskCommandIssue.userMessage(): String = when (this) {
    TaskCommandIssue.EXACT_ALARM_UNAVAILABLE -> "精确闹钟能力未开启，当前使用系统近似时间调度。"
    TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE -> "通知权限或提醒渠道未开启，系统到时可能无法显示提醒。"
    TaskCommandIssue.OVERLAY_PERMISSION_UNAVAILABLE -> "页面上层显示权限未开启，解锁使用手机时只能显示顶部通知。"
    TaskCommandIssue.REMINDER_SYNC_FAILED -> "任务已保存，但提醒同步失败，请重新编辑并保存。"
    TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW -> "系统时钟中的旧闹钟无法由本 App 精确删除，请在时钟中确认。"
}

private fun ReminderScheduleIssue.toTaskCommandIssue(): TaskCommandIssue = when (this) {
    ReminderScheduleIssue.EXACT_ALARM_UNAVAILABLE -> TaskCommandIssue.EXACT_ALARM_UNAVAILABLE
    ReminderScheduleIssue.NOTIFICATION_PERMISSION_UNAVAILABLE ->
        TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE
    ReminderScheduleIssue.OVERLAY_PERMISSION_UNAVAILABLE ->
        TaskCommandIssue.OVERLAY_PERMISSION_UNAVAILABLE
}

private fun TodoTask.hasSystemAlarm(): Boolean = (reminder as? TaskReminder.AtTime)?.useSystemAlarm == true

private fun TodoTask.reminderStartIsAfter(instant: Instant): Boolean = reminder?.startAt?.isAfter(instant) == true

private fun TodoTask.hasEndedBy(now: Instant): Boolean = when (val value = reminder) {
    is TaskReminder.AtTime -> !value.startAt.isAfter(now)
    is TaskReminder.TimeRange -> !value.endAt.isAfter(now)
    null -> true
}

private fun TodoTask.systemAlarmRequestIfNeeded(oldTask: TodoTask?): SystemAlarmRequest? {
    val atTime = reminder as? TaskReminder.AtTime ?: return null
    if (!atTime.useSystemAlarm) return null
    val oldAtTime = oldTask?.reminder as? TaskReminder.AtTime
    if (
        oldAtTime?.useSystemAlarm == true &&
        oldAtTime.startAt == atTime.startAt &&
        oldTask.title == title &&
        oldTask.recurrence == recurrence
    ) {
        return null
    }
    return SystemAlarmRequest(
        title, atTime.startAt, id,
        if (recurrence.isRepeating) DayOfWeek.entries.filterTo(mutableSetOf(), recurrence::includes)
        else emptySet(),
    )
}

private fun TodoTask.requiresSystemAlarmReviewComparedWith(newTask: TodoTask): Boolean {
    val oldAtTime = reminder as? TaskReminder.AtTime ?: return false
    if (!oldAtTime.useSystemAlarm) return false
    val newAtTime = newTask.reminder as? TaskReminder.AtTime
    return newAtTime?.useSystemAlarm != true ||
        newAtTime.startAt != oldAtTime.startAt ||
        newTask.title != title ||
        newTask.recurrence != recurrence
}
