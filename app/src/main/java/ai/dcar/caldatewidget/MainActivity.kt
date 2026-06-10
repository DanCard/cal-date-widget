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

        binding.tvPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dcar.ai/cal-date-widget/privacy")) // Update this URL later
            startActivity(intent)
        }
    }

    private fun pinWidget(providerClass: Class<*>) {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val myProvider = ComponentName(this, providerClass)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
        } else {
            Toast.makeText(this, "Pinned widgets are not supported on this launcher.", Toast.LENGTH_LONG).show()
        }
    }
}
