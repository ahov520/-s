package com.readflow.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ReadingNotificationChannels {
    const val CHANNEL_TTS = "readflow_tts"
    const val CHANNEL_REMINDER = "readflow_reminder"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_TTS,
                "后台听书",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "阅读时后台播放控制"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_REMINDER,
                "阅读提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每日阅读打卡提醒"
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }
}
