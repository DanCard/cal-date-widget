# Plan: Two-Week Option for the Weekly Widget

**Status:** Proposed
**Scope (chosen):** Grid only, height-gated. Stack a second 7-column week-row when the
widget is tall *and* wide enough; otherwise fall back to the current single week.
A 14-day vertical agenda for the tall-and-narrow case is explicitly **out of scope**
(noted as the natural follow-up).

---

## 1. Goal

Add a user setting that, when enabled, renders **two stacked weeks** (this week + next
week) in the weekly widget — but only when there is enough vertical room for two legible
week bands and enough width for 7 legible columns. The decision must be self-healing:
resizing the widget smaller silently reverts to one week on the next draw.

## 2. Background / Why this is cheap

- The weekly widget is a **single Canvas bitmap**, not nested Android views. "Two weeks"
  is not a layout change — it is calling the existing column renderer twice into two
  horizontal **bands** of the same bitmap.
- `calculateWidgetSize()` (`CalendarImageGenerator.kt:529`) already resolves live
  `widthDp`/`heightDp` every draw (from `appWidgetSizes[0]`, falling back to
  `MAX_HEIGHT`/`MIN_WIDTH`). Both dimensions are available before drawing.
- The **daily** widget already does a height-gated day-count decision
  (`if (heightDp >= 100f)` at `CalendarImageGenerator.kt:221`). This feature is the same
  pattern applied vertically.
- The per-week fixed cost is the header band: `CONTENT_TOP = 80f` **pixels**
  (`CalendarImageGenerator.kt:33`). Two weeks = two headers.

## 3. The gate ("enough vertical room")

Two-dimensional gate, evaluated each draw:

```
showTwoWeeks = settings.twoWeeksEnabled
            && heightDp >= TWO_WEEK_MIN_HEIGHT_DP   // tall enough for 2 bands
            && widthDp  >= SEVEN_COL_MIN_WIDTH_DP    // wide enough for 7 legible columns
```

Proposed constants (tunable, defined alongside existing thresholds):

| Constant | Value | Rationale |
|---|---|---|
| `SINGLE_WEEK_MIN_DP` | ~110dp | Header (~50dp from `CONTENT_TOP=80px`) + ~2 event rows. Matches the widget's own default `minHeight` of 110dp. |
| `TWO_WEEK_MIN_HEIGHT_DP` | 220f | `2 * SINGLE_WEEK_MIN_DP`. |
| `SEVEN_COL_MIN_WIDTH_DP` | 250f | 7 columns × `cellWidthDp = 92f` floor used by `calculateNumberOfDays` (`CalendarImageGenerator.kt:583`), rounded. |

**Tall-and-narrow handling:** such a widget passes the height check but **fails the width
check**, so it stays on one week. This is deliberate — a 7-column grid has a width floor;
stacking a second narrow row would just produce two rows of unreadable columns. (The
better answer for that shape is a rotated 14-day vertical agenda — deferred.)

## 4. Decision logic lives in WeeklyDisplayLogic (testable)

Add a pure helper so the Canvas code stays free of branching:

```kotlin
// WeeklyDisplayLogic.kt
/** Returns 2 when the two-week layout should render, else 1. */
fun weeksToShow(
    enabled: Boolean,
    widthDp: Float,
    heightDp: Float,
    minTwoWeekHeightDp: Float = 220f,
    minSevenColWidthDp: Float = 250f
): Int =
    if (enabled && heightDp >= minTwoWeekHeightDp && widthDp >= minSevenColWidthDp) 2 else 1
```

This mirrors how `shouldShowStartTimes` / `getEffectiveDayMillis` are already factored —
the decision is unit-tested; the renderer just consumes the result.

## 5. Implementation steps

### 5.1 PrefsManager.kt
- Add `twoWeeksEnabled: Boolean = false` to the `WidgetSettings` data class.
- Read it in `loadSettings()` and persist it in `saveSettings()` (new pref key, e.g.
  `KEY_TWO_WEEKS`).

### 5.2 WeeklyConfigActivity.kt
- Add a checkbox bound to `twoWeeksEnabled`.
- Label sets expectations: **"Show two weeks (when widget is tall enough)."**
- Wire into the existing save/undo flow (`SettingsStateManager`) like other toggles.

