package com.readflow.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.readflow.app.MainActivity
import com.readflow.app.R

class ReadingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReadingNotificationChannels.ensureCreated(context)

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context,
            210,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReadingNotificationChannels.CHANNEL_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今日阅读提醒")
            .setContentText("继续今天的阅读目标吧")
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(7002, notification)
        }
    }
}
