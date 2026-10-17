package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateWidgetProviderRefreshTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `date changed broadcast refreshes widget with latest saved settings`() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = shadowOf(appWidgetManager)
        val widgetId = shadowAppWidgetManager.createWidget(DateWidgetProvider::class.java, R.layout.widget_date)

        val prefsManager = PrefsManager(context)
        prefsManager.saveSettings(
            widgetId,
            PrefsManager.WidgetSettings(dateFormat = "'UPDATED'")
        )

        val provider = DateWidgetProvider()
        provider.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))

        val widgetView = shadowAppWidgetManager.getViewFor(widgetId)
        val textView = widgetView.findViewById<TextView>(R.id.widget_date_text)

        assertEquals("UPDATED", textView.text.toString())
    }

    @Test
    fun `two column wide widget on Fold 4 cover display renders horizontal date layout without newline`() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = shadowOf(appWidgetManager)
        val widgetId = shadowAppWidgetManager.createWidget(DateWidgetProvider::class.java, R.layout.widget_date)

        val options = android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 195)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 93)
            putInt("semAppWidgetRowSpan", 1)
            putInt("semAppWidgetColumnSpan", 3)
        }
        appWidgetManager.updateAppWidgetOptions(widgetId, options)

        val prefsManager = PrefsManager(context)
        prefsManager.saveSettings(
            widgetId,
            PrefsManager.WidgetSettings(dateFormat = "EEE d")
        )

        val provider = DateWidgetProvider()
        provider.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, options)

        val widgetView = shadowAppWidgetManager.getViewFor(widgetId)
        val textView = widgetView.findViewById<TextView>(R.id.widget_date_text)

        org.junit.Assert.assertFalse(textView.text.toString().contains("\n"))
        org.junit.Assert.assertTrue(textView.text.toString().contains(" "))
    }
}
