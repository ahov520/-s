package com.readflow.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.readflow.app.MainActivity
import com.readflow.app.R
import com.readflow.app.notifications.ReadingNotificationChannels

class BackgroundAudioService : Service() {
    override fun onCreate() {
        super.onCreate()
        ReadingNotificationChannels.ensureCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)?.ifBlank { null } ?: "正在后台听书"
        startForeground(NOTIFICATION_ID, buildNotification(title))
        return START_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val launchPending = PendingIntent.getActivity(
            this,
            101,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, ReadingNotificationChannels.CHANNEL_TTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ReadFlow 后台播放")
            .setContentText(title)
            .setOngoing(true)
            .setContentIntent(launchPending)
            .addAction(0, "停止", stopPending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.readflow.app.action.STOP_BG_AUDIO"
        const val EXTRA_TITLE = "extra_title"
        private const val NOTIFICATION_ID = 7001
    }
}
