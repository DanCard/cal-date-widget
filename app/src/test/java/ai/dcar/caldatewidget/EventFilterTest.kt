package ai.dcar.caldatewidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EventFilterTest {

    @Test
    fun `test shouldDisplayEventOnDay correctly filters all-day events`() {
         val pst = TimeZone.getTimeZone("America/Los_Angeles")
        val defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(pst)

        try {
             val cal = Calendar.getInstance(pst)
            cal.set(2023, Calendar.NOVEMBER, 11, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val saturdayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val saturdayEnd = cal.timeInMillis
            
            val sundayStart = saturdayEnd
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val sundayEnd = cal.timeInMillis

            val utc = TimeZone.getTimeZone("UTC")
            val calUtc = Calendar.getInstance(utc)
            calUtc.set(2023, Calendar.NOVEMBER, 12, 0, 0, 0)
            calUtc.set(Calendar.MILLISECOND, 0)
            val eventStart = calUtc.timeInMillis // Sunday 00:00 UTC
            val eventEnd = eventStart + 24 * 60 * 60 * 1000
            
             val event = CalendarEvent(
                id = 1,
                title = "Sunday All Day",
                startTime = eventStart,
                endTime = eventEnd,
                color = 0,
                isAllDay = true,
                isDeclined = false,
                selfStatus = 0
            )
            
            // Test Saturday
            assertFalse("Sunday All-Day event should not show on Saturday (PST)", 
                WeeklyDisplayLogic.shouldDisplayEventOnDay(event, saturdayStart, saturdayEnd))
                
            // Test Sunday
            assertTrue("Sunday All-Day event should show on Sunday (PST)", 
                WeeklyDisplayLogic.shouldDisplayEventOnDay(event, sundayStart, sundayEnd))
                
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }
}

