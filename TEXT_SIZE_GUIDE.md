# Dynamic Text Sizing for Weekly Widget

This document explains how text sizes are dynamically calculated in the weekly calendar widget.

## Overview

Text size in the weekly widget is **dynamically calculated** for each column independently based on available space. The widget automatically optimizes font size to fill available space.

## How Dynamic Sizing Works

### Binary Search Algorithm

Each column's optimal font scale is determined using a **binary search algorithm** that iterates 10 times:

1. **Search bounds:** minScale=0.3f, maxScale=1.5f
2. **For each iteration:**
   - Test a mid-scale value
   - Measure total height of all events with that scale
   - If fits: increase minScale to midScale
   - If doesn't fit: decrease maxScale to midScale
3. **Result:** Returns the largest scale that fits within available height

**Code location:** `WeeklyWidgetProvider.kt:429-461`

```kotlin
var minScale = 0.3f
var maxScale = 1.5f
var optimalScale = 1.0f

for (iteration in 0 until 10) {
    val midScale = (minScale + maxScale) / 2f
    val totalHeight = measureTotalHeightForScale(midScale, ...)

    if (totalHeight <= availableHeight) {
        minScale = midScale
        optimalScale = midScale
    } else {
        maxScale = midScale
    }
}
return optimalScale
```

## Text Size Formula

```
Final Text Size = Base Size (48f) × Optimal Scale × Event Scale Modifier
```

### Base Size

**Fixed at 48f pixels** for all event text.
**Location:** `WeeklyWidgetProvider.kt:339`

### Optimal Scale

Calculated independently for **each column**:
- **Range:** 0.3x to 1.5x
- **Empty columns:** Returns 0.5x (smaller for aesthetics)
- **Result:** Different columns may have different font sizes

### Event Scale Modifier

Within each column, events get additional scaling:

**Past events on today:**
- Additional 0.8x multiplier
- Past events remain smaller than current/future on same day
- **Rationale:** Past events are less important

**All other events:**
- No additional multiplier
- Use the optimal scale directly

## Summary of Scaling

| Column Type | Events | Optimal Scale | Past Event Modifier | Final Size (at 48f base) |
|-------------|---------|---------------|-------------------|----------------------------|
| Empty column | 0 | 0.5x | N/A | 24px |
| Past day | N/A | 0.3x - 1.5x | N/A | 14.4px - 72px |
| Today - past events | Yes | 0.3x - 1.5x | 0.8x | 11.5px - 57.6px |
| Today - current events | Yes | 0.3x - 1.5x | N/A | 14.4px - 72px |
| Future day | N/A | 0.3x - 1.5x | N/A | 14.4px - 72px |

### Real-World Example

From device logs (4 columns with events):

```
Day 0 (past):     scale=1.21x → final size: 58px
Day 1 (past):     scale=1.50x → final size: 72px
Day 2 (today):    scale=1.50x → final size: 72px
Day 3 (future):    scale=0.90x → final size: 43px
Day 4 (future):    scale=1.50x → final size: 72px
Day 5 (future):    scale=1.50x → final size: 72px
Day 6 (future):    scale=1.50x → final size: 72px
```

Each column is independently optimized based on its content (number of events) and available space.

## Start Times

Start times are dynamically sized using the same scale as event titles.

**Location:** `WeeklyWidgetProvider.kt:381-410`

```kotlin
val spannable = SpannableString("$timeString $safeTitle")
spannable.setSpan(ForegroundColorSpan(settings.startTimeColor), ...)
spannable.setSpan(object : CharacterStyle() {
    override fun updateDrawState(tp: android.text.TextPaint) {
        tp.setShadowLayer(4f, 2f, 2f, settings.startTimeShadowColor)
    }
}, ...)
```

Start time text inherits font scale from the event, so it scales proportionally.

## Day Headers

Day headers (Mon, Tue, Wed, etc.) have **fixed sizes** independent of dynamic sizing:

**Normal days:**
- Size: 40f
- Color: Light gray
- Weight: Normal

**Today:**
- Size: 80f (2x larger)
- Color: Yellow
- Weight: Bold

**Location:** `WeeklyWidgetProvider.kt:152-158`

## Line Spacing

**Within events (StaticLayout):**
- Set to: `.setLineSpacing(0f, 1f)`
- Effect: Standard font line spacing (no extra padding)

**Between events:**
- Today: `textSize × 0.1f`
- Other days: `textSize × 0.2f`

**Location:** `WeeklyWidgetProvider.kt:443`

