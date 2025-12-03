package ai.dcar.caldatewidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ai.dcar.caldatewidget.databinding.ActivityConfigBinding
import ai.dcar.caldatewidget.databinding.WidgetDateBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Stack

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
                // We don't need to manually push here if we treat every text change as an edit? 
                // The requirement was "snapshot whenever you tap". 
                // We can manually push current state to stack without changing settings.
                // But SettingsStateManager.updateSettings does both.
                // We can expose a method to just push state? Or just rely on the "final" edit.
                // Let's keep it simple: The text watcher updates the state. 
                // To support "Undo whole typing session", we push to stack on Focus Gain.
                // But SettingsStateManager.updateSettings(push=true) pushes the OLD state.
                // So if we call updateSettings(..., push=false) during typing, we need to have pushed ONCE before typing.
                // We can manually push to stack:
                // stateManager.updateSettings(stateManager.currentSettings, pushToUndo=true) 
                // This pushes current state as "undoable", and keeps current as current.
                stateManager.updateSettings(stateManager.currentSettings, pushToUndo = true)
            }
        }

        binding.etCustomFormat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isRestoring) return
                val custom = s.toString()
                val newSettings = stateManager.currentSettings.copy(dateFormat = custom)
                // We don't push undo on every char, we pushed on focus.
                updateSettings(newSettings, pushUndo = false)
            }
        })

        // Undo Button
        binding.btnUndo.setOnClickListener {
            if (stateManager.undo()) {
                isRestoring = true
                updateUIFromSettings(stateManager.currentSettings)
                updatePreview()
                saveAndBroadcast(addToUndo = false) 
                isRestoring = false
                updateUndoButton()
            }
        }

        // Color Pickers
        binding.btnPickTextColor.setOnClickListener { showColorPicker("Text Color", stateManager.currentSettings.textColor) { color -> 
            val newSettings = stateManager.currentSettings.copy(textColor = color)
            updateSettings(newSettings)
        }}

        binding.btnPickBgColor.setOnClickListener { showColorPicker("Background Color", stateManager.currentSettings.bgColor) { color ->
            val alpha = Color.alpha(stateManager.currentSettings.bgColor)
            val newColorWithOldAlpha = (color and 0x00FFFFFF) or (alpha shl 24)
            val newSettings = stateManager.currentSettings.copy(bgColor = newColorWithOldAlpha)
            updateSettings(newSettings)
        }}

        binding.btnPickShadowColor.setOnClickListener { showColorPicker("Shadow Color", stateManager.currentSettings.shadowColor) { color ->
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
                // Push state before drag starts
                stateManager.updateSettings(stateManager.currentSettings, pushToUndo = true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveAndBroadcast(addToUndo = false)
                updateUndoButton()
            }
        })
    }
    
    private fun updateSettings(newSettings: PrefsManager.WidgetSettings, pushUndo: Boolean = true) {
        stateManager.updateSettings(newSettings, pushToUndo = pushUndo)
        saveAndBroadcast(addToUndo = false) // StateManager already handled the stack
    }

    private fun updateUndoButton() {
        binding.btnUndo.isEnabled = stateManager.canUndo()
    }

    private fun saveAndBroadcast(addToUndo: Boolean = false) {
        // addToUndo param is legacy here, handled by callers calling updateSettings
        
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

    private fun showColorPicker(title: String, initialColor: Int, onColorPicked: (Int) -> Unit) {
        // Simple RGB slider dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        val seekR = dialogView.findViewById<SeekBar>(R.id.seek_r)
        val seekG = dialogView.findViewById<SeekBar>(R.id.seek_g)
        val seekB = dialogView.findViewById<SeekBar>(R.id.seek_b)
        val preview = dialogView.findViewById<View>(R.id.cp_preview)

        seekR.max = 255; seekG.max = 255; seekB.max = 255
        seekR.progress = Color.red(initialColor)
        seekG.progress = Color.green(initialColor)
        seekB.progress = Color.blue(initialColor)
        preview.setBackgroundColor(initialColor or -0x1000000) // Force full alpha for preview

        val updatePreview = {
            val c = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            preview.setBackgroundColor(c)
        }
        
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
                onColorPicked(color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}