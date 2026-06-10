# Session Log: Two-Line Mode Implementation & Multi-Row Bug Fixes
**Date:** Saturday, May 30, 2026
**Status:** Completed & Pushed to `main`

## Goal
Implement a "Two-Line Mode" option for the Daily and Weekly widgets that doubles the displayed date range when the widget is tall enough. Wide widgets (Weekly) should show two weeks (14 days), while narrow widgets (Daily) should serialize dates into two rows.

## Implementation Details

### 1. Settings & UI
- Added `twoLineModeEnabled` to `PrefsManager.WidgetSettings`.
- Updated `PrefsManager.kt` to persist and delete the new setting.
- Added a "Two-line mode (when tall enough)" CheckBox to `activity_daily_config.xml` and `activity_weekly_config.xml`.
- Updated `BaseWidgetConfigActivity.kt` to wire the CheckBox and handle undo/redo state.

### 2. Multi-Row Rendering Engine
- **Vertical Offset Support:** Refactored `renderDayColumns` and `renderColumnEvents` in `CalendarImageGenerator.kt` to accept a `topOffset`. This allows rendering multiple horizontal bands of content on a single Canvas.
- **Header Alignment:** Updated `drawDailyDayHeader` and `drawWeeklyDayHeader` to respect the vertical offset for multi-row positioning.
- **Row-Level Iteration:** Refactored `drawWeeklyCalendar` and `drawDailyCalendar` to loop through rows (1 or 2) based on height and user preference.

### 3. Critical Bug Fixes
- **All-Day Event Filtering:** Fixed a bug in `WeeklyDisplayLogic.shouldDisplayEventOnDay` where all-day events were filtered out if the range (e.g., an entire week row) started before the event. It now correctly handles multi-day range checks.
- **Day Repetition:** Corrected `drawWeeklyCalendar` to ensure Row 2 starts exactly 7 days after Row 1, avoiding redundant "shifted" future days in the second row.
- **Inconsistent Zooming:** Fixed an issue where "Today" appeared too narrow in multi-row mode. Reverted to calculating column weights **per row** so that the `2.0` today-weight is only compared against the other 6 columns in its row, maintaining visual prominence.

## Verification Results

### Unit Tests
- **DailyDisplayLogicTest.kt (New):** Verified grid calculation logic for various heights and mode states.
- **WeeklyDisplayLogicTest.kt (Updated):** Added a robust reproduction test for the all-day event filtering bug.
- **Result:** All 33 unit tests passed (`./gradlew testDebugUnitTest`).

### Emulator Testing
- Successfully reinstalled debug build and triggered refreshes via ADB.
- Verified that "Today" is the widest column and Row 2 perfectly follows Row 1.
- Verified all-day events (e.g., "Children with Dad") now appear correctly in Row 1.

## Commit Summary
- **Hash:** `23b676a`
- **Files Changed:** 10 files (244 insertions, 134 deletions).
