package com.inktodo.app

import android.app.Application

class InkTodoApplication : Application() {
    val appContainer: AppContainer by lazy { DefaultAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        appContainer.notificationController.createChannels()
    }
}
