package com.readflow.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.TimeZone

object ReadingReminderScheduler {
    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, hour, minute)

        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        scheduleExact(alarmManager, triggerAtMillis = calculateNextTriggerMillis(System.currentTimeMillis(), hour, minute), pendingIntent = pendingIntent)
    }

    fun scheduleNext(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, hour, minute)
        scheduleExact(alarmManager, triggerAtMillis = calculateNextTriggerMillis(System.currentTimeMillis(), hour, minute), pendingIntent = pendingIntent)
    }

    private fun scheduleExact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    internal fun calculateNextTriggerMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance(timeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun buildPendingIntent(context: Context, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, ReadingReminderReceiver::class.java).apply {
            putExtra(EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(EXTRA_MINUTE, minute.coerceIn(0, 59))
        }
        return PendingIntent.getBroadcast(
            context,
            220,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"
}
