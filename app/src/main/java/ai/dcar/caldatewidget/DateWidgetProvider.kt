package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
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

            val views = RemoteViews(context.packageName, R.layout.widget_date)

            // 1. Format Date
            val dateText = try {
                val sdf = SimpleDateFormat(settings.dateFormat, Locale.getDefault())
                sdf.format(Date())
            } catch (e: Exception) {
                "Invalid Format"
            }
            views.setTextViewText(R.id.widget_date_text, dateText)

            // 2. Apply Colors
            views.setTextColor(R.id.widget_date_text, settings.textColor)
            
            // Shadow is tricky in RemoteViews. We can't easily set shadow layer dynamically on all API levels 
            // without a custom view or using reflection (not recommended).
            // However, standard setTextColor works. For Shadow, we often use style or just keep default black if not easily changeable via RemoteViews method.
            // Wait, `setTextViewText` is standard. `setTextColor` is standard.
            // `setShadowLayer` is not a RemotableView method directly exposed via easy setFloat etc usually.
            // Actually, we can use `setInt(viewId, "setShadowColor", color)` if the method exists and is Remotable.
            // TextView.setShadowLayer(radius, dx, dy, color) is Remotable? No, it's not tagged with @RemotableViewMethod usually.
            // But let's check. Usually people use different layout files for different styles, or a bitmap.
            // Requirement: "Adjust colors... shadow text".
            // If I can't set shadow color dynamically, I might just leave it black or use a dark/light variant.
            // Let's try to proceed with just text/bg color first, and maybe see if we can ignore dynamic shadow color or use a hack (creating a bitmap).
            // Generating a bitmap is the most robust way for highly custom text in widgets.
            // BUT "auto-resizing text" using `app:autoSizeTextType` works best with a real TextView.
            // Let's stick to standard TextView. If dynamic shadow color is hard, we might just support black/white shadow or fixed shadow.
            // Actually, there IS no setShadowLayer in RemoteViews.
            // I will try to skip dynamic shadow color setting for the widget view itself in this pass, 
            // OR I can render the text to a Bitmap and set it on an ImageView.
            // Rendering to Bitmap allows FULL control (fonts, shadows, gradients).
            // Given "Highly customizable... auto resize", Bitmap might be better but `autoSizeTextType` on TextView is native and efficient.
            // Let's stick to native TextView for performance and standard look. 
            // We will just set text color and background color. Shadow might remain fixed in XML or we ignore the dynamic shadow color requirement for the *Widget* (but keep in config).
            // Wait, I can use `views.setInt(R.id.widget_date_text, "setShadowLayer", ...)` NO, that doesn't work.
            // I'll stick to Text and Background color updates. I'll note this limitation.
            // Re-reading: "transparent background with shadow text". XML already has shadow.
            
            views.setInt(R.id.widget_root, "setBackgroundColor", settings.bgColor)

            // 3. Launch Calendar PendingIntent
            // Try to find the default calendar app
            val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                data = calendarUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Fallback or generic launch
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

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
