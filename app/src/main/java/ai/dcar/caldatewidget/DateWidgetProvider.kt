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
        /**
         * Adjusts the date text between horizontal (single-line) and vertical (stacked) layout
         * based on widget dimensions and launcher row/column spans.
         *
         * Wide or 1-row widgets (e.g. 2x1 on Fold 4 cover display) use horizontal layout.
         * Multi-row square or tall widgets (e.g. 2x2, 1x2) auto-stack to fill available space.
         */
        fun adjustDateTextForDimensions(
            dateText: String,
            minWidth: Int,
            minHeight: Int,
            rowSpan: Int = -1,
            colSpan: Int = -1
        ): String {
            if (minWidth <= 0 || minHeight <= 0) return dateText

            // A widget is 1 row tall if rowSpan == 1, or on launchers without rowSpan, if minHeight is within typical 1-row height (<= 130dp)
            val isOneRow = rowSpan == 1 || (rowSpan <= 0 && minHeight in 1..130)

            // A widget is wide/horizontal if aspect ratio >= 1.2, or if it spans 2+ columns on 1 row
            val isWide = (minWidth.toFloat() / minHeight.toFloat()) >= 1.2f || (colSpan >= 2 && isOneRow)

            // For wide or 1-row widgets (e.g. 2x1, 3x1, 4x1), force single-line horizontal layout
            if (isOneRow || isWide) {
                return dateText.replace("\n", " ")
            }

            // Stacking is only for multi-row widgets that are square-ish or tall (e.g. 2x2, 1x2, 2x3)
            val hasMultipleRows = rowSpan > 1 || (rowSpan <= 0 && minHeight >= 140)
            val isSquareOrTall = (minWidth.toFloat() / minHeight.toFloat()) < 1.2f

            if (hasMultipleRows && isSquareOrTall && minWidth >= 60 && !dateText.contains("\n") && dateText.contains(" ")) {
                return dateText.replaceFirst(" ", "\n")
            }

            return dateText
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            // 1. Format Date (and locate the day-of-month field for styling)
            val formatted = DateWidgetText.format(settings.dateFormat, Date(), Locale.getDefault())
            var dateText = formatted.text

            // Check widget size options to potentially force single-line
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            if (options != null) {
                // Log all options for debugging Samsung issues
                for (key in options.keySet()) {
                    Log.d("DateWidget", "Option: $key = ${options.get(key)}")
                }

                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val rowSpan = try {
                    options.getInt("semAppWidgetRowSpan", -1)
                } catch (e: Exception) {
                    -1
                }
                val colSpan = try {
                    options.getInt("semAppWidgetColumnSpan", -1)
                } catch (e: Exception) {
                    -1
                }

                Log.d("DateWidget", "Update Widget $appWidgetId: Original Text='$dateText', Dimensions: minW=$minWidth, minH=$minHeight, rowSpan=$rowSpan, colSpan=$colSpan")
                dateText = adjustDateTextForDimensions(dateText, minWidth, minHeight, rowSpan, colSpan)
                Log.d("DateWidget", "Adjusted Text='$dateText'")
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

            // Style the day-of-month: bigger + accent color. Transforms above are 1:1 char
            // replacements so the day range computed on the formatted text is still valid here.
            val styledText = DateWidgetText.applyDayStyle(
                dateText, formatted.dayRange, settings.dayNumberColor, DateWidgetText.DEFAULT_DAY_SIZE_FACTOR
            )
            views.setTextViewText(R.id.widget_date_text, styledText)

            // 2. Apply Colors (base color for the weekday/remainder; day number overrides via span)
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
