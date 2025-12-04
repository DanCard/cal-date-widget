package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeeklyDisplayLogicTest {

    @Test
    fun `getStartDate returns today if today matches start day`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val todayMillis = cal.timeInMillis // Today IS Monday (hypothetically)
        
        // Mocking "Today" by passing a calendar set to Monday
        // If we ask for start day Monday, it should return same day
        val startMillis = WeeklyDisplayLogic.getStartDate(cal, Calendar.MONDAY)
        
        // We need to be careful about time stripping. 
        // The logic strips time. So compare days.
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = startMillis
        
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        // Ensure it didn't go back a week
        // Difference should be < 24 hours (just time stripping)
        val diff = kotlin.math.abs(cal.timeInMillis - startMillis)
        assertTrue(diff < 24 * 60 * 60 * 1000)
    }

    @Test
    fun `getStartDate goes back to past Monday if today is Wednesday`() {
        // Set today to Wednesday
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        
        val startMillis = WeeklyDisplayLogic.getStartDate(cal, Calendar.MONDAY)
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = startMillis
        
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertTrue(startMillis < cal.timeInMillis)
        
        // Diff should be roughly 2 days (Wed -> Tue -> Mon)
        // 48 hours
        val diff = cal.timeInMillis - startMillis
        // Allow some buffer for current time vs midnight
        assertTrue(diff >= 48 * 60 * 60 * 1000 - 10000) 
    }

    @Test
    fun `getColumnWeights assigns weights correctly`() {
        val count = 7
        val todayIndex = 2 // Mon(0), Tue(1), Wed(2=Today)
        
        val weights = WeeklyDisplayLogic.getColumnWeights(todayIndex, count)
        
        // Past (0, 1)
        assertEquals(0.6f, weights[0], 0.01f)
        assertEquals(0.6f, weights[1], 0.01f)
        
        // Today (2)
        assertEquals(2.0f, weights[2], 0.01f)
        
        // Future (3, 4, 5, 6)
        // 3 is immediate future -> 1.5
        assertEquals(1.5f, weights[3], 0.01f)
        // 4 -> 1.35
        assertEquals(1.35f, weights[4], 0.01f)
        
        // Verify assertions:
        // Current (2.0) > Past (0.6) -> YES
        // Current (2.0) > Future (1.5) -> YES
        // Future (1.5) > Past (0.6) -> YES
        // Future Decay: weights[3] > weights[4] -> YES
    }
}
