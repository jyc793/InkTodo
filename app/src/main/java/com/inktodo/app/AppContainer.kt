package com.inktodo.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.inktodo.app.core.database.DatabaseMigrations
import com.inktodo.app.core.database.InkTodoDatabase
import com.inktodo.app.core.alarm.AndroidReminderScheduler
import com.inktodo.app.core.alarm.DeviceProtectedReminderSnapshotStore
import com.inktodo.app.core.alarm.ReminderScheduler
import com.inktodo.app.core.alarm.ReminderSnapshotStore
import com.inktodo.app.core.alarm.ReminderRecoveryCoordinator
import com.inktodo.app.core.alarm.DeviceProtectedReminderHealthStore
import com.inktodo.app.core.alarm.ReminderHealthStore
import com.inktodo.app.core.datastore.DataStorePreferencesRepository
import com.inktodo.app.core.datastore.PreferencesRepository
import com.inktodo.app.core.notification.AndroidTaskNotificationController
import com.inktodo.app.core.notification.TaskNotificationController
import com.inktodo.app.feature.todo.data.RoomTaskRepository
import com.inktodo.app.feature.todo.domain.TaskRepository
import com.inktodo.app.feature.todo.domain.DefaultTaskCommandService
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskCommandIssueNoticeGate
import com.inktodo.app.feature.settings.data.AndroidReminderReliabilityChecker
import com.inktodo.app.feature.settings.data.ReminderReliabilityChecker
import com.inktodo.app.feature.settings.data.ReminderSettingsNavigator
import com.inktodo.app.core.alarm.AndroidPomodoroAlarmScheduler
import com.inktodo.app.core.alarm.PomodoroAlarmScheduler
import com.inktodo.app.core.notification.AndroidPomodoroNotificationController
import com.inktodo.app.core.notification.PomodoroNotificationController
import com.inktodo.app.feature.pomodoro.domain.PomodoroCoordinator
import com.inktodo.app.core.widget.AndroidWidgetUpdater
import com.inktodo.app.core.widget.WidgetUpdater
import com.inktodo.app.core.focus.AndroidDoNotDisturbController
import com.inktodo.app.core.focus.DoNotDisturbController
import com.inktodo.app.core.focus.FullFocusController

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

interface AppContainer {
    val taskRepository: TaskRepository
    val preferencesRepository: PreferencesRepository
    val reminderSnapshotStore: ReminderSnapshotStore
    val reminderHealthStore: ReminderHealthStore
    val notificationController: TaskNotificationController
    val reminderScheduler: ReminderScheduler
    val reminderRecoveryCoordinator: ReminderRecoveryCoordinator
    val taskCommandService: TaskCommandService
    val taskCommandIssueNoticeGate: TaskCommandIssueNoticeGate
    val reminderReliabilityChecker: ReminderReliabilityChecker
    val reminderSettingsNavigator: ReminderSettingsNavigator
    val pomodoroAlarmScheduler: PomodoroAlarmScheduler
    val pomodoroNotificationController: PomodoroNotificationController
    val pomodoroCoordinator: PomodoroCoordinator
    val fullFocusController: FullFocusController
    val widgetUpdater: WidgetUpdater
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val applicationContext = context.applicationContext

    private val database: InkTodoDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            InkTodoDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
    }

    override val taskRepository: TaskRepository by lazy {
        RoomTaskRepository(database.taskDao())
    }

    override val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(applicationContext.userPreferencesDataStore)
    }

    override val reminderSnapshotStore: ReminderSnapshotStore by lazy {
        DeviceProtectedReminderSnapshotStore(applicationContext)
    }

    override val reminderHealthStore: ReminderHealthStore by lazy {
        DeviceProtectedReminderHealthStore(applicationContext)
    }

    override val notificationController: TaskNotificationController by lazy {
        AndroidTaskNotificationController(applicationContext)
    }

    override val reminderScheduler: ReminderScheduler by lazy {
        AndroidReminderScheduler(applicationContext, notificationController)
    }

    override val reminderRecoveryCoordinator: ReminderRecoveryCoordinator by lazy {
        ReminderRecoveryCoordinator(
            repository = { taskRepository },
            snapshotStore = reminderSnapshotStore,
            scheduler = reminderScheduler,
            healthStore = reminderHealthStore,
        )
    }

    override val taskCommandService: TaskCommandService by lazy {
        DefaultTaskCommandService(
            taskRepository,
            reminderScheduler,
            reminderSnapshotStore,
            widgetUpdater,
            reminderHealthStore,
        )
    }

    override val taskCommandIssueNoticeGate: TaskCommandIssueNoticeGate by lazy {
        TaskCommandIssueNoticeGate()
    }

    override val reminderReliabilityChecker: ReminderReliabilityChecker by lazy {
        AndroidReminderReliabilityChecker(applicationContext, notificationController, reminderHealthStore)
    }

    override val reminderSettingsNavigator: ReminderSettingsNavigator by lazy {
        ReminderSettingsNavigator(applicationContext)
    }

    override val pomodoroAlarmScheduler: PomodoroAlarmScheduler by lazy {
        AndroidPomodoroAlarmScheduler(applicationContext)
    }

    override val pomodoroNotificationController: PomodoroNotificationController by lazy {
        AndroidPomodoroNotificationController(applicationContext)
    }

    private val doNotDisturbController: DoNotDisturbController by lazy {
        AndroidDoNotDisturbController(applicationContext)
    }

    override val fullFocusController: FullFocusController by lazy {
        FullFocusController(preferencesRepository, doNotDisturbController)
    }

    override val pomodoroCoordinator: PomodoroCoordinator by lazy {
        PomodoroCoordinator(
            preferencesRepository,
            pomodoroAlarmScheduler,
            pomodoroNotificationController,
            fullFocusController = fullFocusController,
        )
    }

    override val widgetUpdater: WidgetUpdater by lazy {
        AndroidWidgetUpdater(applicationContext)
    }

    private companion object {
        const val DATABASE_NAME = "ink_todo.db"
    }
}
