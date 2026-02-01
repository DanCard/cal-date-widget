# Near-Duplicate Event Detection Plan

## Overview
Add near-duplicate detection to hide similar calendar events (e.g., two slightly different events at 11 am). Uses simple heuristics with deterministic day-based rotation to choose which duplicate to display.

## Near-Duplicate Criteria

Events are considered near-duplicates if ALL of the following match:

1. **Same event type**: Both all-day OR both timed events
2. **Time proximity**: Start times within 15 minutes (900,000 ms)
3. **Title similarity**: Case-insensitive match OR one title contains the other
   - Handles: "Meeting" vs "MEETING", "Lunch" vs "Lunch - Updated"

## Implementation Strategy

### Location
Add new function in `WeeklyDisplayLogic.kt` (pure Kotlin, easily testable):
```kotlin
fun filterNearDuplicates(events: List<CalendarEvent>, dayOfYear: Int): List<CalendarEvent>
```

Call from `WidgetDrawer.kt` after declined event filtering:
```kotlin
// Line 64-66 (current declined filter)
if (!settings.showDeclinedEvents) {
    events = events.filter { !it.isDeclined }
}

// NEW: Near-duplicate filtering (insert after line 66)
events = WeeklyDisplayLogic.filterNearDuplicates(events, calendar.get(Calendar.DAY_OF_YEAR))
```

### Algorithm

1. **Group events into duplicate clusters**
   - Compare each event against all others using `areNearDuplicates()` helper
   - Track processed event IDs to avoid duplicate groups
   - Single events go in singleton clusters

2. **Select one event per cluster**
   - Singleton clusters: Return the only event
   - Duplicate clusters: Use deterministic rotation
   - Selection seed: `(dayOfYear + cluster.sumOf { it.id }) % cluster.size`

3. **Rotation behavior**
   - Stable within a day (widget refreshes show same events)
   - Changes daily (different duplicate shown tomorrow)
   - Fair over time (each duplicate shown roughly equally)

### Helper Function
```kotlin
private fun areNearDuplicates(a: CalendarEvent, b: CalendarEvent): Boolean {
    // Same type check
    if (a.isAllDay != b.isAllDay) return false

    // Time proximity (15 minutes)
    val timeDiff = kotlin.math.abs(a.startTime - b.startTime)
    if (timeDiff > 900_000) return false

    // Title similarity (case-insensitive)
    val titleA = a.title.lowercase()
    val titleB = b.title.lowercase()
    return titleA == titleB || titleA.contains(titleB) || titleB.contains(titleA)
}
```

## Files to Modify

### 1. WeeklyDisplayLogic.kt
**Location**: `/home/dcar/projects/cal-date-widget/app/src/main/java/ai/dcar/caldatewidget/WeeklyDisplayLogic.kt`

**Changes**:
- Add `filterNearDuplicates()` function after line 131
- Add `areNearDuplicates()` private helper function
- Follow existing code style (object with pure functions, no Android dependencies)

### 2. WidgetDrawer.kt
**Location**: `/home/dcar/projects/cal-date-widget/app/src/main/java/ai/dcar/caldatewidget/WidgetDrawer.kt`

**Changes**:
- Add near-duplicate filter call after line 66 (after declined event filter)
- Pass `calendar.get(Calendar.DAY_OF_YEAR)` for rotation seed

### 3. WeeklyDisplayLogicTest.kt
**Location**: `/home/dcar/projects/cal-date-widget/app/src/test/java/ai/dcar/caldatewidget/WeeklyDisplayLogicTest.kt`

**Changes**: Add 11 new test cases (see Test Coverage section)

## Test Coverage

Add comprehensive unit tests following existing pattern (backtick test names):

### Basic Functionality
1. `filterNearDuplicates returns original list when no duplicates`
2. `filterNearDuplicates returns empty list for empty input`
3. `filterNearDuplicates handles single event`

