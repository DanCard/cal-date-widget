package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyConfigActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widgetId = 11223

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `daily activity returns RESULT_OK and widget ID immediately on creation`() {
        val intent = Intent(context, DailyConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        
        ActivityScenario.launch<DailyConfigActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                
                // Verify that RESULT_OK and widgetId are set immediately (the fix for disappearing widgets)
                assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
                val resultIntent = shadowOf(activity).resultIntent
                assertNotNull(resultIntent)
                assertEquals(widgetId, resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            }
        }
    }
}
