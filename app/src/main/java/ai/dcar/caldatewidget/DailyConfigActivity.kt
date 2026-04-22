package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager

class DailyConfigActivity : BaseWidgetConfigActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_daily_config

    override fun updateWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
    }
}
