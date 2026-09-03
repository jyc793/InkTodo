package com.inktodo.app.feature.todo.domain

import com.inktodo.app.core.datastore.UserPreferences
import com.inktodo.app.core.model.TaskSortOrder
import com.inktodo.app.core.model.TodoTask

fun TaskDraft.withReusablePreferences(preferences: UserPreferences): TaskDraft = copy(
    reminderType = preferences.lastReminderType,
    useSystemAlarm = preferences.lastUseSystemAlarm,
)

fun List<TodoTask>.sortedByPreference(order: TaskSortOrder): List<TodoTask> = when (order) {
    TaskSortOrder.TIME_ASCENDING -> sortedWith(
        compareBy<TodoTask> { it.date }
            .thenBy { it.reminder?.startAt }
            .thenBy { it.createdAt },
    )
    TaskSortOrder.CREATED_DESCENDING -> sortedByDescending(TodoTask::createdAt)
}
