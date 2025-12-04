package ai.dcar.caldatewidget

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val color: Int,
    val isAllDay: Boolean
)
