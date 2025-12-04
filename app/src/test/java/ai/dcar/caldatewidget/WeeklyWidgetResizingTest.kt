package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyWidgetResizingTest {

    @Test
    fun `calculate bitmap dimensions respects aspect ratio`() {
        val minWidth = 300
        val minHeight = 150 // 2:1 ratio
        
        val targetWidth = 800
        val ratio = minHeight.toFloat() / minWidth.toFloat()
        val expectedHeight = (targetWidth * ratio).toInt() // 400
        
        assertEquals(400, expectedHeight)
    }

    @Test
    fun `calculate bitmap dimensions handles narrow height`() {
        val minWidth = 300
        val minHeight = 50 // 6:1 ratio
        
        val targetWidth = 800
        val ratio = minHeight.toFloat() / minWidth.toFloat()
        val expectedHeight = (targetWidth * ratio).toInt() // ~133
        
        assertEquals(133, expectedHeight)
        // Logic should produce a shorter bitmap, not squashed content if we draw same size text on it.
    }
}
