package ai.dcar.caldatewidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EventFilterTest {
    private companion object {
        const val JULIAN_DAY_OF_EPOCH = 2440588
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    private fun localMidnight(timeZone: TimeZone, year: Int, month: Int, dayOfMonth: Int): Long {
        return Calendar.getInstance(timeZone).apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun localJulianDay(timeZone: TimeZone, timeMillis: Long): Int {
        val offsetMillis = timeZone.getOffset(timeMillis).toLong()
        return Math.floorDiv(timeMillis + offsetMillis, DAY_MILLIS).toInt() + JULIAN_DAY_OF_EPOCH
    }

    private fun utcMidnight(year: Int, month: Int, dayOfMonth: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `test shouldDisplayEventOnDay correctly filters all-day events`() {
        val pst = TimeZone.getTimeZone("America/Los_Angeles")
        val defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(pst)

        try {
            val saturdayStart = localMidnight(pst, 2023, Calendar.NOVEMBER, 11)
            val saturdayEnd = localMidnight(pst, 2023, Calendar.NOVEMBER, 12)
            val sundayStart = saturdayEnd
            val sundayEnd = localMidnight(pst, 2023, Calendar.NOVEMBER, 13)

            val eventStart = utcMidnight(2023, Calendar.NOVEMBER, 12)
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

    @Test
    fun `multi-day all-day event uses exclusive end day in local time`() {
        val pst = TimeZone.getTimeZone("America/Los_Angeles")
        val defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(pst)

        try {
            val wednesdayStart = localMidnight(pst, 2026, Calendar.APRIL, 29)
            val thursdayStart = localMidnight(pst, 2026, Calendar.APRIL, 30)
            val fridayStart = localMidnight(pst, 2026, Calendar.MAY, 1)
            val saturdayStart = localMidnight(pst, 2026, Calendar.MAY, 2)

            val event = CalendarEvent(
                id = 2,
                title = "Children with Dad",
                startTime = utcMidnight(2026, Calendar.APRIL, 29),
                endTime = utcMidnight(2026, Calendar.MAY, 1),
                color = 0,
                isAllDay = true,
                isDeclined = false,
                selfStatus = 0
            )

            assertTrue(
                "Wed/Thu all-day event should show on Wednesday",
                WeeklyDisplayLogic.shouldDisplayEventOnDay(event, wednesdayStart, thursdayStart)
            )
            assertTrue(
                "Wed/Thu all-day event should show on Thursday",
                WeeklyDisplayLogic.shouldDisplayEventOnDay(event, thursdayStart, fridayStart)
            )
            assertFalse(
                "Wed/Thu all-day event should not show on Friday",
                WeeklyDisplayLogic.shouldDisplayEventOnDay(event, fridayStart, saturdayStart)
            )
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `all-day event uses provider local start and end days when available`() {
        val pst = TimeZone.getTimeZone("America/Los_Angeles")
        val defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(pst)

        try {
            val wednesdayStart = localMidnight(pst, 2026, Calendar.APRIL, 29)
            val thursdayStart = localMidnight(pst, 2026, Calendar.APRIL, 30)
            val fridayStart = localMidnight(pst, 2026, Calendar.MAY, 1)
            val saturdayStart = localMidnight(pst, 2026, Calendar.MAY, 2)

            val wednesdayJulian = localJulianDay(pst, wednesdayStart)
            val thursdayJulian = localJulianDay(pst, thursdayStart)

            val event = CalendarEvent(
                id = 3,
                title = "Children with Dad",
                startTime = 0L,
                endTime = 0L,
                color = 0,
                isAllDay = true,
                isDeclined = false,
                selfStatus = 0,
                startDay = wednesdayJulian,
                endDay = thursdayJulian
            )

            assertTrue(WeeklyDisplayLogic.shouldDisplayEventOnDay(event, wednesdayStart, thursdayStart))
            assertTrue(WeeklyDisplayLogic.shouldDisplayEventOnDay(event, thursdayStart, fridayStart))
            assertFalse(WeeklyDisplayLogic.shouldDisplayEventOnDay(event, fridayStart, saturdayStart))
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `single-day all-day instance from provider matches when startDay equals endDay`() {
        val pst = TimeZone.getTimeZone("America/Los_Angeles")
        val defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(pst)

        try {
            val wednesdayStart = localMidnight(pst, 2026, Calendar.APRIL, 29)
            val thursdayStart = localMidnight(pst, 2026, Calendar.APRIL, 30)
            val wednesdayJulian = localJulianDay(pst, wednesdayStart)

            val event = CalendarEvent(
                id = 4,
                title = "Children with Dad",
                startTime = utcMidnight(2026, Calendar.APRIL, 29),
                endTime = utcMidnight(2026, Calendar.APRIL, 30),
                color = 0,
                isAllDay = true,
                isDeclined = false,
                selfStatus = 0,
                startDay = wednesdayJulian,
                endDay = wednesdayJulian
            )

            assertTrue(WeeklyDisplayLogic.shouldDisplayEventOnDay(event, wednesdayStart, thursdayStart))
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }
}
