package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class CalendarImageGeneratorRefactoredTest {

    private val day0 = GregorianCalendar(2026, Calendar.MAY, 11).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val dayMillisList = (0L until 7).map { day0 + it * (24L * 60 * 60 * 1000) }

    private fun makeEvent(
        title: String,
        startTime: Long,
        endTime: Long,
        isAllDay: Boolean = false,
        isDeclined: Boolean = false,
        selfStatus: Int = 1
    ) = CalendarEvent(
        id = title.hashCode().toLong(),
        title = title,
        startTime = startTime,
        endTime = endTime,
        color = 0,
        isAllDay = isAllDay,
        isDeclined = isDeclined,
        selfStatus = selfStatus
    )

    @Test
    fun `computeAdjustedWeights does not modify weights when all days have events`() {
        // Given
        val events = dayMillisList.map { dayMillis ->
            makeEvent("Event on $dayMillis", dayMillis + 3600000, dayMillis + 7200000)
        }
        val originalWeights = floatArrayOf(2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.6f, 0.6f)

        // When
        val result = WidgetColumnRenderer.computeAdjustedWeights(events, originalWeights, dayMillisList)

        // Then
        assertEquals(originalWeights.toList(), result.toList())
    }

    @Test
    fun `computeAdjustedWeights reduces weight for empty days`() {
        // Given - only day 0 and day 3 have events
        val events = listOf(
            makeEvent("Event", dayMillisList[0] + 3600000, dayMillisList[0] + 7200000),
            makeEvent("Event", dayMillisList[3] + 3600000, dayMillisList[3] + 7200000)
        )
        val originalWeights = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)

        // When
        val result = WidgetColumnRenderer.computeAdjustedWeights(events, originalWeights, dayMillisList)

        // Then - days 1, 2, 4, 5, 6 should be reduced
        val f = WidgetColumnRenderer.EMPTY_COLUMN_WEIGHT_FACTOR
        assertEquals(1.0f, result[0], 0.001f)
        assertEquals(1.0f * f, result[1], 0.001f)
        assertEquals(1.0f * f, result[2], 0.001f)
        assertEquals(1.0f, result[3], 0.001f)
        assertEquals(1.0f * f, result[4], 0.001f)
        assertEquals(1.0f * f, result[5], 0.001f)
        assertEquals(1.0f * f, result[6], 0.001f)
    }

    @Test
    fun `computeAdjustedWeights does not modify original weights array`() {
        // Given
        val events = listOf(
            makeEvent("Event", dayMillisList[0] + 3600000, dayMillisList[0] + 7200000)
        )
        val originalWeights = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)

        // When
        WidgetColumnRenderer.computeAdjustedWeights(events, originalWeights, dayMillisList)

        // Then - original should be unchanged
        assertEquals(1.0f, originalWeights[0], 0.001f)
        assertEquals(1.0f, originalWeights[1], 0.001f)
    }

    @Test
    fun `computeAdjustedWeights with no events reduces all weights`() {
        // Given
        val events = emptyList<CalendarEvent>()
        val originalWeights = floatArrayOf(2.0f, 1.5f, 1.0f, 1.0f, 1.0f, 0.7f, 0.7f)

        // When
        val result = WidgetColumnRenderer.computeAdjustedWeights(events, originalWeights, dayMillisList)

        // Then - all should be reduced
        for (i in result.indices) {
            assertEquals(originalWeights[i] * WidgetColumnRenderer.EMPTY_COLUMN_WEIGHT_FACTOR, result[i], 0.001f)
        }
    }

    // A second stacked week, immediately following dayMillisList (days 7..13).
    private val twoWeekDayMillisList = (0L until 14).map { day0 + it * (24L * 60 * 60 * 1000) }

    @Test
    fun `computeAlignedAdjustedWeights with one week matches computeAdjustedWeights`() {
        // Regression guard: the single-week path must be byte-for-byte identical.
        val events = listOf(
            makeEvent("Event", dayMillisList[0] + 3600000, dayMillisList[0] + 7200000),
            makeEvent("Event", dayMillisList[3] + 3600000, dayMillisList[3] + 7200000)
        )
        val weights = WeeklyDisplayLogic.getColumnWeights(2, 7)

        val aligned = WidgetColumnRenderer.computeAlignedAdjustedWeights(events, weights, dayMillisList, weeks = 1)
        val perBand = WidgetColumnRenderer.computeAdjustedWeights(events, weights, dayMillisList)

        assertEquals(perBand.toList(), aligned.toList())
    }

    @Test
    fun `computeAlignedAdjustedWeights keeps a weekday full width when only one week has events`() {
        // Day index 3: events in week 1 only. Day index 5: events in week 2 only.
        // Both must stay full width so the stacked columns line up vertically.
        val events = listOf(
            makeEvent("Wk1", twoWeekDayMillisList[3] + 3600000, twoWeekDayMillisList[3] + 7200000),
            makeEvent("Wk2", twoWeekDayMillisList[12] + 3600000, twoWeekDayMillisList[12] + 7200000)
        )
        val base = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)

        val result = WidgetColumnRenderer.computeAlignedAdjustedWeights(events, base, twoWeekDayMillisList, weeks = 2)

        val f = WidgetColumnRenderer.EMPTY_COLUMN_WEIGHT_FACTOR
        // Index 3 busy in week 1, index 5 busy in week 2 -> both full width.
        assertEquals(1.0f, result[3], 0.001f)
        assertEquals(1.0f, result[5], 0.001f)
        // Indices empty in BOTH weeks are shrunk.
        assertEquals(1.0f * f, result[0], 0.001f)
        assertEquals(1.0f * f, result[1], 0.001f)
    }

    @Test
    fun `computeAlignedAdjustedWeights shrinks a weekday empty in every week`() {
        // No events anywhere -> every column shrunk by the empty factor.
        val base = WeeklyDisplayLogic.getColumnWeights(2, 7)
        val f = WidgetColumnRenderer.EMPTY_COLUMN_WEIGHT_FACTOR

        val result = WidgetColumnRenderer.computeAlignedAdjustedWeights(
            emptyList(), base, twoWeekDayMillisList, weeks = 2
        )

        for (i in result.indices) {
            assertEquals(base[i] * f, result[i], 0.001f)
        }
    }

    @Test
    fun `ColumnRenderConfig weekly has correct pastEventScaleFactor`() {
        val config = WidgetColumnRenderer.ColumnRenderConfig(
            pastEventScaleFactor = 0.7f,
            useCompressDeclinedMaxLines = true,
            useTimeScaleForPast = true,
            widgetTag = "Weekly"
        )
        assertEquals(0.7f, config.pastEventScaleFactor, 0.001f)
    }

    @Test
    fun `ColumnRenderConfig daily has correct pastEventScaleFactor`() {
        val config = WidgetColumnRenderer.ColumnRenderConfig(
            pastEventScaleFactor = 0.8f,
            useCompressDeclinedMaxLines = true,
            useTimeScaleForPast = true,
            widgetTag = "Daily"
        )
        assertEquals(0.8f, config.pastEventScaleFactor, 0.001f)
    }

    @Test
    fun `todayIndex is clamped to valid range when before start`() {
        // Given - today is before the start of the week
        val todayMillis = dayMillisList[0] - (24L * 60 * 60 * 1000)
        val startMillis = dayMillisList[0]

        // When
        val todayIndex = ((todayMillis - startMillis) / (24L * 60 * 60 * 1000)).toInt().coerceIn(0, 6)

        // Then
        assertEquals(0, todayIndex)
    }

    @Test
    fun `todayIndex is clamped to valid range when after end`() {
        // Given - today is after the end of the week
        val todayMillis = dayMillisList[6] + (24L * 60 * 60 * 1000)
        val startMillis = dayMillisList[0]

        // When
        val todayIndex = ((todayMillis - startMillis) / (24L * 60 * 60 * 1000)).toInt().coerceIn(0, 6)

        // Then
        assertEquals(6, todayIndex)
    }

    @Test
    fun `todayIndex is within bounds for normal case`() {
        // Given - today is day 3 of the week
        val todayMillis = dayMillisList[3]
        val startMillis = dayMillisList[0]

        // When
        val todayIndex = ((todayMillis - startMillis) / (24L * 60 * 60 * 1000)).toInt().coerceIn(0, 6)

        // Then
        assertEquals(3, todayIndex)
    }
}
