package ai.dcar.caldatewidget

import java.util.Calendar
import java.util.TimeZone

object WeeklyDisplayLogic {
    fun getStartDate(calendar: Calendar, weekStartDay: Int): Long {
        val cal = calendar.clone() as Calendar
        // Ensure we are at start of day
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        if (weekStartDay != -1) {
            // Go back until we hit the start day
            // This ensures we pick a past or present day, never future
            while (cal.get(Calendar.DAY_OF_WEEK) != weekStartDay) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        return cal.timeInMillis
    }

    fun shouldDisplayEventOnDay(event: CalendarEvent, dayStart: Long, dayEnd: Long): Boolean {
        if (!event.isAllDay) {
            return event.startTime < dayEnd && event.endTime >= dayStart
        }

        // For All-Day events, start/end are in UTC. We map them to Local dates.
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCal.timeInMillis = event.startTime

        val localCal = Calendar.getInstance() // Default timezone
        localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        localCal.set(Calendar.MILLISECOND, 0)
        val adjustedStart = localCal.timeInMillis

        utcCal.timeInMillis = event.endTime
        localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        localCal.set(Calendar.MILLISECOND, 0)
        val adjustedEnd = localCal.timeInMillis

        return adjustedStart < dayEnd && adjustedEnd >= dayStart
    }

    fun getColumnWeights(todayIndex: Int, daysCount: Int): FloatArray {
        val weights = FloatArray(daysCount)
        for (i in 0 until daysCount) {
            if (i < todayIndex) {
                // Past
                weights[i] = 0.6f
            } else if (i == todayIndex) {
                // Today
                weights[i] = 2.0f
            } else {
                // Future
                // "Make future columns bigger than past columns" -> > 0.6
                // "Make columns further away from current day smaller" -> decay
                val dist = i - todayIndex
                // immediate future (dist=1) -> 1.5
                // decay by 0.15
                
                var w = 1.5f - (dist - 1) * 0.15f
                if (w < 0.7f) w = 0.7f
                weights[i] = w
            }
        }
        return weights
    }
}
