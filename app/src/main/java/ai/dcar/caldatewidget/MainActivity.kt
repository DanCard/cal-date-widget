package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ai.dcar.caldatewidget.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnAddDateWidget.setOnClickListener {
            pinWidget(DateWidgetProvider::class.java)
        }

        binding.btnAddWeeklyWidget.setOnClickListener {
            pinWidget(WeeklyWidgetProvider::class.java)
        }

        binding.btnAddDailyWidget.setOnClickListener {
            pinWidget(DailyWidgetProvider::class.java)
        }

        binding.btnReportBug.setOnClickListener {
            val intent = Intent(this, BugReportActivity::class.java)
            startActivity(intent)
        }

        binding.tvPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy_policy)))
            startActivity(intent)
        }
    }

    private fun pinWidget(providerClass: Class<*>) {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val myProvider = ComponentName(this, providerClass)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
        } else {
            Toast.makeText(this, getString(R.string.toast_pin_unsupported), Toast.LENGTH_LONG).show()
        }
    }
}
