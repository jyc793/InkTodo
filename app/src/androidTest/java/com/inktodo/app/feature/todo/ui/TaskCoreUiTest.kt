package com.inktodo.app.feature.todo.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.core.alarm.ReminderAlarmKind
import com.inktodo.app.core.alarm.ReminderAlarmPendingIntents
import com.inktodo.app.core.model.TaskReminder
import com.inktodo.app.core.model.TodoTask
import com.inktodo.app.feature.todo.domain.TaskCommandService
import com.inktodo.app.feature.todo.domain.TaskRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCoreUiTest {
    private lateinit var device: UiDevice
    private lateinit var repository: TaskRepository
    private lateinit var commands: TaskCommandService
    private lateinit var title: String
    private lateinit var editedTitle: String

    @Before
    fun launchProductionApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        val appContainer = (instrumentation.targetContext.applicationContext as InkTodoApplication)
            .appContainer
        repository = appContainer.taskRepository
        commands = appContainer.taskCommandService
        title = "真机待办-${System.currentTimeMillis()}"
        editedTitle = "已编辑-$title"

        device.executeShellCommand("am start -W -n $PACKAGE_NAME/.MainActivity")
        check(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT)) {
            "正式 MainActivity 未在超时内进入前台"
        }
        waitForResource("add_task")
    }

    @After
    fun removeOnlyTasksCreatedByThisTest() = runBlocking {
        val testTasks = repository.observePending().first() + repository.observeCompleted().first()
        testTasks
            .filter { it.title == title || it.title == editedTitle }
            .forEach { runCatching { commands.delete(it.id) } }
    }

    @Test
    fun createEditCompleteRestoreAndDeleteUseRealInstalledAppAndRoom() {
        waitForResource("add_task").click()
        waitForResource("task_title_input").text = title
        waitForResource("task_note_input").text = "真实 Room 数据"
        scrollEditorToBottom()
        waitForResource("save_task").click()
        dismissReliabilityPromptIfPresent()
        waitForText(title)

        val taskId = runBlocking {
            repository.observePending().first().single { it.title == title }.id
        }
        waitForResource("task_item_$taskId").click()
        waitForText("编辑待办")
        waitForResource("task_title_input").apply {
            clear()
            text = editedTitle
        }
        scrollEditorToBottom()
        waitForResource("save_task").click()
        waitForText(editedTitle)

        waitForDescription("完成 任务：$editedTitle").click()
        check(device.wait(Until.gone(By.text(editedTitle)), TIMEOUT)) { "完成后任务仍在待办列表" }
        waitForResource("nav_completed").click()
        waitForText(editedTitle)

        waitForDescription("恢复 任务：$editedTitle").click()
        check(device.wait(Until.gone(By.text(editedTitle)), TIMEOUT)) { "恢复后任务仍在已完成列表" }
        waitForResource("nav_todo").click()
        waitForText(editedTitle)

        waitForResource("delete_task_$taskId").click()
        waitForDescription("确认删除任务").click()
        check(device.wait(Until.gone(By.text(editedTitle)), TIMEOUT)) { "删除后任务仍在待办列表" }
        runBlocking { check(repository.findById(taskId) == null) }
    }

    @Test
    fun blankTitleShowsValidationAndDoesNotCreateTask() {
        waitForResource("add_task").click()
        scrollEditorToBottom()
        waitForResource("save_task").click()
        waitForText("请输入任务名称。")
        runBlocking {
            check(repository.observePending().first().none { it.title == title })
        }
    }

    @Test
    fun reliabilityPageShowsLiveStatusAndHyperOsManualChecks() {
        waitForText("提醒可靠性").click()

        waitForResource("reliability_summary")
        waitForText("通知权限")
        waitForText("精确闹钟能力")
        scrollUntilText("全屏强提醒")
        scrollUntilText("后台无限制")
        scrollUntilText("HyperOS 自启动")
        scrollUntilText("系统闹钟应用")
    }

    @Test
    fun pomodoroAndCalendarBottomRoutesShowRealFeatureContent() {
        waitForResource("nav_pomodoro").click()
        waitForResource("pomodoro_timer")
        scrollUntilAnyResource("pomodoro_start", "pomodoro_pause", "pomodoro_resume")
        waitForText("专注时间")
        waitForText("休息时间")
        waitForText("自定义")

        waitForResource("nav_calendar").click()
        waitForResource("calendar_today")
        waitForResource("calendar_previous")
        waitForResource("calendar_next")
        waitForText("当日无未完成待办。")
    }

    @Test
    fun ordinaryPomodoroImmersionHidesBottomBarAndBackKeepsTimerRunning() {
        waitForResource("nav_pomodoro").click()
        scrollUntilAnyResource("pomodoro_start", "pomodoro_pause", "pomodoro_resume")
        if (!device.hasObject(By.res("pomodoro_start"))) {
            waitForResource("pomodoro_end").click()
            waitForResource("pomodoro_start")
        }
        val compactTimer = waitForResource("pomodoro_timer").visibleBounds
        waitForResource("pomodoro_start").click()

        check(device.wait(Until.gone(By.res("nav_pomodoro")), TIMEOUT)) {
            "普通开始后底栏未在首段转场结束时隐藏"
        }
        val immersiveTimer = waitForResource("pomodoro_timer").visibleBounds
        check(immersiveTimer.width() >= compactTimer.width()) {
            "沉浸态锦鲤环没有放大"
        }
        waitForResource("pomodoro_pause")

        device.pressBack()
        waitForResource("nav_pomodoro")
        waitForResource("pomodoro_pause")
        waitForText("专注时间")

        waitForResource("pomodoro_timer").click()
        check(device.wait(Until.gone(By.res("nav_pomodoro")), TIMEOUT)) {
            "点击运行中的锦鲤环后没有重新进入沉浸态"
        }
        waitForResource("pomodoro_end").click()
        waitForResource("nav_pomodoro")
        waitForResource("pomodoro_start")
    }

    @Test
    fun activeRangeCanOnlyBeStoppedFromTaskDetailWithConfirmationAndPersistsState() {
        title = "持续真机-${System.currentTimeMillis()}"
        editedTitle = title
        val now = Instant.now()
        val taskId = runBlocking {
            commands.save(
                TodoTask(
                    title = title,
                    date = LocalDate.now(),
                    reminder = TaskReminder.TimeRange(
                        startAt = now.minusSeconds(60),
                        endAt = now.plusSeconds(1_800),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
            ).taskId
        }
        checkNotNull(
            ReminderAlarmPendingIntents.find(
                InstrumentationRegistry.getInstrumentation().targetContext,
                taskId,
                ReminderAlarmKind.RANGE_END,
            ),
        )

        waitForText(title).click()
        waitForText("编辑待办")
        scrollEditorToBottom()
        waitForResource("stop_ongoing_reminder").click()
        waitForDescription("确认结束持续提醒").click()
        check(device.wait(Until.gone(By.res("stop_ongoing_reminder")), TIMEOUT)) {
            "确认结束后仍显示持续提醒结束入口"
        }

        val stoppedRange = runBlocking {
            requireNotNull(repository.findById(taskId)).reminder as TaskReminder.TimeRange
        }
        check(stoppedRange.isManuallyStopped) { "手动结束状态未写入 Room" }
        check(
            ReminderAlarmPendingIntents.find(
                InstrumentationRegistry.getInstrumentation().targetContext,
                taskId,
                ReminderAlarmKind.RANGE_END,
            ) == null,
        ) { "结束持续提醒后仍残留结束 Alarm" }
    }

    private fun scrollEditorToBottom() {
        device.waitForIdle()
        repeat(4) {
            if (device.hasObject(By.res("save_task"))) return
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
    }

    private fun scrollUntilText(text: String) {
        repeat(6) {
            if (device.hasObject(By.text(text))) return
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                24,
            )
            device.waitForIdle()
        }
        waitForText(text)
    }

    private fun scrollUntilAnyResource(vararg resourceNames: String) {
        repeat(6) {
            if (resourceNames.any { device.hasObject(By.res(it)) }) return
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                24,
            )
            device.waitForIdle()
        }
        check(resourceNames.any { device.hasObject(By.res(it)) }) {
            "未找到任一资源标记：${resourceNames.joinToString()}"
        }
    }

    private fun dismissReliabilityPromptIfPresent() {
        val later = device.wait(Until.findObject(By.text("稍后")), 1_500L) ?: return
        later.click()
        device.waitForIdle()
    }

    private fun waitForResource(resourceName: String) =
        checkNotNull(device.wait(Until.findObject(By.res(resourceName)), TIMEOUT)) {
            "未找到资源标记：$resourceName"
        }

    private fun waitForText(text: String) =
        checkNotNull(device.wait(Until.findObject(By.text(text)), TIMEOUT)) {
            "未找到文本：$text"
        }

    private fun waitForDescription(description: String) =
        checkNotNull(device.wait(Until.findObject(By.desc(description)), TIMEOUT)) {
            "未找到无障碍描述：$description"
        }

    private companion object {
        const val PACKAGE_NAME = "com.inktodo.app"
        const val TIMEOUT = 8_000L
    }
}
