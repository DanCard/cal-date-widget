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
}
