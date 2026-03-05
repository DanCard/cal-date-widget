package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDrawerTest {

    @Test
    fun `calculateRainbowIndicatorTextSize returns default scaled size when there is enough width`() {
        // Given
        val dayNameWidth = 80f
        val baseTextSize = 50f
        val availableWidth = 200f

        // When
        val result = WidgetDrawer.calculateRainbowIndicatorTextSize(
            dayNameWidth = dayNameWidth,
            baseTextSize = baseTextSize,
            availableWidth = availableWidth
        ) { textSize -> textSize }

        // Then
        assertEquals(31f, result!!, 0.001f)
    }

    @Test
    fun `calculateRainbowIndicatorTextSize reduces size when default scale does not fit`() {
        // Given
        val dayNameWidth = 80f
        val baseTextSize = 50f
        val availableWidth = 110f

        // When
        val result = WidgetDrawer.calculateRainbowIndicatorTextSize(
            dayNameWidth = dayNameWidth,
            baseTextSize = baseTextSize,
            availableWidth = availableWidth
        ) { textSize -> textSize }

        // Then
        assertEquals(23.5f, result!!, 0.001f)
    }

    @Test
    fun `calculateRainbowIndicatorTextSize applies max clamp for very large headers`() {
        // Given
        val dayNameWidth = 80f
        val baseTextSize = 100f
        val availableWidth = 260f

        // When
        val result = WidgetDrawer.calculateRainbowIndicatorTextSize(
            dayNameWidth = dayNameWidth,
            baseTextSize = baseTextSize,
            availableWidth = availableWidth
        ) { textSize -> textSize }

        // Then
        assertEquals(44f, result!!, 0.001f)
    }

    @Test
    fun `calculateRainbowIndicatorTextSize returns null when indicator cannot fit`() {
        // Given
        val dayNameWidth = 100f
        val baseTextSize = 50f
        val availableWidth = 110f

        // When
        val result = WidgetDrawer.calculateRainbowIndicatorTextSize(
            dayNameWidth = dayNameWidth,
            baseTextSize = baseTextSize,
            availableWidth = availableWidth
        ) { textSize -> textSize }

        // Then
        assertNull(result)
    }
}
