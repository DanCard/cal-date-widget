package ai.dcar.caldatewidget

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrefsManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var prefsManager: PrefsManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        
        prefsManager = PrefsManager(context)
    }

    @Test
    fun `saveSettings saves all values to editor`() {
        val widgetId = 123
        val settings = PrefsManager.WidgetSettings(
            dateFormat = "yyyy-MM-dd",
            textColor = 0xFF0000,
            shadowColor = 0x00FF00,
            bgColor = 0x0000FF
        )

        prefsManager.saveSettings(widgetId, settings)

        verify { editor.putString("widget_123_format", "yyyy-MM-dd") }
        verify { editor.putInt("widget_123_text_color", 0xFF0000) }
        verify { editor.putInt("widget_123_shadow_color", 0x00FF00) }
        verify { editor.putInt("widget_123_bg_color", 0x0000FF) }
        verify { editor.apply() }
    }

    @Test
    fun `loadSettings returns defaults when missing`() {
        val widgetId = 456
        every { prefs.getString("widget_456_format", any()) } returns null
        
        val settings = prefsManager.loadSettings(widgetId)
        
        assertEquals(PrefsManager.DEFAULT_DATE_FORMAT, settings.dateFormat)
    }
}
