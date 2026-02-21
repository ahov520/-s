package com.readflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.readflow.app.MainActivity
import com.readflow.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadingStatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

private fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val now = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())
    val views = RemoteViews(context.packageName, R.layout.widget_reading_stats).apply {
        setTextViewText(R.id.widget_title, "阅流")
        setTextViewText(R.id.widget_subtitle, "轻触打开继续阅读")
        setTextViewText(R.id.widget_time, "更新时间：$now")
        setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
