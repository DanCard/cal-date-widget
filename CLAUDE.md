# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cal Date Widget is an Android calendar widget app that ships **three** widgets: a single-date widget, a daily (today-only) calendar view, and a weekly calendar view. The weekly view renders events from device calendars with intelligent text sizing, adaptive start time display, equitable event clipping, and automatically shifts past-day columns of the current week to show the same weekday of *next* week so every column displays forward-looking events.

## Essential Commands

### Building & Installation
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug            # Install on connected device
./gradlew testDebugUnitTest       # Run unit tests
./gradlew :app:testReleaseUnitTest # Run release unit tests
```

### Testing on Device
```bash
# Set ADB path based on platform (pick whichever exists on your machine):
#   macOS: ~/Library/Android/sdk/platform-tools/adb
#   Linux: ~/.Android/Sdk/platform-tools/adb
ADB=~/.Android/Sdk/platform-tools/adb

# Check connected devices
$ADB devices

# Force widget update (may be blocked by platform security on Android 14+
# when issued from shell; prefer tapping the widget or waiting for the
# 30-min updatePeriodMillis cycle as a fallback)
$ADB shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n ai.dcar.caldatewidget/.WeeklyWidgetProvider

# Capture widget screenshot
$ADB shell screencap -p /sdcard/screenshot.png
$ADB pull /sdcard/screenshot.png /tmp/widget_screenshot.png

# Clear logcat and watch widget logs
$ADB logcat -c
$ADB logcat -s WeeklyWidget:D WidgetDrawer:D

