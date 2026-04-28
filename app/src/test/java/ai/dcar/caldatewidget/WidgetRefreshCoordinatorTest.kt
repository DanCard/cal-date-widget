package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRefreshCoordinatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `shouldRefreshForAction returns true for supported clock actions`() {
        assertTrue(WidgetRefreshCoordinator.shouldRefreshForAction(Intent.ACTION_DATE_CHANGED))
        assertTrue(WidgetRefreshCoordinator.shouldRefreshForAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(WidgetRefreshCoordinator.shouldRefreshForAction(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `shouldRefreshForAction returns false for unrelated actions`() {
        assertFalse(WidgetRefreshCoordinator.shouldRefreshForAction(Intent.ACTION_SCREEN_ON))
        assertFalse(WidgetRefreshCoordinator.shouldRefreshForAction(null))
    }

    @Test
    fun `getWidgetIdsForProvider returns bound widget ids for provider`() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = shadowOf(appWidgetManager)
        val widgetIds = shadowAppWidgetManager.createWidgets(DateWidgetProvider::class.java, R.layout.widget_date, 2)

        val result = WidgetRefreshCoordinator.getWidgetIdsForProvider(context, DateWidgetProvider::class.java)
        val expected = appWidgetManager.getAppWidgetIds(ComponentName(context, DateWidgetProvider::class.java))

        assertArrayEquals(expected.sortedArray(), result.sortedArray())
        assertArrayEquals(widgetIds.sortedArray(), result.sortedArray())
    }
}
