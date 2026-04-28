package ai.dcar.caldatewidget

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object WidgetRefreshCoordinator {

    private val refreshActions = setOf(
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED
    )

    fun shouldRefreshForAction(action: String?): Boolean {
        return action in refreshActions
    }

    fun getWidgetIdsForProvider(
        context: Context,
        providerClass: Class<out AppWidgetProvider>
    ): IntArray {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, providerClass)
        return appWidgetManager.getAppWidgetIds(componentName)
    }
}