### Duplicate Detection
4. `filterNearDuplicates hides event with same start time and title`
5. `filterNearDuplicates matches events within 15 minute window`
6. `filterNearDuplicates uses case-insensitive title matching`
7. `filterNearDuplicates matches when one title contains other`

### Non-Duplicates
8. `filterNearDuplicates keeps events beyond 15 minute window`
9. `filterNearDuplicates keeps all-day separate from timed events`
10. `filterNearDuplicates keeps events with different titles`

### Advanced Cases
11. `filterNearDuplicates handles three-way duplicates`
12. `filterNearDuplicates rotates selection based on day of year`

### Test Helper
```kotlin
private fun createEvent(
    id: Long,
    title: String,
    startTime: Long,
    isAllDay: Boolean = false
) = CalendarEvent(id, title, startTime, startTime + 3600000, 0, isAllDay, false, 0)
```

## Edge Cases Handled

- **Zero/one events**: Return unchanged
- **Multi-way duplicates**: 3+ events at same time handled by modulo rotation
- **Cross-day duplicates**: Time window prevents matching across days
- **All-day vs timed**: Explicit check prevents mixing
- **Null/empty titles**: Handle gracefully with lowercase() on empty strings
- **Performance**: O(n²) grouping acceptable for typical event counts (10-50)

## Configuration

**Initial**: Hardcoded, always enabled (no settings toggle)
- Time window: 15 minutes (reasonable default)
- Selection: Day-based rotation (stable but changing)

**Future** (if user feedback indicates need):
- Add `hideNearDuplicates: Boolean` to `WidgetSettings` in PrefsManager.kt
- Add toggle to WeeklyConfigActivity.kt settings UI

## Verification Steps

### 1. Unit Tests
```bash
./gradlew testDebugUnitTest --tests WeeklyDisplayLogicTest
```
All 12+ new tests should pass.

### 2. Manual Testing on Device
```bash
# Build and install
./gradlew installDebug

# Force widget update
~/Library/Android/sdk/platform-tools/adb shell am broadcast \
  -a android.appwidget.action.APPWIDGET_UPDATE \
  -n ai.dcar.caldatewidget/.WeeklyWidgetProvider

# Capture screenshot
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/screenshot.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/screenshot.png /tmp/widget_screenshot.png

# View debug logs
~/Library/Android/sdk/platform-tools/adb logcat -s WeeklyWidget:D
```

### 3. Test Scenarios

**Before fix**: Two events at 11:00 AM both show
**After fix**: Only one 11:00 AM event shows

**Additional scenarios**:
- Events at 10:50 AM and 11:00 AM (within 10 min) → One hidden
- Events at 10:00 AM and 11:00 AM (60 min apart) → Both shown
- "Meeting" at 11:00 and "Meeting - Updated" at 11:00 → One hidden
- "Meeting" and "Lunch" at 11:00 → Both shown (different titles)
- All-day "Holiday" and timed "Meeting" → Both shown (different types)

**Rotation test**:
- Check widget today → Event A shown
- Wait 24 hours (or change system date)
- Force update → Event B shown (rotated)

### 4. Regression Testing

Ensure existing behavior unchanged:
- Start time display logic still works
- Event clipping still equitable
- Declined event filtering still works
- Week start day preference respected
- Calendar selection works

Run full test suite:
```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
```

## Implementation Notes

- **No Android dependencies**: `filterNearDuplicates()` uses only Kotlin stdlib (testable with JUnit)
- **Follows existing patterns**: Same structure as `shouldDisplayEventOnDay()`, `shouldShowStartTimes()`
- **Logging**: Consider adding debug logs to track which events are filtered (match existing WeeklyWidget:D tag)
- **Performance**: Grouping is O(n²) but acceptable for typical 10-50 events per week

## Success Criteria

✅ Unit tests pass (12+ new tests)
✅ User's duplicate 11 AM events reduced to one
✅ Rotation changes daily (deterministic but varying)
✅ No regression in existing widget behavior
✅ Simple implementation (no new settings UI needed)
