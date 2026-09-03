package com.inktodo.app.core.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.os.Build
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reschedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reschedule(context: Context) {
        val container = (context.applicationContext as InkTodoApplication).appContainer
        val userManager = context.getSystemService(UserManager::class.java)
        container.reminderRecoveryCoordinator.recover(userManager.isUserUnlocked)
        if (userManager.isUserUnlocked) {
            container.pomodoroCoordinator.recover()
            container.widgetUpdater.requestUpdate()
        }
    }

    private fun supportedActions(): Set<String> = buildSet {
        add(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        add(Intent.ACTION_BOOT_COMPLETED)
        add(Intent.ACTION_MY_PACKAGE_REPLACED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        }
    }
}

class ReminderRecoveryCoordinator(
    private val repository: () -> TaskRepository,
    private val snapshotStore: ReminderSnapshotStore,
    private val scheduler: ReminderScheduler,
    private val healthStore: ReminderHealthStore = NoOpReminderHealthStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    constructor(
        repository: TaskRepository,
        snapshotStore: ReminderSnapshotStore,
        scheduler: ReminderScheduler,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        repository = { repository },
        snapshotStore = snapshotStore,
        scheduler = scheduler,
        healthStore = NoOpReminderHealthStore,
        clock = clock,
    )

    suspend fun recover(userUnlocked: Boolean) {
        val previousSnapshots = snapshotStore.getAll()
        val now = Instant.now(clock)
        val awaitingConfirmation = previousSnapshots.filter { snapshot ->
            snapshot.kind == ReminderSnapshotKind.AT_TIME &&
                !snapshot.useSystemAlarm &&
                !snapshot.startAt.isAfter(now) &&
                healthStore.get(snapshot.taskId)?.state == ReminderHealthState.AWAITING_CONFIRMATION
        }
        val awaitingIds = awaitingConfirmation.mapTo(mutableSetOf(), ScheduledTaskSnapshot::taskId)
        val currentSnapshots = if (userUnlocked) {
            (repository().getSchedulableTasks(now)
                .mapNotNull { it.toScheduledSnapshot() }
                .filterNot { it.taskId in awaitingIds } + awaitingConfirmation)
                .also(snapshotStore::replaceAll)
        } else {
            previousSnapshots
        }
        val currentTaskIds = currentSnapshots.mapTo(mutableSetOf(), ScheduledTaskSnapshot::taskId)
        if (userUnlocked) {
            healthStore.getAll()
                .asSequence()
                .filter(ReminderHealthRecord::needsAttention)
                .map(ReminderHealthRecord::taskId)
                .filterNot(currentTaskIds::contains)
                .forEach { taskId -> runCatching { healthStore.remove(taskId) } }
        }
        previousSnapshots
            .asSequence()
            .map(ScheduledTaskSnapshot::taskId)
            .filterNot(currentTaskIds::contains)
            .forEach(scheduler::cancel)
        currentSnapshots.forEach { snapshot ->
            if (snapshot.taskId in awaitingIds) {
                scheduler.restoreAwaitingConfirmation(snapshot)
                return@forEach
            }
            runCatching { scheduler.schedule(snapshot, alertIfRangeAlreadyActive = false) }
                .onSuccess { result ->
                    runCatching {
                        healthStore.record(
                            snapshot.taskId,
                            if (result.issues.isEmpty()) ReminderHealthState.SCHEDULED
                            else ReminderHealthState.SCHEDULED_WITH_WARNING,
                            result.plan.alarms.minByOrNull { it.triggerAt }?.triggerAt,
                            "系统恢复已重新注册提醒",
                        )
                    }
                }
                .onFailure { error ->
                    runCatching {
                        healthStore.record(
                            snapshot.taskId,
                            ReminderHealthState.FAILED,
                            detail = "系统恢复注册失败：${error.message}",
                        )
                    }
                }
        }
    }
}
