package com.readflow.app

import android.app.Application
import com.readflow.app.notifications.ReadingNotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReadFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReadingNotificationChannels.ensureCreated(this)
    }
}
