package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Test

class DateWidgetLayoutLogicTest {

    @Test
    fun `two column wide widget on Fold 4 cover display maintains horizontal layout`() {
        // Given Fold 4 cover display dimensions: minW=195, minH=93, rowSpan=1, colSpan=3
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 195,
            minHeight = 93,
            rowSpan = 1,
            colSpan = 3
        )

        // Then
        assertEquals("Fri 16", result)
    }

    @Test
    fun `two column wide widget on Fold 4 cover display converts newline to space for horizontal layout`() {
        // Given Fold 4 cover display dimensions with a date format that had explicit newline
        val input = "Fri\n16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 195,
            minHeight = 93,
            rowSpan = 1,
            colSpan = 3
        )

        // Then
        assertEquals("Fri 16", result)
    }

    @Test
    fun `two column wide 1-row widget without rowSpan maintains horizontal layout`() {
        // Given standard launcher 2x1 widget: minW=140, minH=70 (aspect ratio 2.0, height < 130)
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 140,
            minHeight = 70,
            rowSpan = -1,
            colSpan = -1
        )

        // Then
        assertEquals("Fri 16", result)
    }

    @Test
    fun `wide 3x1 and 4x1 widgets maintain horizontal layout`() {
        // Given 3x1 and 4x1 wide widgets
        val input = "Fri 16"

        val result3x1 = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 240,
            minHeight = 80,
            rowSpan = 1,
            colSpan = 3
        )
        val result4x1 = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 320,
            minHeight = 80,
            rowSpan = -1,
            colSpan = -1
        )

        assertEquals("Fri 16", result3x1)
        assertEquals("Fri 16", result4x1)
    }

    @Test
    fun `multi-row square 2x2 widget auto-stacks date text`() {
        // Given a 2x2 widget: minW=180, minH=180, rowSpan=2, colSpan=2
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 180,
            minHeight = 180,
            rowSpan = 2,
            colSpan = 2
        )

        // Then
        assertEquals("Fri\n16", result)
    }

    @Test
    fun `multi-row square 2x2 widget on launcher without rowSpan auto-stacks date text`() {
        // Given a 2x2 widget on standard launcher: minW=150, minH=150 (minH >= 140, aspect ratio < 1.2)
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 150,
            minHeight = 150,
            rowSpan = -1,
            colSpan = -1
        )

        // Then
        assertEquals("Fri\n16", result)
    }

    @Test
    fun `tall 1x2 widget auto-stacks date text`() {
        // Given a tall portrait widget: minW=70, minH=160, rowSpan=2, colSpan=1
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 70,
            minHeight = 160,
            rowSpan = 2,
            colSpan = 1
        )

        // Then
        assertEquals("Fri\n16", result)
    }

    @Test
    fun `single cell 1x1 widget maintains single line`() {
        // Given a 1x1 widget on Fold 4: minW=65, minH=93, rowSpan=1, colSpan=1
        val input = "Fri 16"

        // When
        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 65,
            minHeight = 93,
            rowSpan = 1,
            colSpan = 1
        )

        // Then
        assertEquals("Fri 16", result)
    }

    @Test
    fun `missing or invalid dimensions return date text unchanged`() {
        val input = "Fri 16"

        assertEquals(input, DateWidgetProvider.adjustDateTextForDimensions(input, 0, 0))
        assertEquals(input, DateWidgetProvider.adjustDateTextForDimensions(input, -1, -1))
    }

    @Test
    fun `date text without spaces is not auto-stacked in 2x2 widget`() {
        // Given a date format like "16" or "October" with no spaces
        val input = "16"

        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 180,
            minHeight = 180,
            rowSpan = 2,
            colSpan = 2
        )

        assertEquals("16", result)
    }

    @Test
    fun `date text with existing newline in 2x2 widget preserves newline`() {
        // Given a date format that already has newline in 2x2 widget
        val input = "Fri\n16"

        val result = DateWidgetProvider.adjustDateTextForDimensions(
            dateText = input,
            minWidth = 180,
            minHeight = 180,
            rowSpan = 2,
            colSpan = 2
        )

        assertEquals("Fri\n16", result)
    }
}
