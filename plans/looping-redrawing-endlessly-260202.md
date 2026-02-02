# Widget Redrawing Loop Fix Plan

## Problem
Widgets continuously redraw/loop on emulators.

## Root Cause Analysis

### Primary Cause: Missing WorkManager Deduplication
`WeeklyWidgetProvider.updateAppWidget()` and `DailyWidgetProvider.updateAppWidget()` use `WorkManager.enqueue()` WITHOUT unique work naming:

```kotlin
// Current code (WeeklyWidgetProvider.kt:31-35)
val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
    .setInputData(inputData)
    .build()
WorkManager.getInstance(context).enqueue(workRequest)  // No deduplication!
```

Each call creates a NEW independent work request that queues up. If `onAppWidgetOptionsChanged` is called repeatedly (common on emulators), work requests accumulate rapidly.

### Secondary Cause: Potential `onAppWidgetOptionsChanged` Loop
The update flow could create a feedback loop:
1. `onAppWidgetOptionsChanged()` called by launcher
2. Calls `updateAppWidget()` → enqueues WorkManager
3. `WidgetUpdateWorker` sets new bitmap via `setImageViewBitmap()`
4. If bitmap dimensions differ, launcher **may** trigger `onAppWidgetOptionsChanged()` again
5. Loop continues

This is more likely on emulators due to launcher implementation differences.

---

## Solution: Two-Phase Approach

### Phase 1: Diagnostic Logging (Optional but Recommended)

Add logging to confirm root cause before applying fix.

**Files to modify:**
- `WeeklyWidgetProvider.kt` - log `onUpdate`, `onAppWidgetOptionsChanged`, `updateAppWidget`
- `DailyWidgetProvider.kt` - same
- `WidgetUpdateWorker.kt` - log `doWork()` entry and bitmap dimensions

**Logging code to add:**

```kotlin
// In WeeklyWidgetProvider.onAppWidgetOptionsChanged():
Log.d("WidgetLoop", "onAppWidgetOptionsChanged widget=$appWidgetId, ts=${System.currentTimeMillis()}")

// In WeeklyWidgetProvider.updateAppWidget():
Log.d("WidgetLoop", "updateAppWidget enqueueing widget=$appWidgetId, ts=${System.currentTimeMillis()}")

// In WidgetUpdateWorker.doWork():
Log.d("WidgetLoop", "doWork started widget=$appWidgetId, ts=${System.currentTimeMillis()}")
```

**To test:**
```bash
adb logcat -c && adb logcat -s WidgetLoop:D
# Look for rapid repeated logs (>1/second indicates loop)
```

---

### Phase 2: Implement Fix

#### Fix 1: Use `enqueueUniqueWork()` with REPLACE Policy (Critical)

This ensures only ONE pending update per widget at any time.

**WeeklyWidgetProvider.kt:**
```kotlin
import androidx.work.ExistingWorkPolicy

companion object {
    private const val WORK_NAME_PREFIX = "weekly_widget_update_"

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val inputData = Data.Builder()
            .putInt("appWidgetId", appWidgetId)
            .putString("type", "WEEKLY")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_NAME_PREFIX$appWidgetId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
```

**Apply same pattern to:**
- `DailyWidgetProvider.kt` (use `"daily_widget_update_"` prefix)
- `WidgetClickReceiver.kt` (use `"widget_click_refresh_${type}_"` prefix)

#### Fix 2: Add Debouncing for `onAppWidgetOptionsChanged` (Belt-and-suspenders)

Prevent processing rapid successive calls within 500ms.

```kotlin
class WeeklyWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val DEBOUNCE_MS = 500L
        private val lastOptionsChanged = mutableMapOf<Int, Long>()
        // ... existing code
    }

    override fun onAppWidgetOptionsChanged(...) {
        val now = System.currentTimeMillis()
        val last = lastOptionsChanged[appWidgetId] ?: 0L

        if (now - last < DEBOUNCE_MS) {
            Log.d("WidgetLoop", "Debounced widget $appWidgetId")
            return
        }
        lastOptionsChanged[appWidgetId] = now

        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
```

---

## Files to Modify

| File | Changes |
|------|---------|
| `WeeklyWidgetProvider.kt` | Add `enqueueUniqueWork`, debouncing, logging |
| `DailyWidgetProvider.kt` | Same as above |
| `WidgetClickReceiver.kt` | Add `enqueueUniqueWork` |
| `WidgetUpdateWorker.kt` | Add diagnostic logging (optional) |

---

## Verification Plan

1. **Deploy to emulator** with logging enabled
2. **Add widget** to home screen
3. **Check logcat** for rapid repeated calls
4. **After fix**, verify:
   - No looping (logs show single updates)
   - Resize still works (single update on resize)
   - Settings changes work (single update per change)
   - Click-to-calendar + delayed refresh work
5. **Test on real device** to verify no regression

---

## Implementation Order

1. Add diagnostic logging
2. Deploy and confirm root cause in logcat
3. Apply Fix 1 (`enqueueUniqueWork`) to all three files
4. Test on emulator - verify loop is fixed
5. Apply Fix 2 (debouncing) for additional safety
6. Test on real device for regression
