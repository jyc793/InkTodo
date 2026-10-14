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
    TaskDetail("task/detail", "待办详情", "纸", false),
    SpecialDates("calendar/special-dates", "特殊日期", "月", false),
    EditSpecialDate("calendar/special-date/edit", "编辑特殊日期", "月", false),
    HolidayDataInfo("calendar/holiday-data", "节假日数据说明", "月", false),
    ;

    companion object {
        const val EDIT_TASK_PATTERN = "task/edit/{taskId}"
        const val TASK_DETAIL_PATTERN = "task/detail/{taskId}"
        const val EDIT_SPECIAL_DATE_PATTERN = "calendar/special-date/edit/{specialDateId}"
        val bottomDestinations: List<AppDestination> = entries.filter { it.isBottomDestination }

        fun editTaskRoute(taskId: Long): String = "task/edit/$taskId"
        fun taskDetailRoute(taskId: Long): String = "task/detail/$taskId"
        fun editSpecialDateRoute(specialDateId: Long): String = "calendar/special-date/edit/$specialDateId"
    }
}