### 5.3 CalendarImageGenerator.drawWeeklyCalendar (the core change)
- Compute `widthDp`/`heightDp` from `size` + `displayMetrics` (same as daily, line 217–218).
- `val weeks = WeeklyDisplayLogic.weeksToShow(settings.twoWeeksEnabled, widthDp, heightDp)`.
- If `weeks == 2`:
  - Fetch **14 days** from `todayMillis` instead of 7 (`fetchAndFilterEvents(..., numDays=14)`).
  - Compute `bandHeight = size.heightPx / 2`.
  - Render band 1 (this week, days 0..6) into `y in [0, bandHeight)`.
  - Render band 2 (next week, days 7..13) into `y in [bandHeight, height)`.
  - Band 2 column day-millis = `startMillis + (7 + i) * DAY_MILLIS`. The existing
    `getEffectiveDayMillis` past-day shift applies **only to band 1** (this week);
    band 2 is already all-future so no shift / no past-event dimming.
- If `weeks == 1`: unchanged from today.

### 5.4 Thread a vertical offset through the renderer (cross-cutting)
`renderDayColumns` and the per-column draw currently assume canvas-absolute pixels:
- `CONTENT_TOP` top of content, `0f` top in `canvas.clipRect`/`drawLine`
  (`CalendarImageGenerator.kt:320, 330, 381`), and `availableHeight = height - CONTENT_TOP`.
- **Change:** add a `top: Float = 0f` (band origin) and use `bandHeight` as the height the
  column math sees. Concretely:
  - `clipRect(currentX, top + CONTENT_TOP, currentX + colWidth, top + bandHeight)`
  - vertical lines drawn from `top` to `top + bandHeight`
  - `availableHeight = bandHeight - CONTENT_TOP`
  - header y-positions offset by `top`
- Default `top = 0f` keeps single-week behavior byte-identical.

### 5.5 Headers
- Both bands use the existing `"EEE d"` format (already disambiguates next-week dates).
- Band 1 today-column highlight unchanged; band 2 has no "today" (todayIndex out of range)
  — verify the highlight logic no-ops cleanly when `todayIndex >= 7` for band 2.

## 6. Tests

### New (WeeklyDisplayLogicTest.kt)
- `weeksToShow` truth table:
  - disabled → 1 regardless of size
  - enabled + tall + wide → 2
  - enabled + tall + narrow (width < 250) → 1  *(the tall-and-narrow case)*
  - enabled + short + wide (height < 220) → 1
  - boundary values at exactly 220dp / 250dp
- Band-2 day-millis: confirm next-week days are `startMillis + (7..13)*day` and that
  `getEffectiveDayMillis` shift does not double-apply to band 2.

### Update (CalendarImageGeneratorTest.kt / Robolectric)
- Render at a two-week-eligible size → assert bitmap produced, two header rows present
  (e.g. two occurrences of the today/next-week header band), no crash.
- Render at a tall-narrow size with toggle on → assert single-week layout (one header band).
- Regression: existing single-week tests must pass unchanged (default `top=0f`).

## 7. Risks / watch-items

- **Fetch window:** two-week mode must fetch 14 days; confirm `CalendarRepository.getEvents`
  handles a 14-day range and that dedup/filter still scale.
- **Clipping math:** every place that reads `height` in column rendering must switch to the
  band height. Grep for `height` usages in `renderDayColumns` and the per-column drawer
  (`CalendarImageGenerator.kt:317–435`) to ensure none are missed — a stray absolute
  `height` will let band 1 content bleed into band 2.
- **Per-band readability:** at exactly 220dp each band is ~110dp incl. header — tight.
  Consider clamping/scaling event text in two-week mode, or raising `TWO_WEEK_MIN_HEIGHT_DP`
  after on-device testing on the Fold4 (SM-F936U1).
- **Self-healing:** since the gate runs every draw, no migration/state needed; an existing
  widget that's too small simply ignores the toggle until resized.

## 8. Out of scope (follow-ups)
- 14-day **vertical agenda** layout for tall-and-narrow widgets (chosen layout by aspect
  ratio). This is the better UX for that shape but needs rotated rendering + new tests.
- Configurable week count (3+ weeks).
