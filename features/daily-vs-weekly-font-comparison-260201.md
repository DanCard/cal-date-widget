# Font Sizing: Daily vs Weekly Widget

This document compares the font sizing logic between the daily and weekly calendar widgets.

## Overview

Both widgets use the same core algorithm but differ in key parameters that affect final text size.

## Core Algorithm (Shared)

Both widgets use `WidgetRenderingHelper.calculateOptimalFontScale()` which:

1. **Binary search** over scale range 0.7 to 1.5
2. **Measures total height** of all events at each candidate scale
3. **Returns largest scale** that fits within available column height
4. **Per-column optimization** - each day column is sized independently

**Base text size:** `48f * eventScale`

**Code location:** `WidgetRenderingHelper.kt:108-198`

## Key Differences

| Aspect | Weekly Widget | Daily Widget |
|--------|---------------|--------------|
| Number of columns | Always 7 days | 1-7 days (based on widget width) |
| Past event scale factor | 0.7f (70%) | 0.8f (80%) |
| Header text (today) | Fixed 80px | Dynamic: `colWidth * 0.3f` (30-70px) |
| Header text (other) | Fixed 40px | Dynamic: `colWidth * 0.25f` (30-70px) |

### Column Count Impact

The daily widget calculates the number of columns dynamically:

```kotlin
// WidgetDrawer.kt:481-488
val cellWidthDp = 92f
val numDays = ((widthDp / cellWidthDp) + 0.5f).toInt().coerceIn(1, 7)
```

With fewer columns:
- Each column is proportionally wider
- More horizontal space for text
- Font scaling algorithm finds higher optimal scale
- Result: **larger text**

### Past Event Scaling

Past events (already ended) get an additional scale reduction:

```kotlin
// WidgetDrawer.kt
// Weekly (line 152-153):
optimalFontScale * 0.7f  // 70% of optimal

// Daily (line 364-365):
optimalFontScale * 0.8f  // 80% of optimal
```

### Header Sizing

**Weekly** uses fixed sizes regardless of column width:
```kotlin
// WidgetDrawer.kt:107-114
if (i == todayIndex) {
    paints.dayHeaderPaint.textSize = 80f
} else {
    paints.dayHeaderPaint.textSize = 40f
}
```

**Daily** scales headers with column width:
```kotlin
// WidgetDrawer.kt:315-319
val headerTextSize = if (i == todayIndex) {
    colWidth * 0.3f
} else {
    colWidth * 0.25f
}.coerceIn(30f, 70f)
```

## Column Weights (Identical)

Both widgets use the same column weight formula:

| Column Type | Weight |
|-------------|--------|
| Past days | 0.6f |
| Today | 2.0f |
| Future (day +1) | 1.5f |
| Future (day +2) | 1.35f |
| Future (day +3+) | Decays by 0.15f per day, min 0.7f |

**Code locations:**
- `WeeklyDisplayLogic.kt:47-70`
- `DailyDisplayLogic.kt:6-21`

## Why Daily Text Appears Larger

Given identical widget dimensions, the daily widget typically shows larger text because:

1. **Fewer columns** = wider columns = more horizontal text space
2. **Higher past event scale** (80% vs 70%) = larger past events
3. **Dynamic headers** scale with available space

### Example Comparison

For a 400dp wide widget:

| Widget | Columns | Avg Column Width | Typical Scale |
|--------|---------|------------------|---------------|
| Weekly | 7 | ~57dp | 0.8-1.0 |
| Daily | 4 | ~100dp | 1.2-1.5 |

## Minimum Scale Enforcement

Both widgets enforce minimum event scales:

```kotlin
// WidgetDrawer.kt (both widgets)
val minEventScale = if (isLessInteresting) 0.5f else 0.7f
if (eventScale < minEventScale) eventScale = minEventScale
```

"Less interesting" events are those that are:
- Declined
- Status = INVITED (not yet responded)

## Related Files

| File | Purpose |
|------|---------|
| `WidgetDrawer.kt` | Renders both widgets, applies font scales |
| `WidgetRenderingHelper.kt` | Binary search algorithm, height measurement |
| `WeeklyDisplayLogic.kt` | Weekly-specific column weights and logic |
| `DailyDisplayLogic.kt` | Daily-specific column weights and auto-advance |
