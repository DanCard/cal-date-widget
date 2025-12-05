package ai.dcar.caldatewidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkStatic
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
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0xCCCCFF

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
        every { prefs.getFloat("widget_456_text_scale", PrefsManager.DEFAULT_TEXT_SCALE) } returns PrefsManager.DEFAULT_TEXT_SCALE
        
        val settings = prefsManager.loadSettings(widgetId)
        
        assertEquals(PrefsManager.DEFAULT_DATE_FORMAT, settings.dateFormat)
    }

    @Test
    fun `loadSettings falls back to default text scale`() {
        val widgetId = 789
        every { prefs.getFloat("widget_789_text_scale", PrefsManager.DEFAULT_TEXT_SCALE) } returns PrefsManager.DEFAULT_TEXT_SCALE

        val settings = prefsManager.loadSettings(widgetId)

        assertEquals(PrefsManager.DEFAULT_TEXT_SCALE, settings.textSizeScale, 0.0f)
    }
}
