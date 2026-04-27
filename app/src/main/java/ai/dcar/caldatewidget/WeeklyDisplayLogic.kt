package ai.dcar.caldatewidget

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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

    fun getEffectiveDayMillis(startMillis: Long, columnIndex: Int, todayIndex: Int): Long {
        val daysOffset = if (columnIndex < todayIndex) columnIndex + 7 else columnIndex
        return startMillis + daysOffset * 24L * 60 * 60 * 1000
    }

    /**
     * Returns a day-header text size scaled to column width, so narrow columns on
     * small-screen devices don't get overwhelmed by a fixed 80f today-header.
     * Clamped to sensible bounds so wide columns don't go absurdly large either.
     */
    fun getHeaderTextSize(colWidth: Float, isToday: Boolean): Float {
        return if (isToday) {
            (colWidth * 0.45f).coerceIn(30f, 80f)
        } else {
            (colWidth * 0.5f).coerceIn(20f, 40f)
        }
    }

    /**
     * Chooses the most informative day-header text that fits within colWidth given
     * the configured textSize on the measureText function. Tries formats in order
     * (widest/most-informative first) and returns the first one that fits:
     *   "Mon 29" → "M 29" → "29" → "M"
     * The date number is kept as long as possible before the weekday is dropped
     * entirely, because the date is what disambiguates shifted next-week columns
     * from this-week columns with the same weekday letter.
     */
    fun chooseHeaderText(
        colWidth: Float,
        dayMillis: Long,
        targetFraction: Float = 0.9f,
        measureText: (String) -> Float
    ): String {
        val locale = Locale.getDefault()
        val weekdayShort = SimpleDateFormat("EEE", locale).format(dayMillis)   // "Wed"
        val date = SimpleDateFormat("d", locale).format(dayMillis)             // "22"
        // SimpleDateFormat's "EEEEE" is unreliable across JDK versions (often returns full
        // form rather than narrow), so derive the single-letter form from the short form.
        val weekdayLetter = weekdayShort.take(1)                               // "W"
        val candidates = listOf(
            "$weekdayShort $date",   // "Wed 22"
            "$weekdayLetter $date",  // "W 22"
            date,                    // "22"
            weekdayLetter            // "W"
        )
        val budget = colWidth * targetFraction
        for (text in candidates) {
            if (measureText(text) <= budget) return text
        }
        return candidates.last()
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
     * Past events are always limited to a single line so current/future events have room.
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
            isPastEvent -> 1 // Past events on current day always limited to 1 line
            !isClipping -> 10 // No clipping, allow generous lines for current/future
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

    /**
     * Filters out near-duplicate events, keeping only one representative from each duplicate group.
     * Near-duplicates are events that:
     * - Have the same event type (both all-day OR both timed)
     * - Start within 15 minutes of each other
     * - Share at least 5 common words in their titles (case-insensitive)
     *
     * Selection rotates based on refresh time to ensure all duplicates get shown over time.
     *
     * @param events List of calendar events to filter
     * @param currentTimeMillis Current time in milliseconds, used for rotation seed
     * @return Filtered list with one event per duplicate group
     */
    fun filterNearDuplicates(events: List<CalendarEvent>, currentTimeMillis: Long): List<CalendarEvent> {
        if (events.size <= 1) return events

        // Log all events being processed
        android.util.Log.d("NearDuplicate", "=== Processing ${events.size} events ===")
        val dateFormat = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
        events.forEachIndexed { index, event ->
            val timeStr = dateFormat.format(java.util.Date(event.startTime))
            android.util.Log.d("NearDuplicate", "Event $index: '${event.title}' at $timeStr (id=${event.id}, allDay=${event.isAllDay})")
        }

        // Track by list index, not event.id: recurring-series instances share an
        // EVENT_ID, so an id-keyed set would silently drop every instance after the
        // first one before areNearDuplicates() ever runs.
        val processedIndices = mutableSetOf<Int>()
        val clusters = mutableListOf<List<CalendarEvent>>()

        // Group events into duplicate clusters
        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]
            val cluster = mutableListOf(event)
            processedIndices.add(i)

            // Find all duplicates of this event
            for (j in events.indices) {
                if (j in processedIndices) continue
                val other = events[j]
                if (areNearDuplicates(event, other)) {
                    android.util.Log.d("NearDuplicate", "Found duplicate: '${event.title}' matches '${other.title}'")
                    cluster.add(other)
                    processedIndices.add(j)
                }
            }

            if (cluster.size > 1) {
                android.util.Log.d("NearDuplicate", "Cluster of ${cluster.size} events: ${cluster.map { it.title }}")
            }
            clusters.add(cluster)
        }

        // Select one event from each cluster
        val filtered = clusters.map { cluster ->
            if (cluster.size == 1) {
                cluster[0]
            } else {
                // Prioritize events that are NOT "less interesting" (i.e. not pending/invited, not tentative, and not declined)
                // 3 = CalendarContract.Attendees.ATTENDEE_STATUS_INVITED
                // 4 = CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE
                val interestingPool = cluster.filter {
                    it.selfStatus != 3 && it.selfStatus != 4 && !it.isDeclined
                }
                val pool = if (interestingPool.isNotEmpty()) interestingPool else cluster

                // Time-based rotation: changes roughly every minute
                val seed = (currentTimeMillis / 60000 + pool.sumOf { it.id }) % pool.size
                val selected = pool[seed.toInt()]
                android.util.Log.d("NearDuplicate", "Selected '${selected.title}' from pool of ${pool.size} (cluster of ${cluster.size})")
                selected
            }
        }

        android.util.Log.d("NearDuplicate", "Before: ${events.size} events, After: ${filtered.size} events")
        return filtered
    }

    /**
     * Determines if two events are near-duplicates based on:
     * - Same event type (both all-day OR both timed)
     * - Start times within 15 minutes
     * - At least 5 common words in titles (case-insensitive)
     */
    private fun areNearDuplicates(a: CalendarEvent, b: CalendarEvent): Boolean {
        // Same type check
        if (a.isAllDay != b.isAllDay) {
            android.util.Log.d("NearDuplicate", "Different types: '${a.title}' (allDay=${a.isAllDay}) vs '${b.title}' (allDay=${b.isAllDay})")
            return false
        }

        // Time proximity (15 minutes = 900,000 ms)
        val timeDiff = kotlin.math.abs(a.startTime - b.startTime)
        if (timeDiff > 900_000) {
            android.util.Log.d("NearDuplicate", "Too far apart: '${a.title}' vs '${b.title}' (${timeDiff / 60000} minutes)")
            return false
        }

        // Title similarity: At least 5 words in common OR > 60% match (for short titles)
        // Split on whitespace, slashes, hyphens, and other punctuation
        val wordsA = a.title.lowercase().split("[\\s/\\-_,;:]+".toRegex()).filter { it.isNotBlank() }.toSet()
        val wordsB = b.title.lowercase().split("[\\s/\\-_,;:]+".toRegex()).filter { it.isNotBlank() }.toSet()
        val commonWords = wordsA.intersect(wordsB).size
        val maxWords = kotlin.math.max(wordsA.size, wordsB.size)
        val similarity = if (maxWords > 0) commonWords.toDouble() / maxWords else 0.0

        android.util.Log.d("NearDuplicate", "Comparing '${a.title}' vs '${b.title}': ${commonWords} common words (need 5), similarity=${String.format("%.2f", similarity)} (need > 0.60)")
        android.util.Log.d("NearDuplicate", "  Words A: $wordsA")
        android.util.Log.d("NearDuplicate", "  Words B: $wordsB")
        android.util.Log.d("NearDuplicate", "  Common: ${wordsA.intersect(wordsB)}")

        return commonWords >= 5 || (commonWords >= 1 && similarity > 0.60)
    }
}
