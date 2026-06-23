# ProGuard rules for Cal Date Widget

# Keep code shrinking + optimization (minifyEnabled true) but skip obfuscation.
# The source is public on GitHub, so name-mangling buys no IP protection while it
# makes on-device bug-report stack traces unreadable. Disabling it keeps traces
# legible in both Play Android vitals and BugReportActivity.
-dontobfuscate

# Preserve file names + line numbers so traces read as File.kt:NN even though R8
# can strip these attributes independently of obfuscation.
-keepattributes SourceFile,LineNumberTable

# Keep the widget providers
-keep class ai.dcar.caldatewidget.*Provider { *; }

# Keep the configuration activities
-keep class ai.dcar.caldatewidget.*Activity { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep the data classes if you use JSON serialization later
-keep class ai.dcar.caldatewidget.PrefsManager$WidgetSettings { *; }

# General AndroidX / Material keeps (usually handled by default, but safe to include)
-dontwarn com.google.android.material.**
-dontwarn androidx.**