## User Settings

### Removed Settings

The following user-controlled settings have been **removed**:
- ❌ Text Size slider (seekbar_text_size)
- ❌ Text Scale preference (textSizeScale)

### Active Settings

Users can still control:
- ✅ Week start day (Sunday, Monday, Saturday, or current day)
- ✅ Text color
- ✅ Shadow color
- ✅ Background color
- ✅ Start time color
- ✅ Start time shadow color
- ✅ Show declined events

**Text size is now fully automatic** - no user control needed.

## Column Widths

Column widths are determined by **column weights** that prioritize today:

**Weights:**
- Past days: 0.6x (narrower)
- Today: 2.0x (widest)
- Future days: Decay from 1.5x to 0.7x

**Location:** `WeeklyWidgetProvider.kt:118-122`

```kotlin
val allWeights = WeeklyDisplayLogic.getColumnWeights(todayIndex, 7)
```

## Performance Considerations

### Binary Search Cost

- **Maximum iterations:** 10 per column
- **Events measured per iteration:** All events in column
- **Typical cost:** 70 StaticLayout builds (7 columns × 10 iterations max)
- **Impact:** Negligible (sub-100ms on modern devices)

### Caching

Dynamic scale is recalculated:
- When widget resized (`onAppWidgetOptionsChanged`)
- When events change (calendar update)
- On periodic updates (every 30 minutes)

No explicit caching needed - recalculation is fast.

## Benefits of Dynamic Sizing

### ✅ Automatic Space Utilization

Text automatically fills available space:
- Fewer events → Larger font
- More events → Smaller font
- Empty columns → 0.5x scale (aesthetic)

### ✅ No User Tuning Needed

No need for manual slider:
- Widget always looks good
- Scales automatically when events change
- Works for any widget size

### ✅ Per-Column Independence

Each column optimized independently:
- Heavy day: Smaller font to fit all events
- Light day: Larger font for better readability
- Natural visual hierarchy based on content density

### ✅ Consistent Behavior

Predictable sizing algorithm:
- Binary search always converges
- Consistent across device sizes
- Testable and deterministic

## Edge Cases

| Scenario | Behavior |
|----------|-----------|
| No events in widget | All columns get 0.5x scale |
| Very narrow widget | Algorithm naturally reduces scale |
| Very tall widget | Algorithm naturally increases scale |
| One column with 20 events | That column gets smaller scale |
| Other columns empty | Empty columns get 0.5x (small) |

## Troubleshooting

### Issue: "Text is too small"

**Possible causes:**
1. Too many events in column
2. Widget is too narrow
3. Available height is limited

**Solutions:**
1. Resize widget to be larger (wider/taller)
2. Use fewer calendar events
3. Adjust min/max bounds in code (lines 448-449)

### Issue: "Text is too large and truncated"

**Possible causes:**
1. Very few events
2. Widget is very tall
3. Algorithm returned max scale (1.5x) and still too big

**Solutions:**
1. Reduce maxScale bound (currently 1.5x) in code
2. Add safety margin to height comparison

### Issue: "Inconsistent font sizes across columns"

**This is expected behavior!** Each column is independently optimized. Columns with fewer events will have larger fonts than columns with many events.

If you want consistency, you can:
1. Use the same optimal scale for all columns
2. Average scales across columns
3. Use minimum scale across all columns

## Code Locations

| Feature | File | Line | Description |
|---------|-------|-------|-------------|
| Binary search | `WeeklyWidgetProvider.kt` | 429-461 | calculateOptimalFontScale() |
| Height measurement | `WeeklyWidgetProvider.kt` | 463-495 | measureTotalHeightForScale() |
| Base paint | `WeeklyWidgetProvider.kt` | 339 | textSize = 48f |
| Event application | `WeeklyWidgetProvider.kt` | 248-251 | Apply optimal scale to events |
| Start time | `WeeklyWidgetProvider.kt` | 381-410 | buildEventText() |
| Config UI | `WeeklyConfigActivity.kt` | REMOVED | Text Size slider removed |
| Settings | `PrefsManager.kt` | REMOVED | textSizeScale removed |

## Migration Notes

### If Upgrading from Previous Version

- **Old behavior:** User controlled text size with slider (0.5x - 2.5x)
- **New behavior:** Automatic sizing based on content and space

User will notice:
- Text sizes may differ from previous manual setting
- Text automatically adjusts when events change
- Slider no longer available in settings

No data migration needed - old textSizeScale values are simply ignored.
