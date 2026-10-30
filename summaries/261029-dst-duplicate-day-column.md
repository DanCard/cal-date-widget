# Session Summary: DST Duplicate "Sun 1" Column Fix (2026-10-29)

## Bug Report

On the Fold4 (SM-F936U1), the weekly widget showed **two "Sun 1" columns**:

```
Before:  Fri 30 | Sat 31 | Sun 1 | Sun 1 | Mon 2 | Tue 3 | Wed 4
After:   Fri 30 | Sat 31 | Sun 1 | Mon 2 | Tue 3 | Wed 4 | Thu 5
```

## Root Cause

Sun Nov 1, 2026 is the US DST "fall back" day, which is **25 hours long**. Column dates
were computed as `startMillis + n * 24h`:

- `Sun 1 00:00 PDT + 24h = Sun 1 23:00 PST` → the next column also formatted as "Sun 1".
- Every later column sat at 23:00 of the *previous* day. The header and the event window
  `[23:00, 23:00)` were both one day behind. For example, Tuesday's "Empty small trash cans"
  event rendered under the "Mon 2" header.

Day-end boundaries (`dayMillis + DAY_MILLIS`) had the same flaw. On a 25-hour day they
dropped the final hour of events.

## Fix

Added `WeeklyDisplayLogic.addDays(millis, days)`, which steps days with
`Calendar.add(DAY_OF_YEAR, n)` in the default timezone. All fixed-24h day stepping now
goes through it:

| File | Change |
|------|--------|
| `WeeklyDisplayLogic.kt` | New `addDays()`; `getEffectiveDayMillis()` uses it (primary bug) |
| `WeeklyWidgetRenderer.kt` | Two-week offset (`w * 7`) and today-end check |
| `DailyWidgetRenderer.kt` | `todayEnd`, `tomorrowStart`, column day list |
| `WidgetColumnRenderer.kt` | Three `dayEnd` computations; removed the comment claiming `+DAY_MILLIS` was "safe" |
| `DailyAutoAdvanceScheduler.kt` | `todayEnd` |
| `CalendarRepository.kt` | Fetch-window `endMillis` |

Intentionally unchanged: `getLocalJulianDay()` still uses `DAY_MILLIS`. It applies the zone
offset first and then computes an epoch-day, which is correct.

## Testing

- New regression test in `WeeklyDisplayLogicTest`:
  `getEffectiveDayMillis yields distinct local midnights across DST fall-back`. It pins
  `America/Los_Angeles`, starts on Fri Oct 30 2026, and asserts each column lands on local
  midnight with days `[30, 31, 1, 2, 3, 4, 5]`.
- The test was confirmed to **fail on the old code** (an interrupted stash left the fix out
  during one run) and to pass with the fix.
- Full suite: 204 tests pass (`./gradlew testDebugUnitTest`).
- Installed on the Fold4 (`ANDROID_SERIAL=RFCT70G352Y ./gradlew installDebug`). Screenshot
  confirmed correct headers and event placement.

## Notes / Gotchas

- Two devices were connected (Pixel 7 Pro `2A191FDH300PPW` and Fold4 `RFCT70G352Y`), so
  adb needs `-s` or `ANDROID_SERIAL`.
- The shell `APPWIDGET_UPDATE` broadcast is blocked on Android 14+. `pkill` of the Samsung
  launcher was also denied. The reinstall itself triggered the widget refresh.
- An interrupted `git stash … && … && git stash pop` left the fix stashed. If a combined
  command is interrupted, check `git stash list`.

## Status

The changes are **uncommitted** on `main`, and no release has been cut yet.

## Lesson

A millisecond "day" is a duration, not a calendar day. For midnight-to-midnight stepping,
always use `Calendar.add` / `LocalDate.plusDays`. Spring-forward (23h days) hides the bug
because labels still look right. Only fall-back (25h days) pushes a boundary back across
midnight and makes it visible.
