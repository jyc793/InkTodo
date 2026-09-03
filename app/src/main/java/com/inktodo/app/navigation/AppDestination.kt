package com.inktodo.app.navigation

enum class AppDestination(
    val route: String,
    val label: String,
    val mark: String,
    val isBottomDestination: Boolean,
) {
    Todo("todo", "待办", "山", true),
    Calendar("calendar", "日历", "月", true),
    Pomodoro("pomodoro", "番茄钟", "水", true),
    Completed("completed", "已完成", "雾", true),
    Settings("settings", "提醒可靠性", "石", false),
    EditTask("task/edit", "新建待办", "纸", false),
    ;

    companion object {
        const val EDIT_TASK_PATTERN = "task/edit/{taskId}"
        val bottomDestinations: List<AppDestination> = entries.filter { it.isBottomDestination }

        fun editTaskRoute(taskId: Long): String = "task/edit/$taskId"
    }
}
