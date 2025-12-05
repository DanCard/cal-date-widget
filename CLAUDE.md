# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cal Date Widget is an Android calendar widget app that displays both a single-date widget and a weekly calendar view. The weekly view renders events from device calendars with intelligent text sizing, adaptive start time display, and equitable event clipping.

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
# Check connected devices
~/Library/Android/sdk/platform-tools/adb devices

# Force widget update
~/Library/Android/sdk/platform-tools/adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n ai.dcar.caldatewidget/.WeeklyWidgetProvider

# Capture widget screenshot
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/screenshot.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/screenshot.png /tmp/widget_screenshot.png

# Clear logcat and watch widget logs
~/Library/Android/sdk/platform-tools/adb logcat -c
~/Library/Android/sdk/platform-tools/adb logcat -s WeeklyWidget:D

# Restart launcher (to refresh widget)
~/Library/Android/sdk/platform-tools/adb shell "pkill -f com.sec.android.app.launcher && sleep 2"
```

## Architecture

### Weekly Widget Rendering (WeeklyWidgetProvider.kt)

The weekly widget renders a 7-day calendar view as a single bitmap on Canvas:

**Column Sizing:**
- **Today's column:** 2.0x width weight, larger text scale (default 1.5x from settings)
- **Past days:** 0.6x width, 1.0x text scale, dimmed if event ended
- **Future days:** 1.5x to 0.7x width (declining), 1.10x text scale

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

### Settings Management (PrefsManager.kt)

Persists widget settings in SharedPreferences:
- Text color, size scale, shadow color
- Start time color and shadow color
- Week start day preference (Sunday/Monday/etc)
- Show/hide declined events flag

### Widget Types

This project contains TWO distinct widgets:

1. **DateWidgetProvider.kt** - Single date widget with auto-resizing text
2. **WeeklyWidgetProvider.kt** - Weekly calendar widget (primary focus)

Each has its own provider, configuration activity, and settings.

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
├── WeeklyWidgetProvider.kt      # Weekly widget renderer (primary)
├── DateWidgetProvider.kt         # Single date widget
├── CalendarRepository.kt         # Calendar data access
├── CalendarEvent.kt              # Event data model
├── WeeklyDisplayLogic.kt         # Display calculations (testable)
├── PrefsManager.kt               # Settings persistence
├── WeeklyConfigActivity.kt       # Weekly widget settings UI
├── ConfigActivity.kt             # Date widget settings UI
└── SettingsStateManager.kt       # Undo stack management

app/src/test/java/ai/dcar/caldatewidget/
├── WeeklyDisplayLogicTest.kt     # Display logic tests
├── PrefsManagerTest.kt           # Settings persistence tests
├── SettingsStateManagerTest.kt   # Undo functionality tests
├── CalendarRepositoryTest.kt     # Calendar data tests
└── EventFilterTest.kt            # Event filtering tests
```

## Target Device

Primary development device: **SM-F936U1** (Samsung Galaxy Z Fold4)

ADB path: `~/Library/Android/sdk/platform-tools/adb`
