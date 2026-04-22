package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import java.util.Calendar

class WeeklyConfigActivity : BaseWidgetConfigActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_weekly_config

    override fun updateWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
    }

    override fun setupSpecificSettings() {
        val spinner = findViewById<Spinner>(R.id.spinner_start_day)

        val days = arrayOf("Current Day", "Sunday", "Monday", "Saturday")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val spinnerIndex = when (currentSettings.weekStartDay) {
            -1 -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.SATURDAY -> 3
            else -> 2
        }
        spinner.setSelection(spinnerIndex)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val weekStart = when (position) {
                    0 -> -1
                    1 -> Calendar.SUNDAY
                    2 -> Calendar.MONDAY
                    3 -> Calendar.SATURDAY
                    else -> Calendar.MONDAY
                }
                currentSettings = currentSettings.copy(weekStartDay = weekStart)
                saveSettings()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    override fun undoSpecificSettings() {
        val spinner = findViewById<Spinner>(R.id.spinner_start_day)
        val spinnerIndex = when (currentSettings.weekStartDay) {
            -1 -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.SATURDAY -> 3
            else -> 2
        }
        spinner.setSelection(spinnerIndex)
    }

    override fun onRefreshClicked() {
        android.widget.Toast.makeText(this, "Widget refreshing...", android.widget.Toast.LENGTH_SHORT).show()
    }
}
