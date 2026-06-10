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
class CalendarImageGeneratorTomorrowIndicatorRobolectricTest {

    private fun createHeaderPaint(): Paint {
        return Paint().apply {
            color = Color.WHITE
            textSize = 52f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    @Test
    fun `shouldDrawTomorrowIndicator returns true only for first column when auto advanced`() {
        assertTrue(DailyWidgetRenderer.shouldDrawTomorrowIndicator(true, 0))
        assertFalse(DailyWidgetRenderer.shouldDrawTomorrowIndicator(true, 1))
        assertFalse(DailyWidgetRenderer.shouldDrawTomorrowIndicator(false, 0))
    }

    @Test
    fun `buildTomorrowIndicatorHeaderLayout includes rainbow symbol when width allows`() {
        val layout = DailyWidgetRenderer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 280f
        ) { candidateSize -> candidateSize }

        assertTrue(layout != null)
        assertEquals("Thu 5", layout!!.dayName)
        assertEquals("🌈", layout.indicator)
        assertEquals(DailyWidgetRenderer.TOMORROW_INDICATOR, layout.indicator)
        assertTrue(layout.rainbowTextSize > 0f)
        assertTrue(layout.spacing >= 2f)
    }

    @Test
    fun `buildTomorrowIndicatorHeaderLayout returns null when column is too narrow`() {
        val layout = DailyWidgetRenderer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 124f
        ) { candidateSize -> candidateSize }

        assertNull(layout)
    }

    @Test
    fun `drawHeaderWithTomorrowIndicator returns true when width allows`() {
        val bitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = createHeaderPaint()

        val drawn = DailyWidgetRenderer.drawHeaderWithTomorrowIndicator(
            canvas = canvas,
            dayName = "Thu 5",
            centerX = 300f,
            centerY = 40f,
            colWidth = 420f,
            basePaint = paint
        )

        assertTrue(drawn)
    }

    class TestPaint : Paint() {
        init {
            color = Color.WHITE
            textSize = 52f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        override fun measureText(text: String?): Float {
            if (text == null) return 0f
            return text.length * textSize * 0.5f
        }
    }

    @Test
    fun `drawHeaderWithTomorrowIndicator scales down text when column is narrow`() {
        val bitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = TestPaint()
        val originalTextSize = paint.textSize

        // Original text size = 52f. Width of "Thu 5" = 5 * 26 = 130f.
        // We set available width to 100f (colWidth = 110f). 
        // It must scale down to fit.
        val drawn = DailyWidgetRenderer.drawHeaderWithTomorrowIndicator(
            canvas = canvas,
            dayName = "Thu 5",
            centerX = 40f,
            centerY = 40f,
            colWidth = 110f,
            basePaint = paint
        )

        assertTrue("Should return true, indicating it fit after scaling", drawn)
        assertEquals("Original paint should not be mutated", originalTextSize, paint.textSize, 0.001f)
    }
}
