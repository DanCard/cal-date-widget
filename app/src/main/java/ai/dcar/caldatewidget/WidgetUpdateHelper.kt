package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

object WidgetUpdateHelper {

    private const val TAG = "WidgetUpdateHelper"

    // RemoteViews rejects a size map with more entries than this.
    private const val MAX_SIZED_VIEWS = 16

    fun updateWeeklyWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        pushViews(context, appWidgetManager, appWidgetId) { size ->
            buildWeeklyViews(context, appWidgetId, size)
        }
    }

    fun updateDailyWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        pushViews(context, appWidgetManager, appWidgetId) { size ->
            buildDailyViews(context, appWidgetId, size)
        }
        DailyAutoAdvanceScheduler.scheduleNextRefresh(context, appWidgetId)
    }

    /**
     * Supplies one view per size the launcher reports, so the host can show the right bitmap for
     * the current layout and swap instantly on rotate or fold, without waking this app.
     */
    private fun pushViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        buildViews: (WidgetColumnRenderer.WidgetSize) -> RemoteViews
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val sizes = WidgetColumnRenderer.reportedSizesDp(appWidgetManager.getAppWidgetOptions(appWidgetId))
                .take(MAX_SIZED_VIEWS)
            if (sizes.size > 1) {
                try {
                    val mapping = sizes.associateWith { buildViews(WidgetColumnRenderer.toWidgetSize(context, it)) }
                    appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(mapping))
                    return
                } catch (e: RuntimeException) {
                    // The system caps a widget's total bitmap memory, and several large bitmaps
                    // can exceed it. One bitmap for the current layout still fits.
                    Log.w(TAG, "Widget $appWidgetId: per-size update failed, using a single size", e)
                }
            }
        }
        val size = WidgetColumnRenderer.calculateWidgetSize(context, appWidgetManager, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(size))
    }

    private fun buildWeeklyViews(context: Context, appWidgetId: Int, size: WidgetColumnRenderer.WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weekly)

        // Draw Bitmap (Heavy Operation)
        val bitmap = CalendarImageGenerator.drawWeeklyCalendar(context, appWidgetId, size)
        views.setImageViewBitmap(R.id.weekly_canvas, bitmap)

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        // Config Intent
        val configIntent = Intent(context, WeeklyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weekly_config_btn, configPendingIntent)

        // Widget Click Intent
        val pendingIntent = if (hasPermission) {
            // opens calendar and schedules delayed refresh
            val clickIntent = Intent(context, WidgetClickReceiver::class.java).apply {
                action = WidgetClickReceiver.ACTION_WIDGET_CLICK
                putExtra(WidgetClickReceiver.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(WidgetClickReceiver.EXTRA_WIDGET_TYPE, "WEEKLY")
            }
            PendingIntent.getBroadcast(
                context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // Redirect to config activity to grant permission
            configPendingIntent
        }
        views.setOnClickPendingIntent(R.id.weekly_canvas, pendingIntent)

        return views
    }

    private fun buildDailyViews(context: Context, appWidgetId: Int, size: WidgetColumnRenderer.WidgetSize): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_daily)

        // Draw Bitmap (Heavy Operation)
        val bitmap = CalendarImageGenerator.drawDailyCalendar(context, appWidgetId, size)
        views.setImageViewBitmap(R.id.daily_canvas, bitmap)

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        // Config Intent
        val configIntent = Intent(context, DailyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.daily_config_btn, configPendingIntent)

        // Widget Click Intent
        val pendingIntent = if (hasPermission) {
            // opens calendar and schedules delayed refresh
            val clickIntent = Intent(context, WidgetClickReceiver::class.java).apply {
                action = WidgetClickReceiver.ACTION_WIDGET_CLICK
                putExtra(WidgetClickReceiver.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(WidgetClickReceiver.EXTRA_WIDGET_TYPE, "DAILY")
            }
            PendingIntent.getBroadcast(
                context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // Redirect to config activity to grant permission
            configPendingIntent
        }
        views.setOnClickPendingIntent(R.id.daily_canvas, pendingIntent)

        return views
    }
}
