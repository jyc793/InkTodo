package com.inktodo.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.graphics.ColorUtils
import com.inktodo.app.R
import com.inktodo.app.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetAndroidSystemTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun providerMetadataIsVisibleAndFreelyResizableToHyperOsLauncher() {
        val component = ComponentName(context, InkTodoWidgetProvider::class.java)
        val info = AppWidgetManager.getInstance(context).installedProviders
            .firstOrNull { it.provider == component }

        assertNotNull(info)
        requireNotNull(info)
        assertEquals(R.layout.widget_ink_todo, info.initialLayout)
        assertTrue(info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0)
        assertTrue(info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
        assertTrue(info.minResizeWidth > 0)
        assertTrue(info.minResizeHeight > 0)
    }

    @Test
    fun taskPendingIntentsAreStablePerTaskAndUniqueAcrossTasks() {
        val first = WidgetPendingIntents.openTask(context, 101)
        val same = WidgetPendingIntents.openTask(context, 101)
        val other = WidgetPendingIntents.openTask(context, 202)
        val complete = WidgetPendingIntents.confirmComplete(context, 101, "任务一")
        val sameComplete = WidgetPendingIntents.confirmComplete(context, 101, "任务一")
        val otherComplete = WidgetPendingIntents.confirmComplete(context, 202, "任务二")

        assertEquals(first, same)
        assertNotEquals(first, other)
        assertNotEquals(first, complete)
        assertEquals(complete, sameComplete)
        assertNotEquals(complete, otherComplete)
        first.cancel()
        other.cancel()
        complete.cancel()
        otherComplete.cancel()
    }

    @Test
    fun updaterSafelyNoOpsWhenNoWidgetIsPinned() {
        AndroidWidgetUpdater(context).requestUpdate()
    }

    @Test
    fun widgetLayoutPackagesTheSharedMountainAndReadableVeilLayers() {
        val root = LayoutInflater.from(context).inflate(R.layout.widget_ink_todo, null)
        val mountain = root.findViewById<ImageView>(R.id.widget_mountain_background)
        val veil = root.findViewById<ImageView>(R.id.widget_mountain_veil)

        assertNotNull(root.background)
        assertNotNull(root.foreground)
        assertNotNull(mountain.drawable)
        assertNotNull(veil.drawable)
        assertNotNull(root.findViewById<android.view.View>(R.id.widget_rows).background)
        assertNotNull(root.findViewById<android.view.View>(R.id.widget_seal))
    }

    @Test
    fun explicitDarkWidgetPaletteKeepsTextReadableAndSystemModeUsesQualifiers() {
        val palette = requireNotNull(WidgetThemePalette.forMode(ThemeMode.DARK))
        val rows = requireNotNull(context.getDrawable(palette.rowsBackground)) as GradientDrawable
        val translucentSurface = requireNotNull(rows.color).defaultColor
        val surface = ColorUtils.compositeColors(translucentSurface, Color.rgb(10, 17, 14))

        assertEquals(null, WidgetThemePalette.forMode(ThemeMode.SYSTEM))
        assertNotNull(context.getDrawable(palette.rootBackground))
        assertNotNull(context.getDrawable(palette.mountainVeil))
        assertNotNull(context.getDrawable(palette.completeBackground))
        assertTrue(ColorUtils.calculateContrast(palette.ink, surface) >= 7.0)
        assertTrue(ColorUtils.calculateContrast(palette.mist, surface) >= 4.5)
        assertTrue(ColorUtils.calculateContrast(palette.indigo, surface) >= 4.5)
    }

    @Test
    fun completionConfirmationActivityIsPrivateAndBothRowSizesExposeAReadableAction() {
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, WidgetCompleteTaskActivity::class.java),
            0,
        )
        val compact = LayoutInflater.from(context).inflate(R.layout.widget_task_row_compact, null)
        val expanded = LayoutInflater.from(context).inflate(R.layout.widget_task_row_expanded, null)

        assertEquals(false, activity.exported)
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, activity.launchMode)
        assertEquals("完", compact.findViewById<TextView>(R.id.widget_task_complete).text)
        assertEquals("完成", expanded.findViewById<TextView>(R.id.widget_task_complete).text)
        assertTrue(compact.findViewById<TextView>(R.id.widget_task_title).textSize > 0f)
        assertTrue(expanded.findViewById<TextView>(R.id.widget_task_note).textSize > 0f)
    }
}
