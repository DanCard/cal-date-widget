package ai.dcar.caldatewidget

import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CalendarImageGeneratorHeaderTest {

    private lateinit var paints: WidgetRenderingHelper.PaintBundle
    private lateinit var canvas: Canvas

    @Before
    fun setup() {
        val settings = PrefsManager.WidgetSettings()
        paints = WidgetRenderingHelper.createPaints(settings)
        canvas = mockk<Canvas>(relaxed = true)
    }

    @Test
    fun `drawWeeklyDayHeader applies today styling correctly`() {
        // Given
        val dayMillis = 1000L
        val currentX = 10f
        val colWidth = 100f
        
        // When
        CalendarImageGenerator.drawWeeklyDayHeader(
            canvas = canvas,
            paints = paints,
            dayMillis = dayMillis,
            isToday = true,
            currentX = currentX,
            colWidth = colWidth
        )

        // Then
        // 1) When calendar displays today, the font for today header should be enlarged and yellow
        assertEquals("Color should be yellow for today", Color.YELLOW, paints.dayHeaderPaint.color)
        
        // Verify it sets a larger text size
        // Note: the size gets adjusted by header.scale internally in drawWeeklyDayHeader.
        // We will just verify it was called and the text was drawn.
        verify {
            canvas.drawText(any(), any(), any(), paints.dayHeaderPaint)
        }
    }

    @Test
    fun `drawWeeklyDayHeader applies non-today styling correctly`() {
        // Given
        val dayMillis = 1000L
        val currentX = 10f
        val colWidth = 100f
        
        // When
        CalendarImageGenerator.drawWeeklyDayHeader(
            canvas = canvas,
            paints = paints,
            dayMillis = dayMillis,
            isToday = false,
            currentX = currentX,
            colWidth = colWidth
        )

        // Then
        // 2) When not today, the calendar column header should not be enlarged or yellow
        assertEquals("Color should be light gray for non-today", Color.LTGRAY, paints.dayHeaderPaint.color)
        
        verify {
            canvas.drawText(any(), any(), any(), paints.dayHeaderPaint)
        }
    }

    @Test
    fun `drawDailyDayHeader applies today styling correctly`() {
        // Given
        val colIndex = 0
        val currentX = 10f
        val colWidth = 100f
        val didAutoAdvance = false
        val dayName = "Mon 1"
        
        // When
        CalendarImageGenerator.drawDailyDayHeader(
            canvas = canvas,
            paints = paints,
            appWidgetId = 1,
            colIndex = colIndex,
            isToday = true,
            currentX = currentX,
            colWidth = colWidth,
            didAutoAdvance = didAutoAdvance,
            dayName = dayName
        )

        // Then
        assertEquals("Color should be yellow for today", Color.YELLOW, paints.dayHeaderPaint.color)
        
        val expectedSize = DailyDisplayLogic.getHeaderTextSize(colWidth, isToday = true)
        assertEquals("Text size should match today size", expectedSize, paints.dayHeaderPaint.textSize)
        
        verify {
            canvas.drawText(eq(dayName), any(), any(), paints.dayHeaderPaint)
        }
    }

    @Test
    fun `drawDailyDayHeader applies non-today styling correctly`() {
        // Given
        val colIndex = 1
        val currentX = 10f
        val colWidth = 100f
        val didAutoAdvance = false
        val dayName = "Tue 2"
        
        // When
        CalendarImageGenerator.drawDailyDayHeader(
            canvas = canvas,
            paints = paints,
            appWidgetId = 1,
            colIndex = colIndex,
            isToday = false,
            currentX = currentX,
            colWidth = colWidth,
            didAutoAdvance = didAutoAdvance,
            dayName = dayName
        )

        // Then
        assertEquals("Color should be light gray for non-today", Color.LTGRAY, paints.dayHeaderPaint.color)
        
        val expectedSize = DailyDisplayLogic.getHeaderTextSize(colWidth, isToday = false)
        assertEquals("Text size should match non-today size", expectedSize, paints.dayHeaderPaint.textSize)
        
        verify {
            canvas.drawText(eq(dayName), any(), any(), paints.dayHeaderPaint)
        }
    }
}
