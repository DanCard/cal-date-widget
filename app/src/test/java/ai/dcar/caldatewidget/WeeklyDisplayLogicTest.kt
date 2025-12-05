package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeeklyDisplayLogicTest {

    @Test
    fun `getStartDate returns today if today matches start day`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        // Mocking "Today" by passing a calendar set to Monday
        // If we ask for start day Monday, it should return same day
        val startMillis = WeeklyDisplayLogic.getStartDate(cal, Calendar.MONDAY)
        
        // We need to be careful about time stripping. 
        // The logic strips time. So compare days.
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = startMillis
        
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        // Ensure it didn't go back a week
        // Difference should be < 24 hours (just time stripping)
        val diff = kotlin.math.abs(cal.timeInMillis - startMillis)
        assertTrue(diff < 24 * 60 * 60 * 1000)
    }

    @Test
    fun `getStartDate goes back to past Monday if today is Wednesday`() {
        // Set today to Wednesday
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        
        val startMillis = WeeklyDisplayLogic.getStartDate(cal, Calendar.MONDAY)
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = startMillis
        
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertTrue(startMillis < cal.timeInMillis)
        
        // Diff should be roughly 2 days (Wed -> Tue -> Mon)
        // 48 hours
        val diff = cal.timeInMillis - startMillis
        // Allow some buffer for current time vs midnight
        assertTrue(diff >= 48 * 60 * 60 * 1000 - 10000) 
    }

    @Test
    fun `getColumnWeights assigns weights correctly`() {
        val count = 7
        val todayIndex = 2 // Mon(0), Tue(1), Wed(2=Today)

        val weights = WeeklyDisplayLogic.getColumnWeights(todayIndex, count)

        // Past (0, 1)
        assertEquals(0.6f, weights[0], 0.01f)
        assertEquals(0.6f, weights[1], 0.01f)

        // Today (2)
        assertEquals(2.0f, weights[2], 0.01f)

        // Future (3, 4, 5, 6)
        // 3 is immediate future -> 1.5
        assertEquals(1.5f, weights[3], 0.01f)
        // 4 -> 1.35
        assertEquals(1.35f, weights[4], 0.01f)

        // Verify assertions:
        // Current (2.0) > Past (0.6) -> YES
        // Current (2.0) > Future (1.5) -> YES
        // Future (1.5) > Past (0.6) -> YES
        // Future Decay: weights[3] > weights[4] -> YES
    }

    @Test
    fun `shouldShowStartTimes returns true when there is no clipping`() {
        // Given: 2 events, plenty of available space
        val eventCount = 2
        val lineHeight = 50f
        val availableHeight = 500f // Much more than needed (2 * 4 * 50 = 400)

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown (no clipping)
        assertTrue(result)
    }

    @Test
    fun `shouldShowStartTimes returns false when there is clipping`() {
        // Given: 5 events, limited available space
        val eventCount = 5
        val lineHeight = 50f
        val availableHeight = 500f // Not enough (5 * 4 * 50 = 1000 > 500)

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be hidden (clipping would occur)
        assertTrue(!result)
    }

    @Test
    fun `shouldShowStartTimes returns true at the boundary`() {
        // Given: Events fit exactly with the estimate (4 lines per event with start times)
        val eventCount = 5
        val lineHeight = 50f
        val availableHeight = 1000f // Exactly fits (5 * 4 * 50 = 1000)

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown (no clipping, exactly fits)
        assertTrue(result)
    }

    @Test
    fun `shouldShowStartTimes returns false just over the boundary`() {
        // Given: Events just barely don't fit
        val eventCount = 5
        val lineHeight = 50f
        val availableHeight = 999f // Just under what's needed (5 * 4 * 50 = 1000)

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be hidden (would clip)
        assertTrue(!result)
    }

    @Test
    fun `shouldShowStartTimes handles zero events`() {
        // Given: No events
        val eventCount = 0
        val lineHeight = 50f
        val availableHeight = 100f

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown (no clipping possible with no events)
        assertTrue(result)
    }

    @Test
    fun `shouldShowStartTimes handles single event with plenty of space`() {
        // Given: 1 event, plenty of space
        val eventCount = 1
        val lineHeight = 50f
        val availableHeight = 1000f

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown
        assertTrue(result)
    }

    @Test
    fun `shouldShowStartTimes for current day with realistic clipping scenario`() {
        // Given: Realistic scenario from logs - 4 events on current day
        // Available height: 761.0, Line height: 86.4
        // Actual heights were: 90, 255, 425, 340 = 1110 total (clipping!)
        val eventCount = 4
        val lineHeight = 86.4f
        val availableHeight = 761.0f
        // Estimate: 4 * 4 * 86.4 = 1382.4 > 761, so should detect clipping

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should NOT be shown (clipping detected)
        assertTrue(!result)
    }

    @Test
    fun `shouldShowStartTimes for current day with few events and space`() {
        // Given: Current day with only 2 events and plenty of space
        val eventCount = 2
        val lineHeight = 86.4f
        val availableHeight = 761.0f
        // Estimate: 2 * 4 * 86.4 = 691.2 < 761, no clipping

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown (no clipping)
        assertTrue(result)
    }

    @Test
    fun `shouldShowStartTimes detects clipping when many events on current day`() {
        // Given: 5 events on current day with limited space
        val eventCount = 5
        val lineHeight = 86.4f
        val availableHeight = 761.0f
        // Estimate: 5 * 4 * 86.4 = 1728 > 761, clipping

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should NOT be shown (clipping detected)
        assertTrue(!result)
    }

    @Test
    fun `shouldShowStartTimes for weekend day with sparse events`() {
        // Given: Weekend day (Sat/Sun) with 1-3 events and good vertical space
        val eventCount = 3
        val lineHeight = 60f
        val availableHeight = 800f
        // Estimate: 3 * 4 * 60 = 720 < 800, no clipping

        // When
        val result = WeeklyDisplayLogic.shouldShowStartTimes(eventCount, availableHeight, lineHeight)

        // Then: Start times should be shown (no clipping)
        assertTrue(result)
    }

    // Tests for getMaxLinesForCurrentDayEvent

    @Test
    fun `getMaxLinesForCurrentDayEvent returns 1 line for past events when clipping`() {
        // Given: Past event on current day with clipping occurring
        val isPastEvent = true
        val isClipping = true
        val pastEventCount = 2
        val currentFutureEventCount = 2
        val availableHeight = 761.0f
        val lineHeight = 86.4f

        // When
        val maxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // Then: Past events should be limited to 1 line when clipping
        assertEquals(1, maxLines)
    }

    @Test
    fun `getMaxLinesForCurrentDayEvent returns 1 line for past events even when no clipping`() {
        // Given: Past event on current day with NO clipping
        val isPastEvent = true
        val isClipping = false
        val pastEventCount = 1
        val currentFutureEventCount = 1
        val availableHeight = 1000.0f
        val lineHeight = 50.0f

        // When
        val maxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // Then: Past events are capped to 1 line to save space for current/future
        assertEquals(1, maxLines)
    }

    @Test
    fun `getMaxLinesForCurrentDayEvent distributes space equitably for future events when clipping`() {
        // Given: Future event on current day with clipping
        // 2 past events (2 * 86.4 = 172.8 height), 2 future events
        // Remaining height: 761 - 172.8 = 588.2
        // Per future event: 588.2 / (2 * 86.4) = 3.4 lines -> 3 lines
        val isPastEvent = false
        val isClipping = true
        val pastEventCount = 2
        val currentFutureEventCount = 2
        val availableHeight = 761.0f
        val lineHeight = 86.4f

        // When
        val maxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // Then: Future events get equitable distribution of remaining space
        assertEquals(3, maxLines)
    }

    @Test
    fun `getMaxLinesForCurrentDayEvent returns at least 1 line for future events`() {
        // Given: Future event with very limited space
        val isPastEvent = false
        val isClipping = true
        val pastEventCount = 5
        val currentFutureEventCount = 10
        val availableHeight = 500.0f
        val lineHeight = 50.0f

        // When
        val maxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // Then: Even with very limited space, ensure at least 1 line
        assertTrue(maxLines >= 1)
    }

    @Test
    fun `getMaxLinesForCurrentDayEvent caps current future events when clipping`() {
        // Given: Clipping scenario with two current/future events and no past events
        // Available height allows only 2 lines per event (200 / (2 * 50) = 2)
        val isPastEvent = false
        val isClipping = true
        val pastEventCount = 0
        val currentFutureEventCount = 2
        val availableHeight = 200.0f
        val lineHeight = 50.0f

        val maxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent = isPastEvent,
            isClipping = isClipping,
            pastEventCount = pastEventCount,
            currentFutureEventCount = currentFutureEventCount,
            availableHeight = availableHeight,
            lineHeight = lineHeight
        )

        assertEquals(2, maxLines)
    }

    @Test
    fun `getMaxLinesForCurrentDayEvent realistic scenario from logs`() {
        // Given: Realistic scenario from device logs
        // 4 events total: 2 past, 2 current/future
        // Available height: 761.0, Line height: 86.4
        val isClipping = true
        val pastEventCount = 2
        val currentFutureEventCount = 2
        val availableHeight = 761.0f
        val lineHeight = 86.4f

        // When: Calculate max lines for past event
        val pastMaxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent = true, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // When: Calculate max lines for future event
        val futureMaxLines = WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent(
            isPastEvent = false, isClipping, pastEventCount, currentFutureEventCount, availableHeight, lineHeight
        )

        // Then: Past events get 1 line, future events get more
        assertEquals(1, pastMaxLines)
        assertTrue(futureMaxLines > 1)

        // Verify the allocation makes sense
        // 2 past events * 1 line * 86.4 = 172.8
        // Remaining: 761 - 172.8 = 588.2
        // 2 future events should share this: 588.2 / (2 * 86.4) = ~3.4 -> 3 lines each
        assertEquals(3, futureMaxLines)
    }
}
