package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class DailyConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var prefsManager: PrefsManager
    private var currentSettings = PrefsManager.WidgetSettings()
    private lateinit var initialSettings: PrefsManager.WidgetSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_config)

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
        initialSettings = currentSettings.copy()

        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val btnUndo = findViewById<Button>(R.id.btn_undo)
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

        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            btnGrant.visibility = View.VISIBLE
        } else {
            btnGrant.visibility = View.GONE
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

        btnRefresh.setOnClickListener {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }

        saveSettings()
    }

    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
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
        DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        updateUndoButtonState()
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })
    }

    private fun undoSettings() {
        currentSettings = initialSettings.copy()
        prefsManager.saveSettings(appWidgetId, currentSettings)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        DailyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        val cbShowDeclined = findViewById<CheckBox>(R.id.cb_show_declined)
        val cbShowAmPm = findViewById<CheckBox>(R.id.cb_show_ampm)
        cbShowDeclined.isChecked = currentSettings.showDeclinedEvents
        cbShowAmPm.isChecked = currentSettings.showAmPm

        val previewText = findViewById<View>(R.id.preview_text_color)
        val previewShadow = findViewById<View>(R.id.preview_shadow_color)
        val previewStartColor = findViewById<View>(R.id.preview_start_time_color)
        val previewStartShadow = findViewById<View>(R.id.preview_start_time_shadow)
        updateColorPreviews(previewText, previewShadow, previewStartColor, previewStartShadow)

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
        }
    }
}
