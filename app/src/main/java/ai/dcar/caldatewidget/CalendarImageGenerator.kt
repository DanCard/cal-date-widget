package ai.dcar.caldatewidget

import android.content.Context
import android.graphics.Bitmap

object CalendarImageGenerator {

    fun drawWeeklyCalendar(context: Context, appWidgetId: Int, size: WidgetColumnRenderer.WidgetSize): Bitmap {
        return WeeklyWidgetRenderer.draw(context, appWidgetId, size)
    }

    fun drawDailyCalendar(context: Context, appWidgetId: Int, size: WidgetColumnRenderer.WidgetSize): Bitmap {
        return DailyWidgetRenderer.draw(context, appWidgetId, size)
    }
}
