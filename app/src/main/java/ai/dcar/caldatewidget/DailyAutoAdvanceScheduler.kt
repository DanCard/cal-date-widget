package ai.dcar.caldatewidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyAutoAdvanceScheduler {
    private const val WORK_NAME_PREFIX = "daily-auto-advance-"

    fun scheduleNextRefresh(context: Context, appWidgetId: Int) {
        val workManager = WorkManager.getInstance(context)
        val workName = uniqueWorkName(appWidgetId)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.d("DailyAutoAdvance", "cancel schedule for widget=$appWidgetId because READ_CALENDAR is missing")
            workManager.cancelUniqueWork(workName)
            return
        }

        val prefsManager = PrefsManager(context)
        val repo = CalendarRepository(context)
        val settings = prefsManager.loadSettings(appWidgetId)
        val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
        val selection = selectedIds.ifEmpty { null }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + (24L * 60 * 60 * 1000)

        val todayEvents = repo.getEvents(todayStart, 1, selection).filter {
            WeeklyDisplayLogic.shouldDisplayEventOnDay(it, todayStart, todayEnd)
        }
        val now = System.currentTimeMillis()
        val nextCheckTime = DailyDisplayLogic.getNextAutoAdvanceCheckTime(todayEvents, now)

        Log.d(
            "DailyAutoAdvance",
            "widget=$appWidgetId now=$now todayEvents=${todayEvents.size} nextCheckTime=$nextCheckTime titles=${todayEvents.joinToString { it.title }}"
        )

        if (nextCheckTime == null) {
            Log.d("DailyAutoAdvance", "cancel schedule for widget=$appWidgetId because no future auto-advance check is needed")
            workManager.cancelUniqueWork(workName)
            return
        }

        val inputData = Data.Builder()
            .putInt("appWidgetId", appWidgetId)
            .putString("type", "DAILY")
            .build()

        val delayMillis = (nextCheckTime - now).coerceAtLeast(0L)
        Log.d(
            "DailyAutoAdvance",
            "schedule widget=$appWidgetId nextCheckTime=$nextCheckTime delayMillis=$delayMillis"
        )
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun cancel(context: Context, appWidgetId: Int) {
        Log.d("DailyAutoAdvance", "cancel schedule for widget=$appWidgetId")
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(appWidgetId))
    }

    private fun uniqueWorkName(appWidgetId: Int): String {
        return "$WORK_NAME_PREFIX$appWidgetId"
    }
}
