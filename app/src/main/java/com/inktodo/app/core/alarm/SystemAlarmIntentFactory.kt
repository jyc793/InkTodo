package com.inktodo.app.core.alarm

import android.content.Intent
import android.provider.AlarmClock
import com.inktodo.app.feature.todo.domain.SystemAlarmRequest
import java.time.ZoneId

object SystemAlarmIntentFactory {
    fun create(request: SystemAlarmRequest, zoneId: ZoneId = ZoneId.systemDefault()): Intent {
        val localTime = request.triggerAt.atZone(zoneId).toLocalTime()
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, localTime.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, localTime.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, request.title)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }
}
