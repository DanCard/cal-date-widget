package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap

object CalendarImageGenerator {

    fun drawWeeklyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        return WeeklyWidgetRenderer.draw(context, appWidgetManager, appWidgetId)
    }

    fun drawDailyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        return DailyWidgetRenderer.draw(context, appWidgetManager, appWidgetId)
    }
}
