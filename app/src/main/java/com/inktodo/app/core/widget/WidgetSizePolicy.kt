package com.inktodo.app.core.widget

data class WidgetLayoutSpec(
    val maxTaskRows: Int,
    val showSecondaryText: Boolean,
)

object WidgetSizePolicy {
    fun resolve(
        minWidthDp: Int,
        minHeightDp: Int,
        userMaxTasks: Int,
    ): WidgetLayoutSpec {
        val sizeRows = when {
            minHeightDp < 140 -> 1
            minHeightDp < 210 -> 2
            minHeightDp < 280 -> 4
            else -> 6
        }
        return WidgetLayoutSpec(
            maxTaskRows = minOf(sizeRows, userMaxTasks.coerceIn(1, 20)),
            showSecondaryText = minWidthDp >= 240 && minHeightDp >= 170,
        )
    }
}
