package ai.dcar.caldatewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class WidgetDrawerTomorrowIndicatorRobolectricTest {

    private fun createHeaderPaint(): Paint {
        return Paint().apply {
            color = Color.WHITE
            textSize = 52f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    @Test
    fun `shouldDrawTomorrowIndicator returns true only for first visible column when auto advanced`() {
        assertTrue(WidgetDrawer.shouldDrawTomorrowIndicator(true, 0, 0))
        assertFalse(WidgetDrawer.shouldDrawTomorrowIndicator(true, 1, 0))
        assertFalse(WidgetDrawer.shouldDrawTomorrowIndicator(false, 0, 0))
    }

    @Test
    fun `buildTomorrowIndicatorHeaderLayout includes rainbow symbol when width allows`() {
        val layout = WidgetDrawer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 280f
        ) { candidateSize -> candidateSize }

        assertTrue(layout != null)
        assertEquals("Thu 5", layout!!.dayName)
        assertEquals("🌈", layout.indicator)
        assertEquals(WidgetDrawer.TOMORROW_INDICATOR, layout.indicator)
        assertTrue(layout.rainbowTextSize > 0f)
        assertTrue(layout.spacing >= 2f)
    }

    @Test
    fun `buildTomorrowIndicatorHeaderLayout returns null when column is too narrow`() {
        val layout = WidgetDrawer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 124f
        ) { candidateSize -> candidateSize }

        assertNull(layout)
    }

    @Test
    fun `drawHeaderWithTomorrowIndicatorForTest returns true when width allows`() {
        val bitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = createHeaderPaint()

        val drawn = WidgetDrawer.drawHeaderWithTomorrowIndicatorForTest(
            canvas = canvas,
            dayName = "Thu 5",
            centerX = 300f,
            centerY = 40f,
            colWidth = 420f,
            basePaint = paint
        )

        assertTrue(drawn)
    }
}
