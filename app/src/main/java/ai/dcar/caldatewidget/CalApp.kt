package ai.dcar.caldatewidget

import android.app.Application
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application subclass whose only job is bug visibility.
 *
 * It installs a global uncaught-exception handler that persists the most recent
 * crash via [CrashStore]. [BugReportActivity] reads it back so a user-submitted
 * report can include the actual crash even after the process was killed and
 * restarted (logcat is volatile and may no longer hold the trace by then).
 *
 * It deliberately delegates to the previously-installed handler afterward, so the
 * OS still terminates the process normally and Google Play Android vitals still
 * records the crash. Everything stays on-device — nothing here touches the network.
 */
class CalApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistCrash(thread, throwable)
            } catch (e: Throwable) {
                // Never let crash-logging mask the original crash.
                Log.e(TAG, "Failed to persist crash", e)
            }
            // Preserve normal crash behavior + Android vitals reporting.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun persistCrash(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val report = buildString {
            append("Time: ").append(timestamp).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append("App: ").append(appVersionLabel()).append('\n')
            append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE).append('\n')
            append('\n')
            append(Log.getStackTraceString(throwable))
        }
        CrashStore.write(filesDir, report)
    }

    private fun appVersionLabel(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    } catch (e: Exception) {
        "unknown"
    }

    companion object {
        private const val TAG = "CalApp"
    }
}
