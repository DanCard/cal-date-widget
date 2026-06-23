package ai.dcar.caldatewidget

import java.io.File

/**
 * Single owner of the on-device "last crash" file.
 *
 * [CalApp] writes to it from its global uncaught-exception handler; [BugReportActivity]
 * reads it when assembling a user-submitted report. Keeping both sides here means the
 * filename contract lives in exactly one place (it can't drift between writer and
 * reader), and the read/write logic is unit-testable with plain `File` I/O — no
 * Android framework, no Robolectric, no reflection. This mirrors the codebase's
 * pattern of pulling testable logic into standalone objects (e.g. WeeklyDisplayLogic).
 */
object CrashStore {
    const val CRASH_FILE_NAME = "last_crash.txt"
    const val NO_CRASH_MESSAGE = "No crash recorded."

    /** Persists [report] as the most recent crash, overwriting any previous one. */
    fun write(filesDir: File, report: String) {
        File(filesDir, CRASH_FILE_NAME).writeText(report)
    }

    /**
     * Returns the persisted crash text, [NO_CRASH_MESSAGE] when none has been
     * recorded, or a short error string if the file can't be read.
     */
    fun read(filesDir: File): String {
        return try {
            val file = File(filesDir, CRASH_FILE_NAME)
            if (file.exists()) file.readText() else NO_CRASH_MESSAGE
        } catch (e: Exception) {
            "Failed to read crash log: ${e.message}"
        }
    }
}
