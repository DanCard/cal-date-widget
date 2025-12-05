package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyWidgetProviderTest {

    @Test
    fun `past events on current day are capped to one line`() {
        val now = 1_000L
        val pastEnd = 900L

        val result = WeeklyWidgetProvider.maxLinesForEvent(
            isToday = true,
            eventEndTime = pastEnd,
            nowMillis = now
        )

        assertEquals(1, result)
    }

    @Test
    fun `future or current events remain uncapped`() {
        val now = 1_000L
        val futureEnd = 1_100L

        val result = WeeklyWidgetProvider.maxLinesForEvent(
            isToday = true,
            eventEndTime = futureEnd,
            nowMillis = now
        )

        assertEquals(0, result)
    }

    @Test
    fun `non-today past events are uncapped`() {
        val now = 1_000L
        val pastEnd = 900L

        val result = WeeklyWidgetProvider.maxLinesForEvent(
            isToday = false,
            eventEndTime = pastEnd,
            nowMillis = now
        )

        assertEquals(0, result)
    }
}
