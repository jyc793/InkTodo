package com.inktodo.app.feature.pomodoro.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.inktodo.app.InkTodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PomodoroCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as InkTodoApplication).appContainer
                container.pomodoroCoordinator.finishIfDue(showNotification = true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
