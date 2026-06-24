# Align columns in two-week (two-line) view via a shared lensing curve

## Context

The weekly widget applies a **fisheye / focus+context distortion** ("lensing"): today's
column is magnified (weight 2.0×) and columns further from today shrink. Over a single row
this reads fine. But in **two-week mode** (`twoLineModeEnabled`, two stacked 7-column bands
when the widget is ≥220dp tall), the two rows use **different** width profiles, so the same
weekday lands at a different x-position and width in row 1 vs row 2. The vertical separators
zig-zag between bands — that mismatch is what looks "odd."

Two independent causes of the misalignment, both in `WeeklyWidgetRenderer.kt`:

1. **Different base curves per band.** The render loop passes `rowTodayIndex = todayIndexInWeek`
   for band 0 but `-1` for band 1 (`WeeklyWidgetRenderer.kt:60`). With `todayIndex = -1`,
   every column of band 1 falls into `getColumnWeights`' "future" branch, producing a monotonic
   taper `[1.5, 1.35, 1.20, 1.05, 0.90, 0.75, 0.70]` — no magnified-today spike — vs band 0's
   `[0.6, 0.6, 0.6, 2.0, 1.5, …]`.
2. **Per-band empty-column shrink.** `computeAdjustedWeights` (`WidgetColumnRenderer.kt:204`)
   multiplies any event-empty column by `EMPTY_COLUMN_WEIGHT_FACTOR` (0.35), called separately
   for each band with that band's own events. A weekday busy in week 1 but empty in week 2
   gets different widths in the two rows even if the base curve matched.

**Decision (confirmed with user):** keep the lensing but make both bands share one column-width
profile so the grid aligns vertically (today's weekday stays magnified in both rows). Apply this
**only in two-week mode** — the single-week render must stay exactly as it is today. The yellow
"today" highlight must remain on the real today only (band 0).

Intended outcome: in two-week view, every vertical separator in row 2 lines up directly under
its counterpart in row 1; today's weekday column is the widest in both rows; single-week view
is unchanged.

## Approach

### 1. Compute one shared width profile for all bands — `WeeklyWidgetRenderer.kt`

In `draw()`, replace the per-iteration weight computation (currently lines 60–63 inside the
`for (w in 0 until weeks)` loop) with a single shared array computed **once** before the loop:

- `sharedBaseWeights = WeeklyDisplayLogic.getColumnWeights(todayIndexInWeek, 7)` — band 0's
  curve, with the 2.0× spike at today's weekday. Used for every band.
- Apply the empty-column shrink **across all weeks** (see new helper below) so a weekday is only
  narrowed when it is empty in *every* stacked week — preserving alignment while keeping the
  space-saving compaction for genuinely-empty weekdays.

Inside the loop, keep `rowTodayIndex = if (w == 0) todayIndexInWeek else -1` **only** for the
rendering/highlight/font-scale path (it is passed separately to `renderDayColumns` as
`todayIndex`), but pass the **shared** weight array as `weights` for both bands. Weights and
today-index are already separate parameters to `renderDayColumns`, so decoupling them is clean.

Net effect by mode:
- **weeks == 1:** `sharedBaseWeights` == `getColumnWeights(todayIndexInWeek, 7)` and the new
  helper reduces to the old `computeAdjustedWeights` over the single band → **identical output,
  single-week unaffected.**
- **weeks == 2:** both bands use the same magnified-today profile and the same empty mask →
  separators align; next week's same-weekday column inherits today's wide width (but is not
  highlighted, since `isToday` is date-based).

### 2. Add a cross-week empty-column helper — `WidgetColumnRenderer.kt`

Add an internal, `@VisibleForTesting` function next to `computeAdjustedWeights` (~line 204) that
shrinks a column only when it is event-empty in **all** stacked weeks:

```kotlin
internal fun computeAlignedAdjustedWeights(
    events: List<CalendarEvent>,
    baseWeights: FloatArray,          // size 7
    allDayMillis: List<Long>,         // size 7 * weeks, week-major
    weeks: Int
): FloatArray {
    val adjusted = baseWeights.copyOf()
    for (i in 0 until 7) {
        val anyWeekHasEvents = (0 until weeks).any { w ->
            val dayMillis = allDayMillis[w * 7 + i]
            events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayMillis + DAY_MILLIS) }
        }
        if (!anyWeekHasEvents) adjusted[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
    }
    return adjusted
}
```

Reuses existing `shouldDisplayEventOnDay`, `EMPTY_COLUMN_WEIGHT_FACTOR`, and `DAY_MILLIS`.
For `weeks == 1` it is identical to `computeAdjustedWeights`, so the existing single-week call
site can either keep `computeAdjustedWeights` or route through this — recommend `WeeklyWidgetRenderer`
calls the new helper for both modes (with `globalDayMillisList` and `weeks`) so there is one path.

## Critical files

- `app/src/main/java/ai/dcar/caldatewidget/WeeklyWidgetRenderer.kt` — hoist shared weights out of
  the band loop (lines ~57–79); pass shared `weights`, keep per-band `rowTodayIndex`.
- `app/src/main/java/ai/dcar/caldatewidget/WidgetColumnRenderer.kt` — add
  `computeAlignedAdjustedWeights` (~line 204, beside `computeAdjustedWeights`).
- `app/src/main/java/ai/dcar/caldatewidget/WeeklyDisplayLogic.kt` — **no change**;
  `getColumnWeights` is reused as-is.

## What deliberately does NOT change

- `getColumnWeights` formula (still 0.6 / 2.0 / 1.5-decay) — strength of the lens is unchanged;
  we only make both rows share it.
- Single-week (one-band) rendering — provably identical output.
- "Today" highlight semantics — still keyed on the real date, so next week's same-weekday column
  is wide but not yellow.

## Testing / verification

1. **Unit tests** (`WeeklyDisplayLogicTest.kt` / a renderer test): add cases for
   `computeAlignedAdjustedWeights`:
   - `weeks == 1` returns the same array as `computeAdjustedWeights` (regression guard for the
     single-week path).
   - `weeks == 2`: a weekday with events in only one of the two weeks is **not** shrunk; a weekday
     empty in both weeks **is** shrunk by 0.35.
   - Both bands resolve to the same `weights` array (assert the shared array is reused, e.g. via a
     renderer-level check or by asserting the base profile equals `getColumnWeights(todayIndexInWeek, 7)`).
   Run: `./gradlew testDebugUnitTest`.
2. **On-device visual check** (Fold4 / SM-F936U1): enable two-line mode, place a tall (≥220dp)
   weekly widget, and confirm the vertical separators in row 2 sit directly under row 1, with
   today's weekday widest in both rows and highlighted only in row 1.
   - Build/install: `./gradlew installDebug`
   - Screenshot: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/widget_screenshot.png`
   - Compare a busy-week-1 / empty-week-2 weekday to confirm it stays full width (aligned), and a
     both-weeks-empty weekday to confirm it compacts in both rows symmetrically.
