package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class WeeklyConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var prefsManager: PrefsManager
    private var currentSettings = PrefsManager.WidgetSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_config)

        setResult(Activity.RESULT_CANCELED)

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
        currentSettings = prefsManager.loadSettings(appWidgetId)
        
        // UI Elements
        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val btnSave = findViewById<Button>(R.id.btn_save)
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
        
        val seekSize = findViewById<SeekBar>(R.id.seekbar_text_size)

        // Setup Spinner
        val days = arrayOf("Current Day", "Sunday", "Monday", "Saturday")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        val cbShowDeclined = findViewById<CheckBox>(R.id.cb_show_declined)
        cbShowDeclined.isChecked = currentSettings.showDeclinedEvents
        
        // Set initial state
        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
        
        // Initial Progress: scale 1.0 -> progress 50 (if range is 0.5-2.5, 0->0.5, 100->1.5, 200->2.5)
        seekSize.progress = ((currentSettings.textSizeScale - 0.5f) * 100).toInt()
        
        // Map weekStart to spinner index
        val spinnerIndex = when(currentSettings.weekStartDay) {
            -1 -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.SATURDAY -> 3
            else -> 2 // Default Monday
        }
        spinner.setSelection(spinnerIndex)

        // Check Permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            btnGrant.visibility = View.VISIBLE
            btnSave.isEnabled = false
        } else {
            btnGrant.visibility = View.GONE
            btnSave.isEnabled = true
        }

        btnGrant.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_CALENDAR), 101)
        }
        
        btnPickText.setOnClickListener {
            ColorPickerDialog.show(this, "Text Color", currentSettings.textColor) { color ->
                currentSettings = currentSettings.copy(textColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
            }
        }
        
        btnPickShadow.setOnClickListener {
             ColorPickerDialog.show(this, "Shadow Color", currentSettings.shadowColor) { color ->
                currentSettings = currentSettings.copy(shadowColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
            }
        }

        btnPickStartColor.setOnClickListener {
             ColorPickerDialog.show(this, "Start Time Color", currentSettings.startTimeColor) { color ->
                currentSettings = currentSettings.copy(startTimeColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
            }
        }

        btnPickStartShadow.setOnClickListener {
             ColorPickerDialog.show(this, "Start Time Shadow", currentSettings.startTimeShadowColor) { color ->
                currentSettings = currentSettings.copy(startTimeShadowColor = color)
                updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)
            }
        }

        btnSave.setOnClickListener {
            // Map spinner to weekStart
            val selectedPos = spinner.selectedItemPosition
            val weekStart = when(selectedPos) {
                0 -> -1
                1 -> Calendar.SUNDAY
                2 -> Calendar.MONDAY
                3 -> Calendar.SATURDAY
                else -> Calendar.MONDAY
            }
            
            // Map seekbar to scale
            val scale = 0.5f + (seekSize.progress / 100f)
            
            currentSettings = currentSettings.copy(
                weekStartDay = weekStart, 
                textSizeScale = scale,
                showDeclinedEvents = cbShowDeclined.isChecked
            )
            prefsManager.saveSettings(appWidgetId, currentSettings)
            
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            
            // Trigger update
            val appWidgetManager = AppWidgetManager.getInstance(this)
            WeeklyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
            
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
    
    private fun updateColorPreviews(text: View, shadow: View, startColor: View, startShadow: View) {
        text.setBackgroundColor(currentSettings.textColor)
        shadow.setBackgroundColor(currentSettings.shadowColor)
        startColor.setBackgroundColor(currentSettings.startTimeColor)
        startShadow.setBackgroundColor(currentSettings.startTimeShadowColor)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            findViewById<Button>(R.id.btn_grant_permission).visibility = View.GONE
            findViewById<Button>(R.id.btn_save).isEnabled = true
        }
    }
}
