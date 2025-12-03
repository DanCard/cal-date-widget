package ai.dcar.caldatewidget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
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
}
