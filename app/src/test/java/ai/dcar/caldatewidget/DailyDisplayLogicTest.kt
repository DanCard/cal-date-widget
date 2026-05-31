package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyDisplayLogicTest {

    @Test
    fun testComputeGridLayout_disabled() {
        val grid = DailyDisplayLogic.computeGridLayout(cols = 3, heightDp = 300f, twoLineModeEnabled = false)
        assertEquals(3, grid.cols)
        assertEquals(1, grid.rows)
        assertEquals(3, grid.days)
    }

    @Test
    fun testComputeGridLayout_enabled_tall() {
        val grid = DailyDisplayLogic.computeGridLayout(cols = 3, heightDp = 220f, twoLineModeEnabled = true)
        assertEquals(3, grid.cols)
        assertEquals(2, grid.rows)
        assertEquals(6, grid.days)
    }

    @Test
    fun testComputeGridLayout_enabled_short() {
        val grid = DailyDisplayLogic.computeGridLayout(cols = 3, heightDp = 150f, twoLineModeEnabled = true)
        assertEquals(3, grid.cols)
        assertEquals(1, grid.rows)
        assertEquals(3, grid.days)
    }

    @Test
    fun testComputeGridLayout_wide_twoWeeks() {
        // Wide (7 columns) and tall -> 14 days
        val grid = DailyDisplayLogic.computeGridLayout(cols = 7, heightDp = 220f, twoLineModeEnabled = true)
        assertEquals(7, grid.cols)
        assertEquals(2, grid.rows)
        assertEquals(14, grid.days)
    }
}
