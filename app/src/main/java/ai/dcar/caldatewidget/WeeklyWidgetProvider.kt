package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WeeklyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets, ts=$now")
        for (appWidgetId in appWidgetIds) {
            val last = lastUpdate[appWidgetId] ?: 0L
            if (now - last < DEBOUNCE_MS) {
                Log.d(TAG, "onUpdate DEBOUNCED widget=$appWidgetId, delta=${now - last}ms")
                continue
            }
            lastUpdate[appWidgetId] = now
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        val now = System.currentTimeMillis()
        val last = lastOptionsChanged[appWidgetId] ?: 0L

        if (now - last < DEBOUNCE_MS) {
            Log.d(TAG, "onAppWidgetOptionsChanged DEBOUNCED widget=$appWidgetId, delta=${now - last}ms")
            return
        }
        lastOptionsChanged[appWidgetId] = now

        Log.d(TAG, "onAppWidgetOptionsChanged widget=$appWidgetId, ts=$now")
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        private const val TAG = "WidgetLoop"
        private const val WORK_NAME_PREFIX = "weekly_widget_update_"
        private const val DEBOUNCE_MS = 2000L
        private val lastUpdate = mutableMapOf<Int, Long>()
        private val lastOptionsChanged = mutableMapOf<Int, Long>()

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            Log.d(TAG, "updateAppWidget enqueueing WEEKLY widget=$appWidgetId, ts=${System.currentTimeMillis()}")

            val inputData = Data.Builder()
                .putInt("appWidgetId", appWidgetId)
                .putString("type", "WEEKLY")
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$appWidgetId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        // Exposed for testing
        internal fun maxLinesForEvent(isToday: Boolean, eventEndTime: Long, nowMillis: Long): Int {
            return if (isToday && eventEndTime < nowMillis) 1 else 0
        }
    }
}
