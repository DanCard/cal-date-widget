package ai.dcar.caldatewidget

import android.content.Context
import android.graphics.Color

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ai.dcar.caldatewidget.prefs"
        private const val KEY_PREFIX = "widget_"
        
        const val DEFAULT_DATE_FORMAT = "EEE d"
        const val DEFAULT_TEXT_COLOR = Color.WHITE
        const val DEFAULT_SHADOW_COLOR = Color.BLACK
        const val DEFAULT_BG_COLOR = Color.TRANSPARENT
    }

    data class WidgetSettings(
        val dateFormat: String = DEFAULT_DATE_FORMAT,
        val textColor: Int = DEFAULT_TEXT_COLOR,
        val shadowColor: Int = DEFAULT_SHADOW_COLOR,
        val bgColor: Int = DEFAULT_BG_COLOR
    )

    fun saveSettings(widgetId: Int, settings: WidgetSettings) {
        prefs.edit().apply {
            putString("${KEY_PREFIX}${widgetId}_format", settings.dateFormat)
            putInt("${KEY_PREFIX}${widgetId}_text_color", settings.textColor)
            putInt("${KEY_PREFIX}${widgetId}_shadow_color", settings.shadowColor)
            putInt("${KEY_PREFIX}${widgetId}_bg_color", settings.bgColor)
            apply()
        }
    }

    fun loadSettings(widgetId: Int): WidgetSettings {
        val format = prefs.getString("${KEY_PREFIX}${widgetId}_format", DEFAULT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT
        val textColor = prefs.getInt("${KEY_PREFIX}${widgetId}_text_color", DEFAULT_TEXT_COLOR)
        val shadowColor = prefs.getInt("${KEY_PREFIX}${widgetId}_shadow_color", DEFAULT_SHADOW_COLOR)
        val bgColor = prefs.getInt("${KEY_PREFIX}${widgetId}_bg_color", DEFAULT_BG_COLOR)
        
        return WidgetSettings(format, textColor, shadowColor, bgColor)
    }

    fun deleteSettings(widgetId: Int) {
        prefs.edit().apply {
            remove("${KEY_PREFIX}${widgetId}_format")
            remove("${KEY_PREFIX}${widgetId}_text_color")
            remove("${KEY_PREFIX}${widgetId}_shadow_color")
            remove("${KEY_PREFIX}${widgetId}_bg_color")
            apply()
        }
    }
}
