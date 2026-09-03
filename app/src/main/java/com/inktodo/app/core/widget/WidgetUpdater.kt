package com.inktodo.app.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

fun interface WidgetUpdater {
    fun requestUpdate()
}

object NoOpWidgetUpdater : WidgetUpdater {
    override fun requestUpdate() = Unit
}

class AndroidWidgetUpdater(context: Context) : WidgetUpdater {
    private val applicationContext = context.applicationContext

    override fun requestUpdate() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(applicationContext, InkTodoWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(provider)
        if (widgetIds.isEmpty()) return
        applicationContext.sendBroadcast(
            Intent(applicationContext, InkTodoWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds),
        )
    }
}
