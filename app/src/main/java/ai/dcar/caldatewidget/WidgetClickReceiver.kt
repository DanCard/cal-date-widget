package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Handles widget clicks to open calendar and schedule delayed refresh.
 */
class WidgetClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WIDGET_CLICK -> {
                val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE) ?: "WEEKLY"

                // 1. Open calendar immediately (no latency)
                openCalendar(context)

                // 2. Schedule delayed low-priority refresh (90 seconds later)
                scheduleDelayedRefresh(context, appWidgetId, widgetType)
            }
        }
    }

    private fun openCalendar(context: Context) {
        val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
            data = calendarUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(calendarIntent)
    }

    private fun scheduleDelayedRefresh(context: Context, appWidgetId: Int, widgetType: String) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        Log.d(TAG, "scheduleDelayedRefresh $widgetType widget=$appWidgetId, ts=${System.currentTimeMillis()}")

        // Battery-friendly constraints: only refresh when device is not low on battery
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)  // Don't run if battery is low
            .build()

        val inputData = Data.Builder()
            .putInt("appWidgetId", appWidgetId)
            .putString("type", widgetType)
            .build()

        // Schedule refresh 90 seconds after calendar opens
        // This gives user time to add/edit events, then widget refreshes with new data
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(inputData)
            .setInitialDelay(90, TimeUnit.SECONDS)  // Delay: 1.5 minutes
            .setConstraints(constraints)
            .build()

        val workName = "${WORK_NAME_PREFIX}${widgetType.lowercase()}_$appWidgetId"
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        private const val TAG = "WidgetLoop"
        private const val WORK_NAME_PREFIX = "widget_click_refresh_"
        const val ACTION_WIDGET_CLICK = "ai.dcar.caldatewidget.WIDGET_CLICK"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_WIDGET_TYPE = "widget_type"
    }
}
