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

class DailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshWidgetsAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        scope.launch {
            try {
                WidgetUpdateHelper.updateDailyWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e("DailyWidgetProvider", "Error updating daily widget options", e)
            } finally {
                pendingResult.finish()
            }
        }
        
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (WidgetRefreshCoordinator.shouldRefreshForAction(intent.action)) {
            val appWidgetIds = WidgetRefreshCoordinator.getWidgetIdsForProvider(context, DailyWidgetProvider::class.java)
            if (appWidgetIds.isNotEmpty()) {
                refreshWidgetsAsync(context, appWidgetIds)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            DailyAutoAdvanceScheduler.cancel(context, appWidgetId)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun refreshWidgetsAsync(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val appWidgetManager = AppWidgetManager.getInstance(context)

        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    WidgetUpdateHelper.updateDailyWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e("DailyWidgetProvider", "Error updating daily widgets", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val inputData = Data.Builder()
                .putInt("appWidgetId", appWidgetId)
                .putString("type", "DAILY")
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
