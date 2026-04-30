package ai.dcar.caldatewidget

import java.util.Calendar

object DailyDisplayLogic {
    private const val AUTO_ADVANCE_CUTOFF_HOUR = 15
    private const val AUTO_ADVANCE_EVENT_END_BUFFER_MILLIS = 1_000L

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
     * Two branches:
     *  - If today had non-declined events and all of them have ended, advance immediately
     *    (no time-of-day gate — once the day's last event is over, there is nothing left to wait for).
     *  - If today had no non-declined events at all, only advance after the local cutoff hour.
     *    This prevents jumping to "tomorrow" first thing in the morning on a calendar that
     *    just hasn't had events added yet.
     *
     * Declined events are always ignored for this decision.
     */
    fun shouldAutoAdvance(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long
    ): Boolean {
        val validEvents = todayEvents.filter { !it.isDeclined }

        if (validEvents.isNotEmpty()) {
            return validEvents.all { it.endTime < nowMillis }
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        return calendar.get(Calendar.HOUR_OF_DAY) >= AUTO_ADVANCE_CUTOFF_HOUR
    }

    fun getNextAutoAdvanceCheckTime(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long
    ): Long? {
        if (shouldAutoAdvance(todayEvents, nowMillis)) return null

        val validEvents = todayEvents.filter { !it.isDeclined }
        val nextEventEnd = validEvents
            .filter { it.endTime >= nowMillis }
            .minOfOrNull { it.endTime + AUTO_ADVANCE_EVENT_END_BUFFER_MILLIS }

        if (nextEventEnd != null) {
            return nextEventEnd
        }

        val cutoffTime = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, AUTO_ADVANCE_CUTOFF_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return if (nowMillis < cutoffTime) cutoffTime else null
    }
}
