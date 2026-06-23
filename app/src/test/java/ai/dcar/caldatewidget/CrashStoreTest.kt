package ai.dcar.caldatewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Direct tests for the crash read/write contract — plain JUnit over a temp dir,
 * no Robolectric or reflection. Because [CalApp] and [BugReportActivity] both go
 * through [CrashStore], covering it here covers both sides of that contract.
 */
class CrashStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `read returns placeholder when no crash file exists`() {
        assertEquals(CrashStore.NO_CRASH_MESSAGE, CrashStore.read(tempFolder.root))
    }

    @Test
    fun `write then read round-trips exact content including newlines`() {
        val report = "Time: 2026-06-23 05:00:00 UTC\n" +
            "Thread: main\n\n" +
            "java.lang.RuntimeException: boom\n" +
            "\tat ai.dcar.caldatewidget.WeeklyWidgetProvider.onUpdate(WeeklyWidgetProvider.kt:42)"
        CrashStore.write(tempFolder.root, report)
        assertEquals(report, CrashStore.read(tempFolder.root))
    }

    @Test
    fun `write overwrites the previous crash`() {
        CrashStore.write(tempFolder.root, "first crash")
        CrashStore.write(tempFolder.root, "second crash")
        assertEquals("second crash", CrashStore.read(tempFolder.root))
    }

    @Test
    fun `read returns error string when the entry is unreadable`() {
        // Make the crash path a directory so readText() throws — exercises the catch.
        File(tempFolder.root, CrashStore.CRASH_FILE_NAME).mkdir()
        val result = CrashStore.read(tempFolder.root)
        assertTrue("expected error message, got: $result", result.startsWith("Failed to read crash log:"))
    }
}
