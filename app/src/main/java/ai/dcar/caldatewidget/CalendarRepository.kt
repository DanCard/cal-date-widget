package ai.dcar.caldatewidget

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

class CalendarRepository(private val context: Context) {

    fun getEventsForWeek(startMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        // 1. Get Visible Calendar IDs
        val visibleCalendarIds = getVisibleCalendarIds()
        if (visibleCalendarIds.isEmpty()) return events

        // End of the week (7 days later)
        val endMillis = startMillis + (7 * 24 * 60 * 60 * 1000)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY
        )

        // Build selection for calendar IDs
        val calendarSelection = StringBuilder()
        calendarSelection.append("${CalendarContract.Instances.CALENDAR_ID} IN (")
        for (i in visibleCalendarIds.indices) {
            if (i > 0) calendarSelection.append(",")
            calendarSelection.append("?")
        }
        calendarSelection.append(")")

        val selection = "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.BEGIN} <= ? AND $calendarSelection"
        
        val argsList = mutableListOf<String>()
        argsList.add(startMillis.toString())
        argsList.add(endMillis.toString())
        visibleCalendarIds.forEach { argsList.add(it.toString()) }
        
        val selectionArgs = argsList.toTypedArray()
        
        // Construct the URI for the instance table range
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
                val colorIdx = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                while (it.moveToNext()) {
                    val allDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false
                    events.add(
                        CalendarEvent(
                            it.getLong(idIdx),
                            it.getString(titleIdx) ?: "No Title",
                            it.getLong(beginIdx),
                            it.getLong(endIdx),
                            if (colorIdx >= 0) it.getInt(colorIdx) else 0xFF000000.toInt(),
                            allDay
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
        return events
    }

    private fun getVisibleCalendarIds(): List<Long> {
        val ids = mutableListOf<Long>()
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        
        try {
            val cursor = context.contentResolver.query(uri, projection, selection, null, null)
            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                while (it.moveToNext()) {
                    ids.add(it.getLong(idIdx))
                }
            }
        } catch (e: SecurityException) {
            // Ignore
        }
        return ids
    }
}
