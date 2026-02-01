package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class WeeklyConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var prefsManager: PrefsManager
    private lateinit var calendarRepository: CalendarRepository
    private var currentSettings = PrefsManager.WidgetSettings()
    private lateinit var initialSettings: PrefsManager.WidgetSettings
    private var availableCalendars: List<CalendarInfo> = emptyList()
    private val calendarCheckboxes = mutableMapOf<Long, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_config)

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

        // UI Elements
        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val btnUndo = findViewById<Button>(R.id.btn_undo)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh)
        val spinner = findViewById<Spinner>(R.id.spinner_start_day)
        // val cbTitle = findViewById<CheckBox>(R.id.cb_show_title) // Not using title logic yet in provider but we can save it
        
        // Colors
        val previewText = findViewById<View>(R.id.preview_text_color)
        val btnPickText = findViewById<Button>(R.id.btn_pick_text_color)
        val previewShadow = findViewById<View>(R.id.preview_shadow_color)
        val btnPickShadow = findViewById<Button>(R.id.btn_pick_shadow_color)
        
        val previewStartColor = findViewById<View>(R.id.preview_start_time_color)
        val btnPickStartColor = findViewById<Button>(R.id.btn_pick_start_time_color)
        val previewStartShadow = findViewById<View>(R.id.preview_start_time_shadow)
        val btnPickStartShadow = findViewById<Button>(R.id.btn_pick_start_time_shadow)

        // Setup Spinner
        val days = arrayOf("Current Day", "Sunday", "Monday", "Saturday")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
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

        // Calendar selection - populate inline checkboxes
        populateCalendarCheckboxes()

        // Set initial state
        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
        
        // Map weekStart to spinner index
        val spinnerIndex = when(currentSettings.weekStartDay) {
            -1 -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.SATURDAY -> 3
            else -> 2 // Default Monday
        }
        spinner.setSelection(spinnerIndex)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val weekStart = when(position) {
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

        // Check Permission
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
            WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

            // Show feedback toast
            android.widget.Toast.makeText(this, "Widget refreshing...", android.widget.Toast.LENGTH_SHORT).show()
        }

        saveSettings()
    }
    
    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }

    private fun updateColorPreviews(text: View, shadow: View, startColor: View, startShadow: View) {
        text.setBackgroundColor(currentSettings.textColor)
        shadow.setBackgroundColor(currentSettings.shadowColor)
        startColor.setBackgroundColor(currentSettings.startTimeColor)
        startShadow.setBackgroundColor(currentSettings.startTimeShadowColor)
    }

    private fun saveSettings() {
        prefsManager.saveSettings(appWidgetId, currentSettings)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        updateUndoButtonState()
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })
    }

    private fun undoSettings() {
        currentSettings = initialSettings.copy()
        prefsManager.saveSettings(appWidgetId, currentSettings)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        val spinner = findViewById<Spinner>(R.id.spinner_start_day)
        val cbShowDeclined = findViewById<CheckBox>(R.id.cb_show_declined)
        val cbShowAmPm = findViewById<CheckBox>(R.id.cb_show_ampm)
        val spinnerIndex = when(currentSettings.weekStartDay) {
            -1 -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.SATURDAY -> 3
            else -> 2
        }
        spinner.setSelection(spinnerIndex)
        cbShowDeclined.isChecked = currentSettings.showDeclinedEvents
        cbShowAmPm.isChecked = currentSettings.showAmPm

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
            // Refresh calendar list after permission granted
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

        // Determine which calendars should be checked
        val selectedIds = currentSettings.selectedCalendarIds

        for (cal in availableCalendars) {
            val checkBox = CheckBox(this).apply {
                val suffix = if (cal.isPersonalCalendar()) "" else " [shared]"
                text = "${cal.displayName}$suffix"
                setTextColor(Color.WHITE)

                // If no prior selection, use smart default (personal calendars only)
                isChecked = if (selectedIds.isEmpty()) {
                    cal.isPersonalCalendar()
                } else {
                    selectedIds.contains(cal.id)
                }

                // Style: dimmed text for shared calendars
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

            // Temporarily remove listener to avoid triggering save
            checkBox.setOnCheckedChangeListener(null)

            checkBox.isChecked = if (selectedIds.isEmpty()) {
                cal.isPersonalCalendar()
            } else {
                selectedIds.contains(calId)
            }

            // Re-add listener
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

        // Store the selection (don't convert to empty set for "all" - keep explicit selection)
        currentSettings = currentSettings.copy(selectedCalendarIds = selectedIds)
        saveSettings()
    }
}
