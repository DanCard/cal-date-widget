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
     * Returns true if there are events today AND all non-declined events have ended.
     * Declined events are always ignored for this decision.
     */
    fun shouldAutoAdvance(
        todayEvents: List<CalendarEvent>,
        nowMillis: Long
    ): Boolean {
        val validEvents = todayEvents.filter { !it.isDeclined }

        if (validEvents.isEmpty()) return false

        return validEvents.all { it.endTime < nowMillis }
    }
}
