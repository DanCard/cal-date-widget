package ai.dcar.caldatewidget

import java.util.Calendar

object DailyDisplayLogic {
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
     * Returns true if there are events today AND all valid events (respecting showDeclined) have ended.
     */
    fun shouldAutoAdvance(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long,
        showDeclined: Boolean
    ): Boolean {
        // Filter valid events based on settings
        val validEvents = if (!showDeclined) {
            todayEvents.filter { !it.isDeclined }
        } else {
            todayEvents
        }

        // If no events today, don't auto-advance (stay on today to show "No events")
        if (validEvents.isEmpty()) return false

        // Check if ALL valid events are in the past
        return validEvents.all { it.endTime < nowMillis }
    }
}
