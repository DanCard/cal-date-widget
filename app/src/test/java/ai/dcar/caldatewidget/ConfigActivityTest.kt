package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widgetId = 12345

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `activity initializes correctly with widget id`() {
        val intent = Intent(context, ConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        
        ActivityScenario.launch<ConfigActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Check if running
                assertNotNull(activity)
                
                // Check basic UI elements
                val undoBtn = activity.findViewById<Button>(R.id.btn_undo)
                val spinner = activity.findViewById<Spinner>(R.id.spinner_date_format)
                
                assertNotNull(undoBtn)
                assertNotNull(spinner)
                
                // Verify initial result is set (ConfigActivity sets RESULT_OK early in onCreate per code)
                val result = shadowOf(activity).resultIntent
                assertNotNull(result)
                assertEquals(widgetId, result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
                assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            }
        }
    }

    @Test
    fun `selecting custom format shows edit text`() {
        val intent = Intent(context, ConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

        ActivityScenario.launch<ConfigActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val spinner = activity.findViewById<Spinner>(R.id.spinner_date_format)
                val customEt = activity.findViewById<View>(R.id.et_custom_format)
                
                // Initial state might be GONE if default is not custom
                // "Custom" is usually last in the array.
                // Let's find the index of "Custom"
                val adapter = spinner.adapter
                var customIndex = -1
                for (i in 0 until adapter.count) {
                    if (adapter.getItem(i).toString() == "Custom") {
                        customIndex = i
                        break
                    }
                }
                
                assertTrue("Custom option found", customIndex >= 0)
                
                // Select "Custom"
                spinner.setSelection(customIndex)
                
                // Wait for listeners (Robolectric runs on main thread so immediate usually)
                // However, setSelection calls onItemSelected which we hook into
                
                assertEquals(View.VISIBLE, customEt.visibility)
            }
        }
    }
}
