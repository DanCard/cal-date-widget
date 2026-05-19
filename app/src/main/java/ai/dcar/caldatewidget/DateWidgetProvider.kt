package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("DateWidget", "onUpdate for ${appWidgetIds.size} widgets. Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (WidgetRefreshCoordinator.shouldRefreshForAction(intent.action)) {
            Log.d("DateWidget", "onReceive: Action=${intent.action}")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = WidgetRefreshCoordinator.getWidgetIdsForProvider(context, DateWidgetProvider::class.java)
            if (appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        Log.d("DateWidget", "onAppWidgetOptionsChanged for widget $appWidgetId")
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefsManager = PrefsManager(context)
        for (appWidgetId in appWidgetIds) {
            prefsManager.deleteSettings(appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            // 1. Format Date
            var dateText = try {
                val sdf = SimpleDateFormat(settings.dateFormat, Locale.getDefault())
                sdf.format(Date())
            } catch (e: Exception) {
                Log.e("DateWidget", "Error formatting date with pattern '${settings.dateFormat}'", e)
                "Invalid Format"
            }

            // Check widget size options to potentially force single-line
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            if (options != null) {
                // Log all options for debugging Samsung issues
                for (key in options.keySet()) {
                    Log.d("DateWidget", "Option: $key = ${options.get(key)}")
                }

                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                
                Log.d("DateWidget", "Update Widget $appWidgetId: Text='$dateText'")
                Log.d("DateWidget", "Dimensions: minW=$minWidth, minH=$minHeight")
                
                // Logic for 2x2 or larger square-ish widgets:
                if (minHeight >= 90 && minWidth >= 90 && !dateText.contains("\n") && dateText.contains(" ")) {
                    Log.d("DateWidget", "Auto-stacking for 2x2+ widget")
                    dateText = dateText.replaceFirst(" ", "\n")
                }
                
                // Logic for 1x2 or smaller:
                if (minHeight > 0 && minHeight < 90) { 
                    Log.d("DateWidget", "Forcing single line due to height < 90dp")
                    dateText = dateText.replace("\n", " ")
                }
            } else {
                Log.d("DateWidget", "Options are null for widget $appWidgetId")
            }

            // Use a layout with larger max text size for single digits
            val layoutId = if (dateText.length == 1) {
                R.layout.widget_date_single_digit
            } else {
                R.layout.widget_date
            }
            Log.d("DateWidget", "Using layout: ${if (dateText.length == 1) "single_digit" else "standard"} for text '$dateText' (len=${dateText.length})")
            
            val views = RemoteViews(context.packageName, layoutId)

            views.setTextViewText(R.id.widget_date_text, dateText)

            // 2. Apply Colors
            views.setTextColor(R.id.widget_date_text, settings.textColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", settings.bgColor)

            // 3. Launch Calendar PendingIntent
            val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                data = calendarUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                calendarIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_date_text, pendingIntent)

            // 4. Launch Config PendingIntent
            val configIntent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // Unique per widget
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_config_btn, configPendingIntent)

            try {
                Log.d("DateWidget", "Calling updateAppWidget for $appWidgetId")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d("DateWidget", "updateAppWidget successful for $appWidgetId")
            } catch (e: Exception) {
                Log.e("DateWidget", "CRITICAL: updateAppWidget failed for $appWidgetId", e)
            }
        }
    }
}
