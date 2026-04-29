package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeeklyDisplayLogicTest {

    @Test
    fun `chooseHeaderText keeps short weekday plus date when text is slightly wider than column`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val dayMillis = GregorianCalendar(2026, Calendar.MAY, 4).timeInMillis

            val result = WeeklyDisplayLogic.chooseHeaderText(
                colWidth = 100f,
                dayMillis = dayMillis
            ) { text ->
                when (text) {
                    "Mon 4" -> 103f
                    "M 4" -> 60f
                    "4" -> 20f
                    "M" -> 15f
                    else -> error("Unexpected header: $text")
                }
            }

            assertEquals("Mon 4", result.text)
            assertEquals(1.0f, result.scale, 0.001f)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `chooseHeaderText still abbreviates when short weekday plus date is far too wide`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val dayMillis = GregorianCalendar(2026, Calendar.MAY, 4).timeInMillis

            // "Mon 4" at 1.0x = 140px > 105 budget; at 0.85x = 119px > 105 budget too.
            // Forces fallthrough to the next ladder rung ("M 4").
            val result = WeeklyDisplayLogic.chooseHeaderText(
                colWidth = 100f,
                dayMillis = dayMillis
            ) { text ->
                when (text) {
                    "Mon 4" -> 140f
                    "M 4" -> 60f
                    "4" -> 20f
                    "M" -> 15f
                    else -> error("Unexpected header: $text")
                }
            }

            assertEquals("M 4", result.text)
            assertEquals(1.0f, result.scale, 0.001f)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

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

    // Tests for filterNearDuplicates

    private fun createEvent(
        id: Long,
        title: String,
        startTime: Long,
        isAllDay: Boolean = false
    ) = CalendarEvent(id, title, startTime, startTime + 3600000, 0, isAllDay, false, 0)

    @Test
    fun `filterNearDuplicates returns original list when no duplicates`() {
        // Given: Three distinct events at different times
        val events = listOf(
            createEvent(1, "Morning Meeting", 1000000000L),
            createEvent(2, "Lunch Break", 2000000000L),
            createEvent(3, "Afternoon Review", 3000000000L)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: All events should be kept
        assertEquals(3, result.size)
    }

    @Test
    fun `filterNearDuplicates returns empty list for empty input`() {
        // Given: Empty list
        val events = emptyList<CalendarEvent>()

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Empty list returned
        assertEquals(0, result.size)
    }

    @Test
    fun `filterNearDuplicates handles single event`() {
        // Given: Single event
        val events = listOf(
            createEvent(1, "Solo Meeting", 1000000000L)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Single event returned
        assertEquals(1, result.size)
        assertEquals("Solo Meeting", result[0].title)
    }

    @Test
    fun `filterNearDuplicates keeps all instances of a recurring series`() {
        // Mirror Android Instances semantics: every instance of a recurring event
        // shares the parent EVENT_ID but has a distinct BEGIN. Five weekday rows
        // 24h apart must all survive — they are NOT near-duplicates of each other.
        val mondayBase = 1000000000L
        val day = 24L * 60 * 60 * 1000L
        val events = (0..4).map { dayOffset ->
            createEvent(338L, "Dad take Sophia to school", mondayBase + dayOffset * day)
        }

        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        assertEquals(5, result.size)
        val dayOffsets = result.map { (it.startTime - mondayBase) / day }.toSet()
        assertEquals(setOf(0L, 1L, 2L, 3L, 4L), dayOffsets)
    }

    @Test
    fun `filterNearDuplicates hides event with same start time and title`() {
        // Given: Two events at same time with identical titles
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Team Weekly Standup Meeting Discussion", baseTime),
            createEvent(2, "Team Weekly Standup Meeting Discussion", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Only one event should remain
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates matches events within 15 minute window`() {
        // Given: Two events 10 minutes apart with similar titles
        val baseTime = 1000000000L
        val tenMinutes = 10 * 60 * 1000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone C-UAS Group Weekly", baseTime + tenMinutes)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should be detected as duplicates, only one kept
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates uses case-insensitive word matching`() {
        // Given: Two events with different case
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU ANTI-DRONE C-UAS GROUP WEEKLY", baseTime),
            createEvent(2, "dtu anti-drone c-uas group weekly", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should match regardless of case
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates matches events with at least 5 common words`() {
        // Given: Two events with exactly 5 common words
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Team Weekly Planning Meeting Session", baseTime),
            createEvent(2, "Team Weekly Planning Meeting Session Extra", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should be detected as duplicates (5 common words)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates handles word reordering`() {
        // Given: Two events with words in different order
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone Weekly C-UAS Group", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should match regardless of word order (6 common words)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates keeps events beyond 15 minute window`() {
        // Given: Two events 20 minutes apart
        val baseTime = 1000000000L
        val twentyMinutes = 20 * 60 * 1000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone C-UAS Group Weekly", baseTime + twentyMinutes)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should keep both (outside 15 minute window)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterNearDuplicates keeps all-day separate from timed events`() {
        // Given: One all-day event and one timed event with same title
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime, isAllDay = true),
            createEvent(2, "DTU Anti-Drone C-UAS Group Weekly", baseTime, isAllDay = false)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should keep both (different event types)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterNearDuplicates matches events with high similarity but fewer than 5 words`() {
        // Given: Two events with 4 words total, 3 common (75% match)
        // Similarity: 3/4 = 0.75 > 0.60
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Team Meeting Room A", baseTime),
            createEvent(2, "Team Meeting Room B", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should be detected as duplicates under new majority rule
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates handles three-way duplicates`() {
        // Given: Three duplicate events
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(3, "DTU Anti-Drone C-UAS Group Weekly", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should keep only one
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates rotates selection based on time`() {
        // Given: Two duplicate events with different IDs
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone C-UAS Group Weekly", baseTime)
        )

        // When: Filter at two different times
        val time1 = 1000000L
        val time2 = 1000000L + (60000L * 2) // 2 minutes later

        val result1 = WeeklyDisplayLogic.filterNearDuplicates(events, time1)
        val result2 = WeeklyDisplayLogic.filterNearDuplicates(events, time2)

        // Then: Both should return one event, but may be different
        assertEquals(1, result1.size)
        assertEquals(1, result2.size)
        // Verify it's deterministic for same time
        val result1Again = WeeklyDisplayLogic.filterNearDuplicates(events, time1)
        assertEquals(result1[0].id, result1Again[0].id)
    }

    @Test
    fun `filterNearDuplicates handles real-world DTU Anti-Drone case`() {
        // Given: Real-world scenario with two similar events at 11:00 AM
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "DTU Anti-Drone C-UAS Group Weekly", baseTime),
            createEvent(2, "DTU Anti-Drone Weekly C-UAS Group", baseTime),
            createEvent(3, "Completely Different Meeting", baseTime + 3600000L) // Different event 1 hour later
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should have 2 events (one DTU Anti-Drone, one different meeting)
        assertEquals(2, result.size)
        assertTrue(result.any { it.title.contains("Different") })
    }

    @Test
    fun `filterNearDuplicates matches short titles with majority match`() {
        // Given: Two events "Team Sync" and "Team Sync" (2/2 match)
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Team Sync", baseTime),
            createEvent(2, "Team Sync", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should be detected as duplicates (100% match, > 2 words? No 2 words)
        assertEquals(1, result.size)
    }
    
    @Test
    fun `filterNearDuplicates matches short titles with partial majority`() {
        // Given: "Project Alpha Sync" vs "Project Alpha Review" (2/3 match = 66%)
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Project Alpha Sync", baseTime),
            createEvent(2, "Project Alpha Review", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should be detected as duplicates
        assertEquals(1, result.size)
    }

    @Test
    fun `filterNearDuplicates does not match distinct short titles`() {
        // Given: "Team Meeting" vs "Client Meeting" (1/2 match = 50%)
        val baseTime = 1000000000L
        val events = listOf(
            createEvent(1, "Team Meeting", baseTime),
            createEvent(2, "Client Meeting", baseTime)
        )

        // When
        val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

        // Then: Should NOT be duplicates
        assertEquals(2, result.size)
    }

    @Test
    fun `filterNearDuplicates prioritizes interesting events over pending ones`() {
        // Given: Four duplicate events where three are pending (selfStatus=3) and one is accepted (selfStatus=1)
        val baseTime = 1000000000L
        val pending1 = CalendarEvent(1, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 3)
        val pending2 = CalendarEvent(2, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 3)
        val accepted = CalendarEvent(3, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 1)
        val pending3 = CalendarEvent(4, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 3)
        
        val events = listOf(pending1, pending2, accepted, pending3)

        // When
        // Test multiple times with different seeds to ensure it always picks the accepted one
        for (i in 0..10) {
            val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis() + (i * 60000))
            
            // Then: It should always pick the accepted one
            assertEquals(1, result.size)
            assertEquals(3L, result[0].id)
            assertEquals(1, result[0].selfStatus)
        }
    }

    @Test
    fun `filterNearDuplicates prioritizes interesting events over tentative ones`() {
        // Given: Four duplicate events where three are tentative (selfStatus=4) and one is accepted (selfStatus=1)
        val baseTime = 1000000000L
        val tentative1 = CalendarEvent(1, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 4)
        val tentative2 = CalendarEvent(2, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 4)
        val accepted = CalendarEvent(3, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 1)
        val tentative3 = CalendarEvent(4, "Daily Sync", baseTime, baseTime + 3600000, 0, false, false, 4)

        val events = listOf(tentative1, tentative2, accepted, tentative3)

        // When
        // Test multiple times with different seeds to ensure it always picks the accepted one
        for (i in 0..10) {
            val result = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis() + (i * 60000))

            // Then: It should always pick the accepted one
            assertEquals(1, result.size)
            assertEquals(3L, result[0].id)
            assertEquals(1, result[0].selfStatus)
        }
    }

    // ===== getEffectiveDayMillis =====

    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `getEffectiveDayMillis returns unchanged offsets when today is week start`() {
        val start = 1_700_000_000_000L
        val todayIndex = 0

        for (i in 0..6) {
            val expected = start + i * dayMs
            assertEquals("col $i", expected, WeeklyDisplayLogic.getEffectiveDayMillis(start, i, todayIndex))
        }
    }

    @Test
    fun `getEffectiveDayMillis shifts past columns forward by 7 days when today is midweek`() {
        val start = 1_700_000_000_000L
        val todayIndex = 3 // e.g., Wed with Sun-start

        // Past columns (0, 1, 2) shifted by +7
        assertEquals(start + (0 + 7) * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 0, todayIndex))
        assertEquals(start + (1 + 7) * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 1, todayIndex))
        assertEquals(start + (2 + 7) * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 2, todayIndex))

        // Today and future columns (3..6) unchanged
        assertEquals(start + 3 * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 3, todayIndex))
        assertEquals(start + 4 * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 4, todayIndex))
        assertEquals(start + 5 * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 5, todayIndex))
        assertEquals(start + 6 * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 6, todayIndex))
    }

    @Test
    fun `getEffectiveDayMillis shifts all past columns when today is end of week`() {
        val start = 1_700_000_000_000L
        val todayIndex = 6 // e.g., Sat with Sun-start

        // All prior columns (0..5) shift +7 days
        for (i in 0..5) {
            val expected = start + (i + 7) * dayMs
            assertEquals("col $i", expected, WeeklyDisplayLogic.getEffectiveDayMillis(start, i, todayIndex))
        }
        // Today's column (6) unchanged
        assertEquals(start + 6 * dayMs, WeeklyDisplayLogic.getEffectiveDayMillis(start, 6, todayIndex))
    }

    @Test
    fun `getEffectiveDayMillis shifts col 0 by exactly one week when today is after it`() {
        val start = 1_700_000_000_000L
        val result = WeeklyDisplayLogic.getEffectiveDayMillis(start, 0, 1)
        assertEquals(start + 7 * dayMs, result)
    }

    @Test
    fun `getEffectiveDayMillis fits all shifted columns within 7 days from today`() {
        // Verifies the design invariant: every displayed day falls within [today, today+6],
        // which is what allows fetching 7 days from todayMillis to cover all columns.
        val start = 1_700_000_000_000L

        for (todayIndex in 0..6) {
            val todayMillis = start + todayIndex * dayMs
            for (i in 0..6) {
                val effective = WeeklyDisplayLogic.getEffectiveDayMillis(start, i, todayIndex)
                val offsetFromToday = (effective - todayMillis) / dayMs
                assertTrue(
                    "col $i on todayIndex=$todayIndex had offset=$offsetFromToday, expected 0..6",
                    offsetFromToday in 0..6
                )
            }
        }
    }

    // ===== getHeaderTextSize =====

    @Test
    fun `getHeaderTextSize today clamps to 80 max on very wide columns`() {
        assertEquals(80f, WeeklyDisplayLogic.getHeaderTextSize(500f, isToday = true), 0.01f)
    }

    @Test
    fun `getHeaderTextSize today clamps to 30 min on very narrow columns`() {
        assertEquals(30f, WeeklyDisplayLogic.getHeaderTextSize(10f, isToday = true), 0.01f)
    }

    @Test
    fun `getHeaderTextSize today scales linearly in the middle`() {
        // 100 * 0.45 = 45 — within [30, 80]
        assertEquals(45f, WeeklyDisplayLogic.getHeaderTextSize(100f, isToday = true), 0.01f)
    }

    @Test
    fun `getHeaderTextSize non-today clamps to 40 max on wide columns`() {
        assertEquals(40f, WeeklyDisplayLogic.getHeaderTextSize(200f, isToday = false), 0.01f)
    }

    @Test
    fun `getHeaderTextSize non-today clamps to 20 min on narrow columns`() {
        assertEquals(20f, WeeklyDisplayLogic.getHeaderTextSize(10f, isToday = false), 0.01f)
    }

    @Test
    fun `getHeaderTextSize non-today scales linearly in the middle`() {
        // 50 * 0.5 = 25 — within [20, 40]
        assertEquals(25f, WeeklyDisplayLogic.getHeaderTextSize(50f, isToday = false), 0.01f)
    }

    // ===== chooseHeaderText =====
    // Uses a synthetic 10-pixels-per-character measurer so tests are deterministic
    // regardless of device fonts / locale.

    private val tenPxPerChar: (String) -> Float = { it.length * 10f }

    private fun wedApril22_2026Millis(): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.APRIL, 22, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `chooseHeaderText picks full EEE d format when column is wide enough`() {
        // "Wed 22" = 60px. Budget = 100 * 1.05 = 105. Fits at 1.0x.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 100f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("Wed 22", result.text)
        assertEquals(1.0f, result.scale, 0.001f)
    }

    @Test
    fun `chooseHeaderText shrinks full form before falling back to abbreviation`() {
        // colWidth=65, budget=68.25. "Wed 22"@1.0=60 fits → no shrink needed.
        // colWidth=55, budget=57.75. "Wed 22"@1.0=60 doesn't fit; @0.85=51 does.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 55f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("Wed 22", result.text)
        assertEquals(0.85f, result.scale, 0.001f)
    }

    @Test
    fun `chooseHeaderText falls back to single-letter weekday plus date when even shrunk full form doesnt fit`() {
        // colWidth=45, budget=47.25. "Wed 22"@1.0=60 no; @0.85=51 no. "W 22"@1.0=40 yes.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 45f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("W 22", result.text)
        assertEquals(1.0f, result.scale, 0.001f)
    }

    @Test
    fun `chooseHeaderText drops weekday before date on extreme narrowness`() {
        // "W 22"@1.0=40, "22"@1.0=20. Budget = 25 * 1.05 = 26.25. Only "22" fits.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 25f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("22", result.text)
        assertEquals(1.0f, result.scale, 0.001f)
    }

    @Test
    fun `chooseHeaderText returns single letter as last resort`() {
        // "22"@1.0=20. Budget = 15 * 1.05 = 15.75. Only "W"@1.0=10 fits.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 15f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("W", result.text)
        assertEquals(1.0f, result.scale, 0.001f)
    }

    @Test
    fun `chooseHeaderText returns shortest candidate even when nothing technically fits`() {
        // Budget = 1.05. Nothing fits. Should still return the shortest candidate.
        val result = WeeklyDisplayLogic.chooseHeaderText(
            colWidth = 1f,
            dayMillis = wedApril22_2026Millis(),
            measureText = tenPxPerChar
        )
        assertEquals("W", result.text)
    }

    @Test
    fun `chooseHeaderText preserves date signal as columns narrow`() {
        // Scans a range of widths and confirms we drop the weekday before dropping
        // the date — this is the load-bearing property for shifted next-week columns.
        val dayMillis = wedApril22_2026Millis()
        val budgets = listOf(200f, 100f, 50f, 30f, 20f, 10f)
        for (budget in budgets) {
            val result = WeeklyDisplayLogic.chooseHeaderText(
                colWidth = budget,
                dayMillis = dayMillis,
                measureText = tenPxPerChar
            )
            // If a weekday-only form ("W") appears, budget must be so tight that "22" didn't fit either.
            // The forbidden outcome is: weekday-only form while width would have fit "22".
            if (result.text == "W") {
                val twoCharWidth = tenPxPerChar("22")
                val effectiveBudget = budget * 1.05f
                assertTrue(
                    "At colWidth=$budget, result was 'W' but '22' ($twoCharWidth px) should have fit in budget $effectiveBudget",
                    twoCharWidth > effectiveBudget
                )
            }
        }
    }
}
