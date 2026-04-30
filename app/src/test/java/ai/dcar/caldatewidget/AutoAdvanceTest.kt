package ai.dcar.caldatewidget

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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

    private fun timeMillis(hourOfDay: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `shouldAutoAdvance returns false when there are no events before cutoff`() {
        val todayEvents = emptyList<CalendarEvent>()
        val result = DailyDisplayLogic.shouldAutoAdvance(todayEvents, timeMillis(14, 59))
        assertFalse("Should not advance before cutoff when there are no events", result)
    }

    @Test
    fun `shouldAutoAdvance returns true when there are no events at cutoff`() {
        val todayEvents = emptyList<CalendarEvent>()
        val result = DailyDisplayLogic.shouldAutoAdvance(todayEvents, timeMillis(15, 0))
        assertTrue("Should advance at cutoff when there are no events", result)
    }

    @Test
    fun `shouldAutoAdvance returns false when there is a future event after cutoff`() {
        val now = timeMillis(16, 0)
        val events = listOf(
            createEvent("Past Event", now - 1000),
            createEvent("Future Event", now + 1000)
        )
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertFalse("Should not advance if future events exist", result)
    }

    @Test
    fun `shouldAutoAdvance returns true when all events are in the past even before cutoff`() {
        val now = timeMillis(14, 59)
        val events = listOf(
            createEvent("Past Event 1", now - 1000),
            createEvent("Past Event 2", now - 500)
        )
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance once last event ends, regardless of time of day", result)
    }

    @Test
    fun `shouldAutoAdvance returns true at 1pm when last event ended at noon`() {
        val now = timeMillis(13, 0)
        val events = listOf(createEvent("Lunch", endMillis = timeMillis(12, 0)))
        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance once last event ends, even before 3 PM cutoff", result)
    }

    @Test
    fun `shouldAutoAdvance returns true when all events are in the past after cutoff`() {
        val now = timeMillis(15, 0)
        val events = listOf(
            createEvent("Past Event 1", now - 1000),
            createEvent("Past Event 2", now - 500)
        )

        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance after cutoff when all events are past", result)
    }

    @Test
    fun `shouldAutoAdvance always ignores declined events`() {
        val now = timeMillis(15, 30)
        val events = listOf(
            createEvent("Past Event", now - 1000),
            createEvent("Future Declined Event", now + 1000, isDeclined = true)
        )

        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance if only remaining future event is declined and cutoff passed", result)
    }

    @Test
    fun `shouldAutoAdvance returns false when only declined events exist before cutoff`() {
        val now = timeMillis(14, 59)
        val events = listOf(
            createEvent("Past Declined Event", now - 1000, isDeclined = true)
        )

        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertFalse("Should not advance before cutoff if only declined events exist", result)
    }

    @Test
    fun `shouldAutoAdvance returns true when only declined events exist after cutoff`() {
        val now = timeMillis(15, 0)
        val events = listOf(
            createEvent("Past Declined Event", now - 1000, isDeclined = true)
        )

        val result = DailyDisplayLogic.shouldAutoAdvance(events, now)
        assertTrue("Should advance after cutoff if only declined events exist", result)
    }

    @Test
    fun `getNextAutoAdvanceCheckTime returns next event end plus buffer when future events remain`() {
        val now = timeMillis(12, 0)
        val firstEnd = timeMillis(12, 30)
        val secondEnd = timeMillis(17, 0)
        val events = listOf(
            createEvent("Lunch", endMillis = firstEnd),
            createEvent("Dinner", endMillis = secondEnd)
        )

        val result = DailyDisplayLogic.getNextAutoAdvanceCheckTime(events, now)

        assertEquals(firstEnd + 1000L, result)
    }

    @Test
    fun `getNextAutoAdvanceCheckTime returns cutoff for empty day before cutoff`() {
        val now = timeMillis(14, 59)

        val result = DailyDisplayLogic.getNextAutoAdvanceCheckTime(emptyList(), now)

        assertEquals(timeMillis(15, 0), result)
    }

    @Test
    fun `getNextAutoAdvanceCheckTime returns null after auto advance condition is met`() {
        val now = timeMillis(13, 0)
        val events = listOf(createEvent("Lunch", endMillis = timeMillis(12, 0)))

        val result = DailyDisplayLogic.getNextAutoAdvanceCheckTime(events, now)

        assertEquals(null, result)
    }
}
