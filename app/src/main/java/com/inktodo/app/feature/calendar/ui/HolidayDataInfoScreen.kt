package com.inktodo.app.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inktodo.app.core.ui.components.InkCompactButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkSceneHeader
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.feature.calendar.domain.OfficialHolidayScheduleData

@Composable
fun HolidayDataInfoScreen(onBack: () -> Unit) {
    val spacing = InkTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        InkSceneHeader(
            title = "节假日数据说明",
            scene = InkScene.CALENDAR,
            action = { InkCompactButton("返回", onBack) },
        )
        InkGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    "当前正式休班安排覆盖：${OfficialHolidayScheduleData.coveredYears.sorted().joinToString("、")} 年",
                    color = InkTheme.colors.ink,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "墨待只收录国务院已经发布的正式安排，不推测未来调休。安装包含新年度数据的版本后，此提示会自动消失。",
                    color = InkTheme.colors.mist,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OfficialHolidayScheduleData.sources.forEach { source ->
                    Text(
                        "${source.year} · ${source.title}",
                        color = InkTheme.colors.indigo,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
