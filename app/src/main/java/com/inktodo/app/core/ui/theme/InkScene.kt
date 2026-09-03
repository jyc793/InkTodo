package com.inktodo.app.core.ui.theme

import androidx.compose.ui.graphics.Color

enum class InkScene(
    val mark: String,
    val contentName: String,
) {
    TODO("山", "待办"),
    CALENDAR("月", "日历"),
    POMODORO("水", "番茄钟"),
    COMPLETED("雾", "已完成"),
    EDITOR("纸", "任务编辑"),
    REMINDER("印", "重要提醒"),
    SETTINGS("石", "提醒可靠性"),
}

fun InkScene.accent(palette: InkPalette): Color = when (this) {
    InkScene.TODO -> palette.moss
    InkScene.CALENDAR -> palette.indigo
    InkScene.POMODORO -> palette.jade
    InkScene.COMPLETED -> palette.mist
    InkScene.EDITOR -> palette.moss
    InkScene.REMINDER -> palette.cinnabar
    InkScene.SETTINGS -> palette.inkSoft
}
