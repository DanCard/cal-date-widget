package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyConfigActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widgetId = 54321

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `weekly activity initializes correctly`() {
        val intent = Intent(context, WeeklyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        
        ActivityScenario.launch<WeeklyConfigActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                
                // Check UI
                val spinner = activity.findViewById<Spinner>(R.id.spinner_start_day)
                val cbDeclined = activity.findViewById<CheckBox>(R.id.cb_show_declined)
                
                assertNotNull(spinner)
                assertNotNull(cbDeclined)
                
                // Result OK
                assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            }
        }
    }

    @Test
    fun `week start spinner defaults to correct index`() {
        // First, save a specific setting to prefs so we can verify it loads
        val prefs = PrefsManager(context)
        val initialSettings = PrefsManager.WidgetSettings(weekStartDay = Calendar.SUNDAY)
        prefs.saveSettings(widgetId, initialSettings)

        val intent = Intent(context, WeeklyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

        ActivityScenario.launch<WeeklyConfigActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val spinner = activity.findViewById<Spinner>(R.id.spinner_start_day)
                // In code: 0=-1, 1=SUNDAY, 2=MONDAY, 3=SATURDAY
                assertEquals(1, spinner.selectedItemPosition)
            }
        }
    }
}
