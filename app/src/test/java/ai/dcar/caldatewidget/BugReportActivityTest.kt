package ai.dcar.caldatewidget

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.work.testing.WorkManagerTestInitHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BugReportActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `activity initializes correctly`() {
        val intent = Intent(context, BugReportActivity::class.java)
        ActivityScenario.launch<BugReportActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                
                val etDesc = activity.findViewById<EditText>(R.id.et_description)
                val switchSys = activity.findViewById<SwitchMaterial>(R.id.switch_system_info)
                val switchLogs = activity.findViewById<SwitchMaterial>(R.id.switch_app_logs)
                val btnSubmit = activity.findViewById<Button>(R.id.btn_submit_report)
                val btnCopy = activity.findViewById<Button>(R.id.btn_copy_report)

                assertNotNull(etDesc)
                assertNotNull(switchSys)
                assertNotNull(switchLogs)
                assertNotNull(btnSubmit)
                assertNotNull(btnCopy)

                assertTrue(switchSys.isChecked)
                assertTrue(switchLogs.isChecked)
            }
        }
    }
}
