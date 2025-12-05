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

    /**
     * Determines whether start times should be shown for events based on available space.
     * Start times are shown only when there's enough room to display all events without clipping.
     *
     * @param eventCount Number of events to display
     * @param availableHeight Available height in pixels
     * @param lineHeight Height of a single line of text in pixels
     * @return true if start times should be shown, false if they should be hidden due to space constraints
     */
    fun shouldShowStartTimes(
        eventCount: Int,
        availableHeight: Float,
        lineHeight: Float
    ): Boolean {
        // Estimate height needed for events WITH start times
        // Events with start times take more space due to text wrapping
        // Use conservative estimate: 4 lines per event to account for start time prefix and wrapping
        val estimatedHeightPerEventWithTime = lineHeight * 4.0f
        val wouldClipWithStartTimes = (eventCount * estimatedHeightPerEventWithTime) > availableHeight
        // Show start times only if all events can fit with start times
        return !wouldClipWithStartTimes
    }

    /**
     * Calculates the maximum number of lines for an event on the current day.
     * When clipping occurs, past events are limited to 1 line to save space for current/future events.
     *
     * @param isPastEvent True if the event has already ended
     * @param isClipping True if there's not enough space to show all events
     * @param pastEventCount Number of past events on current day
     * @param currentFutureEventCount Number of current/future events on current day
     * @param availableHeight Available height in pixels
     * @param lineHeight Height of a single line of text in pixels
     * @return Maximum number of lines for this event
     */
    fun getMaxLinesForCurrentDayEvent(
        isPastEvent: Boolean,
        isClipping: Boolean,
        pastEventCount: Int,
        currentFutureEventCount: Int,
        availableHeight: Float,
        lineHeight: Float
    ): Int {
        return when {
            !isClipping -> 10 // No clipping, allow generous lines
            isPastEvent -> 1 // Past events on current day when clipping: 1 line only
            else -> {
                // Current/future events: equitable distribution of remaining space
                val pastEventsHeight = pastEventCount * lineHeight
                val remainingHeight = availableHeight - pastEventsHeight
                if (currentFutureEventCount == 0) {
                    1
                } else {
                    val maxLinesForCurrentFuture = (remainingHeight / (currentFutureEventCount * lineHeight)).toInt().coerceAtLeast(1)
                    maxLinesForCurrentFuture
                }
            }
        }
    }
}
