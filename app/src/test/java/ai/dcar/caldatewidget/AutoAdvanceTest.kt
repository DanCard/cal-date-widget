package ai.dcar.caldatewidget

import android.provider.CalendarContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAdvanceTest {

    private fun createEvent(title: String, endMillis: Long, isDeclined: Boolean = false): CalendarEvent {
        return CalendarEvent(
            id = 1,
            title = title,
            startTime = endMillis - 3600000, // 1 hour duration
            endTime = endMillis,
            color = 0,
            isAllDay = false,
            isDeclined = isDeclined,
            selfStatus = if (isDeclined) CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED else CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
        )
    }

    @Test
    fun `shouldAutoAdvance returns false when there are no events`() {
        val todayEvents = emptyList<CalendarEvent>()
        val result = DailyDisplayLogic.shouldAutoAdvance(todayEvents, 1000L)
        assertFalse("Should not advance if no events", result)
    }

    @Test
    fun `shouldAutoAdvance returns false when there is a future event`() {
        val now = 1000L
        val events = listOf(
            createEvent("Past Event", now - 100),
            createEvent("Future Event", now + 100)
        )
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertFalse("Should not advance if future events exist", result)
    }

    @Test
    fun `shouldAutoAdvance returns true when all events are in the past`() {
        val now = 1000L
        val events = listOf(
            createEvent("Past Event 1", now - 100),
            createEvent("Past Event 2", now - 50)
        )
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance if all events are past", result)
    }

    @Test
    fun `shouldAutoAdvance always ignores declined events`() {
        val now = 1000L
        val events = listOf(
            createEvent("Past Event", now - 100),
            createEvent("Future Declined Event", now + 100, isDeclined = true)
        )
        
        // Declined events are always ignored, so the only relevant event is "Past Event"
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should always advance if only future event is declined", result)
    }

    @Test
    fun `shouldAutoAdvance returns false when only declined events exist`() {
        val now = 1000L
        val events = listOf(
            createEvent("Past Declined Event", now - 100, isDeclined = true)
        )
        
        // No non-declined events -> don't advance (stay on today to show nothing)
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertFalse("Should not advance if only declined events exist", result)
    }
}