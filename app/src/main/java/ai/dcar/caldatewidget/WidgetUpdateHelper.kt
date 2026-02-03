package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

object WidgetUpdateHelper {

    fun updateWeeklyWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_weekly)

        // Draw Bitmap (Heavy Operation)
        val bitmap = WidgetDrawer.drawWeeklyCalendar(context, appWidgetManager, appWidgetId)
        views.setImageViewBitmap(R.id.weekly_canvas, bitmap)

        // Config Intent
        val configIntent = Intent(context, WeeklyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weekly_config_btn, configPendingIntent)

        // Widget Click Intent - opens calendar and schedules delayed refresh
        val clickIntent = Intent(context, WidgetClickReceiver::class.java).apply {
            action = WidgetClickReceiver.ACTION_WIDGET_CLICK
            putExtra(WidgetClickReceiver.EXTRA_WIDGET_ID, appWidgetId)
            putExtra(WidgetClickReceiver.EXTRA_WIDGET_TYPE, "WEEKLY")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weekly_canvas, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun updateDailyWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_daily)

        // Draw Bitmap (Heavy Operation)
        val bitmap = WidgetDrawer.drawDailyCalendar(context, appWidgetManager, appWidgetId)
        views.setImageViewBitmap(R.id.daily_canvas, bitmap)

        // Config Intent
        val configIntent = Intent(context, DailyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.daily_config_btn, configPendingIntent)

        // Widget Click Intent - opens calendar and schedules delayed refresh
        val clickIntent = Intent(context, WidgetClickReceiver::class.java).apply {
            action = WidgetClickReceiver.ACTION_WIDGET_CLICK
            putExtra(WidgetClickReceiver.EXTRA_WIDGET_ID, appWidgetId)
            putExtra(WidgetClickReceiver.EXTRA_WIDGET_TYPE, "DAILY")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.daily_canvas, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
