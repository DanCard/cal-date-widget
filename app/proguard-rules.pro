# ProGuard rules for Cal Date Widget

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