# Restart Samsung launcher to force redraw (Fold4 / Samsung-only)
$ADB shell "pkill -f com.sec.android.app.launcher && sleep 2"
```

## Architecture

### Weekly Widget Rendering (WeeklyWidgetProvider.kt)

The weekly widget renders a 7-day calendar view as a single bitmap on Canvas:

**Column Sizing:**
- **Today's column:** 2.0x width weight, larger text scale (default 1.5x from settings)
- **"Past" day columns (i < todayIndex):** 0.6x width. These columns now show the *same weekday of next week* (not the stale past day), so the narrow width acts as visual de-prioritization for the further-out info rather than signaling "ended."
- **Future days:** 1.5x to 0.7x width (declining), 1.10x text scale

**Past-Day Shift (Per-Column Next-Week Render):**
- `WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndex)` returns `startMillis + (i+7)*day` when `i < todayIndex`, else `startMillis + i*day`.
- Fetch window is `repo.getEvents(todayMillis, 7, …)` — i.e., 7 days from today, which exactly covers every column's effective date regardless of `todayIndex`.
- All column headers use the `"EEE d"` format ("Mon 29") to disambiguate next-week columns from this-week columns.
- Past-event dimming (gated on `event.endTime < now`) naturally no-ops for shifted columns since their events are in the future.

**Event Rendering Flow:**
1. Calculate available vertical space per day column
2. Estimate if events will fit using conservative heuristic
3. Decide whether to show start times based on estimate
4. Render events with StaticLayout (multi-line text)
5. Apply clipping at column and canvas boundaries

**Start Time Display Logic (WeeklyDisplayLogic.shouldShowStartTimes):**
- Conservative estimate: 4 lines per event (accounts for start time text + wrapping)
- Formula: `(eventCount * lineHeight * 4.0f) <= availableHeight`
- Returns true only when ALL events will fit WITH start times
- When hidden, saves space to fit more event titles

**Event Clipping & Max Lines (WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent):**
- **No clipping detected:** 10 lines max per event (generous display)
- **Clipping on current day:**
  - Past events (already ended): 1 line max (minimize space)
  - Current/future events: Equitable distribution of remaining space
  - Formula: `(availableHeight - pastEventsHeight) / (futureCount * lineHeight)`
- **Clipping on other days:** Equitable distribution across all events

**Critical Implementation Detail:**
- `StaticLayout.setEllipsize(TextUtils.TruncateAt.END)` is REQUIRED for maxLines to work
- Without ellipsize, maxLines is ignored and text renders beyond limit
- See WeeklyWidgetProvider.kt:327

### Calendar Data (CalendarRepository.kt)

**Event Fetching:**
- Queries `CalendarContract.Instances` for 7-day range
- Only includes visible calendars (`CalendarContract.Calendars.VISIBLE == 1`)
- Deduplicates events by (title, start time, end time, all-day status)
- See lines 94-96 for deduplication logic

**Event Filtering:**
- Declined events optionally filtered based on user setting
- Time-based filtering per day using `WeeklyDisplayLogic.shouldDisplayEventOnDay()`

### Display Logic (WeeklyDisplayLogic.kt)

Utility object containing testable functions for week display calculations:

**Key Methods:**
- `shouldShowStartTimes()`: Determines if start times should be shown based on available space
- `getMaxLinesForCurrentDayEvent()`: Calculates max lines for events on current day with clipping
- `shouldDisplayEventOnDay()`: Handles timezone conversion for all-day events
- `getColumnWeights()`: Returns width weights for past/today/future columns
- `getStartDate()`: Calculates week start based on configured start day
- `getEffectiveDayMillis()`: Returns the displayed day-millis for a column, shifting past-day columns forward by 7 days so they show next week's same weekday
- `filterNearDuplicates()`: Drops near-identical events (same-type, start within 15 min, ≥5 common title words or >60% word overlap) — rotates selection by minute to surface different duplicates over time

### Settings Management (PrefsManager.kt)

Persists widget settings in SharedPreferences:
- Text color, size scale, shadow color
- Start time color and shadow color
- Week start day preference (Sunday/Monday/etc)
- Show/hide declined events flag

### Widget Types

This project contains THREE distinct widgets:

1. **DateWidgetProvider.kt** — Single date widget with auto-resizing text.
   Config: `ConfigActivity.kt`. Info XML: `date_widget_info.xml`.
   Uses synchronous `RemoteViews` updates (no async IO needed — just formats a `Date`).

2. **DailyWidgetProvider.kt** — Single-day calendar view (today's events).
   Config: `DailyConfigActivity.kt`. Info XML: `daily_widget_info.xml`. Target cells: 2×2.
   Renders as a bitmap on `R.id.daily_canvas`. Auto-advances to next day when all non-declined
   events have ended (see `DailyDisplayLogic.shouldAutoAdvance()`).

3. **WeeklyWidgetProvider.kt** — 7-day calendar grid (primary focus).
   Config: `WeeklyConfigActivity.kt`. Info XML: `weekly_widget_info.xml`. Target cells: 4×2.
   Renders as a bitmap on `R.id.weekly_canvas`.

Each widget has its own `ConfigActivity` (wired via `android:configure`) and independent
`PrefsManager` settings keyed by `appWidgetId`.

**Shared rendering infrastructure (Daily + Weekly):**
- `WidgetDrawer.kt` — `drawDailyCalendar()` / `drawWeeklyCalendar()` bitmap rendering.
- `WidgetUpdateHelper.kt` — `updateDailyWidget()` / `updateWeeklyWidget()` build `RemoteViews`,
  set the drawn bitmap, and wire click intents.
- `WidgetUpdateWorker.kt` — `WorkManager` worker dispatching async updates.
- `WidgetClickReceiver.kt` — handles widget taps (opens calendar, schedules delayed refresh).
- `WidgetRenderingHelper.kt` — shared paint/font-scale utilities used by both drawers.

Both Daily and Weekly providers use `goAsync()` + a `Dispatchers.IO` coroutine in `onUpdate()`
to offload calendar IO off the main thread.

## Testing Philosophy

**Test Coverage:**
- Unit tests for display logic (WeeklyDisplayLogicTest.kt)
- Preferences and state management tests
- Event filtering and deduplication tests
- Realistic device scenario tests with actual metrics

**Key Test Areas:**
- `shouldShowStartTimes()`: 11 tests covering clipping detection, boundary conditions
- `getMaxLinesForCurrentDayEvent()`: 5 tests including realistic device scenarios
- Tests use actual device metrics (e.g., 761px height, 86.4px line height)

## Recent Critical Changes

### Accurate Clipping Detection & Start Time Display (Dec 2024)

**Problem:** Start times not showing on days with space; clipping detection inaccurate.

**Solution:** Implemented `WeeklyDisplayLogic.shouldShowStartTimes()` with 4-line estimate per event.

**Key Behavior:**
- Days with sparse events (Sat/Sun): Start times shown, events have room
- Days with many events: Start times hidden, past events limited to 1 line
- Clipping accurately detected before rendering

### Equitable Event Clipping (Dec 2024)

**Problem:** Events clipped inequitably - one event taking 4 rows, last event only 1.75 rows.

**Solution:** Implemented intelligent max lines distribution in `getMaxLinesForCurrentDayEvent()`.

**Key Behavior:**
- Current day with clipping: Past events get 1 line, future events share remaining space equitably
- Other days: All events share space equitably
- No clipping: All events can use up to 10 lines

**Critical Bug Fix:** Added `.setEllipsize(TextUtils.TruncateAt.END)` to make maxLines work correctly.

### Past-Day Columns Show Next Week (Apr 2026)

**Problem:** Columns for days earlier than today in the current week displayed already-happened events — wasted real estate, zero actionability.

**Solution:** `WeeklyDisplayLogic.getEffectiveDayMillis()` shifts past-day columns forward by 7 days. Fetch window changed from `startMillis` to `todayMillis` (still 7 days, since all displayed days fall within `[today, today+6]`). Header format unified to `"EEE d"` across all columns so next-week dates are unambiguous.

**Key Behavior:**
- Past columns keep 0.6× width (visual de-prioritization, not "staleness" signal).
- Past-event dimming logic auto-disables for shifted columns (gated on `event.endTime < now`).
- When today is the week-start day, no shifting happens (behavior identical to pre-change).

## Development Notes

### Adjusting Display Behavior

**Start Time Threshold:**
Edit `WeeklyDisplayLogic.shouldShowStartTimes()`:
```kotlin
val estimatedHeightPerEvent = lineHeight * 4.0f  // Adjust multiplier
```

**Event Max Lines:**
Edit `WeeklyDisplayLogic.getMaxLinesForCurrentDayEvent()`:
```kotlin
if (!isClipping) {
    return 10  // Adjust this value
}
```

**Column Weights:**
Edit `WeeklyDisplayLogic.getColumnWeights()` to change past/today/future width ratios.

### Adding New Settings

1. Add field to `WidgetSettings` data class in PrefsManager.kt
2. Update `PrefsManager.loadSettings()` and `saveSettings()`
3. Add UI control in `WeeklyConfigActivity.kt`

### Debug Logging

Weekly widget includes debug logging at lines 229-267, 332-334:
- Tracks clipping detection, event counts, max lines per event
- Logs actual vs requested line counts
- View logs: `adb logcat -s WeeklyWidget:D`

## Build Configuration

- **Namespace:** `ai.dcar.caldatewidget`
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35
- **JVM Target:** Java 21
- **Test Framework:** JUnit 4, Robolectric, MockK

## File Structure

```
app/src/main/java/ai/dcar/caldatewidget/
├── WeeklyWidgetProvider.kt      # Weekly widget provider (primary)
├── DailyWidgetProvider.kt       # Daily (today-only) widget provider
├── DateWidgetProvider.kt        # Single date widget
├── CalendarRepository.kt        # Calendar data access
├── CalendarEvent.kt             # Event data model
├── WeeklyDisplayLogic.kt        # Weekly display calculations (testable)
├── DailyDisplayLogic.kt         # Daily display calculations (auto-advance, weights)
├── WidgetDrawer.kt              # Shared bitmap renderer (Daily + Weekly)
├── WidgetRenderingHelper.kt     # Shared paint/font-scale utilities
├── WidgetUpdateHelper.kt        # Shared RemoteViews + click-intent wiring
├── WidgetUpdateWorker.kt        # WorkManager worker for async updates
├── WidgetClickReceiver.kt       # Widget tap handler (opens calendar + delayed refresh)
├── PrefsManager.kt              # Settings persistence
├── WeeklyConfigActivity.kt      # Weekly widget settings UI
├── DailyConfigActivity.kt       # Daily widget settings UI
├── ConfigActivity.kt            # Date widget settings UI
├── ColorPickerDialog.kt         # Shared color picker
└── SettingsStateManager.kt      # Undo stack management

