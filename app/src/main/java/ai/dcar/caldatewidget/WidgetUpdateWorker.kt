package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
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
                "WEEKLY" -> WidgetUpdateHelper.updateWeeklyWidget(applicationContext, appWidgetManager, appWidgetId)
                "DAILY" -> WidgetUpdateHelper.updateDailyWidget(applicationContext, appWidgetManager, appWidgetId)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
