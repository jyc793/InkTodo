package com.inktodo.app.feature.todo.domain

class TaskCommandIssueNoticeGate {
    fun takeUnseen(issues: Set<TaskCommandIssue>): Set<TaskCommandIssue> = issues
}
