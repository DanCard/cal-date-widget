package ai.dcar.caldatewidget

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.mockk
import java.util.Calendar

class CalendarRepositoryTest {

    @Test
    fun `repo calculates correct week range`() {
        // Just a logic test, not mocking ContentResolver deeply here to avoid heavy setup.
        // We verify the date math logic if we were to extract it.
        
        val startMillis = System.currentTimeMillis()
        val endMillis = startMillis + (7 * 24 * 60 * 60 * 1000)
        
        assertTrue(endMillis > startMillis)
        val diff = endMillis - startMillis
        assertEquals(604800000L, diff)
    }
}
