package ai.dcar.caldatewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarImageGeneratorTomorrowIndicatorInstrumentedTest {

    private fun createHeaderPaint(): Paint {
        return Paint().apply {
            color = Color.WHITE
            textSize = 52f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    @Test
    fun buildTomorrowIndicatorHeaderLayoutExposesRainbowSymbol() {
        val layout = DailyWidgetRenderer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 280f
        ) { candidateSize -> candidateSize }

        assertTrue(layout != null)
        assertEquals("🌈", layout!!.indicator)
        assertEquals(DailyWidgetRenderer.TOMORROW_INDICATOR, layout.indicator)
    }

    @Test
    fun drawHeaderWithTomorrowIndicatorScalesTextForNarrowColumn() {
        val wideBitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
        val widePaint = createHeaderPaint()
        val wideDrawn = DailyWidgetRenderer.drawHeaderWithTomorrowIndicator(
            canvas = Canvas(wideBitmap),
            dayName = "Thu 5",
            centerX = 300f,
            centerY = 40f,
            colWidth = 420f,
            basePaint = widePaint
        )
        assertTrue(wideDrawn)
        assertEquals(52f, widePaint.textSize)

        val narrowBitmap = Bitmap.createBitmap(160, 180, Bitmap.Config.ARGB_8888)
        val narrowPaint = createHeaderPaint()
        val narrowDrawn = DailyWidgetRenderer.drawHeaderWithTomorrowIndicator(
            canvas = Canvas(narrowBitmap),
            dayName = "Thu 5",
            centerX = 80f,
            centerY = 40f,
            colWidth = 90f,
            basePaint = narrowPaint
        )
        assertTrue("Indicator should be drawn by scaling down text", narrowDrawn)
        assertEquals("Original paint should not be mutated", 52f, narrowPaint.textSize, 0.001f)
    }
}
