# Text Overlap Bug Fix

## Problem
Text overlapping between consecutive events on the current day, especially when past events use smaller font (0.8x scale).

## Root Cause
In `WeeklyWidgetProvider.kt:298-305`, the `consumedHeight` calculation for past events used a calculated heuristic (`lineHeight = textSize * 1.2f`) instead of the actual measured height (`layout.height`) from StaticLayout:

```kotlin
val consumedHeight = when {
    i != todayIndex -> layout.height.toFloat() + spacing
    isPastTodayEvent -> lineHeight + spacing // ← BUG: Uses calculated value
    else -> {
        layout.height.toFloat() + spacing
    }
}
```

The issue: `lineHeight` (calculated as `textSize * 1.2f`) doesn't always match the actual `layout.height` returned by `StaticLayout.Builder`. When `layout.height > lineHeight`, using `lineHeight + spacing` causes the next event to start before the current event finishes, creating overlap.

## Fix
Changed to always use `layout.height` for consistency:

```kotlin
val consumedHeight = when {
    i != todayIndex -> layout.height.toFloat() + spacing
    isPastTodayEvent -> layout.height.toFloat() + spacing
    else -> {
        layout.height.toFloat() + spacing
    }
}
```

Or more simply:
```kotlin
val consumedHeight = layout.height.toFloat() + spacing
```

This ensures the consumed height always matches what's actually drawn.

## Test File
Created `app/src/test/java/ai/dcar/caldatewidget/TextOverlapTest.kt` with tests to verify:

1. **`past event on current day consumed height uses layout height not line height`**: Verifies the fixed approach
2. **`consecutive past events on current day do not overlap`**: Ensures proper spacing between events
3. **`past event followed by current event on current day does not overlap`**: Tests mixed font sizes
4. **`overlapping detection when past events have smaller font than current events`**: Demonstrates the bug scenario
5. **`bug demonstration lineHeight approach can cause overlap`**: Shows when the old approach would fail

## Why the Bug Occurred
`StaticLayout` height is calculated based on actual font metrics:
```kotlin
height = fontMetrics.bottom - fontMetrics.top
```

With `textSize = 38.4f` (48f * 0.8f):
- `lineHeight` = 38.4f * 1.2f = 46.08f (fixed heuristic)
- `layout.height` ≈ 42.0f (actual measured, varies by font)

When `layout.height > lineHeight`, using `lineHeight` causes overlap.

## Impact
- **Before**: Past events could overlap with following events
- **After**: All events use consistent height calculation, preventing overlap
