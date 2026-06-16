package ai.dcar.caldatewidget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.appwidget.AppWidgetManager
import ai.dcar.caldatewidget.databinding.ActivityBugReportBinding
import kotlin.concurrent.thread

class BugReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBugReportBinding
    private var diagnosticData: String = ""
    private var logsData: String = ""
    private var isLoadingData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBugReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadDiagnosticsAsync()
    }

    private fun setupUI() {
        // Expand/Collapse preview
        binding.layoutPreviewHeader.setOnClickListener {
            if (binding.layoutPreviewContent.visibility == View.GONE) {
                binding.layoutPreviewContent.visibility = View.VISIBLE
                binding.imgPreviewArrow.setImageResource(android.R.drawable.arrow_up_float)
                updatePreviewText()
            } else {
                binding.layoutPreviewContent.visibility = View.GONE
                binding.imgPreviewArrow.setImageResource(android.R.drawable.arrow_down_float)
            }
        }

        binding.switchSystemInfo.setOnCheckedChangeListener { _, _ ->
            updatePreviewText()
        }

        binding.switchAppLogs.setOnCheckedChangeListener { _, _ ->
            updatePreviewText()
        }

        binding.btnSubmitReport.setOnClickListener {
            sendEmailReport()
        }

        binding.btnCopyReport.setOnClickListener {
            copyToClipboard()
        }
    }

    private fun loadDiagnosticsAsync() {
        isLoadingData = true
        binding.progressPreview.visibility = View.VISIBLE
        binding.tvPreview.text = "Loading diagnostics..."

        thread {
            // 1. Gather System & Widget settings info
            val sysInfo = gatherSystemAndSettingsInfo()
            
            // 2. Gather logs (logcat)
            val logs = gatherAppLogs()

            runOnUiThread {
                diagnosticData = sysInfo
                logsData = logs
                isLoadingData = false
                binding.progressPreview.visibility = View.GONE
                updatePreviewText()
            }
        }
    }

    private fun updatePreviewText() {
        if (isLoadingData) {
            return
        }

        val builder = StringBuilder()
        if (binding.switchSystemInfo.isChecked) {
            builder.append("=== SYSTEM & DIAGNOSTICS ===\n")
            builder.append(diagnosticData)
            builder.append("\n\n")
        }
        if (binding.switchAppLogs.isChecked) {
            builder.append("=== RECENT APP LOGS ===\n")
            builder.append(logsData)
        }

        val text = builder.toString()
        binding.tvPreview.text = if (text.isEmpty()) "No diagnostics selected." else text
    }

    private fun gatherSystemAndSettingsInfo(): String {
        val builder = StringBuilder()

        // App details
        try {
            val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            builder.append("App Package: ").append(packageName).append("\n")
            builder.append("App Version: ").append(version).append(" ($code)\n")
        } catch (e: Exception) {
            builder.append("App Package: ").append(packageName).append(" (Error loading version info)\n")
        }

        // Device details
        builder.append("Android Release: ").append(Build.VERSION.RELEASE).append("\n")
        builder.append("Android SDK Level: ").append(Build.VERSION.SDK_INT).append("\n")
        builder.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        builder.append("Brand: ").append(Build.BRAND).append("\n")
        builder.append("Model: ").append(Build.MODEL).append("\n")
        builder.append("Device: ").append(Build.DEVICE).append("\n")
        builder.append("Timezone: ").append(java.util.TimeZone.getDefault().id).append("\n")
        
        // Active Widgets info
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val dateWidgets = appWidgetManager.getAppWidgetIds(ComponentName(this, DateWidgetProvider::class.java))
            val weeklyWidgets = appWidgetManager.getAppWidgetIds(ComponentName(this, WeeklyWidgetProvider::class.java))
            val dailyWidgets = appWidgetManager.getAppWidgetIds(ComponentName(this, DailyWidgetProvider::class.java))

            builder.append("\n--- Active Widgets ---\n")
            builder.append("Date Widgets (Count: ${dateWidgets.size}): ${dateWidgets.joinToString()}\n")
            builder.append("Weekly Widgets (Count: ${weeklyWidgets.size}): ${weeklyWidgets.joinToString()}\n")
            builder.append("Daily Widgets (Count: ${dailyWidgets.size}): ${dailyWidgets.joinToString()}\n")
        } catch (e: Exception) {
            builder.append("\nActive Widgets: Error reading widget counts (${e.message})\n")
        }

        // Shared Preferences dump
        try {
            val prefs = getSharedPreferences("ai.dcar.caldatewidget.prefs", Context.MODE_PRIVATE)
            val allEntries = prefs.all
            builder.append("\n--- Widget Configurations ---\n")
            if (allEntries.isEmpty()) {
                builder.append("No saved widget settings found.\n")
            } else {
                for ((key, value) in allEntries) {
                    if (key.endsWith("_color") || key.endsWith("_shadow") || key.contains("_color") || key.contains("_shadow")) {
                        val colorVal = value as? Int
                        if (colorVal != null) {
                            builder.append("$key: #${Integer.toHexString(colorVal).uppercase()}\n")
                            continue
                        }
                    }
                    builder.append("$key: $value\n")
                }
            }
        } catch (e: Exception) {
            builder.append("SharedPreferences dump error: ${e.message}\n")
        }

        // Calendars list
        try {
            val repo = CalendarRepository(this)
            val calendars = repo.getAvailableCalendars()
            builder.append("\n--- Available Calendars ---\n")
            if (calendars.isEmpty()) {
                builder.append("No calendars found or permission missing.\n")
            } else {
                for (cal in calendars) {
                    val isPersonal = cal.isPersonalCalendar()
                    builder.append("ID: ${cal.id} | Name: ${cal.displayName} | Personal: $isPersonal\n")
                }
            }
        } catch (e: Exception) {
            builder.append("Calendars reading error: ${e.message}\n")
        }

        return builder.toString()
    }

    private fun gatherAppLogs(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "200"))
            val reader = process.inputStream.bufferedReader()
            val builder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line).append("\n")
            }
            if (builder.isEmpty()) {
                "No logs found in logcat buffer."
            } else {
                builder.toString()
            }
        } catch (e: Exception) {
            "Failed to retrieve logcat: ${e.message}"
        }
    }

    private fun generateReportBody(): String {
        val userDescription = binding.etDescription.text.toString().trim()
        val builder = StringBuilder()

        builder.append("[Tip: If you're experiencing a visual/layout bug, please attach a screenshot to this email!]\n\n")
        builder.append("=== USER DESCRIPTION ===\n")
        if (userDescription.isEmpty()) {
            builder.append("(No description provided)\n")
        } else {
            builder.append(userDescription).append("\n")
        }
        builder.append("\n")

        if (binding.switchSystemInfo.isChecked) {
            builder.append("=== SYSTEM & DIAGNOSTICS ===\n")
            builder.append(diagnosticData)
            builder.append("\n")
        }

        if (binding.switchAppLogs.isChecked) {
            builder.append("=== RECENT APP LOGS ===\n")
            builder.append(logsData)
            builder.append("\n")
        }

        return builder.toString()
    }

    private fun sendEmailReport() {
        val reportBody = generateReportBody()
        val recipientEmail = getString(R.string.bug_report_email)
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Cal Date Widget bug report")
            putExtra(Intent.EXTRA_TEXT, reportBody)
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Report via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open email app. Diagnostic data copied to clipboard.", Toast.LENGTH_LONG).show()
            copyToClipboard(silent = true)
        }
    }

    private fun copyToClipboard(silent: Boolean = false) {
        val reportBody = generateReportBody()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Cal Date Widget Bug Report", reportBody)
        clipboard.setPrimaryClip(clip)

        if (!silent) {
            Toast.makeText(this, "Diagnostics copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}
