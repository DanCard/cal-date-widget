package ai.dcar.caldatewidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integration test for the crash-capture wiring across [CalApp] and [CrashStore].
 *
 * Robolectric instantiates the real manifest-registered [CalApp], so the default
 * uncaught-exception handler under test is the one CalApp.onCreate installed — not
 * a mock. Triggering it exercises the full path: handler -> persistCrash -> CrashStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalAppCrashHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        File(context.filesDir, CrashStore.CRASH_FILE_NAME).delete()
    }

    @Test
    fun `installed handler persists a readable crash that CrashStore can read back`() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("CalApp should install a default uncaught-exception handler", handler)

        try {
            handler!!.uncaughtException(Thread.currentThread(), RuntimeException("integration boom"))
        } catch (ignored: Throwable) {
            // The delegate handler's terminate behavior isn't under test; persistCrash
            // runs before delegation, so the file is already written by this point.
        }

        val persisted = CrashStore.read(context.filesDir)
        assertTrue(
            "crash trace should be persisted, got: $persisted",
            persisted.contains("integration boom")
        )
        // Sanity-check the report shape produced by CalApp.persistCrash.
        assertTrue("report should include a Thread line", persisted.contains("Thread:"))
        assertTrue("report should include a Device line", persisted.contains("Device:"))
    }
}
