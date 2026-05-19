package ai.dcar.caldatewidget

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.text.format.DateFormat
import android.widget.Button
import java.text.DateFormat as JavaDateFormat
import java.util.Calendar
import java.util.Locale

class DailyConfigActivity : BaseWidgetConfigActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_daily_config

    override fun setupSpecificSettings() {
        val btnAutoAdvanceCutoff = findViewById<Button>(R.id.btn_auto_advance_cutoff)
        updateAutoAdvanceCutoffButton(btnAutoAdvanceCutoff)

        btnAutoAdvanceCutoff.setOnClickListener {
            val cutoff = currentSettings.dailyAutoAdvanceCutoffMinuteOfDay
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    currentSettings = currentSettings.copy(
                        dailyAutoAdvanceCutoffMinuteOfDay = (hourOfDay * 60) + minute
                    )
                    updateAutoAdvanceCutoffButton(btnAutoAdvanceCutoff)
                    saveSettings()
                },
                cutoff / 60,
                cutoff % 60,
                DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    override fun undoSpecificSettings() {
        updateAutoAdvanceCutoffButton(findViewById(R.id.btn_auto_advance_cutoff))
    }

    override fun updateWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
    }

    private fun updateAutoAdvanceCutoffButton(button: Button) {
        button.text = formatAutoAdvanceCutoff(currentSettings.dailyAutoAdvanceCutoffMinuteOfDay)
    }

    private fun formatAutoAdvanceCutoff(cutoffMinuteOfDay: Int): String {
        val cutoff = cutoffMinuteOfDay.coerceIn(0, (24 * 60) - 1)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, cutoff / 60)
            set(Calendar.MINUTE, cutoff % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT, Locale.getDefault())
            .format(calendar.time)
    }
}
