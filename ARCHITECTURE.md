# Architecture & Development Guide

## Project Overview
Android calendar widget app that displays a weekly view of calendar events with customizable appearance and intelligent text sizing.

## Key Components

### WeeklyWidgetProvider.kt
Main widget provider that renders the weekly calendar view.

**Key Features:**
- Renders 7-day week view with events from device calendars
- Dynamic column sizing (past days smaller, today larger, future days medium)
- Intelligent start time display (hides when events would be clipped)
- Custom text scaling per day column
- Event filtering (declined events can be hidden)

**Rendering Flow:**
1. `onUpdate()` called when widget needs refresh
2. `drawWeeklyCalendar()` creates bitmap canvas
3. For each day column:
   - Calculate column width based on weights (past/today/future)
   - Filter events for that day
   - **Estimate if all events will fit** (5 lines per event heuristic)
   - Render events with/without start times based on estimation
   - **Ungraceful clipping:** Events render up to 10 lines, canvas clips overflow without ellipsis

**Start Time Logic (Lines 205-214):**
```kotlin
// Estimates available space and event count to decide if start times should be shown
val estimatedHeightPerEvent = estimatedLineHeight * 5.0f
val showStartTimes = (dayEvents.size * estimatedHeightPerEvent) <= availableHeight
```
- Uses conservative 5-line estimate per event to account for text wrapping
- Hides start times on days where events would be clipped
- Allows more event titles to fit in limited widget height

### CalendarRepository.kt
Handles fetching calendar events from Android's CalendarContract.

**Key Methods:**
- `getEventsForWeek(startMillis)`: Queries calendar instances for 7-day range
- `getVisibleCalendarIds()`: Gets only calendars marked visible
- Deduplicates events based on title, time, and declined status
- Returns `CalendarEvent` data objects

### WeeklyDisplayLogic.kt
Utility object for week display calculations.

**Key Methods:**
- `getStartDate()`: Calculates week start based on configured start day
- `shouldDisplayEventOnDay()`: Determines if event should show on a given day
  - Handles all-day events (UTC to local timezone conversion)
  - Handles regular timed events
- `getColumnWeights()`: Calculates relative widths for day columns
  - Past days: 0.6 weight
  - Today: 2.0 weight
  - Future days: 1.5 to 0.7 (decaying)

### PrefsManager.kt
Manages widget settings persistence using SharedPreferences.

**Settings Stored:**
- Text color, size scale, shadow color
- Start time color and shadow color
- Week start day preference
- Show/hide declined events flag

### CalendarEvent.kt
Data class for calendar event representation.

**Fields:**
- id, title, startTime, endTime
- color, isAllDay, isDeclined

## Recent Implementations

### Auto-Hide Start Times (Dec 2024)
**Problem:** When widget height is limited, events get clipped but start times still show, wasting space.

**Solution:** Before rendering events for each day, estimate if all events will fit:
- Calculate available vertical space
- Estimate space needed (event count × 5 lines per event)
- If won't fit, hide start times for ALL events on that day
- This allows more event titles to display

**Iterations:**
- Initial: 1.5 lines per event (too optimistic)
- Second: 3.5 lines per event (better but still clipped on narrow columns)
- Final: 5.0 lines per event (conservative enough for all column widths)

### Ungraceful Clipping for Last Event (Dec 2025)
**Problem:** Last event on current day was not showing completely due to:
- Hard break prevented events from rendering if `yPos > height - 20`
- Dynamic line calculation limited text based on remaining space
- Ellipsize truncated text with "..." reducing visible content

**Solution:** Allow events to render and clip naturally at canvas boundaries:
1. **Removed hard break** (line 218): Events now always attempt to render
2. **Fixed max lines to 10**: Changed from dynamic calculation to constant value
3. **Removed ellipsize**: No "..." truncation, text clips ungracefully at edge
4. **Canvas clipping handles overflow**: `clipRect()` does hard cut-off

**Result:**
- More text visible from last event, even if partially clipped
- Better use of available widget space
- Text cuts off mid-line at bottom (ungraceful but shows more content)

**Code Location:** `WeeklyWidgetProvider.kt` lines 217-270

## Important Implementation Notes

### Column Scaling
- Past days (< today): 0.6 width, 1.0 text scale
- Today: 2.0 width, settings.textSizeScale
- Future days: 0.7-1.5 width (decay), 1.10 text scale
- Completed events on today: 0.8 text scale (dimmed)

### Text Wrapping & Layout
- Uses `StaticLayout.Builder` for multi-line text rendering
- Fixed max lines (10) per event to allow more text visibility
- **Ungraceful clipping:** No ellipsis, canvas hard-clips text at widget boundary
- Text clipped to column width horizontally
- Line height: textSize × 1.2

### Event Filtering
- Only visible calendars included
- Declined events optionally filtered (user setting)
- Events deduplicated by (title, start, end, allDay, declined)
- Time-based filtering per day using `shouldDisplayEventOnDay()`

## Testing
- Unit tests in `app/src/test/`
- Build: `./gradlew assembleDebug`
- Install: `./gradlew installDebug`
- Run tests: `./gradlew test`

## Common Development Tasks

### Adjusting Start Time Threshold
Edit line 212 in `WeeklyWidgetProvider.kt`:
```kotlin
val estimatedHeightPerEvent = estimatedLineHeight * 5.0f  // Adjust multiplier
```

### Adjusting Event Text Clipping
Edit line 231 in `WeeklyWidgetProvider.kt`:
```kotlin
val dynamicMaxLines = 10  // Increase to show more text, decrease to clip sooner
```
Note: Higher values allow more text but may cause performance issues with StaticLayout

### Modifying Column Weights
Edit `getColumnWeights()` in `WeeklyDisplayLogic.kt`

### Adding New Settings
1. Add field to `WidgetSettings` data class
2. Update `PrefsManager.loadSettings()` and `saveSettings()`
3. Add UI control in `WeeklyConfigActivity.kt`

## File Structure
```
app/src/main/java/ai/dcar/caldatewidget/
├── WeeklyWidgetProvider.kt      # Main widget renderer
├── CalendarRepository.kt         # Calendar data access
├── CalendarEvent.kt             # Event data model
├── WeeklyDisplayLogic.kt        # Display calculations
├── PrefsManager.kt              # Settings persistence
└── WeeklyConfigActivity.kt      # Settings UI
```
