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
class WidgetDrawerTomorrowIndicatorInstrumentedTest {

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
        val layout = WidgetDrawer.buildTomorrowIndicatorHeaderLayout(
            dayName = "Thu 5",
            dayNameWidth = 120f,
            baseTextSize = 52f,
            availableWidth = 280f
        ) { candidateSize -> candidateSize }

        assertTrue(layout != null)
        assertEquals("🌈", layout!!.indicator)
        assertEquals(WidgetDrawer.TOMORROW_INDICATOR, layout.indicator)
    }

    @Test
    fun drawHeaderWithTomorrowIndicatorForTestReturnsTrueForWideColumnAndFalseForNarrowColumn() {
        val wideBitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
        val wideDrawn = WidgetDrawer.drawHeaderWithTomorrowIndicatorForTest(
            canvas = Canvas(wideBitmap),
            dayName = "Thu 5",
            centerX = 300f,
            centerY = 40f,
            colWidth = 420f,
            basePaint = createHeaderPaint()
        )
        assertTrue(wideDrawn)

        val narrowBitmap = Bitmap.createBitmap(160, 180, Bitmap.Config.ARGB_8888)
        val narrowDrawn = WidgetDrawer.drawHeaderWithTomorrowIndicatorForTest(
            canvas = Canvas(narrowBitmap),
            dayName = "Thu 5",
            centerX = 80f,
            centerY = 40f,
            colWidth = 90f,
            basePaint = createHeaderPaint()
        )
        assertFalse(narrowDrawn)
    }
}
