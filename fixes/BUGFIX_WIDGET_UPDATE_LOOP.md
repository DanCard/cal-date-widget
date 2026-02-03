# Bugfix: Infinite Widget Update Loop

## Problem
Widgets (Daily and Weekly) were caught in an infinite redrawing loop, updating approximately once every second. This resulted in high CPU usage, battery drain, and visual flickering.

## Root Cause Analysis
The loop was caused by a feedback cycle between the app and the Android System:

1.  **System Trigger**: The launcher or system triggers `onUpdate` or `onAppWidgetOptionsChanged` in the `AppWidgetProvider`.
2.  **App Action**: The app calls `WorkManager.enqueue()` to handle the update logic (drawing the bitmap and calling `appWidgetManager.updateAppWidget`).
3.  **Side Effect**: Enqueueing a work request in `WorkManager` (especially on certain Android versions or emulators) can trigger internal state changes that result in a `PACKAGE_CHANGED` broadcast or other system events.
4.  **System Response**: The Android `AppWidgetServiceImpl` observes the package change and, as a safety/consistency measure, triggers `onUpdate` again for the provider.
5.  **Loop**: This cycle repeats indefinitely.

Logs showed `ShortcutService: changing package: ai.dcar.caldatewidget` followed immediately by `AppWidgetServiceImpl: Trying to notify widget update`.

## Solution: Immediate vs. Scheduled Updates
The fix involved separating **immediate** system-triggered updates from **scheduled** or **delayed** updates.

1.  **Centralized Logic**: Moved the widget drawing and `RemoteViews` update logic into `WidgetUpdateHelper.kt`.
2.  **Direct Execution for System Events**: In `onUpdate` and `onAppWidgetOptionsChanged`, the app now uses `goAsync()` combined with a `CoroutineScope(Dispatchers.IO)`. This allows the update to run on a background thread immediately without involving `WorkManager`.
    *   `goAsync()` informs the system that the broadcast is still active so the process isn't killed before the coroutine finishes.
3.  **Preserved WorkManager for Scheduling**: `WorkManager` is still used for cases where a delay is required (e.g., `WidgetClickReceiver` scheduling a refresh 90 seconds after opening the calendar) or when external triggers occur.

## Implementation Details
- **New File**: `app/src/main/java/ai/dcar/caldatewidget/WidgetUpdateHelper.kt`
- **Refactored**: `WeeklyWidgetProvider.kt`, `DailyWidgetProvider.kt`, `WidgetUpdateWorker.kt`

## Verification Results
- **Logcat**: Confirmed that `onUpdate` is only called when expected (e.g., on widget placement or resize) and does not loop.
- **Functionality**: Widgets correctly render their calendar bitmaps and respond to configuration changes.
- **Stability**: No regressions in existing unit tests.
