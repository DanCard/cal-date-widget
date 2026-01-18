package ai.dcar.caldatewidget

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Using a stable SDK for Robolectric
class CalendarRepositoryTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: CalendarRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        repository = CalendarRepository(context)
    }

    @Test
    fun `getEvents returns empty when no visible calendars`() {
        // Mock getVisibleCalendarIds query to return empty cursor
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { cursor.close() } returns Unit

        every {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                any(), any(), any(), any()
            )
        } returns cursor

        val events = repository.getEvents(System.currentTimeMillis(), 7)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `getEvents returns events correctly`() {
        // 1. Mock Visible Calendars
        val calCursor = mockk<Cursor>(relaxed = true)
        val calIdIndex = 0
        every { calCursor.getColumnIndex(CalendarContract.Calendars._ID) } returns calIdIndex
        // First call true (found 1 cal), second false
        every { calCursor.moveToNext() } returnsMany listOf(true, false)
        every { calCursor.getLong(calIdIndex) } returns 1L
        every { calCursor.close() } returns Unit

        every {
            contentResolver.query(
                match { it == CalendarContract.Calendars.CONTENT_URI },
                any(), any(), any(), any()
            )
        } returns calCursor

        // 2. Mock Events
        val eventCursor = mockk<Cursor>(relaxed = true)
        val idIdx = 0
        val titleIdx = 1
        val beginIdx = 2
        val endIdx = 3
        val colorIdx = 4
        val allDayIdx = 5
        val selfStatusIdx = 6

        every { eventCursor.getColumnIndex(CalendarContract.Instances.EVENT_ID) } returns idIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.TITLE) } returns titleIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.BEGIN) } returns beginIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.END) } returns endIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR) } returns colorIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.ALL_DAY) } returns allDayIdx
        every { eventCursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS) } returns selfStatusIdx

        // Return 2 events
        every { eventCursor.moveToNext() } returnsMany listOf(true, true, false)
        
        // Event 1
        every { eventCursor.getLong(idIdx) } returnsMany listOf(101L, 102L)
        every { eventCursor.getString(titleIdx) } returnsMany listOf("Meeting A", "Meeting B")
        every { eventCursor.getLong(beginIdx) } returnsMany listOf(1000L, 2000L)
        every { eventCursor.getLong(endIdx) } returnsMany listOf(1500L, 2500L)
        every { eventCursor.getInt(colorIdx) } returnsMany listOf(1, 2)
        every { eventCursor.getInt(allDayIdx) } returnsMany listOf(0, 1) // First timed, second all-day
        every { eventCursor.getInt(selfStatusIdx) } returnsMany listOf(1, 1)

        every { eventCursor.close() } returns Unit

        // Capture the URI passed to query to ensure it matches Instances table
        val uriSlot = slot<Uri>()
        every {
            contentResolver.query(
                match { it != CalendarContract.Calendars.CONTENT_URI },
                any(), any(), any(), any()
            )
        } answers {
            uriSlot.captured = firstArg()
            eventCursor
        }

        val startMillis = 100000L
        val events = repository.getEvents(startMillis, 7)

        assertEquals(2, events.size)
        assertEquals("Meeting A", events[0].title)
        assertEquals(false, events[0].isAllDay)
        assertEquals("Meeting B", events[1].title)
        assertEquals(true, events[1].isAllDay)
        
        // Verify URI authority
        assertEquals(CalendarContract.AUTHORITY, uriSlot.captured.authority)
    }

    @Test
    fun `getEvents handles duplicates`() {
        // 1. Mock Visible Calendars
        val calCursor = mockk<Cursor>(relaxed = true)
        every { calCursor.getColumnIndex(any()) } returns 0
        every { calCursor.moveToNext() } returnsMany listOf(true, false)
        every { calCursor.getLong(0) } returns 1L
        every { calCursor.close() } returns Unit

        every {
            contentResolver.query(
                match { it == CalendarContract.Calendars.CONTENT_URI },
                any(), any(), any(), any()
            )
        } returns calCursor

        // 2. Mock Events with Duplicate
        val eventCursor = mockk<Cursor>(relaxed = true)
        // Indices
        every { eventCursor.getColumnIndex(any()) } answers {
            when (firstArg<String>()) {
                CalendarContract.Instances.EVENT_ID -> 0
                CalendarContract.Instances.TITLE -> 1
                CalendarContract.Instances.BEGIN -> 2
                CalendarContract.Instances.END -> 3
                CalendarContract.Instances.DISPLAY_COLOR -> 4
                CalendarContract.Instances.ALL_DAY -> 5
                CalendarContract.Instances.SELF_ATTENDEE_STATUS -> 6
                else -> -1
            }
        }

        // Return 2 identical events
        every { eventCursor.moveToNext() } returnsMany listOf(true, true, false)
        
        // Event data (identical)
        every { eventCursor.getLong(0) } returns 101L
        every { eventCursor.getString(1) } returns "Duplicate Meeting"
        every { eventCursor.getLong(2) } returns 1000L
        every { eventCursor.getLong(3) } returns 2000L
        every { eventCursor.getInt(4) } returns -1
        every { eventCursor.getInt(5) } returns 0
        every { eventCursor.getInt(6) } returns 1

        every { eventCursor.close() } returns Unit

        every {
            contentResolver.query(
                match { it != CalendarContract.Calendars.CONTENT_URI }, 
                any(), any(), any(), any()
            )
        } returns eventCursor

        val events = repository.getEvents(0L, 7)

        // Should only be 1 because distinctBy filters out exact matches
        assertEquals(1, events.size)
        assertEquals("Duplicate Meeting", events[0].title)
    }

    @Test
    fun `getEvents handles SecurityException gracefully`() {
        // Mock query throwing SecurityException
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } throws SecurityException("Permission denied")

        val events = repository.getEvents(System.currentTimeMillis(), 7)
        assertTrue(events.isEmpty())
    }
}