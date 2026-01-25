package ai.dcar.caldatewidget

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val ownerAccount: String
)

fun CalendarInfo.isPersonalCalendar(): Boolean {
    return accountName == ownerAccount
}

class CalendarRepository(private val context: Context) {

    fun getEvents(startMillis: Long, numDays: Int, calendarIds: Set<Long>? = null): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        // 1. Get Visible Calendar IDs (or use provided list)
        val visibleCalendarIds = if (calendarIds != null && calendarIds.isNotEmpty()) {
            calendarIds.toList()
        } else {
            getVisibleCalendarIds()
        }
        
        if (visibleCalendarIds.isEmpty()) return events

        // End of the range
        val endMillis = startMillis + (numDays.toLong() * 24 * 60 * 60 * 1000)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS
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
                val selfStatusIdx = it.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)

                while (it.moveToNext()) {
                    val allDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false
                    val selfStatus = if (selfStatusIdx >= 0) it.getInt(selfStatusIdx) else 0
                    val isDeclined = selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                    
                    events.add(
                        CalendarEvent(
                            it.getLong(idIdx),
                            it.getString(titleIdx) ?: "No Title",
                            it.getLong(beginIdx),
                            it.getLong(endIdx),
                            if (colorIdx >= 0) it.getInt(colorIdx) else 0xFF000000.toInt(),
                            allDay,
                            isDeclined,
                            selfStatus
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
        
        // Deduplicate events that have same title, start, end, and allDay status
        return events.distinctBy { 
            listOf(it.title, it.startTime, it.endTime, it.isAllDay, it.isDeclined, it.selfStatus)
        }
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

    fun getAvailableCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val ownerIdx = it.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)

                while (it.moveToNext()) {
                    calendars.add(
                        CalendarInfo(
                            it.getLong(idIdx),
                            it.getString(nameIdx) ?: "Unknown",
                            it.getString(accountIdx) ?: "",
                            it.getString(ownerIdx) ?: ""
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
        return calendars
    }

    fun getDefaultCalendarIds(): Set<Long> {
        return getAvailableCalendars()
            .filter { it.isPersonalCalendar() }
            .map { it.id }
            .toSet()
    }
}