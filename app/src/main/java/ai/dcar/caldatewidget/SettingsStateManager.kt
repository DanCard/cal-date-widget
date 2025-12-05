package ai.dcar.caldatewidget

import java.util.Stack

class SettingsStateManager(initialSettings: PrefsManager.WidgetSettings) {
    
    var currentSettings: PrefsManager.WidgetSettings = initialSettings
        private set
        
    private val undoStack = Stack<PrefsManager.WidgetSettings>()
    private var inChangeSession = false
    
    fun updateSettings(newSettings: PrefsManager.WidgetSettings, pushToUndo: Boolean = true) {
        if (pushToUndo) {
            undoStack.push(currentSettings.copy())
        }
        inChangeSession = false
        currentSettings = newSettings
    }

    fun beginChangeSession() {
        if (!inChangeSession) {
            undoStack.push(currentSettings.copy())
            inChangeSession = true
        }
    }

    fun applySessionChange(newSettings: PrefsManager.WidgetSettings) {
        currentSettings = newSettings
    }

    fun endChangeSession() {
        inChangeSession = false
    }
    
    fun undo(): Boolean {
        if (undoStack.isNotEmpty()) {
            currentSettings = undoStack.pop()
            inChangeSession = false
            return true
        }
        return false
    }
    
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    
    // Helper for testing state
    fun peekUndoStack(): PrefsManager.WidgetSettings? {
        return if (undoStack.isNotEmpty()) undoStack.peek() else null
    }
    
    fun getStackSize(): Int = undoStack.size
}
