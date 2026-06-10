package ai.dcar.caldatewidget

import java.util.Calendar

object DailyDisplayLogic {
    private const val AUTO_ADVANCE_EVENT_END_BUFFER_MILLIS = 1_000L

    data class GridLayout(val cols: Int, val rows: Int, val days: Int)

    /**
     * Computes the grid for the multi-line flow mode.
     * @param cols Number of columns (days per row)
     * @param heightDp Current widget height in dp
     * @param twoLineModeEnabled User's preference
     * @param minRowHeightDp Minimum height for one legible row
     * @return cols x rows grid
     */
    fun computeGridLayout(
        cols: Int,
        heightDp: Float,
        twoLineModeEnabled: Boolean,
        minRowHeightDp: Float = 110f
    ): GridLayout {
        val safeCols = cols.coerceAtLeast(1)
        val rows = if (twoLineModeEnabled && heightDp >= minRowHeightDp * 2) 2 else 1
        val days = safeCols * rows
        return GridLayout(safeCols, rows, days)
    }

    fun getHeaderTextSize(colWidth: Float, isToday: Boolean): Float {
        val ratio = if (isToday) 0.3f else 0.25f
        return (colWidth * ratio).coerceIn(30f, 70f)
    }

    fun getColumnWeights(todayIndex: Int, daysCount: Int): FloatArray {
        val weights = FloatArray(daysCount)
        for (i in 0 until daysCount) {
            if (i < todayIndex) {
                weights[i] = 0.6f
            } else if (i == todayIndex) {
                weights[i] = 2.0f
            } else {
                val dist = i - todayIndex
                var w = 1.5f - (dist - 1) * 0.15f
                if (w < 0.7f) w = 0.7f
                weights[i] = w
            }
        }
        return weights
    }

    /**
     * Determines if the widget should auto-advance to the next day.
     *
     * Two conditions must BOTH hold to advance:
     *  - The local time is at or past the cutoff hour (default 3 PM). This prevents jumping
     *    to "tomorrow" earlier in the day — keeping today's view in place even when all of
     *    today's events have already ended.
     *  - Every remaining non-declined event has ended. (For an empty day this is vacuously
     *    true, so an event-less day advances exactly at the cutoff.)
     *
     * Declined events are always ignored for this decision.
     */
    fun shouldAutoAdvance(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long,
        cutoffMinuteOfDay: Int = PrefsManager.DEFAULT_DAILY_AUTO_ADVANCE_CUTOFF_MINUTE_OF_DAY
    ): Boolean {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        if (minuteOfDay(calendar) < cutoffMinuteOfDay.coerceIn(0, (24 * 60) - 1)) {
            return false
        }

        val validEvents = todayEvents.filter { !it.isDeclined }
        return validEvents.all { it.endTime < nowMillis }
    }

    fun getNextAutoAdvanceCheckTime(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long,
        cutoffMinuteOfDay: Int = PrefsManager.DEFAULT_DAILY_AUTO_ADVANCE_CUTOFF_MINUTE_OF_DAY
    ): Long? {
        if (shouldAutoAdvance(todayEvents, nowMillis, cutoffMinuteOfDay)) return null

        val validEvents = todayEvents.filter { !it.isDeclined }
        val nextEventEnd = validEvents
            .filter { it.endTime >= nowMillis }
            .minOfOrNull { it.endTime + AUTO_ADVANCE_EVENT_END_BUFFER_MILLIS }

        val cutoffTime = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            val cutoff = cutoffMinuteOfDay.coerceIn(0, (24 * 60) - 1)
            set(Calendar.HOUR_OF_DAY, cutoff / 60)
            set(Calendar.MINUTE, cutoff % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val cutoffCandidate = if (nowMillis < cutoffTime) cutoffTime else null

        // Wake at whichever milestone comes first. An intermediate event-end before the
        // cutoff just re-renders (refreshing past-event dimming) and re-schedules toward
        // the next milestone; the chain terminates once both advance conditions hold.
        return listOfNotNull(nextEventEnd, cutoffCandidate).minOrNull()
    }

    private fun minuteOfDay(calendar: Calendar): Int {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
}
