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
        val bgColor: Int = DEFAULT_BG_COLOR,
        val weekStartDay: Int = 2, // Default Monday
        val startTimeColor: Int = Color.parseColor("#ccccff"),
        val startTimeShadowColor: Int = Color.BLACK,
        val showDeclinedEvents: Boolean = true,
        val showAmPm: Boolean = false,
        val selectedCalendarIds: Set<Long> = emptySet()
    )

    fun saveSettings(widgetId: Int, settings: WidgetSettings) {
        prefs.edit().apply {
            putString("${KEY_PREFIX}${widgetId}_format", settings.dateFormat)
            putInt("${KEY_PREFIX}${widgetId}_text_color", settings.textColor)
            putInt("${KEY_PREFIX}${widgetId}_shadow_color", settings.shadowColor)
            putInt("${KEY_PREFIX}${widgetId}_bg_color", settings.bgColor)
            putInt("${KEY_PREFIX}${widgetId}_week_start", settings.weekStartDay)
            putInt("${KEY_PREFIX}${widgetId}_start_time_color", settings.startTimeColor)
            putInt("${KEY_PREFIX}${widgetId}_start_time_shadow", settings.startTimeShadowColor)
            putBoolean("${KEY_PREFIX}${widgetId}_show_declined", settings.showDeclinedEvents)
            putBoolean("${KEY_PREFIX}${widgetId}_show_ampm", settings.showAmPm)
            putStringSet("${KEY_PREFIX}${widgetId}_calendars", settings.selectedCalendarIds.map { it.toString() }.toSet())
            apply()
        }
    }

    fun loadSettings(widgetId: Int): WidgetSettings {
        val format = prefs.getString("${KEY_PREFIX}${widgetId}_format", DEFAULT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT
        val textColor = prefs.getInt("${KEY_PREFIX}${widgetId}_text_color", DEFAULT_TEXT_COLOR)
        val shadowColor = prefs.getInt("${KEY_PREFIX}${widgetId}_shadow_color", DEFAULT_SHADOW_COLOR)
        val bgColor = prefs.getInt("${KEY_PREFIX}${widgetId}_bg_color", DEFAULT_BG_COLOR)
        val weekStart = prefs.getInt("${KEY_PREFIX}${widgetId}_week_start", 2)
        val startTimeColor = prefs.getInt("${KEY_PREFIX}${widgetId}_start_time_color", Color.parseColor("#ccccff"))
        val startTimeShadowColor = prefs.getInt("${KEY_PREFIX}${widgetId}_start_time_shadow", Color.BLACK)
        val showDeclined = prefs.getBoolean("${KEY_PREFIX}${widgetId}_show_declined", true)
        val showAmPm = prefs.getBoolean("${KEY_PREFIX}${widgetId}_show_ampm", false)
        val selectedCalendarIds = prefs.getStringSet("${KEY_PREFIX}${widgetId}_calendars", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

        return WidgetSettings(format, textColor, shadowColor, bgColor, weekStart, startTimeColor, startTimeShadowColor, showDeclined, showAmPm, selectedCalendarIds)
    }

    fun deleteSettings(widgetId: Int) {
        prefs.edit().apply {
            remove("${KEY_PREFIX}${widgetId}_format")
            remove("${KEY_PREFIX}${widgetId}_text_color")
            remove("${KEY_PREFIX}${widgetId}_shadow_color")
            remove("${KEY_PREFIX}${widgetId}_bg_color")
            remove("${KEY_PREFIX}${widgetId}_week_start")
            remove("${KEY_PREFIX}${widgetId}_start_time_color")
            remove("${KEY_PREFIX}${widgetId}_start_time_shadow")
            remove("${KEY_PREFIX}${widgetId}_show_declined")
            remove("${KEY_PREFIX}${widgetId}_show_ampm")
            remove("${KEY_PREFIX}${widgetId}_calendars")
            apply()
        }
    }
}
