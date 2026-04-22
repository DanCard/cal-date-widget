package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

abstract class BaseWidgetConfigActivity : AppCompatActivity() {

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    protected lateinit var prefsManager: PrefsManager
    protected lateinit var calendarRepository: CalendarRepository
    protected var currentSettings = PrefsManager.WidgetSettings()
    protected lateinit var initialSettings: PrefsManager.WidgetSettings
    protected var availableCalendars: List<CalendarInfo> = emptyList()
    protected val calendarCheckboxes = mutableMapOf<Long, CheckBox>()

    abstract fun getLayoutResId(): Int
    abstract fun updateWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int)
    protected open fun setupSpecificSettings() {}
    protected open fun undoSpecificSettings() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResId())

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        prefsManager = PrefsManager(this)
        calendarRepository = CalendarRepository(this)
        currentSettings = prefsManager.loadSettings(appWidgetId)
        initialSettings = currentSettings.copy()

        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val btnUndo = findViewById<Button>(R.id.btn_undo)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh)

        val previewText = findViewById<View>(R.id.preview_text_color)
        val btnPickText = findViewById<Button>(R.id.btn_pick_text_color)
        val previewShadow = findViewById<View>(R.id.preview_shadow_color)
        val btnPickShadow = findViewById<Button>(R.id.btn_pick_shadow_color)

        val previewStartColor = findViewById<View>(R.id.preview_start_time_color)
        val btnPickStartColor = findViewById<Button>(R.id.btn_pick_start_time_color)
        val previewStartShadow = findViewById<View>(R.id.preview_start_time_shadow)
        val btnPickStartShadow = findViewById<Button>(R.id.btn_pick_start_time_shadow)

        val cbShowDeclined = findViewById<CheckBox>(R.id.cb_show_declined)
        cbShowDeclined.isChecked = currentSettings.showDeclinedEvents
        cbShowDeclined.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(showDeclinedEvents = isChecked)
            saveSettings()
        }

        val cbShowAmPm = findViewById<CheckBox>(R.id.cb_show_ampm)
        cbShowAmPm.isChecked = currentSettings.showAmPm
        cbShowAmPm.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(showAmPm = isChecked)
            saveSettings()
        }

        populateCalendarCheckboxes()

        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)

        setupSpecificSettings()

        val settingsContainer = findViewById<View>(R.id.settings_container)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            btnGrant.visibility = View.VISIBLE
            settingsContainer.visibility = View.GONE
        } else {
            btnGrant.visibility = View.GONE
            settingsContainer.visibility = View.VISIBLE
        }

        btnGrant.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_CALENDAR), 101)
        }

        btnPickText.setOnClickListener {
            ColorPickerDialog.show(this, "Text Color", currentSettings.textColor) { color ->
                currentSettings = currentSettings.copy(textColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
                saveSettings()
            }
        }

        btnPickShadow.setOnClickListener {
            ColorPickerDialog.show(this, "Shadow Color", currentSettings.shadowColor) { color ->
                currentSettings = currentSettings.copy(shadowColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
                saveSettings()
            }
        }

        btnPickStartColor.setOnClickListener {
            ColorPickerDialog.show(this, "Start Time Color", currentSettings.startTimeColor) { color ->
                currentSettings = currentSettings.copy(startTimeColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
                saveSettings()
            }
        }

        btnPickStartShadow.setOnClickListener {
            ColorPickerDialog.show(this, "Start Time Shadow", currentSettings.startTimeShadowColor) { color ->
                currentSettings = currentSettings.copy(startTimeShadowColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
                saveSettings()
            }
        }

        btnUndo.setOnClickListener {
            undoSettings()
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnRefresh.setOnClickListener {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateWidget(appWidgetManager, appWidgetId)

            onRefreshClicked()
        }

        saveSettings()
    }

    protected open fun onRefreshClicked() {}

    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateWidget(appWidgetManager, appWidgetId)
        }
    }

    private fun updateColorPreviews(text: View, shadow: View, startColor: View, startShadow: View) {
        text.setBackgroundColor(currentSettings.textColor)
        shadow.setBackgroundColor(currentSettings.shadowColor)
        startColor.setBackgroundColor(currentSettings.startTimeColor)
        startShadow.setBackgroundColor(currentSettings.startTimeShadowColor)
    }

    protected fun saveSettings() {
        prefsManager.saveSettings(appWidgetId, currentSettings)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateWidget(appWidgetManager, appWidgetId)
        updateUndoButtonState()
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })
    }

    private fun undoSettings() {
        currentSettings = initialSettings.copy()
        prefsManager.saveSettings(appWidgetId, currentSettings)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateWidget(appWidgetManager, appWidgetId)

        val cbShowDeclined = findViewById<CheckBox>(R.id.cb_show_declined)
        val cbShowAmPm = findViewById<CheckBox>(R.id.cb_show_ampm)
        cbShowDeclined.isChecked = currentSettings.showDeclinedEvents
        cbShowAmPm.isChecked = currentSettings.showAmPm

        undoSpecificSettings()

        val previewText = findViewById<View>(R.id.preview_text_color)
        val previewShadow = findViewById<View>(R.id.preview_shadow_color)
        val previewStartColor = findViewById<View>(R.id.preview_start_time_color)
        val previewStartShadow = findViewById<View>(R.id.preview_start_time_shadow)
        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)

        updateCalendarCheckboxes()
        updateUndoButtonState()
    }

    private fun updateUndoButtonState() {
        val btnUndo = findViewById<Button>(R.id.btn_undo)
        btnUndo.isEnabled = currentSettings != initialSettings
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            findViewById<Button>(R.id.btn_grant_permission).visibility = View.GONE
            findViewById<View>(R.id.settings_container).visibility = View.VISIBLE
            populateCalendarCheckboxes()
        }
    }

    private fun populateCalendarCheckboxes() {
        val container = findViewById<LinearLayout>(R.id.calendar_checkboxes_container) ?: return
        container.removeAllViews()
        calendarCheckboxes.clear()

        try {
            availableCalendars = calendarRepository.getAvailableCalendars()
        } catch (e: Exception) {
            return
        }

        if (availableCalendars.isEmpty()) return

        val selectedIds = currentSettings.selectedCalendarIds

        for (cal in availableCalendars) {
            val checkBox = CheckBox(this).apply {
                val suffix = if (cal.isPersonalCalendar()) "" else " [shared]"
                text = "${cal.displayName}$suffix"
                setTextColor(Color.WHITE)

                isChecked = if (selectedIds.isEmpty()) {
                    cal.isPersonalCalendar()
                } else {
                    selectedIds.contains(cal.id)
                }

                if (!cal.isPersonalCalendar()) {
                    setTextColor(Color.LTGRAY)
                    setTypeface(null, Typeface.ITALIC)
                }

                setOnCheckedChangeListener { _, _ ->
                    saveCalendarSelection()
                }
            }

            calendarCheckboxes[cal.id] = checkBox
            container.addView(checkBox)
        }
    }

    private fun updateCalendarCheckboxes() {
        val selectedIds = currentSettings.selectedCalendarIds

        for ((calId, checkBox) in calendarCheckboxes) {
            val cal = availableCalendars.find { it.id == calId } ?: continue

            checkBox.setOnCheckedChangeListener(null)

            checkBox.isChecked = if (selectedIds.isEmpty()) {
                cal.isPersonalCalendar()
            } else {
                selectedIds.contains(calId)
            }

            checkBox.setOnCheckedChangeListener { _, _ ->
                saveCalendarSelection()
            }
        }
    }

    private fun saveCalendarSelection() {
        val selectedIds = calendarCheckboxes
            .filter { it.value.isChecked }
            .keys
            .toSet()

        currentSettings = currentSettings.copy(selectedCalendarIds = selectedIds)
        saveSettings()
    }
}