app/src/test/java/ai/dcar/caldatewidget/
├── WeeklyDisplayLogicTest.kt             # Weekly display logic (includes getEffectiveDayMillis)
├── WeeklyWidgetProviderTest.kt           # Weekly provider integration
├── WeeklyWidgetResizingTest.kt           # Column-sizing behavior
├── WeeklyConfigActivityTest.kt           # Config UI
├── WidgetDrawerTest.kt                   # Drawer-level rendering checks
├── WidgetDrawerTomorrowIndicatorRobolectricTest.kt  # Robolectric UI test
├── WidgetRenderingHelperTest.kt          # Shared render helper
├── ConfigActivityTest.kt                 # Date widget config
├── DateFormatTest.kt                     # Date formatting
├── AutoAdvanceTest.kt                    # Daily auto-advance logic
├── TextOverlapTest.kt                    # Text-layout regression guard
├── PrefsManagerTest.kt                   # Settings persistence
├── SettingsStateManagerTest.kt           # Undo functionality
├── CalendarRepositoryTest.kt             # Calendar data
└── EventFilterTest.kt                    # Event filtering
```

Note: `DailyDisplayLogic` is currently not covered by a dedicated unit-test file — `AutoAdvanceTest.kt` exercises its `shouldAutoAdvance()` path but `getColumnWeights()` is not unit-tested.

## Target Device

Primary development device: **SM-F936U1** (Samsung Galaxy Z Fold4)

ADB path:
- macOS: `~/Library/Android/sdk/platform-tools/adb`
- Linux: `~/.Android/Sdk/platform-tools/adb`
