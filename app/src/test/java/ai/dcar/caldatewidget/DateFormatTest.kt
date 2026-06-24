package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class DateFormatTest {

    @Test
    fun `EEE dd format produces valid non-empty string`() {
        val format = "EEE dd"
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        val result = sdf.format(Date())

        println("Formatted date for '$format': $result")

        assertTrue("Result should not be empty", result.isNotEmpty())
        // Regex to match roughly "Tue 02" (Day 2 digits) or "Tue 2"
        // EEE is 3 letters usually.
        // Let's just ensure it's length > 3.
        assertTrue("Result length should be sufficient", result.length >= 4)
    }

    // June 24, 2026 — day-of-month is "24" for the styling tests below.
    private val june24 = GregorianCalendar(2026, Calendar.JUNE, 24).time

    private fun dayToken(pattern: String): String? {
        val f = DateWidgetText.format(pattern, june24, Locale.US)
        return f.dayRange?.let { f.text.substring(it.first, it.last + 1) }
    }

    @Test
    fun `day-of-month range is found in weekday plus day format`() {
        assertEquals("24", dayToken("EEE d"))
    }

    @Test
    fun `day-of-month range is found in slash numeric format`() {
        assertEquals("24", dayToken("MM/dd/yyyy"))
    }

    @Test
    fun `day-of-month range is found in iso format`() {
        assertEquals("24", dayToken("yyyy-MM-dd"))
    }

    @Test
    fun `day-of-month range covers the whole string for day-only format`() {
        assertEquals("24", dayToken("d"))
    }

    @Test
    fun `format with no day field yields null range`() {
        val f = DateWidgetText.format("EEEE", june24, Locale.US)
        assertNull(f.dayRange)
        assertEquals("Wednesday", f.text)
    }

    @Test
    fun `invalid pattern degrades gracefully`() {
        // Unterminated quote -> SimpleDateFormat throws IllegalArgumentException.
        val f = DateWidgetText.format("'unterminated", june24, Locale.US)
        assertEquals("Invalid Format", f.text)
        assertNull(f.dayRange)
    }
}
