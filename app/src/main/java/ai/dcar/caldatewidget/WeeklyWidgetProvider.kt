package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WeeklyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshWidgetsAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        scope.launch {
            try {
                WidgetUpdateHelper.updateWeeklyWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e("WeeklyWidgetProvider", "Error updating weekly widget options", e)
            } finally {
                pendingResult.finish()
            }
        }
        
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (WidgetRefreshCoordinator.shouldRefreshForAction(intent.action)) {
            val appWidgetIds = WidgetRefreshCoordinator.getWidgetIdsForProvider(context, WeeklyWidgetProvider::class.java)
            if (appWidgetIds.isNotEmpty()) {
                refreshWidgetsAsync(context, appWidgetIds)
            }
        }
    }

    private fun refreshWidgetsAsync(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val appWidgetManager = AppWidgetManager.getInstance(context)

        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    WidgetUpdateHelper.updateWeeklyWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e("WeeklyWidgetProvider", "Error updating weekly widgets", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val inputData = Data.Builder()
                .putInt("appWidgetId", appWidgetId)
                .putString("type", "WEEKLY")
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }

        // Exposed for testing
        internal fun maxLinesForEvent(isToday: Boolean, eventEndTime: Long, nowMillis: Long): Int {
            return if (isToday && eventEndTime < nowMillis) 1 else 0
        }
    }
}
