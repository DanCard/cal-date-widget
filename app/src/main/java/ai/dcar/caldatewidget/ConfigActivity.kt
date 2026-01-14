package ai.dcar.caldatewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import ai.dcar.caldatewidget.databinding.ActivityConfigBinding
import ai.dcar.caldatewidget.databinding.WidgetDateBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityConfigBinding
    private lateinit var previewBinding: WidgetDateBinding // Binding for the included layout
    private lateinit var prefsManager: PrefsManager
    private lateinit var stateManager: SettingsStateManager

    // Undo Stack
    // Managed by SettingsStateManager
    private var isRestoring = false // Flag to prevent loops during undo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup Result
        setResult(Activity.RESULT_CANCELED)

        // 2. Get Widget ID
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

        // 3. Inflate & Bind
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Manually bind the included layout
        val includedView = binding.root.findViewById<View>(R.id.preview_widget)
        previewBinding = WidgetDateBinding.bind(includedView)

        prefsManager = PrefsManager(this)
        
        // 4. Load Settings
        val initialSettings = prefsManager.loadSettings(appWidgetId)
        stateManager = SettingsStateManager(initialSettings)
        
        updateUIFromSettings(stateManager.currentSettings)
        updatePreview() // Initial render

        // 5. Listeners
        setupListeners()
        
        // Return OK result immediately so if they back out, it keeps the widget (since we auto-save)
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
    }

    private fun setupListeners() {
        // Date Format Spinner
        binding.spinnerDateFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isRestoring) return
                
                val selected = parent?.getItemAtPosition(position).toString()
                if (selected == "Custom") {
                    binding.etCustomFormat.visibility = View.VISIBLE
                    // Don't update settings yet, wait for text input
                } else {
                    binding.etCustomFormat.visibility = View.GONE
                    // pushToUndo handled by stateManager
                    val newSettings = stateManager.currentSettings.copy(dateFormat = selected)
                    updateSettings(newSettings, pushUndo = true)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Custom Format Text
        binding.etCustomFormat.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                stateManager.beginChangeSession()
            } else {
                stateManager.endChangeSession()
            }
        }

        binding.etCustomFormat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isRestoring) return
                val custom = s.toString()
                val newSettings = stateManager.currentSettings.copy(dateFormat = custom)
                stateManager.applySessionChange(newSettings)
                saveAndBroadcast()
            }
        })

        // Undo Button
        binding.btnUndo.setOnClickListener {
            if (stateManager.undo()) {
                isRestoring = true
                updateUIFromSettings(stateManager.currentSettings)
                updatePreview()
                saveAndBroadcast() 
                isRestoring = false
                updateUndoButton()
            }
        }

        // Color Pickers
        binding.btnPickTextColor.setOnClickListener { ColorPickerDialog.show(this, "Text Color", stateManager.currentSettings.textColor) { color ->
            val newSettings = stateManager.currentSettings.copy(textColor = color)
            updateSettings(newSettings)
        }}

        binding.btnPickBgColor.setOnClickListener { ColorPickerDialog.show(this, "Background Color", stateManager.currentSettings.bgColor) { color ->
            val alpha = Color.alpha(stateManager.currentSettings.bgColor)
            val newColorWithOldAlpha = (color and 0x00FFFFFF) or (alpha shl 24)
            val newSettings = stateManager.currentSettings.copy(bgColor = newColorWithOldAlpha)
            updateSettings(newSettings)
        }}

        binding.btnPickShadowColor.setOnClickListener { ColorPickerDialog.show(this, "Shadow Color", stateManager.currentSettings.shadowColor) { color ->
            val newSettings = stateManager.currentSettings.copy(shadowColor = color)
            updateSettings(newSettings)
        }}

        // Opacity Seekbar (controls BG Alpha)
        binding.seekbarBgOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                   val baseColor = stateManager.currentSettings.bgColor and 0x00FFFFFF
                   val newColor = baseColor or (progress shl 24)
                   val newSettings = stateManager.currentSettings.copy(bgColor = newColor)
                   // Update view but don't save/push yet
                   stateManager.updateSettings(newSettings, pushToUndo = false)
                   updatePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stateManager.beginChangeSession()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveAndBroadcast()
                stateManager.endChangeSession()
                updateUndoButton()
            }
        })
    }
    
    private fun updateSettings(newSettings: PrefsManager.WidgetSettings, pushUndo: Boolean = true) {
        stateManager.updateSettings(newSettings, pushToUndo = pushUndo)
        saveAndBroadcast()
    }

    private fun updateUndoButton() {
        binding.btnUndo.isEnabled = stateManager.canUndo()
    }

    private fun saveAndBroadcast() {
        
        prefsManager.saveSettings(appWidgetId, stateManager.currentSettings)
        
        // Update Widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        DateWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        
        // Also update preview here just in case
        updatePreview()
        updateUndoButton()
    }

    private fun updateUIFromSettings(settings: PrefsManager.WidgetSettings) {
        // Date Format
        val formats = resources.getStringArray(R.array.date_formats)
        val index = formats.indexOf(settings.dateFormat)
        if (index >= 0) {
            binding.spinnerDateFormat.setSelection(index)
            binding.etCustomFormat.visibility = View.GONE
        } else {
            binding.spinnerDateFormat.setSelection(formats.indexOf("Custom"))
            binding.etCustomFormat.visibility = View.VISIBLE
            binding.etCustomFormat.setText(settings.dateFormat)
        }

        // Colors Preview
        binding.previewTextColor.setBackgroundColor(settings.textColor)
        binding.previewBgColor.setBackgroundColor(settings.bgColor)
        binding.previewShadowColor.setBackgroundColor(settings.shadowColor)

        // Opacity
        binding.seekbarBgOpacity.progress = Color.alpha(settings.bgColor)
    }

    private fun updatePreview() {
        // Apply settings to previewBinding
        val settings = stateManager.currentSettings
        
        // Date Text
        val dateText = try {
            val sdf = SimpleDateFormat(settings.dateFormat, Locale.getDefault())
            sdf.format(Date())
        } catch (e: Exception) {
            "Invalid Format"
        }
        previewBinding.widgetDateText.text = dateText
        
        // Colors
        previewBinding.widgetDateText.setTextColor(settings.textColor)
        previewBinding.widgetRoot.setBackgroundColor(settings.bgColor)
        
        // Shadow
        previewBinding.widgetDateText.setShadowLayer(3f, 3f, 3f, settings.shadowColor)
        
        // Update small color previews in UI
        binding.previewTextColor.setBackgroundColor(settings.textColor)
        binding.previewBgColor.setBackgroundColor(settings.bgColor)
        binding.previewShadowColor.setBackgroundColor(settings.shadowColor)
    }
}
