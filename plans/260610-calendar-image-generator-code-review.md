# CalendarImageGenerator Code Review Implementation Plan

## Source
Code review of `app/src/main/java/ai/dcar/caldatewidget/CalendarImageGenerator.kt`

## Phase 1: Bug Fixes & Quick Wins
- **#2** Fix paint mutation in `drawHeaderWithTomorrowIndicator` — copy paint before mutating so `dayHeaderPaint.textSize` isn't corrupted for subsequent columns
- **#3** Remove redundant `if (hasPermission)` block in `drawDailyCalendar` (lines 194-216) — permission already checked and early-returned at line 174
- **#1** Fix inconsistent indentation on lines 88-91 (globalDayMillisList continuation)
- **#12** Remove unused `heightPx` parameter from `calculateNumberOfDays`

**Commit**, then proceed.

## Phase 2: String Extraction
- **#5** Move `"Permission Required\nTap to grant"` in `drawPermissionRequired` to `strings.xml`
- **#6** Move `createErrorBitmap` error text to `strings.xml`, improve layout for small widgets

**Commit**, then proceed.

## Phase 3: Code Quality
- **#9** Name magic numbers: rainbow indicator scales (0.62f, 0.35f, 0.05f, 0.12f, 18f, 44f, 10f scale-down floor, 20 max iterations), CONTENT_TOP, LEFT_PADDING, COL_WIDTH_PADDING, cellWidthDp
- **#4** Cache `dayFormatEEE`/`dayFormatEEEd` — pass them in or compute once rather than per frame
- **#8** Tighten `timeFormat` parameter type to `SimpleDateFormat` in `renderDayColumns` and `renderColumnEvents`
- **#10** Add clarifying comment on `computeAdjustedWeights` day-end calculation (event filtering, not day counting, so +DAY_MILLIS is acceptable)
- **#11** Standardize log tags — use a single companion TAG constant per class

**Commit**, then proceed.

## Phase 4: Structural Refactor
- **#7** Split `CalendarImageGenerator` (784 lines) into:
  - `WeeklyWidgetRenderer` — `drawWeeklyCalendar`, weekly header, weekly-specific logic
  - `DailyWidgetRenderer` — `drawDailyCalendar`, daily header, tomorrow indicator, daily-specific logic
  - `WidgetColumnRenderer` — shared `renderDayColumns`, `renderColumnEvents`, `computeAdjustedWeights`
  - `CalendarImageGenerator` — keeps `calculateWidgetSize`, `drawBackground`, `startOfDayMillis`, `daysBetween`, `fetchAndFilterEvents`, `drawPermissionRequired`, `createErrorBitmap`, `calculateStartDate` as shared utilities; delegates to renderers
- Update all callers (widget providers) to use new renderer classes
- Update imports in test files as needed

**Commit**, then done.
