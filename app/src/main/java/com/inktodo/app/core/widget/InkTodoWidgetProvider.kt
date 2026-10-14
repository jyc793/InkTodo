package com.inktodo.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.inktodo.app.InkTodoApplication
import com.inktodo.app.MainActivity
import com.inktodo.app.R
import com.inktodo.app.core.model.ThemeMode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InkTodoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in DATE_BOUNDARY_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = android.content.ComponentName(context, InkTodoWidgetProvider::class.java)
            updateAsync(context, manager, manager.getAppWidgetIds(provider))
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun updateAsync(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgets(context.applicationContext, manager, widgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        val container = (context as InkTodoApplication).appContainer
        val today = LocalDate.now(ZoneId.systemDefault())
        val tasksResult = runCatching { container.taskRepository.observeForDate(today).first() }
        val preferences = container.preferencesRepository.preferences.first()
        val palette = WidgetThemePalette.forMode(preferences.themeMode)
        widgetIds.forEach { widgetId ->
            val options = manager.getAppWidgetOptions(widgetId)
            val spec = WidgetSizePolicy.resolve(
                minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP),
                minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP),
                userMaxTasks = preferences.widgetMaxTasks,
            )
            val views = tasksResult.fold(
                onSuccess = { tasks ->
                    buildViews(
                        context,
                        WidgetTaskContentFactory.create(tasks, spec),
                        spec,
                        palette,
                    )
                },
                onFailure = { buildFailureViews(context, palette) },
            )
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildViews(
        context: Context,
        content: WidgetTaskContent,
        spec: WidgetLayoutSpec,
        palette: WidgetThemePalette?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_ink_todo).apply {
        applyRootPalette(palette)
        setTextViewText(R.id.widget_title, "今日 · ${content.totalPendingCount}件待办")
        setOnClickPendingIntent(R.id.widget_header, WidgetPendingIntents.openApp(context))
        setOnClickPendingIntent(R.id.widget_empty, WidgetPendingIntents.openApp(context))
        removeAllViews(R.id.widget_rows)
        setViewVisibility(R.id.widget_empty, if (content.rows.isEmpty()) View.VISIBLE else View.GONE)
        content.rows.forEach { row ->
            val rowViews = RemoteViews(
                context.packageName,
                if (spec.showSecondaryText) R.layout.widget_task_row_expanded else R.layout.widget_task_row_compact,
            ).apply {
                applyRowPalette(palette, spec.showSecondaryText)
                setTextViewText(R.id.widget_task_time, row.timeLabel)
                setTextViewText(R.id.widget_task_title, row.title)
                if (spec.showSecondaryText) {
                    setTextViewText(R.id.widget_task_note, row.note ?: "轻触查看详情")
                }
                setContentDescription(R.id.widget_task_row, "${row.timeLabel}，${row.title}")
                setOnClickPendingIntent(R.id.widget_task_row, WidgetPendingIntents.openTask(context, row.taskId))
                setTextViewText(R.id.widget_task_complete, if (spec.showSecondaryText) "完成" else "完")
                setContentDescription(R.id.widget_task_complete, "完成任务：${row.title}")
                setOnClickPendingIntent(
                    R.id.widget_task_complete,
                    WidgetPendingIntents.confirmComplete(context, row.taskId, row.title),
                )
            }
            addView(R.id.widget_rows, rowViews)
        }
    }

    private fun buildFailureViews(context: Context, palette: WidgetThemePalette?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_ink_todo).apply {
            applyRootPalette(palette)
            setTextViewText(R.id.widget_title, "今日待办")
            removeAllViews(R.id.widget_rows)
            setViewVisibility(R.id.widget_empty, View.VISIBLE)
            setTextViewText(R.id.widget_empty, "暂时无法读取任务，轻触打开墨待")
            setOnClickPendingIntent(R.id.widget_header, WidgetPendingIntents.openApp(context))
            setOnClickPendingIntent(R.id.widget_empty, WidgetPendingIntents.openApp(context))
        }

    private companion object {
        const val DEFAULT_WIDTH_DP = 110
        const val DEFAULT_HEIGHT_DP = 110
        val DATE_BOUNDARY_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

private fun RemoteViews.applyRootPalette(palette: WidgetThemePalette?) {
    palette ?: return
    setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackground)
    setImageViewResource(R.id.widget_mountain_veil, palette.mountainVeil)
    setInt(R.id.widget_rows, "setBackgroundResource", palette.rowsBackground)
    setInt(R.id.widget_seal, "setBackgroundResource", palette.sealBackground)
    setTextColor(R.id.widget_title, palette.ink)
    setTextColor(R.id.widget_empty, palette.mist)
    setTextColor(R.id.widget_seal, palette.cinnabar)
}

private fun RemoteViews.applyRowPalette(palette: WidgetThemePalette?, expanded: Boolean) {
    palette ?: return
    setTextColor(R.id.widget_task_time, palette.indigo)
    setTextColor(R.id.widget_task_title, palette.ink)
    if (expanded) setTextColor(R.id.widget_task_note, palette.mist)
    setTextColor(R.id.widget_task_complete, palette.jade)
    setInt(R.id.widget_task_complete, "setBackgroundResource", palette.completeBackground)
}

internal data class WidgetThemePalette(
    val rootBackground: Int,
    val mountainVeil: Int,
    val rowsBackground: Int,
    val completeBackground: Int,
    val sealBackground: Int,
    val ink: Int,
    val mist: Int,
    val indigo: Int,
    val jade: Int,
    val cinnabar: Int,
) {
    companion object {
        fun forMode(mode: ThemeMode): WidgetThemePalette? = when (mode) {
            ThemeMode.SYSTEM -> null
            ThemeMode.LIGHT -> light()
            ThemeMode.DARK -> dark()
        }

        private fun light() = WidgetThemePalette(
            rootBackground = R.drawable.widget_ink_background_light,
            mountainVeil = R.drawable.widget_mountain_veil_light,
            rowsBackground = R.drawable.widget_rows_background_light,
            completeBackground = R.drawable.widget_complete_background_light,
            sealBackground = R.drawable.widget_seal_background_light,
            ink = 0xFF14262F.toInt(),
            mist = 0xFF526A76.toInt(),
            indigo = 0xFF315F73.toInt(),
            jade = 0xFF3E7883.toInt(),
            cinnabar = 0xFFA33C32.toInt(),
        )

        private fun dark() = WidgetThemePalette(
            rootBackground = R.drawable.widget_ink_background_dark,
            mountainVeil = R.drawable.widget_mountain_veil_dark,
            rowsBackground = R.drawable.widget_rows_background_dark,
            completeBackground = R.drawable.widget_complete_background_dark,
            sealBackground = R.drawable.widget_seal_background_dark,
            ink = 0xFFF0F6F7.toInt(),
            mist = 0xFFB7C8CE.toInt(),
            indigo = 0xFF9CC3D0.toInt(),
            jade = 0xFF87BAC3.toInt(),
            cinnabar = 0xFFF08B7E.toInt(),
        )
    }
}

object WidgetPendingIntents {
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_APP_REQUEST_CODE,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_APP)
            .setData(Uri.parse("inktodo://widget/home")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun openTask(context: Context, taskId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(taskId, TYPE_OPEN_TASK),
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TASK)
            .setData(Uri.parse("inktodo://widget/task/$taskId"))
            .putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun confirmComplete(context: Context, taskId: Long, title: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(taskId, TYPE_COMPLETE_TASK),
            WidgetCompleteTaskActivity.createIntent(context, taskId, title)
                .setData(Uri.parse("inktodo://widget/task/$taskId/complete")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(taskId: Long, kind: Int): Int = 31 * taskId.hashCode() + kind

    private const val OPEN_APP_REQUEST_CODE = 8_801
    private const val ACTION_OPEN_APP = "com.inktodo.app.action.WIDGET_OPEN_APP"
    private const val ACTION_OPEN_TASK = "com.inktodo.app.action.WIDGET_OPEN_TASK"
    private const val TYPE_OPEN_TASK = 1
    private const val TYPE_COMPLETE_TASK = 2
}
