package ai.dcar.caldatewidget

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyEventsRegressionTest {

    private fun localMidnight(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun makeEvent(title: String, startTime: Long, endTime: Long, isAllDay: Boolean = false) = CalendarEvent(
        id = title.hashCode().toLong(),
        title = title,
        startTime = startTime,
        endTime = endTime,
        color = 0,
        isAllDay = isAllDay,
        isDeclined = false,
        selfStatus = 1
    )

    @Test
    fun `verify events for Tue-Sat show up in correct columns when today is Wed`() {
        // Week starts on Sunday, May 10, 2026
        val weekStart = localMidnight(2026, Calendar.MAY, 10)
        val todayIndex = 3 // Sun(0), Mon(1), Tue(2), Wed(3)

        // Events to verify:
        // Tue (Next week), Wed (Today), Thu (Future), Fri (Future), Sat (Future)
        val events = listOf(
            makeEvent("Tue Event", localMidnight(2026, Calendar.MAY, 19) + 10 * 3600000, localMidnight(2026, Calendar.MAY, 19) + 11 * 3600000),
            makeEvent("Wed Event", localMidnight(2026, Calendar.MAY, 13) + 10 * 3600000, localMidnight(2026, Calendar.MAY, 13) + 11 * 3600000),
            makeEvent("Thu Event", localMidnight(2026, Calendar.MAY, 14) + 10 * 3600000, localMidnight(2026, Calendar.MAY, 14) + 11 * 3600000),
            makeEvent("Fri Event", localMidnight(2026, Calendar.MAY, 15) + 10 * 3600000, localMidnight(2026, Calendar.MAY, 15) + 11 * 3600000),
            makeEvent("Sat Event", localMidnight(2026, Calendar.MAY, 16) + 10 * 3600000, localMidnight(2026, Calendar.MAY, 16) + 11 * 3600000)
        )

        // Simulation of WidgetDrawer.drawWeeklyCalendar logic
        val dayMillisList = (0 until 7).map { i ->
            WeeklyDisplayLogic.getEffectiveDayMillis(weekStart, i, todayIndex)
        }

        // Column mapping:
        // Col 0: Sun May 17
        // Col 1: Mon May 18
        // Col 2: Tue May 19  <-- Tue Event
        // Col 3: Wed May 13  <-- Wed Event
        // Col 4: Thu May 14  <-- Thu Event
        // Col 5: Fri May 15  <-- Fri Event
        // Col 6: Sat May 16  <-- Sat Event

        val eventsByDay = dayMillisList.map { dayMillis ->
            val dayEnd = dayMillis + 24 * 60 * 60 * 1000
            events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
        }

        // Assertions
        assertEquals("Wed Event should be in Col 3", "Wed Event", eventsByDay[3].first().title)
        assertEquals("Thu Event should be in Col 4", "Thu Event", eventsByDay[4].first().title)
        assertEquals("Fri Event should be in Col 5", "Fri Event", eventsByDay[5].first().title)
        assertEquals("Sat Event should be in Col 6", "Sat Event", eventsByDay[6].first().title)
        assertEquals("Tue Event should be in Col 2 (Next Week)", "Tue Event", eventsByDay[2].first().title)
    }

    @Test
    fun `verify all-day events for Tue-Sat show up in correct columns when today is Wed`() {
        // Use UTC for all-day events as per CalendarRepository behavior
        val utc = TimeZone.getTimeZone("UTC")
        fun utcMidnight(year: Int, month: Int, day: Int): Long {
            return Calendar.getInstance(utc).apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val weekStart = localMidnight(2026, Calendar.MAY, 10)
        val todayIndex = 3

        val events = listOf(
            makeEvent("Tue All-Day", utcMidnight(2026, Calendar.MAY, 19), utcMidnight(2026, Calendar.MAY, 20), true),
            makeEvent("Wed All-Day", utcMidnight(2026, Calendar.MAY, 13), utcMidnight(2026, Calendar.MAY, 14), true),
            makeEvent("Thu All-Day", utcMidnight(2026, Calendar.MAY, 14), utcMidnight(2026, Calendar.MAY, 15), true),
            makeEvent("Fri All-Day", utcMidnight(2026, Calendar.MAY, 15), utcMidnight(2026, Calendar.MAY, 16), true),
            makeEvent("Sat All-Day", utcMidnight(2026, Calendar.MAY, 16), utcMidnight(2026, Calendar.MAY, 17), true)
        )

        val dayMillisList = (0 until 7).map { i ->
            WeeklyDisplayLogic.getEffectiveDayMillis(weekStart, i, todayIndex)
        }

        val eventsByDay = dayMillisList.map { dayMillis ->
            val dayEnd = dayMillis + 24 * 60 * 60 * 1000
            events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
        }

        assertEquals("Wed All-Day should be in Col 3", "Wed All-Day", eventsByDay[3].first().title)
        assertEquals("Thu All-Day should be in Col 4", "Thu All-Day", eventsByDay[4].first().title)
        assertEquals("Fri All-Day should be in Col 5", "Fri All-Day", eventsByDay[5].first().title)
        assertEquals("Sat All-Day should be in Col 6", "Sat All-Day", eventsByDay[6].first().title)
        assertEquals("Tue All-Day should be in Col 2", "Tue All-Day", eventsByDay[2].first().title)
    }
}
