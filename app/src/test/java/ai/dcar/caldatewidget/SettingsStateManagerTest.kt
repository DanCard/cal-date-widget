package ai.dcar.caldatewidget

import android.graphics.Color
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsStateManagerTest {

    private val initialSettings = PrefsManager.WidgetSettings(
        dateFormat = "Initial",
        textColor = 0xFFFFFF,
        startTimeColor = 0xCCCCFF
    )

    @Before
    fun setup() {
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0xCCCCFF
    }

    @Test
    fun `updateSettings pushes to undo stack when requested`() {
        val manager = SettingsStateManager(initialSettings)
        
        val newSettings = initialSettings.copy(dateFormat = "New")
        manager.updateSettings(newSettings, pushToUndo = true)
        
        assertTrue(manager.canUndo())
        assertEquals(1, manager.getStackSize())
        assertEquals("Initial", manager.peekUndoStack()?.dateFormat)
        assertEquals("New", manager.currentSettings.dateFormat)
    }

    @Test
    fun `undo reverts to previous settings`() {
        val manager = SettingsStateManager(initialSettings)
        
        val settings1 = initialSettings.copy(dateFormat = "One")
        manager.updateSettings(settings1, pushToUndo = true)
        
        val settings2 = settings1.copy(dateFormat = "Two")
        manager.updateSettings(settings2, pushToUndo = true)
        
        assertEquals("Two", manager.currentSettings.dateFormat)
        assertEquals(2, manager.getStackSize())
        
        // First Undo
        val undoSuccess = manager.undo()
        assertTrue(undoSuccess)
        assertEquals("One", manager.currentSettings.dateFormat)
        assertEquals(1, manager.getStackSize())
        
        // Second Undo
        manager.undo()
        assertEquals("Initial", manager.currentSettings.dateFormat)
        assertFalse(manager.canUndo())
    }

    @Test
    fun `multiple levels of undo work correctly`() {
        val manager = SettingsStateManager(initialSettings)
        
        // Push 5 changes
        for (i in 1..5) {
            val s = initialSettings.copy(dateFormat = "Step $i")
            manager.updateSettings(s, pushToUndo = true)
        }
        
        assertEquals("Step 5", manager.currentSettings.dateFormat)
        assertEquals(5, manager.getStackSize()) // Stack contains Initial, Step 1, Step 2, Step 3, Step 4
        
        // Undo all 5
        for (i in 5 downTo 1) {
            manager.undo()
            val expected = if (i == 1) "Initial" else "Step ${i-1}"
            assertEquals(expected, manager.currentSettings.dateFormat)
        }
        
        assertEquals("Initial", manager.currentSettings.dateFormat)
        assertFalse(manager.canUndo())
    }

    @Test
    fun `undo on empty stack does nothing`() {
        val manager = SettingsStateManager(initialSettings)
        assertFalse(manager.undo())
        assertEquals("Initial", manager.currentSettings.dateFormat)
    }
}
