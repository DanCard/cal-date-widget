package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appWidgetId = inputData.getInt("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
        val type = inputData.getString("type")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || type == null) {
            return@withContext Result.failure()
        }

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        try {
            when (type) {
                "WEEKLY" -> updateWeeklyWidget(appWidgetId, appWidgetManager)
                "DAILY" -> updateDailyWidget(appWidgetId, appWidgetManager)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun updateWeeklyWidget(appWidgetId: Int, appWidgetManager: AppWidgetManager) {
        val views = RemoteViews(applicationContext.packageName, R.layout.widget_weekly)

        // Draw Bitmap (Heavy Operation)
        val bitmap = WidgetDrawer.drawWeeklyCalendar(applicationContext, appWidgetManager, appWidgetId)
        views.setImageViewBitmap(R.id.weekly_canvas, bitmap)

        // Config Intent
        val configIntent = Intent(applicationContext, WeeklyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            applicationContext, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weekly_config_btn, configPendingIntent)

        // Calendar Intent
        val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
            data = calendarUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weekly_canvas, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateDailyWidget(appWidgetId: Int, appWidgetManager: AppWidgetManager) {
        val views = RemoteViews(applicationContext.packageName, R.layout.widget_daily)

        // Draw Bitmap (Heavy Operation)
        val bitmap = WidgetDrawer.drawDailyCalendar(applicationContext, appWidgetManager, appWidgetId)
        views.setImageViewBitmap(R.id.daily_canvas, bitmap)

        // Config Intent
        val configIntent = Intent(applicationContext, DailyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val configPendingIntent = PendingIntent.getActivity(
            applicationContext, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.daily_config_btn, configPendingIntent)

        // Calendar Intent
        val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
            data = calendarUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.daily_canvas, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
