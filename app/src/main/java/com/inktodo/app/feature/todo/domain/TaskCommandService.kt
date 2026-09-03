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
import com.inktodo.app.core.widget.NoOpWidgetUpdater
import com.inktodo.app.core.widget.WidgetUpdater
import java.time.Instant

data class SystemAlarmRequest(
    val title: String,
    val triggerAt: Instant,
    val taskId: Long = 0L,
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
        oldTask?.let(::cancelBeforeMutation)
        val savedId = try {
            repository.save(task)
        } catch (error: Throwable) {
            oldTask?.let(::restoreReminderBestEffort)
            throw error
        }
        val savedTask = checkNotNull(repository.findById(savedId)) { "保存后的任务不存在" }
        val issues = syncReminder(savedTask).toMutableSet()
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
        cancelBeforeMutation(oldTask)
        val deleted = try {
            repository.delete(taskId)
        } catch (error: Throwable) {
            restoreReminderBestEffort(oldTask)
            throw error
        }
        if (!deleted) {
            restoreReminderBestEffort(oldTask)
            error("任务不存在")
        }
        refreshWidgetBestEffort()
        return TaskCommandResult(
            taskId = taskId,
            issues = buildSet {
                if (oldTask.hasSystemAlarm()) add(TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW)
            },
        )
    }

    override suspend fun setCompleted(
        taskId: Long,
        completed: Boolean,
        changedAt: Instant,
    ): TaskCommandResult {
        val oldTask = checkNotNull(repository.findById(taskId)) { "任务不存在" }
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
                if (completed && oldTask.hasSystemAlarm()) {
                    add(TaskCommandIssue.SYSTEM_ALARM_REQUIRES_MANUAL_REVIEW)
                }
            },
            systemAlarmRequest = if (completed || !updatedTask.reminderStartIsAfter(changedAt)) {
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
    TaskCommandIssue.NOTIFICATION_PERMISSION_UNAVAILABLE -> "通知权限未开启，系统到时可能无法显示提醒。"
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

private fun TodoTask.systemAlarmRequestIfNeeded(oldTask: TodoTask?): SystemAlarmRequest? {
    val atTime = reminder as? TaskReminder.AtTime ?: return null
    if (!atTime.useSystemAlarm) return null
    val oldAtTime = oldTask?.reminder as? TaskReminder.AtTime
    if (
        oldAtTime?.useSystemAlarm == true &&
        oldAtTime.startAt == atTime.startAt &&
        oldTask.title == title
    ) {
        return null
    }
    return SystemAlarmRequest(title, atTime.startAt, id)
}

private fun TodoTask.requiresSystemAlarmReviewComparedWith(newTask: TodoTask): Boolean {
    val oldAtTime = reminder as? TaskReminder.AtTime ?: return false
    if (!oldAtTime.useSystemAlarm) return false
    val newAtTime = newTask.reminder as? TaskReminder.AtTime
    return newAtTime?.useSystemAlarm != true ||
        newAtTime.startAt != oldAtTime.startAt ||
        newTask.title != title
}
